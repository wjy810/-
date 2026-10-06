package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.concurrent.TimeoutException;
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
    void cancellationCommittedWhileClaimWaitsCannotBeOverwrittenByWorker() throws Exception {
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

            Future<Boolean> claim = executor.submit(() -> taskService.claimNext(taskType).isPresent());
            try {
                assertThatThrownBy(() -> claim.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                releaseCancellation.countDown();
            }

            cancellation.get(2, TimeUnit.SECONDS);
            assertThat(claim.get(2, TimeUnit.SECONDS)).isFalse();
            assertThat(tasks.findById(created.id()).orElseThrow().getStatus())
                    .isEqualTo(TaskStatus.CANCELLED.name());
        } finally {
            releaseCancellation.countDown();
            executor.shutdownNow();
        }
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
