package com.jobproof.modules.airesume.application;

import com.jobproof.modules.airesume.application.AiGenerationAttemptService.CancellationView;
import com.jobproof.modules.airesume.application.AiGenerationAttemptService.Handle;
import com.jobproof.modules.airesume.application.AiGenerationAttemptService.TaskClass;
import com.jobproof.modules.airesume.application.AiQuotaService.Reservation;
import com.jobproof.modules.airesume.application.AiResumeSseService.Event;
import com.jobproof.modules.aigateway.application.AiGatewayException;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiResumeGenerationService {
    private static final String OPERATION = "RESUME_CHAT";
    private static final String PROMPT_VERSION = "2026-08-v1";

    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    private final AiGenerationAttemptService attempts;
    private final AiQuotaService quota;
    private final AiResumeConversationEventService events;
    private final AiResumeSseService sse;
    private final TransactionTemplate transactions;
    private final TransactionTemplate preparationTransactions;
    private final TransactionTemplate modelExecution;
    private final String defaultModel;

    public AiResumeGenerationService(JdbcTemplate jdbc, ClockPort clock, AiGenerationAttemptService attempts,
            AiQuotaService quota, AiResumeConversationEventService events, AiResumeSseService sse,
            PlatformTransactionManager transactionManager,
            @Value("${jobproof.ai.resume-model:qwen-plus}") String defaultModel) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.attempts = attempts;
        this.quota = quota;
        this.events = events;
        this.sse = sse;
        this.transactions = new TransactionTemplate(transactionManager);
        this.preparationTransactions = new TransactionTemplate(transactionManager);
        this.preparationTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.modelExecution = new TransactionTemplate(transactionManager);
        this.modelExecution.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.defaultModel = defaultModel;
    }

    public PreparedRequest prepare(String accountId, String conversationId, String requestId, String text) {
        return preparationTransactions.execute(status -> {
            lockConversation(accountId, conversationId);
            String clientId = requestId == null || requestId.isBlank() ? Ids.newId() : requestId.trim();
            AiResumeMessageView user = acceptMessage(accountId, conversationId, clientId, text);
            return new PreparedRequest(accountId, conversationId, clientId, user,
                    assistantFor(accountId, conversationId, user.id()));
        });
    }

    public <T> GenerationResult<T> execute(PreparedRequest request, String fallbackErrorCode,
            Function<Cancellation, GeneratedReply<T>> generate) {
        return modelExecution.execute(status -> run(request, fallbackErrorCode, generate));
    }

    private <T> GenerationResult<T> run(PreparedRequest request, String fallbackErrorCode,
            Function<Cancellation, GeneratedReply<T>> generate) {
        Admission admission;
        try {
            // Recheck replay while holding the same conversation lock as terminal writes.
            admission = inTransaction(() -> {
                lockConversation(request.accountId(), request.conversationId());
                AiResumeMessageView replay = assistantFor(request.accountId(), request.conversationId(), request.user().id());
                return replay == null
                        ? new Admission(attempts.start(request.accountId(), request.conversationId(), request.requestId(),
                                OPERATION, TaskClass.FOREGROUND), null)
                        : new Admission(null, replay);
            });
        } catch (RuntimeException exception) {
            return new GenerationResult<>(null, null, exception, true, false);
        }
        if (admission.replay() != null) {
            return new GenerationResult<>(admission.replay(), null, null, false, true);
        }

        Handle attempt = admission.attempt();
        Reservation reservation = null;
        GenerationResult<T> result;
        String model = defaultModel;
        long inputTokens = 0;
        long outputTokens = 0;
        try {
            reservation = inTransaction(() -> quota.reserve(request.accountId(),
                    "chat:" + request.conversationId() + ":" + request.requestId(), OPERATION, 1));
            GeneratedReply<T> reply = generate.apply(attempt::cancellationRequested);
            model = reply.model();
            inputTokens = reply.inputTokens();
            outputTokens = reply.outputTokens();
            Reservation held = reservation;
            result = inTransaction(() -> complete(request, attempt, held, reply));
        } catch (RuntimeException exception) {
            String errorCode = exception instanceof AppException app ? app.reason()
                    : exception instanceof AiGatewayException ? "AI_MODEL_FAILED" : fallbackErrorCode;
            Reservation held = reservation;
            result = inTransaction(() -> fail(request, held, errorCode, exception));
            model = defaultModel;
            inputTokens = 0;
            outputTokens = 0;
        }
        // Recording an attempt cannot turn an already committed reply into a failed reply or refund it.
        attempts.finish(attempt, result.message().status(), result.message().errorCode(), model,
                inputTokens, outputTokens);
        return result;
    }

    public CancellationView cancel(String accountId, String conversationId, String requestId) {
        String clean = requestId == null ? "" : requestId.trim();
        if (clean.isEmpty() || clean.length() > 128) {
            throw AppException.user("AI_REQUEST_ID_INVALID", "取消请求标识无效");
        }
        return attempts.cancel(accountId, conversationId, clean);
    }

    public AiResumeMessageView addAssistantMessage(String accountId, String conversationId, String answer,
            String model, long inputTokens, long outputTokens) {
        return inTransaction(() -> {
            lockConversation(accountId, conversationId);
            return insertAssistant(accountId, conversationId, null, "COMPLETED", answer, null,
                    model, inputTokens, outputTokens);
        });
    }

    public List<AiResumeMessageView> recentMessages(String accountId, String conversationId) {
        List<AiResumeMessageView> messages = new ArrayList<>(jdbc.query(
                "SELECT * FROM ai_resume_messages WHERE account_id=? AND conversation_id=? ORDER BY sequence_no DESC LIMIT 50",
                this::message, accountId, conversationId));
        Collections.reverse(messages);
        return messages;
    }

    private <T> GenerationResult<T> complete(PreparedRequest request, Handle attempt, Reservation reservation,
            GeneratedReply<T> reply) {
        lockConversation(request.accountId(), request.conversationId());
        AiResumeMessageView existing = assistantFor(request.accountId(), request.conversationId(), request.user().id());
        if (existing != null) return new GenerationResult<>(existing, null, null, false, true);
        if (attempt.cancellationRequested()) {
            quota.release(request.accountId(), reservation.id());
            AiResumeMessageView cancelled = insertAssistant(request.accountId(), request.conversationId(), request.user().id(),
                    "CANCELLED", null, "AI_TASK_CANCELLED", defaultModel, 0, 0);
            return new GenerationResult<>(cancelled, null, null, false, false);
        }
        AiResumeMessageView completed = insertAssistant(request.accountId(), request.conversationId(), request.user().id(),
                "COMPLETED", reply.answer(), null, reply.model(), reply.inputTokens(), reply.outputTokens());
        T payload = reply.persistContent() == null ? null : reply.persistContent().apply(completed);
        quota.settle(request.accountId(), reservation.id(), 1);
        return new GenerationResult<>(completed, payload, null, false, false);
    }

    private <T> GenerationResult<T> fail(PreparedRequest request, Reservation reservation, String errorCode,
            RuntimeException exception) {
        lockConversation(request.accountId(), request.conversationId());
        AiResumeMessageView existing = assistantFor(request.accountId(), request.conversationId(), request.user().id());
        if (existing != null) return new GenerationResult<>(existing, null, null, false, true);
        if (reservation != null) quota.release(request.accountId(), reservation.id());
        AiResumeMessageView failed = insertAssistant(request.accountId(), request.conversationId(), request.user().id(),
                "FAILED", null, errorCode, defaultModel, 0, 0);
        return new GenerationResult<>(failed, null, exception, false, false);
    }

    private AiResumeMessageView acceptMessage(String accountId, String conversationId, String requestId, String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty() || clean.length() > 8000) {
            throw AppException.user("AI_MESSAGE_INVALID", "消息必填且最长 8000 个字符");
        }
        AiResumeMessageView replay = jdbc.query(
                "SELECT * FROM ai_resume_messages WHERE account_id=? AND conversation_id=? AND client_message_id=?",
                this::message, accountId, conversationId, requestId).stream().findFirst().orElse(null);
        if (replay != null) return replay;
        Instant now = clock.now();
        String id = Ids.newId();
        Event event = events.append(accountId, conversationId, "message.completed",
                Map.of("messageId", id, "role", "USER", "content", clean), now);
        jdbc.update("INSERT INTO ai_resume_messages(id,account_id,conversation_id,sequence_no,role,message_type,status,content_text,client_message_id,parent_message_id,attempt_no,current_attempt,error_code,model_code,input_tokens,output_tokens,prompt_version,response_hash,created_at,completed_at) VALUES(?,?,?,?,?,'TEXT','COMPLETED',?,?,NULL,1,1,NULL,NULL,0,0,NULL,?,?,?)",
                id, accountId, conversationId, event.sequence(), "USER", clean, requestId, sha256(clean), now, now);
        sse.publish(event);
        return new AiResumeMessageView(id, event.sequence(), "USER", "TEXT", "COMPLETED", clean,
                null, null, 0, 0, now, now);
    }

    private AiResumeMessageView assistantFor(String accountId, String conversationId, String parentMessageId) {
        return jdbc.query("SELECT * FROM ai_resume_messages WHERE account_id=? AND conversation_id=? AND parent_message_id=? AND role='ASSISTANT' AND current_attempt=1 ORDER BY attempt_no DESC LIMIT 1",
                this::message, accountId, conversationId, parentMessageId).stream().findFirst().orElse(null);
    }

    private AiResumeMessageView insertAssistant(String accountId, String conversationId, String parentMessageId,
            String status, String answer, String errorCode, String model, long inputTokens, long outputTokens) {
        Instant now = clock.now();
        String id = Ids.newId();
        boolean completed = "COMPLETED".equals(status);
        Map<String, Object> payload = completed
                ? Map.of("messageId", id, "role", "ASSISTANT", "content", answer, "model", model,
                        "inputTokens", inputTokens, "outputTokens", outputTokens)
                : Map.of("messageId", id, "role", "ASSISTANT", "errorCode", errorCode);
        String eventType = completed ? "message.completed" : "CANCELLED".equals(status) ? "message.cancelled" : "message.failed";
        Event event = events.append(accountId, conversationId, eventType, payload, now);
        jdbc.update("INSERT INTO ai_resume_messages(id,account_id,conversation_id,sequence_no,role,message_type,status,content_text,client_message_id,parent_message_id,attempt_no,current_attempt,error_code,model_code,input_tokens,output_tokens,prompt_version,response_hash,created_at,completed_at) VALUES(?,?,?,?,?,'TEXT',?,?,NULL,?,1,1,?,?,?,?,?,?,?,?)",
                id, accountId, conversationId, event.sequence(), "ASSISTANT", status, answer, parentMessageId,
                errorCode, model, inputTokens, outputTokens, PROMPT_VERSION, completed ? sha256(answer) : null, now, now);
        sse.publish(event);
        return new AiResumeMessageView(id, event.sequence(), "ASSISTANT", "TEXT", status, answer,
                errorCode, model, inputTokens, outputTokens, now, now);
    }

    private void lockConversation(String accountId, String conversationId) {
        String owner = jdbc.query("SELECT account_id FROM ai_resume_conversations WHERE id=? FOR UPDATE",
                (rs, n) -> rs.getString("account_id"), conversationId).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_CONVERSATION_NOT_FOUND", "AI 简历会话不存在"));
        if (!accountId.equals(owner)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能访问他人的 AI 简历会话");
        }
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private AiResumeMessageView message(ResultSet rs, int row) throws SQLException {
        return new AiResumeMessageView(rs.getString("id"), rs.getLong("sequence_no"), rs.getString("role"),
                rs.getString("message_type"), rs.getString("status"), rs.getString("content_text"),
                rs.getString("error_code"), rs.getString("model_code"), rs.getLong("input_tokens"),
                rs.getLong("output_tokens"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record PreparedRequest(String accountId, String conversationId, String requestId,
            AiResumeMessageView user, AiResumeMessageView existingAssistant) {}
    public record GeneratedReply<T>(String answer, String model, long inputTokens, long outputTokens,
            Function<AiResumeMessageView, T> persistContent) {}
    public record GenerationResult<T>(AiResumeMessageView message, T payload, RuntimeException failure,
            boolean rejected, boolean replayed) {}
    @FunctionalInterface
    public interface Cancellation { boolean requested(); }
    private record Admission(Handle attempt, AiResumeMessageView replay) {}
}
