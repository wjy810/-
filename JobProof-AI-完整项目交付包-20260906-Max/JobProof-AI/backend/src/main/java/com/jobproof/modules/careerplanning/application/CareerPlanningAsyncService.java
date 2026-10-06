package com.jobproof.modules.careerplanning.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.GenerateCanvasCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.GenerateRecommendationsCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.InterviewRoundView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.InterviewStartCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.RecommendationSetView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.SessionView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.GenerateNodeInferenceCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.StartValidationBatchCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ValidationView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ProposalView;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.modules.task.infra.AsyncTaskEntity;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareerPlanningAsyncService {

    private static final Logger log = LoggerFactory.getLogger(CareerPlanningAsyncService.class);
    private static final int MAX_REQUEST_ID_LENGTH = 72;

    private final CareerPlanningService planning;
    private final CareerPlanningExecutionService execution;
    private final TaskService tasks;
    private final ObjectMapper mapper;
    private final CareerPlanningEventService events;

    public CareerPlanningAsyncService(CareerPlanningService planning, CareerPlanningExecutionService execution,
            TaskService tasks, ObjectMapper mapper, CareerPlanningEventService events) {
        this.planning = planning;
        this.execution = execution;
        this.tasks = tasks;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional
    public TaskView startCanvasGeneration(CurrentAccount current, String sessionId, GenerateCanvasCommand command) {
        SessionView session = planning.get(current, sessionId);
        if (!session.aiConsent()) {
            throw AppException.conflict("CP_AI_CONSENT_REQUIRED", "生成职业能力树前需要明确授权");
        }
        if (session.activeGoal() == null) {
            throw AppException.conflict("CP_GOAL_NOT_CONFIRMED", "请先确认目标职业");
        }
        if (session.canvas() == null) {
            throw AppException.conflict("CP_CANVAS_NOT_FOUND", "职业能力画布尚未创建");
        }
        Integer expectedVersion = command == null ? null : command.expectedVersion();
        if (expectedVersion != null && expectedVersion != session.canvas().version()) {
            throw AppException.conflict("CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新载入");
        }
        String requestId = normalizedRequestId(command == null ? null : command.requestId());
        String generationScale = command == null || command.generationScale() == null
                ? "STANDARD" : command.generationScale().trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("COMPACT", "STANDARD", "DEEP").contains(generationScale)) {
            throw AppException.user("CP_CANVAS_SCALE_INVALID", "能力画布规模必须是精简、标准或深入");
        }
        String payload = json(Map.of(
                "sessionId", sessionId,
                "requestId", requestId,
                "generationScale", generationScale,
                "expectedVersion", expectedVersion == null ? session.canvas().version() : expectedVersion));
        TaskView task = tasks.create(current.accountId(), TaskTypes.CAREER_PLANNING_CANVAS,
                sessionId + ":" + requestId, payload);
        events.appendOnce(current.accountId(), sessionId, "task.queued", task.id() + ":queued", Map.of(
                "taskId", task.id(), "taskType", task.taskType(), "sequence", 0));
        if (session.canvas().nodes().size() > 1 && !task.statusEnum().terminal()) {
            boolean completed = tasks.markSucceeded(task.id(), "career-canvas-v" + session.canvas().version(), json(Map.of(
                    "sessionId", sessionId,
                    "canvasVersion", session.canvas().version(),
                    "versionId", session.canvas().versionId())));
            if (completed) {
                events.appendOnce(current.accountId(), sessionId, "task.completed", task.id() + ":completed", Map.of(
                        "taskId", task.id(), "taskType", task.taskType(),
                        "resultRef", session.canvas().versionId(), "responseHash", ""));
            }
            return tasks.getOwned(current.accountId(), task.id());
        }
        return task;
    }

    @Transactional
    public TaskView startNodeInference(CurrentAccount current, String sessionId,
            GenerateNodeInferenceCommand command) {
        String requestId = normalizedRequestId(command == null ? null : command.requestId());
        String idempotencyKey = sessionId + ":" + requestId;
        TaskView existing = tasks.findExisting(current.accountId(),
                TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL, idempotencyKey).orElse(null);
        if (existing != null) return existing;
        execution.assertNodeInferenceCanStart(current, sessionId, command);
        int expectedVersion = command.expectedVersion() == null
                ? planning.get(current, sessionId).canvas().version() : command.expectedVersion();
        String inputVersion = "career-inference:" + sessionId;
        if (tasks.hasOpen(current.accountId(), TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL, inputVersion)) {
            throw AppException.conflict("CP_INFERENCE_ALREADY_RUNNING", "当前画布已有节点推演正在后台运行");
        }
        JsonNode payload = mapper.createObjectNode()
                .put("sessionId", sessionId)
                .put("requestId", requestId)
                .put("targetNodeId", command.targetNodeId())
                .put("direction", command.direction())
                .put("depth", command.depth())
                .put("expectedVersion", expectedVersion);
        if (command.instruction() != null && !command.instruction().isBlank()) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("instruction", command.instruction().trim());
        }
        TaskView task = tasks.create(current.accountId(), TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL,
                idempotencyKey, payload.toString(), inputVersion);
        events.appendOnce(current.accountId(), sessionId, "task.queued", task.id() + ":queued", Map.of(
                "taskId", task.id(), "taskType", task.taskType(), "sequence", 0));
        return task;
    }

    @Transactional
    public TaskView startInterviewGeneration(CurrentAccount current, String sessionId, InterviewStartCommand command) {
        SessionView session = planning.get(current, sessionId);
        if (!session.aiConsent()) {
            throw AppException.conflict("CP_AI_CONSENT_REQUIRED", "使用 AI 补充访谈前需要明确授权");
        }
        if (session.interviewRounds().stream().anyMatch(round -> "OPEN".equals(round.status()))) {
            throw AppException.conflict("CP_INTERVIEW_ALREADY_OPEN", "当前已有待回答的访谈问题");
        }
        String requestId = normalizedRequestId(command == null ? null : command.requestId());
        planning.recordInterviewRequest(current, sessionId, requestId);
        String payload = json(Map.of("sessionId", sessionId, "requestId", requestId));
        TaskView task = tasks.create(current.accountId(), TaskTypes.CAREER_PLANNING_INTERVIEW,
                sessionId + ":" + requestId, payload);
        events.appendOnce(current.accountId(), sessionId, "task.queued", task.id() + ":queued", Map.of(
                "taskId", task.id(), "taskType", task.taskType(), "sequence", 0));
        return task;
    }

    @Transactional
    public TaskView startRecommendationGeneration(CurrentAccount current, String sessionId,
            GenerateRecommendationsCommand command) {
        SessionView session = planning.get(current, sessionId);
        if (!session.aiConsent()) {
            throw AppException.conflict("CP_AI_CONSENT_REQUIRED", "生成职业方向前需要明确授权");
        }
        if (!"CONFIRMED".equals(session.profile().status())) {
            throw AppException.conflict("CP_PROFILE_NOT_CONFIRMED", "请先确认职业画像");
        }
        String requestId = normalizedRequestId(command == null ? null : command.requestId());
        String payload = json(Map.of("sessionId", sessionId, "requestId", requestId));
        TaskView task = tasks.create(current.accountId(), TaskTypes.CAREER_PLANNING_RECOMMENDATIONS,
                sessionId + ":" + requestId, payload);
        events.appendOnce(current.accountId(), sessionId, "task.queued", task.id() + ":queued", Map.of(
                "taskId", task.id(), "taskType", task.taskType(), "sequence", 0));
        return task;
    }

    @Transactional
    public TaskView startValidationBatch(CurrentAccount current, String sessionId,
            StartValidationBatchCommand command) {
        String requestId = normalizedRequestId(command == null ? null : command.requestId());
        String idempotencyKey = sessionId + ":" + requestId;
        TaskView existing = tasks.findExisting(current.accountId(),
                TaskTypes.CAREER_PLANNING_VALIDATION_BATCH, idempotencyKey).orElse(null);
        if (existing != null) return existing;
        StartValidationBatchCommand resolved = new StartValidationBatchCommand(requestId,
                command == null ? null : command.expectedCanvasVersion(),
                command == null ? null : command.method(), command == null ? null : command.items());
        execution.assertValidationBatchCanStart(current, sessionId, resolved);
        String batchId = Ids.newId();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("sessionId", sessionId);
        payload.put("batchId", batchId);
        payload.set("command", mapper.valueToTree(resolved));
        TaskView task = tasks.create(current.accountId(), TaskTypes.CAREER_PLANNING_VALIDATION_BATCH,
                idempotencyKey, payload.toString(), "career-validation:" + sessionId);
        events.appendOnce(current.accountId(), sessionId, "validation.batch-queued", task.id() + ":queued", Map.of(
                "taskId", task.id(), "taskType", task.taskType(), "batchId", batchId,
                "count", resolved.items().size()));
        return task;
    }

    public void processClaimedTask(AsyncTaskEntity task) {
        if (TaskTypes.CAREER_PLANNING_VALIDATION_BATCH.equals(task.getTaskType())) {
            processValidationBatchTask(task);
            return;
        }
        if (TaskTypes.CAREER_PLANNING_INTERVIEW.equals(task.getTaskType())) {
            processInterviewTask(task);
            return;
        }
        if (TaskTypes.CAREER_PLANNING_RECOMMENDATIONS.equals(task.getTaskType())) {
            processRecommendationTask(task);
            return;
        }
        if (TaskTypes.CAREER_PLANNING_CANVAS_PROPOSAL.equals(task.getTaskType())) {
            processNodeInferenceTask(task);
            return;
        }
        processCanvasTask(task);
    }

    private void processValidationBatchTask(AsyncTaskEntity task) {
        String sessionId = null;
        try {
            JsonNode payload = mapper.readTree(task.getPayloadJson());
            sessionId = payload.path("sessionId").asText();
            String batchId = payload.path("batchId").asText();
            StartValidationBatchCommand command = mapper.treeToValue(payload.path("command"),
                    StartValidationBatchCommand.class);
            if (sessionId.isBlank() || batchId.isBlank() || command == null) {
                fail(task, sessionId, "CAREER_VALIDATION_BATCH_PAYLOAD_INVALID", "批量验证任务参数无效，请重新发起");
                return;
            }
            events.appendAsync(task.getAccountId(), sessionId, "validation.batch-started",
                    task.getId() + ":started", Map.of("taskId", task.getId(), "batchId", batchId));
            reportProgress(task, sessionId, 18, "VALIDATING_BATCH_INPUT");
            CurrentAccount current = new CurrentAccount(task.getAccountId(), "", "SEEKER", "");
            String resolvedSessionId = sessionId;
            List<ValidationView> results = execution.evaluateValidationBatch(current, sessionId, batchId, command,
                    (progress, checkpoint) -> reportProgress(task, resolvedSessionId, progress, checkpoint));
            reportProgress(task, sessionId, 94, "PERSISTING_VALIDATIONS");
            if (tasks.markSucceeded(task.getId(), batchId, json(Map.of(
                    "sessionId", sessionId, "batchId", batchId, "count", results.size())))) {
                events.appendAsync(task.getAccountId(), sessionId, "validation.batch-completed",
                        task.getId() + ":completed", Map.of(
                                "taskId", task.getId(), "batchId", batchId, "count", results.size()));
            }
        } catch (AppException exception) {
            fail(task, sessionId, exception.reason(), exception.getMessage());
        } catch (Exception exception) {
            log.warn("career planning validation batch failed taskId={}", task.getId(), exception);
            fail(task, sessionId, "CAREER_VALIDATION_BATCH_FAILED", "批量能力验证失败，画布未被修改，可稍后重试");
        }
    }

    private void processNodeInferenceTask(AsyncTaskEntity task) {
        String sessionId = null;
        try {
            JsonNode payload = mapper.readTree(task.getPayloadJson());
            sessionId = payload.path("sessionId").asText();
            String requestId = payload.path("requestId").asText();
            String targetNodeId = payload.path("targetNodeId").asText();
            String direction = payload.path("direction").asText();
            String depth = payload.path("depth").asText();
            int expectedVersion = payload.path("expectedVersion").asInt(-1);
            String instruction = payload.path("instruction").asText(null);
            if (sessionId.isBlank() || requestId.isBlank() || targetNodeId.isBlank()
                    || direction.isBlank() || depth.isBlank() || expectedVersion < 1) {
                fail(task, sessionId, "CAREER_INFERENCE_TASK_PAYLOAD_INVALID", "节点推演任务参数无效，请重新发起");
                return;
            }
            events.appendAsync(task.getAccountId(), sessionId, "task.started", task.getId() + ":started", Map.of(
                    "taskId", task.getId(), "taskType", task.getTaskType(), "sequence", 0));
            CurrentAccount current = new CurrentAccount(task.getAccountId(), "", "SEEKER", "");
            ProposalView existing = execution.proposalForTask(current, sessionId, task.getId());
            if (existing != null) {
                completeInference(task, sessionId, existing);
                return;
            }
            reportProgress(task, sessionId, 14, "VALIDATING_NODE");
            String resolvedSessionId = sessionId;
            ProposalView proposal = execution.generateNodeInference(current, sessionId,
                    new GenerateNodeInferenceCommand(requestId, targetNodeId, direction, depth,
                            instruction, expectedVersion), task.getId(),
                    (progress, checkpoint) -> reportProgress(task, resolvedSessionId, progress, checkpoint));
            reportProgress(task, sessionId, 94, "PERSISTING_PROPOSAL");
            completeInference(task, sessionId, proposal);
        } catch (AppException exception) {
            fail(task, sessionId, exception.reason(), exception.getMessage());
        } catch (Exception exception) {
            log.warn("career planning inference task failed taskId={}", task.getId(), exception);
            fail(task, sessionId, "CAREER_INFERENCE_GENERATION_FAILED", "节点推演失败，原画布未被修改，可稍后重试");
        }
    }

    private void completeInference(AsyncTaskEntity task, String sessionId, ProposalView proposal) {
        if (tasks.markSucceeded(task.getId(), proposal.id(), json(Map.of(
                "sessionId", sessionId, "proposalId", proposal.id(), "status", proposal.status())))) {
            events.appendAsync(task.getAccountId(), sessionId, "task.completed", task.getId() + ":completed", Map.of(
                    "taskId", task.getId(), "taskType", task.getTaskType(),
                    "resultRef", proposal.id(), "responseHash", ""));
        }
    }

    private void processRecommendationTask(AsyncTaskEntity task) {
        String sessionId = null;
        try {
            JsonNode payload = mapper.readTree(task.getPayloadJson());
            sessionId = payload.path("sessionId").asText();
            String requestId = payload.path("requestId").asText();
            if (sessionId.isBlank() || requestId.isBlank()) {
                fail(task, sessionId, "CAREER_RECOMMENDATION_TASK_PAYLOAD_INVALID",
                        "职业方向任务参数无效，请重新发起");
                return;
            }
            events.appendAsync(task.getAccountId(), sessionId, "task.started", task.getId() + ":started", Map.of(
                    "taskId", task.getId(), "taskType", task.getTaskType(), "sequence", 0));
            reportProgress(task, sessionId, 18, "VALIDATING_PROFILE");
            CurrentAccount current = new CurrentAccount(task.getAccountId(), "", "SEEKER", "");
            reportProgress(task, sessionId, 36, "GENERATING_RECOMMENDATIONS");
            RecommendationSetView result = planning.generateRecommendations(current, sessionId,
                    new GenerateRecommendationsCommand(requestId));
            reportProgress(task, sessionId, 92, "PERSISTING_RECOMMENDATIONS");
            if (tasks.markSucceeded(task.getId(), "career-recommendations-" + result.id(), json(Map.of(
                    "sessionId", sessionId, "recommendationSetId", result.id(), "status", result.status())))) {
                events.appendAsync(task.getAccountId(), sessionId, "task.completed", task.getId() + ":completed", Map.of(
                        "taskId", task.getId(), "taskType", task.getTaskType(),
                        "resultRef", result.id(), "responseHash", ""));
            }
        } catch (AppException exception) {
            fail(task, sessionId, exception.reason(), exception.getMessage());
        } catch (Exception exception) {
            log.warn("career planning recommendation task failed taskId={}", task.getId(), exception);
            fail(task, sessionId, "CAREER_RECOMMENDATION_GENERATION_FAILED",
                    "职业方向生成失败，已确认画像未被修改，可稍后重试");
        }
    }

    private void processCanvasTask(AsyncTaskEntity task) {
        String sessionId = null;
        try {
            JsonNode payload = mapper.readTree(task.getPayloadJson());
            String resolvedSessionId = payload.path("sessionId").asText();
            sessionId = resolvedSessionId;
            String requestId = payload.path("requestId").asText();
            int expectedVersion = payload.path("expectedVersion").asInt(-1);
            String generationScale = payload.path("generationScale").asText("STANDARD");
            if (resolvedSessionId.isBlank() || requestId.isBlank() || expectedVersion < 0) {
                fail(task, resolvedSessionId, "CAREER_CANVAS_TASK_PAYLOAD_INVALID", "能力树任务参数无效，请重新发起");
                return;
            }
            events.appendAsync(task.getAccountId(), resolvedSessionId, "task.started", task.getId() + ":started", Map.of(
                    "taskId", task.getId(), "taskType", task.getTaskType(), "sequence", 0));
            reportProgress(task, resolvedSessionId, 12, "VALIDATING_INPUT");
            CurrentAccount current = new CurrentAccount(task.getAccountId(), "", "SEEKER", "");
            SessionView before = planning.get(current, resolvedSessionId);
            if (before.canvas() != null && before.canvas().nodes().size() > 1) {
                complete(task, resolvedSessionId, before.canvas());
                return;
            }
            reportProgress(task, resolvedSessionId, 28, "GENERATING_CANVAS");
            CanvasView canvas = planning.generateCanvas(current, resolvedSessionId,
                    new GenerateCanvasCommand(requestId, expectedVersion, generationScale),
                    (progress, checkpoint) -> reportProgress(task, resolvedSessionId, progress, checkpoint));
            reportProgress(task, resolvedSessionId, 94, "PERSISTING_RESULT");
            complete(task, resolvedSessionId, canvas);
        } catch (AppException exception) {
            fail(task, sessionId, exception.reason(), exception.getMessage());
        } catch (Exception exception) {
            log.warn("career planning canvas task failed taskId={}", task.getId(), exception);
            fail(task, sessionId, "CAREER_CANVAS_GENERATION_FAILED", "能力树生成失败，原画布未被修改，可稍后重试");
        }
    }

    private void processInterviewTask(AsyncTaskEntity task) {
        String sessionId = null;
        try {
            JsonNode payload = mapper.readTree(task.getPayloadJson());
            sessionId = payload.path("sessionId").asText();
            String requestId = payload.path("requestId").asText();
            if (sessionId.isBlank() || requestId.isBlank()) {
                fail(task, sessionId, "CAREER_INTERVIEW_TASK_PAYLOAD_INVALID", "AI 访谈任务参数无效，请重新发起");
                return;
            }
            events.appendAsync(task.getAccountId(), sessionId, "task.started", task.getId() + ":started", Map.of(
                    "taskId", task.getId(), "taskType", task.getTaskType(), "sequence", 0));
            events.appendAsync(task.getAccountId(), sessionId, "assistant.started",
                    task.getId() + ":assistant-started", Map.of("taskId", task.getId(), "requestId", requestId));
            reportProgress(task, sessionId, 18, "PREPARING_INTERVIEW_CONTEXT");
            CurrentAccount current = new CurrentAccount(task.getAccountId(), "", "SEEKER", "");
            AtomicInteger deltaSequence = new AtomicInteger();
            AtomicLong lastCancellationCheck = new AtomicLong();
            String resolvedSessionId = sessionId;
            SessionView result = planning.startInterviewStreaming(current, sessionId, requestId, delta -> {
                if (delta == null || delta.isEmpty()) return;
                int sequence = deltaSequence.incrementAndGet();
                events.appendAsync(task.getAccountId(), resolvedSessionId, "assistant.delta",
                        task.getId() + ":assistant-delta:" + sequence,
                        Map.of("taskId", task.getId(), "requestId", requestId, "delta", delta,
                                "deltaSequence", sequence));
            }, () -> isCancelled(task, lastCancellationCheck), round -> tasks.markSucceeded(
                    task.getId(), "career-interview-" + round.id(), json(Map.of(
                            "sessionId", resolvedSessionId, "roundId", round.id(),
                            "questionCount", round.questions().size()))));
            if (isCancelled(task, new AtomicLong())) {
                emitCancelled(task, sessionId, requestId);
                return;
            }
            InterviewRoundView round = result.interviewRounds().stream()
                    .filter(value -> "OPEN".equals(value.status())).findFirst()
                    .orElseThrow(() -> AppException.dependency("CP_INTERVIEW_ROUND_MISSING", "AI 访谈问题保存失败"));
            reportProgress(task, sessionId, 92, "PERSISTING_INTERVIEW");
            events.appendAsync(task.getAccountId(), sessionId, "question.batch",
                    task.getId() + ":question-batch", Map.of("taskId", task.getId(), "roundId", round.id(),
                            "questions", round.questions()));
            events.appendAsync(task.getAccountId(), sessionId, "assistant.completed",
                    task.getId() + ":assistant-completed", Map.of("taskId", task.getId(), "requestId", requestId,
                            "roundId", round.id()));
            events.appendAsync(task.getAccountId(), sessionId, "task.completed",
                    task.getId() + ":completed", Map.of("taskId", task.getId(), "taskType", task.getTaskType(),
                            "resultRef", round.id(), "responseHash", ""));
        } catch (AppException exception) {
            if ("CP_AI_TASK_CANCELLED".equals(exception.reason()) || isCancelled(task, new AtomicLong())) {
                emitCancelled(task, sessionId, readRequestId(task));
                return;
            }
            emitAssistantFailure(task, sessionId, exception.reason());
            fail(task, sessionId, exception.reason(), exception.getMessage());
        } catch (Exception exception) {
            log.warn("career planning interview task failed taskId={}", task.getId(), exception);
            emitAssistantFailure(task, sessionId, "CAREER_INTERVIEW_GENERATION_FAILED");
            fail(task, sessionId, "CAREER_INTERVIEW_GENERATION_FAILED",
                    "AI 访谈生成失败，当前画像未被修改，可稍后重试");
        }
    }

    private boolean isCancelled(AsyncTaskEntity task, AtomicLong lastCheck) {
        long now = System.nanoTime();
        long previous = lastCheck.get();
        if (previous != 0 && now - previous < 200_000_000L) return false;
        lastCheck.set(now);
        return "CANCELLED".equals(tasks.getOwned(task.getAccountId(), task.getId()).status());
    }

    private void emitCancelled(AsyncTaskEntity task, String sessionId, String requestId) {
        if (sessionId == null || sessionId.isBlank()) return;
        events.appendAsync(task.getAccountId(), sessionId, "assistant.cancelled",
                task.getId() + ":assistant-cancelled", Map.of("taskId", task.getId(),
                        "requestId", requestId == null ? "" : requestId));
    }

    private void emitAssistantFailure(AsyncTaskEntity task, String sessionId, String errorCode) {
        if (sessionId == null || sessionId.isBlank()) return;
        events.appendAsync(task.getAccountId(), sessionId, "assistant.failed",
                task.getId() + ":assistant-failed", Map.of("taskId", task.getId(), "errorCode", errorCode));
    }

    private String readRequestId(AsyncTaskEntity task) {
        try { return mapper.readTree(task.getPayloadJson()).path("requestId").asText(""); }
        catch (Exception ignored) { return ""; }
    }

    private void complete(AsyncTaskEntity task, String sessionId, CanvasView canvas) {
        boolean completed = tasks.markSucceeded(task.getId(), "career-canvas-v" + canvas.version(), json(Map.of(
                "sessionId", sessionId,
                "canvasVersion", canvas.version(),
                "versionId", canvas.versionId())));
        if (completed) {
            events.appendAsync(task.getAccountId(), sessionId, "task.completed", task.getId() + ":completed", Map.of(
                    "taskId", task.getId(), "taskType", task.getTaskType(),
                    "resultRef", canvas.versionId(), "responseHash", ""));
        }
    }

    private void reportProgress(AsyncTaskEntity task, String sessionId, int progress, String checkpoint) {
        try {
            tasks.updateProgress(task.getId(), progress, checkpoint);
            events.appendAsync(task.getAccountId(), sessionId, "task.progress",
                    task.getId() + ":progress:" + checkpoint, Map.of(
                            "taskId", task.getId(), "taskType", task.getTaskType(),
                            "stage", checkpoint, "percent", progress));
        } catch (RuntimeException exception) {
            log.warn("career planning progress update failed taskId={} checkpoint={}", task.getId(), checkpoint, exception);
        }
    }

    private void fail(AsyncTaskEntity task, String sessionId, String code, String message) {
        if (!tasks.markFailed(task.getId(), code, message)) return;
        if (sessionId == null || sessionId.isBlank()) return;
        try {
            events.appendAsync(task.getAccountId(), sessionId, "task.failed", task.getId() + ":failed", Map.of(
                    "taskId", task.getId(), "taskType", task.getTaskType(),
                    "errorCode", code, "retryable", true));
        } catch (RuntimeException exception) {
            log.warn("career planning failure event unavailable taskId={}", task.getId(), exception);
        }
    }

    private static String normalizedRequestId(String value) {
        String requestId = value == null || value.isBlank() ? Ids.newId() : value.trim();
        if (requestId.length() > MAX_REQUEST_ID_LENGTH || !requestId.matches("[A-Za-z0-9._:-]+")) {
            throw AppException.user("CP_REQUEST_ID_INVALID", "请求标识格式无效");
        }
        return requestId;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("career planning task serialization failed", exception);
        }
    }
}
