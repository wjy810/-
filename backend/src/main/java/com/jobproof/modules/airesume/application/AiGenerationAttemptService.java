package com.jobproof.modules.airesume.application;

import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGenerationAttemptService {

    private final Map<String, State> active = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final ClockPort clock;

    public AiGenerationAttemptService(JdbcTemplate jdbc, ClockPort clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public synchronized Handle start(String accountId, String conversationId, String requestId, String operationCode,
            TaskClass taskClass) {
        String requestKey = key(accountId, conversationId, requestId);
        if (taskClass == TaskClass.FOREGROUND && active.values().stream()
                .anyMatch(value -> value.accountId.equals(accountId)
                        && value.conversationId.equals(conversationId)
                        && value.taskClass == TaskClass.FOREGROUND)) {
            throw AppException.conflict("AI_FOREGROUND_TASK_LIMIT", "每个对话同时只能执行一个前台 AI 任务");
        }
        if (taskClass == TaskClass.BACKGROUND && active.values().stream()
                .filter(value -> value.accountId.equals(accountId) && value.taskClass == TaskClass.BACKGROUND)
                .count() >= 2) {
            throw AppException.conflict("AI_BACKGROUND_TASK_LIMIT", "每个账号同时最多执行两个后台 AI 任务");
        }
        State state = new State(Ids.newId(), accountId, conversationId, requestId, operationCode,
                taskClass, clock.now());
        State existing = active.putIfAbsent(requestKey, state);
        if (existing != null) {
            throw AppException.conflict("AI_REQUEST_IN_PROGRESS", "相同请求正在处理中");
        }
        return new Handle(requestKey, state);
    }

    public CancellationView cancel(String accountId, String conversationId, String requestId) {
        State state = active.get(key(accountId, conversationId, requestId));
        if (state == null) {
            return new CancellationView(requestId, false, "NOT_RUNNING");
        }
        state.cancelRequested.set(true);
        return new CancellationView(requestId, true, "CANCEL_REQUESTED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Handle handle, String status, String errorCode, String model, long inputTokens,
            long outputTokens) {
        if (handle == null || !handle.state.finished.compareAndSet(false, true)) return;
        active.remove(handle.requestKey, handle.state);
        State state = handle.state;
        Instant completed = clock.now();
        jdbc.update("INSERT INTO ai_resume_generation_attempts(id,account_id,conversation_id,request_id,operation_code,task_class,status,cancel_requested,error_code,model_code,input_tokens,output_tokens,started_at,completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                state.id, state.accountId, state.conversationId, state.requestId, state.operationCode,
                state.taskClass.name(), status, state.cancelRequested.get(), errorCode, model,
                Math.max(0, inputTokens), Math.max(0, outputTokens), state.startedAt, completed);
    }

    public enum TaskClass { FOREGROUND, BACKGROUND }

    public static final class Handle {
        private final String requestKey;
        private final State state;

        private Handle(String requestKey, State state) {
            this.requestKey = requestKey;
            this.state = state;
        }

        public boolean cancellationRequested() {
            return state.cancelRequested.get();
        }

        public String requestId() {
            return state.requestId;
        }
    }

    private static final class State {
        private final String id;
        private final String accountId;
        private final String conversationId;
        private final String requestId;
        private final String operationCode;
        private final TaskClass taskClass;
        private final Instant startedAt;
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private State(String id, String accountId, String conversationId, String requestId, String operationCode,
                TaskClass taskClass, Instant startedAt) {
            this.id = id;
            this.accountId = accountId;
            this.conversationId = conversationId;
            this.requestId = requestId;
            this.operationCode = operationCode;
            this.taskClass = taskClass;
            this.startedAt = startedAt;
        }
    }

    private static String key(String accountId, String conversationId, String requestId) {
        return accountId + ":" + conversationId + ":" + requestId;
    }

    public record CancellationView(String requestId, boolean accepted, String status) {}
}
