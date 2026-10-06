package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.modules.task.domain.TaskStatus;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import com.jobproof.modules.task.infra.AsyncTaskJpaRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "jobproof.worker.in-process=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskStateConcurrencyIT {

    @Autowired TaskService taskService;
    @Autowired AsyncTaskJpaRepository tasks;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void claimSkipsATaskLockedByCancellationAndNeverOverwritesIt() throws Exception {
        String taskType = "LOCK_TEST_" + UUID.randomUUID();
        TaskView created = taskService.create("account-lock-test", taskType, UUID.randomUUID().toString(), "{}");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch cancellationLocked = new CountDownLatch(1);
        CountDownLatch releaseCancellation = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> cancellation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                AsyncTaskEntity task = tasks.findByIdForUpdate(created.id()).orElseThrow();
                cancellationLocked.countDown();
                await(releaseCancellation);
                task.setStatus(TaskStatus.CANCELLED.name());
                task.setFailureReason("concurrent cancellation");
            }));
            assertThat(cancellationLocked.await(2, TimeUnit.SECONDS)).isTrue();

            // The locked row is skipped immediately instead of being waited on (no deadlock, no stall).
            Future<Boolean> claim = executor.submit(() -> taskService.claimNext(taskType).isPresent());
            assertThat(claim.get(2, TimeUnit.SECONDS)).isFalse();
            releaseCancellation.countDown();

            cancellation.get(2, TimeUnit.SECONDS);
            assertThat(taskService.claimNext(taskType)).isEmpty();
            assertThat(tasks.findById(created.id()).orElseThrow().getStatus())
                    .isEqualTo(TaskStatus.CANCELLED.name());
        } finally {
            releaseCancellation.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentClaimersNeverTakeTheSameTask() throws Exception {
        String taskType = "PARALLEL_CLAIM_" + UUID.randomUUID();
        TaskView first = taskService.create("account-parallel", taskType, UUID.randomUUID().toString(), "{}");
        taskService.create("account-parallel", taskType, UUID.randomUUID().toString(), "{}");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> holder = executor.submit(() -> transaction.execute(status -> {
                String id = tasks.lockNextClaimable(taskType, java.time.Instant.now()).orElseThrow().getId();
                firstLocked.countDown();
                await(releaseFirst);
                return id;
            }));
            assertThat(firstLocked.await(2, TimeUnit.SECONDS)).isTrue();
            Future<String> other = executor.submit(() -> taskService.claimNext(taskType).map(AsyncTaskEntity::getId).orElse(null));
            String otherId = other.get(2, TimeUnit.SECONDS);
            releaseFirst.countDown();
            String heldId = holder.get(2, TimeUnit.SECONDS);
            assertThat(heldId).isEqualTo(first.id());
            // MySQL hands the second claimer the next free task; H2 may return none. Never the held one.
            assertThat(otherId).isNotEqualTo(heldId);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void anExpiredLeaseIsReclaimedButALiveForeignLeaseCannotBeFinishedHere() {
        String taskType = "LEASE_TEST_" + UUID.randomUUID();
        TaskView expired = taskService.create("account-lease", taskType, UUID.randomUUID().toString(), "{}");
        TaskView live = taskService.create("account-lease", taskType, UUID.randomUUID().toString(), "{}");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            AsyncTaskEntity dead = tasks.findById(expired.id()).orElseThrow();
            dead.setStatus(TaskStatus.RUNNING.name());
            dead.setLeaseOwner("crashed-host#1#deadbeef");
            dead.setLeaseExpiresAt(java.time.Instant.now().minusSeconds(60));
            AsyncTaskEntity foreign = tasks.findById(live.id()).orElseThrow();
            foreign.setStatus(TaskStatus.RUNNING.name());
            foreign.setLeaseOwner("other-host#2#cafebabe");
            foreign.setLeaseExpiresAt(java.time.Instant.now().plusSeconds(600));
        });

        AsyncTaskEntity reclaimed = taskService.claimNext(taskType).orElseThrow();
        assertThat(reclaimed.getId()).isEqualTo(expired.id());
        assertThat(reclaimed.getCheckpointCode()).isEqualTo("RESUMING");
        assertThat(reclaimed.getLeaseOwner()).isNotEqualTo("crashed-host#1#deadbeef");
        assertThat(taskService.claimNext(taskType)).isEmpty();

        assertThat(taskService.markSucceeded(live.id(), "stolen", "{}")).isFalse();
        assertThat(tasks.findById(live.id()).orElseThrow().getStatus()).isEqualTo(TaskStatus.RUNNING.name());
        assertThat(taskService.markSucceeded(reclaimed.getId(), "done", "{}")).isTrue();
        assertThat(tasks.findById(reclaimed.getId()).orElseThrow().getLeaseOwner()).isNull();
    }

    @Test
    void cancelledTaskRejectsLateSuccessAndFailureTransitions() {
        TaskView created = taskService.create(
                "account-late-terminal", "LATE_TERMINAL_TEST", UUID.randomUUID().toString(), "{}");

        TaskView cancelled = taskService.cancel("account-late-terminal", created.id());

        assertThat(cancelled.status()).isEqualTo(TaskStatus.CANCELLED.name());
        assertThat(taskService.markSucceeded(created.id(), "late-result", "{}"))
                .isFalse();
        assertThat(taskService.markFailed(created.id(), "LATE_FAILURE", "late failure"))
                .isFalse();
        assertThat(tasks.findById(created.id()).orElseThrow().getStatus())
                .isEqualTo(TaskStatus.CANCELLED.name());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release cancellation transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to release cancellation transaction", exception);
        }
    }
}
