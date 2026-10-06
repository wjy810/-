package com.jobproof.modules.careerplanning.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.AiProposalItem;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.AiResult;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.BatchValidationItem;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.BatchValidationPayload;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.ProposalPayload;
import com.jobproof.modules.careerplanning.application.CareerPlanningAiService.ValidationPayload;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ConfirmValidationCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.CreateEvidenceCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.CreateLearningPlanCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.DecideProposalCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ExecutionOverview;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.GenerateProposalCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.GenerateNodeInferenceCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.LearningEvidenceView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.LearningPlanRevisionView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.LearningPlanView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.PlanTaskView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ProposalApplyResult;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ProposalDecision;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ProposalItemView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ProposalView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.RestoreCanvasCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.StartValidationCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.StartValidationBatchCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.StartValidationBatchItem;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.UpdatePlanCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.UpdateTaskCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ValidationView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.WeeklyReviewCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.WeeklyReviewView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasNodeView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasRelationView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasView;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CareerPlanningExecutionService {
    private static final Set<String> OPERATIONS = Set.of("ADD", "UPDATE", "DELETE", "MOVE", "ADD_RELATION");
    private static final Set<String> INFERENCE_DIRECTIONS = Set.of(
            "DOWNWARD", "PREREQUISITES", "SIBLINGS", "TARGET_GAP");
    private static final Set<String> INFERENCE_DEPTHS = Set.of("ONE_LEVEL", "FULL_BRANCH");
    private static final Set<String> NODE_TYPES = Set.of(
            "CAREER", "DOMAIN", "SKILL", "KNOWLEDGE", "TASK", "EVIDENCE");
    private static final Set<String> NODE_STATUSES = Set.of(
            "NOT_STARTED", "PLANNED", "LEARNING", "PENDING_VALIDATION", "MASTERED", "PAUSED");
    private static final Set<String> EDITABLE_STATUSES = Set.of(
            "NOT_STARTED", "PLANNED", "LEARNING", "PENDING_VALIDATION", "PAUSED");
    private static final Set<String> INTENSITIES = Set.of("LIGHT", "STANDARD", "FOCUSED");
    private static final Set<String> PLAN_STATUSES = Set.of("ACTIVE", "PAUSED", "COMPLETED", "ARCHIVED");
    private static final Set<String> TASK_STATUSES = Set.of(
            "TODO", "IN_PROGRESS", "BLOCKED", "DONE", "SKIPPED");
    private static final Set<String> EVIDENCE_TYPES = Set.of("CAREER_FILE", "CAREER_RECORD", "USER_NOTE");
    private static final Set<String> VALIDATION_METHODS = Set.of(
            "CODE_REVIEW", "PROJECT_CHECK", "QUIZ", "SCENARIO", "MOCK_INTERVIEW", "EVIDENCE_REVIEW");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Integer>> INTEGER_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final CareerPlanningAiService ai;
    private final CareerPlanningService planning;
    private final AuditService audit;
    private final CareerPlanningEventService events;
    private final TransactionTemplate transactions;
    private final boolean enabled;

    public CareerPlanningExecutionService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock,
            CareerPlanningAiService ai, CareerPlanningService planning, AuditService audit,
            CareerPlanningEventService events, PlatformTransactionManager transactionManager,
            @Value("${jobproof.career-planning.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.ai = ai;
        this.planning = planning;
        this.audit = audit;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
        this.enabled = enabled;
    }

    @Transactional
    public ExecutionOverview overview(CurrentAccount current, String sessionId) {
        SessionRow session = requireSession(current, sessionId);
        if (session.goalId() != null) {
            Integer latestVersion = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(version_no),0) FROM canvas_versions WHERE goal_id=? AND account_id=?",
                    Integer.class, session.goalId(), current.accountId());
            jdbc.update("UPDATE career_canvas_ai_proposals SET status='STALE',decided_at=? WHERE session_id=? AND account_id=? AND status='DRAFT' AND base_version_no<>?",
                    clock.now(), sessionId, current.accountId(), latestVersion == null ? 0 : latestVersion);
        }
        ProposalView proposal = jdbc.query("SELECT * FROM career_canvas_ai_proposals WHERE session_id=? AND account_id=? AND status IN ('DRAFT','STALE') ORDER BY created_at DESC LIMIT 1",
                (rs, n) -> proposalView(rs), sessionId, current.accountId()).stream().findFirst().orElse(null);
        LearningPlanView plan = activePlan(current, sessionId);
        List<ValidationView> validations = validations(current, sessionId);
        return new ExecutionOverview(proposal, plan, validations);
    }

    public ProposalView generateProposal(CurrentAccount current, String sessionId,
            GenerateProposalCommand command) {
        SessionRow session = requireCanvasSession(current, sessionId);
        if (!session.aiConsent()) throw AppException.conflict("CP_AI_CONSENT_REQUIRED", "生成 AI 差异建议前需要明确授权");
        String instruction = required(command == null ? null : command.instruction(),
                "CP_PROPOSAL_INSTRUCTION_REQUIRED", "请说明希望 AI 优化的内容");
        if (instruction.length() > 1000) throw AppException.user("CP_PROPOSAL_INSTRUCTION_TOO_LONG", "优化要求不能超过 1000 字");
        CanvasGraph graph = latestGraph(current.accountId(), session.goalId(), false);
        assertVersion(command == null ? null : command.expectedVersion(), graph.base().version(),
                "CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新载入");
        Set<String> allowedRefs = graph.nodes().values().stream().flatMap(node -> node.sourceRefs().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        ObjectNode context = mapper.createObjectNode();
        context.put("instruction", instruction);
        context.put("baseVersion", graph.base().version());
        context.set("nodes", mapper.valueToTree(graph.nodes().values()));
        context.set("relations", mapper.valueToTree(graph.relations()));
        context.set("allowedSourceRefs", mapper.valueToTree(allowedRefs));
        AiResult<ProposalPayload> result = ai.generateCanvasProposal(current.accountId(),
                command == null ? null : command.requestId(), context,
                payload -> validateProposal(graph, payload.items(), allowedRefs));
        return persistAiResult(current, result,
                () -> persistProposal(current, session, graph, instruction, result));
    }

    @Transactional(readOnly = true)
    public void assertNodeInferenceCanStart(CurrentAccount current, String sessionId,
            GenerateNodeInferenceCommand command) {
        InferenceRequest request = validateInferenceRequest(current, sessionId, command);
        int pending = count("SELECT COUNT(*) FROM career_canvas_ai_proposals WHERE session_id=? AND account_id=? AND status='DRAFT'",
                sessionId, current.accountId());
        if (pending > 0) {
            throw AppException.conflict("CP_PROPOSAL_PENDING", "当前已有待确认的 AI 差异，请先审阅或放弃");
        }
        if (request.graph().base().version() != request.expectedVersion()) {
            throw AppException.conflict("CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新载入");
        }
    }

    public ProposalView generateNodeInference(CurrentAccount current, String sessionId,
            GenerateNodeInferenceCommand command, String taskId,
            CareerPlanningAiService.ProgressListener progress) {
        InferenceRequest request = validateInferenceRequest(current, sessionId, command);
        if (count("SELECT COUNT(*) FROM career_canvas_ai_proposals WHERE session_id=? AND account_id=? AND status='DRAFT'",
                sessionId, current.accountId()) > 0) {
            throw AppException.conflict("CP_PROPOSAL_PENDING", "当前已有待确认的 AI 差异，请先审阅或放弃");
        }
        ObjectNode context = mapper.createObjectNode();
        context.put("proposalType", "NODE_INFERENCE");
        context.put("direction", request.direction());
        context.put("depth", request.depth());
        context.put("instruction", request.instruction());
        context.put("baseVersion", request.graph().base().version());
        context.set("targetCareer", mapper.valueToTree(Map.of(
                "goalId", request.session().goalId(), "title", request.goal().title())));
        context.set("selectedNode", mapper.valueToTree(request.target()));
        if (request.parentId() != null) context.put("selectedParentId", request.parentId());
        context.set("nodes", mapper.valueToTree(request.graph().nodes().values()));
        context.set("relations", mapper.valueToTree(request.graph().relations()));
        context.set("allowedSourceRefs", mapper.valueToTree(request.allowedRefs()));
        AiResult<ProposalPayload> result = ai.generateNodeInference(current.accountId(),
                command.requestId(), context, progress, payload -> {
                    validateProposal(request.graph(), payload.items(), request.allowedRefs());
                    validateNodeInference(request, payload.items());
                });
        return persistAiResult(current, result, () -> {
            CanvasGraph latest = latestGraph(current.accountId(), request.session().goalId(), true);
            if (latest.base().version() != request.expectedVersion()) {
                throw AppException.conflict("CP_CANVAS_VERSION_CONFLICT", "推演期间画布已更新，请重新生成");
            }
            if (count("SELECT COUNT(*) FROM career_canvas_ai_proposals WHERE session_id=? AND account_id=? AND status='DRAFT'",
                    sessionId, current.accountId()) > 0) {
                throw AppException.conflict("CP_PROPOSAL_PENDING", "推演期间已产生其他待确认差异，请稍后重试");
            }
            return persistProposal(current, request.session(), latest, request.instruction(), result,
                    "NODE_INFERENCE", request.target().logicalId(), request.direction(), request.depth(), taskId);
        });
    }

    @Transactional(readOnly = true)
    public ProposalView proposal(CurrentAccount current, String sessionId, String proposalId) {
        requireCanvasSession(current, sessionId);
        return requireProposal(current.accountId(), sessionId, proposalId, false);
    }

    @Transactional(readOnly = true)
    public ProposalView proposalForTask(CurrentAccount current, String sessionId, String taskId) {
        requireCanvasSession(current, sessionId);
        return jdbc.query("SELECT * FROM career_canvas_ai_proposals WHERE session_id=? AND account_id=? AND task_id=? ORDER BY created_at DESC LIMIT 1",
                (rs, n) -> proposalView(rs), sessionId, current.accountId(), taskId)
                .stream().findFirst().orElse(null);
    }

    @Transactional
    public ProposalView discardProposal(CurrentAccount current, String sessionId, String proposalId) {
        requireCanvasSession(current, sessionId);
        ProposalRow row = requireProposalRow(current.accountId(), sessionId, proposalId, true);
        if (!Set.of("DRAFT", "STALE").contains(row.status())) {
            throw AppException.conflict("CP_PROPOSAL_ALREADY_DECIDED", "该差异建议已经处理");
        }
        Instant now = clock.now();
        jdbc.update("UPDATE career_canvas_ai_proposals SET status='SUPERSEDED',decided_at=? WHERE id=? AND account_id=?",
                now, proposalId, current.accountId());
        jdbc.update("UPDATE career_canvas_ai_proposal_items SET decision_status='REJECTED',rejection_reason='用户放弃整份提案',decided_at=? WHERE proposal_id=? AND account_id=? AND decision_status='PENDING'",
                now, proposalId, current.accountId());
        audit.append(current.accountId(), "CAREER_CANVAS_AI_PROPOSAL_DISCARDED",
                "CAREER_CANVAS_AI_PROPOSAL", proposalId, "no canvas version created");
        return requireProposal(current.accountId(), sessionId, proposalId, false);
    }

    @Transactional(readOnly = true)
    public List<ProposalView> proposals(CurrentAccount current, String sessionId) {
        requireCanvasSession(current, sessionId);
        return jdbc.query("SELECT * FROM career_canvas_ai_proposals WHERE session_id=? AND account_id=? ORDER BY created_at DESC",
                (rs, n) -> proposalView(rs), sessionId, current.accountId());
    }

    public ProposalApplyResult decideProposal(CurrentAccount current, String sessionId, String proposalId,
            DecideProposalCommand command) {
        requireCanvasSession(current, sessionId);
        return transactions.execute(status -> applyProposal(current, sessionId, proposalId, command));
    }

    @Transactional
    public LearningPlanView createPlan(CurrentAccount current, String sessionId,
            CreateLearningPlanCommand command) {
        SessionRow session = requireCanvasSession(current, sessionId);
        if (command == null) throw AppException.user("CP_PLAN_REQUIRED", "学习计划参数不能为空");
        int weeks = command.durationWeeks();
        if (weeks < 2 || weeks > 24) throw AppException.user("CP_PLAN_DURATION_INVALID", "计划周期必须为 2 至 24 周");
        String intensity = enumValue(command.intensity(), INTENSITIES,
                "CP_PLAN_INTENSITY_INVALID", "计划强度无效");
        if (command.weeklyHours() < 1 || command.weeklyHours() > 40) {
            throw AppException.user("CP_PLAN_WEEKLY_HOURS_INVALID", "每周学习时间必须为 1 至 40 小时");
        }
        List<Integer> days = normalizedDays(command.learningDays());
        CanvasGraph graph = latestGraph(current.accountId(), session.goalId(), true);
        assertVersion(command.expectedCanvasVersion(), graph.base().version(),
                "CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新生成计划");
        List<GraphNode> candidates = orderedPlanNodes(graph);
        if (candidates.isEmpty()) throw AppException.conflict("CP_PLAN_NO_ACTIONABLE_NODES", "当前画布没有可生成计划的学习节点");

        Instant now = clock.now();
        LocalDate start = command.startDate() == null
                ? LocalDate.ofInstant(now, ZoneId.systemDefault()) : command.startDate();
        LocalDate target = start.plusWeeks(weeks).minusDays(1);
        List<String> replacedPlanIds = jdbc.query("SELECT id FROM career_learning_plans WHERE goal_id=? AND account_id=? AND status IN ('DRAFT','ACTIVE','PAUSED') FOR UPDATE",
                (rs, n) -> rs.getString("id"), session.goalId(), current.accountId());
        jdbc.update("UPDATE career_learning_plans SET status='ARCHIVED',archived_at=?,updated_at=?,version_no=version_no+1 WHERE goal_id=? AND account_id=? AND status IN ('DRAFT','ACTIVE','PAUSED')",
                now, now, session.goalId(), current.accountId());
        for (String replacedPlanId : replacedPlanIds) {
            recordPlanRevision(replacedPlanId, current.accountId(), "PLAN_REPLACED", "USER", now);
        }
        String planId = Ids.newId();
        jdbc.update("INSERT INTO career_learning_plans(id,session_id,goal_id,account_id,canvas_version_id,canvas_version_no,duration_weeks,intensity,weekly_hours,learning_days_json,start_date,target_date,status,version_no,generation_method,prompt_version,schema_version,model_code,response_hash,created_at,updated_at,completed_at,archived_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',0,'SYSTEM',NULL,NULL,NULL,NULL,?,?,NULL,NULL)",
                planId, sessionId, session.goalId(), current.accountId(), graph.base().id(), graph.base().version(),
                weeks, intensity, command.weeklyHours(), json(days), start, target, now, now);
        int weeklyBudget = command.weeklyHours() * 60;
        int used = 0;
        int order = 0;
        for (GraphNode node : candidates) {
            int minutes = estimatedMinutes(node);
            int week = Math.min(weeks, Math.max(1, used / weeklyBudget + 1));
            used += minutes;
            insertPlanTask(planId, current.accountId(), node.logicalId(), "LEARNING", week,
                    node.type().equals("TASK") ? node.title() : "掌握：" + node.title(),
                    detailText(node.detail(), "summary"), priority(node), minutes,
                    weekDueDate(start, week, days), true, order++, now);
        }
        for (int week = 2; week <= weeks; week += 2) {
            insertPlanTask(planId, current.accountId(), null, "CHECKPOINT", week,
                    "第 " + week + " 周检查点", "回顾完成情况、证据和阻塞项，并调整后续任务。",
                    "HIGH", 30, weekDueDate(start, week, days), false, order++, now);
        }
        recordPlanRevision(planId, current.accountId(), "PLAN_CREATED", "SYSTEM", now);
        jdbc.update("UPDATE career_planning_sessions SET phase_code='PLAN',version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                now, sessionId, current.accountId());
        audit.append(current.accountId(), "CAREER_LEARNING_PLAN_CREATED", "CAREER_LEARNING_PLAN", planId,
                "weeks=" + weeks + " tasks=" + order + " canvasVersion=" + graph.base().version());
        return appendPlanEvent(current.accountId(), sessionId, "plan.created", planId);
    }

    @Transactional(readOnly = true)
    public LearningPlanView activePlan(CurrentAccount current, String sessionId) {
        SessionRow session = requireCanvasSession(current, sessionId);
        return jdbc.query("SELECT id FROM career_learning_plans WHERE session_id=? AND account_id=? AND status IN ('ACTIVE','PAUSED') ORDER BY updated_at DESC LIMIT 1",
                (rs, n) -> rs.getString("id"), sessionId, current.accountId()).stream().findFirst()
                .map(id -> planView(id, current.accountId())).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<LearningPlanRevisionView> planRevisions(CurrentAccount current, String sessionId,
            String planId) {
        requireCanvasSession(current, sessionId);
        requirePlan(current.accountId(), sessionId, planId, false);
        return jdbc.query("SELECT * FROM career_learning_plan_revisions WHERE plan_id=? AND account_id=? ORDER BY revision_no DESC",
                (rs, n) -> planRevisionView(rs), planId, current.accountId());
    }

    @Transactional
    public LearningPlanView updatePlan(CurrentAccount current, String sessionId, String planId,
            UpdatePlanCommand command) {
        requireCanvasSession(current, sessionId);
        PlanRow row = requirePlan(current.accountId(), sessionId, planId, true);
        assertVersion(command == null ? null : command.expectedVersion(), row.version(),
                "CP_PLAN_VERSION_CONFLICT", "学习计划已更新，请重新载入");
        String status = enumValue(command == null ? null : command.status(), PLAN_STATUSES,
                "CP_PLAN_STATUS_INVALID", "学习计划状态无效");
        Instant now = clock.now();
        jdbc.update("UPDATE career_learning_plans SET status=?,version_no=version_no+1,updated_at=?,completed_at=?,archived_at=? WHERE id=? AND account_id=?",
                status, now, "COMPLETED".equals(status) ? now : null,
                "ARCHIVED".equals(status) ? now : null, planId, current.accountId());
        recordPlanRevision(planId, current.accountId(), "PLAN_STATUS_UPDATED", "USER", now);
        audit.append(current.accountId(), "CAREER_LEARNING_PLAN_STATUS_UPDATED", "CAREER_LEARNING_PLAN", planId,
                "status=" + status);
        return appendPlanEvent(current.accountId(), sessionId, "plan.updated", planId);
    }

    @Transactional
    public LearningPlanView updateTask(CurrentAccount current, String sessionId, String planId,
            String taskId, UpdateTaskCommand command) {
        SessionRow session = requireCanvasSession(current, sessionId);
        requirePlan(current.accountId(), sessionId, planId, true);
        TaskRow task = requireTask(current.accountId(), planId, taskId, true);
        assertVersion(command == null ? null : command.expectedVersion(), task.version(),
                "CP_PLAN_TASK_VERSION_CONFLICT", "任务已更新，请重新载入");
        String status = command.status() == null ? task.status() : enumValue(command.status(), TASK_STATUSES,
                "CP_PLAN_TASK_STATUS_INVALID", "任务状态无效");
        int week = command.targetWeek() == null ? task.week() : command.targetWeek();
        PlanRow plan = requirePlan(current.accountId(), sessionId, planId, false);
        if (week < 1 || week > plan.durationWeeks()) throw AppException.user("CP_PLAN_TASK_WEEK_INVALID", "任务周次超出计划范围");
        if (command.sortOrder() != null && (command.sortOrder() < 0 || command.sortOrder() > 1000)) {
            throw AppException.user("CP_PLAN_TASK_ORDER_INVALID", "任务顺序必须在 0 至 1000 之间");
        }
        int minutes = command.estimatedMinutes() == null ? task.estimatedMinutes() : command.estimatedMinutes();
        if (minutes < 10 || minutes > 2400) throw AppException.user("CP_PLAN_TASK_DURATION_INVALID", "任务预计时间必须为 10 至 2400 分钟");
        if ("DONE".equals(status) && task.evidenceRequired()) {
            int evidenceCount = count("SELECT COUNT(*) FROM career_learning_evidences WHERE task_id=? AND account_id=? AND archived_at IS NULL",
                    taskId, current.accountId());
            if (evidenceCount == 0) throw AppException.conflict("CP_PLAN_TASK_EVIDENCE_REQUIRED", "该任务需要先关联至少一项证据");
        }
        LocalDate due = command.dueDate() == null ? task.dueDate() : command.dueDate();
        Instant now = clock.now();
        jdbc.update("UPDATE career_learning_plan_tasks SET status=?,week_no=?,estimated_minutes=?,due_date=?,version_no=version_no+1,updated_at=?,completed_at=? WHERE id=? AND account_id=?",
                status, week, minutes, due, now, "DONE".equals(status) ? now : null, taskId, current.accountId());
        if (week != task.week() || command.sortOrder() != null) {
            reorderPlanTask(planId, current.accountId(), taskId, task.week(), week, command.sortOrder());
        }
        jdbc.update("UPDATE career_learning_plans SET version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                now, planId, current.accountId());
        recordPlanRevision(planId, current.accountId(), "TASK_UPDATED", "USER", now);
        if (task.nodeId() != null && ("IN_PROGRESS".equals(status) || "DONE".equals(status))) {
            updateNodeFromTask(current.accountId(), session.goalId(), task.nodeId(),
                    "DONE".equals(status) ? "PENDING_VALIDATION" : "LEARNING", task.title());
        }
        audit.append(current.accountId(), "CAREER_PLAN_TASK_UPDATED", "CAREER_PLAN_TASK", taskId,
                "status=" + status + " week=" + week);
        return appendPlanEvent(current.accountId(), sessionId, "plan.task-updated", planId);
    }

    @Transactional
    public LearningPlanView addEvidence(CurrentAccount current, String sessionId, String planId,
            CreateEvidenceCommand command) {
        requireCanvasSession(current, sessionId);
        requirePlan(current.accountId(), sessionId, planId, true);
        if (command == null) throw AppException.user("CP_EVIDENCE_REQUIRED", "证据不能为空");
        String type = enumValue(command.sourceType(), EVIDENCE_TYPES,
                "CP_EVIDENCE_TYPE_INVALID", "证据类型无效");
        String nodeId = required(command.nodeId(), "CP_EVIDENCE_NODE_REQUIRED", "请选择证据对应的能力节点");
        TaskRow task = command.taskId() == null ? null : requireTask(current.accountId(), planId, command.taskId(), true);
        if (task != null && task.nodeId() != null && !nodeId.equals(task.nodeId())) {
            throw AppException.user("CP_EVIDENCE_NODE_MISMATCH", "证据节点与任务节点不一致");
        }
        if (task != null) assertVersion(command.expectedTaskVersion(), task.version(),
                "CP_PLAN_TASK_VERSION_CONFLICT", "任务已更新，请重新载入");
        validateEvidenceSource(current.accountId(), type, command.sourceId());
        String title = required(command.title(), "CP_EVIDENCE_TITLE_REQUIRED", "请输入证据名称");
        if (title.length() > 255) throw AppException.user("CP_EVIDENCE_TITLE_TOO_LONG", "证据名称不能超过 255 字");
        String note = clean(command.note());
        if (note != null && note.length() > 2000) throw AppException.user("CP_EVIDENCE_NOTE_TOO_LONG", "证据说明不能超过 2000 字");
        Instant now = clock.now();
        String evidenceId = Ids.newId();
        jdbc.update("INSERT INTO career_learning_evidences(id,plan_id,task_id,logical_node_id,account_id,source_type,source_id,title,note_text,verification_status,created_at,confirmed_at,archived_at) VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?,NULL,NULL)",
                evidenceId, planId, command.taskId(), nodeId, current.accountId(), type,
                clean(command.sourceId()), title, note, now);
        jdbc.update("UPDATE career_learning_plans SET version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                now, planId, current.accountId());
        recordPlanRevision(planId, current.accountId(), "EVIDENCE_ADDED", "USER", now);
        audit.append(current.accountId(), "CAREER_ABILITY_EVIDENCE_ADDED", "CAREER_ABILITY_EVIDENCE", evidenceId,
                "type=" + type + " nodeId=" + nodeId);
        return appendPlanEvent(current.accountId(), sessionId, "plan.evidence-added", planId);
    }

    @Transactional
    public LearningPlanView saveWeeklyReview(CurrentAccount current, String sessionId, String planId,
            int week, WeeklyReviewCommand command) {
        PlanRow plan = requirePlan(current.accountId(), sessionId, planId, true);
        if (week < 1 || week > plan.durationWeeks()) throw AppException.user("CP_REVIEW_WEEK_INVALID", "复盘周次超出计划范围");
        if (command == null) throw AppException.user("CP_REVIEW_REQUIRED", "周复盘内容不能为空");
        Instant now = clock.now();
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM career_learning_weekly_reviews WHERE plan_id=? AND week_no=?",
                Integer.class, planId, week);
        if (exists != null && exists > 0) {
            jdbc.update("UPDATE career_learning_weekly_reviews SET completed_summary=?,blockers_text=?,adjustment_text=?,next_week_focus=?,updated_at=? WHERE plan_id=? AND week_no=? AND account_id=?",
                    limit(command.completedSummary(), 2000), limit(command.blockers(), 2000),
                    limit(command.adjustment(), 2000), limit(command.nextWeekFocus(), 1000),
                    now, planId, week, current.accountId());
        } else {
            jdbc.update("INSERT INTO career_learning_weekly_reviews(id,plan_id,account_id,week_no,completed_summary,blockers_text,adjustment_text,next_week_focus,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    Ids.newId(), planId, current.accountId(), week, limit(command.completedSummary(), 2000),
                    limit(command.blockers(), 2000), limit(command.adjustment(), 2000),
                    limit(command.nextWeekFocus(), 1000), now, now);
        }
        jdbc.update("UPDATE career_learning_plans SET version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                now, planId, current.accountId());
        recordPlanRevision(planId, current.accountId(), "WEEKLY_REVIEW_UPDATED", "USER", now);
        audit.append(current.accountId(), "CAREER_WEEKLY_REVIEW_SAVED", "CAREER_LEARNING_PLAN", planId,
                "week=" + week);
        return appendPlanEvent(current.accountId(), sessionId, "plan.review-updated", planId);
    }

    public ValidationView startValidation(CurrentAccount current, String sessionId,
            StartValidationCommand command) {
        SessionRow session = requireCanvasSession(current, sessionId);
        if (!session.aiConsent()) throw AppException.conflict("CP_AI_CONSENT_REQUIRED", "能力验证前需要明确授权 AI 评估");
        if (command == null) throw AppException.user("CP_VALIDATION_REQUIRED", "能力验证参数不能为空");
        String nodeId = required(command.nodeId(), "CP_VALIDATION_NODE_REQUIRED", "请选择需要验证的能力节点");
        String method = enumValue(command.method(), VALIDATION_METHODS,
                "CP_VALIDATION_METHOD_INVALID", "能力验证方式无效");
        CanvasGraph graph = latestGraph(current.accountId(), session.goalId(), false);
        assertVersion(command.expectedCanvasVersion(), graph.base().version(),
                "CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新发起验证");
        GraphNode node = requireNode(graph, nodeId);
        if (Set.of("CAREER", "DOMAIN", "EVIDENCE").contains(node.type())) {
            throw AppException.user("CP_VALIDATION_NODE_INVALID", "请选择技能、知识或实践任务节点进行验证");
        }
        List<String> evidenceIds = command.evidenceIds() == null ? List.of()
                : command.evidenceIds().stream().filter(value -> clean(value) != null).distinct().toList();
        List<LearningEvidenceView> evidence = evidenceIds.stream()
                .map(id -> requireEvidence(current.accountId(), id)).toList();
        if (evidence.stream().anyMatch(item -> !nodeId.equals(item.nodeId()))) {
            throw AppException.user("CP_VALIDATION_EVIDENCE_MISMATCH", "验证证据不属于当前能力节点");
        }
        JsonNode submission = command.submission() == null ? mapper.createObjectNode() : command.submission();
        if (!submission.isObject() || submission.toString().length() > 30_000) {
            throw AppException.user("CP_VALIDATION_SUBMISSION_INVALID", "验证提交内容无效或过长");
        }
        ObjectNode context = mapper.createObjectNode();
        context.put("method", method);
        context.set("node", mapper.valueToTree(node));
        context.set("evidenceSummaries", mapper.valueToTree(evidence));
        context.set("submission", submission);
        String requestId = clean(command.requestId()) == null ? Ids.newId() : command.requestId().trim();
        AiResult<ValidationPayload> result = ai.evaluateAbility(current.accountId(), requestId, context);
        return persistAiResult(current, result, () -> {
            if ("PASSED".equals(result.value().result()) && evidence.isEmpty()) {
                throw AppException.conflict("CP_VALIDATION_EVIDENCE_MISSING", "验证材料不足，不能判定为已通过");
            }
            return persistValidation(current, session, graph, node, method, evidenceIds, submission, result);
        });
    }

    @Transactional(readOnly = true)
    public List<ValidationView> validations(CurrentAccount current, String sessionId) {
        return validations(current, sessionId, null);
    }

    @Transactional(readOnly = true)
    public List<ValidationView> validations(CurrentAccount current, String sessionId, String batchId) {
        requireCanvasSession(current, sessionId);
        if (clean(batchId) == null) {
            return jdbc.query("SELECT * FROM career_ability_validations WHERE session_id=? AND account_id=? ORDER BY created_at DESC",
                    (rs, n) -> validationView(rs), sessionId, current.accountId());
        }
        return jdbc.query("SELECT * FROM career_ability_validations WHERE session_id=? AND account_id=? AND batch_id=? ORDER BY created_at,id",
                (rs, n) -> validationView(rs), sessionId, current.accountId(), batchId.trim());
    }

    @Transactional(readOnly = true)
    public void assertValidationBatchCanStart(CurrentAccount current, String sessionId,
            StartValidationBatchCommand command) {
        prepareValidationBatch(current, sessionId, command);
    }

    public List<ValidationView> evaluateValidationBatch(CurrentAccount current, String sessionId, String batchId,
            StartValidationBatchCommand command, CareerPlanningAiService.ProgressListener progress) {
        PreparedValidationBatch prepared = prepareValidationBatch(current, sessionId, command);
        ObjectNode context = mapper.createObjectNode();
        context.put("method", prepared.method());
        ArrayNode items = context.putArray("items");
        for (PreparedValidation item : prepared.items()) {
            ObjectNode input = items.addObject();
            input.put("nodeId", item.node().logicalId());
            input.put("hasEvidence", !item.evidenceIds().isEmpty());
            input.set("node", mapper.valueToTree(item.node()));
            input.set("evidenceSummaries", mapper.valueToTree(item.evidence()));
            input.set("submission", item.submission());
        }
        AiResult<BatchValidationPayload> result = ai.evaluateAbilities(current.accountId(), command.requestId(),
                context, progress);
        return persistAiResult(current, result,
                () -> persistValidationBatch(current, prepared, batchId, result));
    }

    private <T> T persistAiResult(CurrentAccount current, AiResult<?> result, Supplier<T> persistence) {
        try {
            return transactions.execute(status -> {
                T saved = persistence.get();
                ai.settle(current.accountId(), result);
                return saved;
            });
        } catch (RuntimeException exception) {
            ai.release(current.accountId(), result);
            throw exception;
        }
    }

    public ValidationView confirmValidation(CurrentAccount current, String sessionId, String validationId,
            ConfirmValidationCommand command) {
        SessionRow session = requireCanvasSession(current, sessionId);
        return transactions.execute(status -> confirmValidationTx(current, session, validationId, command));
    }

    @Transactional
    public CanvasView restoreCanvas(CurrentAccount current, String sessionId, int sourceVersion,
            RestoreCanvasCommand command) {
        SessionRow session = requireCanvasSession(current, sessionId);
        CanvasGraph currentGraph = latestGraph(current.accountId(), session.goalId(), true);
        assertVersion(command == null ? null : command.expectedVersion(), currentGraph.base().version(),
                "CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新载入");
        VersionRow source = requireVersion(current.accountId(), session.goalId(), sourceVersion);
        CanvasGraph sourceGraph = graphByVersion(source);
        CanvasGraph restored = new CanvasGraph(currentGraph.base(), new LinkedHashMap<>(sourceGraph.nodes()),
                new ArrayList<>(sourceGraph.relations()));
        CanvasView saved = persistGraph(current.accountId(), session.goalId(), restored,
                "USER_VERSION_RESTORED", "从 v" + sourceVersion + " 恢复为新版本", "USER",
                null, null, null, null);
        audit.append(current.accountId(), "CAREER_CANVAS_VERSION_RESTORED", "CAREER_CANVAS", saved.versionId(),
                "sourceVersion=" + sourceVersion + " newVersion=" + saved.version());
        appendCanvasEvent(current.accountId(), sessionId, "canvas.restored", saved);
        return saved;
    }

    private ProposalView persistProposal(CurrentAccount current, SessionRow session, CanvasGraph graph,
            String instruction, AiResult<ProposalPayload> result) {
        return persistProposal(current, session, graph, instruction, result, "GLOBAL_OPTIMIZATION",
                null, null, null, null);
    }

    private ProposalView persistProposal(CurrentAccount current, SessionRow session, CanvasGraph graph,
            String instruction, AiResult<ProposalPayload> result, String proposalType,
            String targetNodeId, String direction, String depth, String taskId) {
        Instant now = clock.now();
        if ("GLOBAL_OPTIMIZATION".equals(proposalType)) {
            jdbc.update("UPDATE career_canvas_ai_proposals SET status='SUPERSEDED',decided_at=? WHERE session_id=? AND account_id=? AND status='DRAFT'",
                    now, session.id(), current.accountId());
        }
        String proposalId = Ids.newId();
        jdbc.update("INSERT INTO career_canvas_ai_proposals(id,session_id,goal_id,account_id,base_version_id,base_version_no,status,instruction_text,prompt_version,schema_version,model_code,response_hash,created_at,decided_at,proposal_type,target_logical_node_id,direction_code,depth_code,task_id) VALUES(?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?,NULL,?,?,?,?,?)",
                proposalId, session.id(), session.goalId(), current.accountId(), graph.base().id(),
                graph.base().version(), instruction, CareerPlanningAiService.PROPOSAL_PROMPT_VERSION,
                CareerPlanningAiService.PROPOSAL_SCHEMA_VERSION, result.model(), result.responseHash(), now,
                proposalType, targetNodeId, direction, depth, taskId);
        int sequence = 0;
        for (AiProposalItem item : result.value().items()) {
            GraphNode target = item.targetNodeId() == null ? null : graph.nodes().get(item.targetNodeId());
            ObjectNode after = mapper.createObjectNode();
            put(after, "nodeType", item.nodeType());
            put(after, "title", item.title());
            put(after, "status", item.status());
            put(after, "relationType", item.relationType());
            put(after, "fromNodeId", item.fromNodeId());
            put(after, "toNodeId", item.toNodeId());
            after.set("detail", item.detail());
            after.set("sourceRefs", mapper.valueToTree(item.sourceRefs()));
            jdbc.update("INSERT INTO career_canvas_ai_proposal_items(id,proposal_id,account_id,sequence_no,proposal_key,operation_code,target_logical_node_id,parent_logical_node_id,before_json,after_json,reason_text,source_refs_json,impact_node_ids_json,decision_status,rejection_reason,decided_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',NULL,NULL,?)",
                    Ids.newId(), proposalId, current.accountId(), sequence++, item.key(), item.operation(),
                    item.targetNodeId(), item.parentNodeId(), json(target == null ? mapper.createObjectNode() : mapper.valueToTree(target)),
                    json(after), item.reason(), json(item.sourceRefs()), json(item.impactNodeIds()), now);
        }
        audit.append(current.accountId(), "CAREER_CANVAS_AI_PROPOSAL_CREATED", "CAREER_CANVAS_AI_PROPOSAL", proposalId,
                "type=" + proposalType + " baseVersion=" + graph.base().version()
                        + " items=" + result.value().items().size());
        events.append(current.accountId(), session.id(), "proposal.created", Map.of(
                "proposalId", proposalId, "baseVersion", graph.base().version(),
                "itemCount", result.value().items().size()));
        return requireProposal(current.accountId(), session.id(), proposalId, false);
    }

    private ProposalApplyResult applyProposal(CurrentAccount current, String sessionId, String proposalId,
            DecideProposalCommand command) {
        if (command == null || command.decisions() == null) {
            throw AppException.user("CP_PROPOSAL_DECISIONS_REQUIRED", "请逐项确认 AI 差异建议");
        }
        SessionRow session = requireCanvasSession(current, sessionId);
        ProposalRow proposal = requireProposalRow(current.accountId(), sessionId, proposalId, true);
        if (!"DRAFT".equals(proposal.status())) throw AppException.conflict("CP_PROPOSAL_ALREADY_DECIDED", "该差异建议已经处理");
        CanvasGraph graph = latestGraph(current.accountId(), session.goalId(), true);
        assertVersion(command.expectedVersion(), graph.base().version(),
                "CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新生成差异建议");
        if (proposal.baseVersion() != graph.base().version()) {
            throw AppException.conflict("CP_PROPOSAL_STALE", "差异建议基于旧画布，请重新生成");
        }
        List<ProposalItemRow> items = proposalItemRows(proposalId, current.accountId());
        Map<String, ProposalDecision> decisions = command.decisions().stream().collect(Collectors.toMap(
                ProposalDecision::itemId, Function.identity(), (left, right) -> right, LinkedHashMap::new));
        if (decisions.size() != items.size() || items.stream().anyMatch(item -> !decisions.containsKey(item.id()))) {
            throw AppException.user("CP_PROPOSAL_DECISIONS_INCOMPLETE", "每一项 AI 差异都必须接受或拒绝");
        }
        Map<String, String> addedIds = new LinkedHashMap<>();
        Set<String> acceptedProposalKeys = items.stream()
                .filter(item -> "ACCEPTED".equals(enumValue(decisions.get(item.id()).decision(),
                        Set.of("ACCEPTED", "REJECTED"), "CP_PROPOSAL_DECISION_INVALID", "差异决定无效")))
                .map(ProposalItemRow::proposalKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (ProposalItemRow item : items) {
            if (!acceptedProposalKeys.contains(item.proposalKey())) continue;
            assertAcceptedProposalReference(item.parentNodeId(), acceptedProposalKeys);
            if ("ADD_RELATION".equals(item.operation())) {
                assertAcceptedProposalReference(item.after().path("fromNodeId").asText(null), acceptedProposalKeys);
                assertAcceptedProposalReference(item.after().path("toNodeId").asText(null), acceptedProposalKeys);
            }
        }
        int accepted = 0;
        int rejected = 0;
        Instant now = clock.now();
        for (ProposalItemRow item : items) {
            ProposalDecision decision = decisions.get(item.id());
            String value = enumValue(decision.decision(), Set.of("ACCEPTED", "REJECTED"),
                    "CP_PROPOSAL_DECISION_INVALID", "差异决定无效");
            if ("REJECTED".equals(value)) {
                String reason = required(decision.rejectionReason(), "CP_PROPOSAL_REJECTION_REASON_REQUIRED", "请填写拒绝原因");
                jdbc.update("UPDATE career_canvas_ai_proposal_items SET decision_status='REJECTED',rejection_reason=?,decided_at=? WHERE id=?",
                        limit(reason, 1000), now, item.id());
                rejected++;
                continue;
            }
            applyItem(graph, item, addedIds);
            jdbc.update("UPDATE career_canvas_ai_proposal_items SET decision_status='ACCEPTED',rejection_reason=NULL,decided_at=? WHERE id=?",
                    now, item.id());
            accepted++;
        }
        CanvasView canvas = accepted == 0 ? canvasView(graph.base()) : persistGraph(current.accountId(),
                session.goalId(), graph, "AI_PROPOSAL_APPLIED", "采纳 AI 差异 " + accepted + " 项，拒绝 " + rejected + " 项",
                "USER", CareerPlanningAiService.PROPOSAL_PROMPT_VERSION,
                CareerPlanningAiService.PROPOSAL_SCHEMA_VERSION, proposal.model(), proposal.responseHash());
        String status = accepted == 0 ? "REJECTED" : rejected == 0 ? "ACCEPTED" : "PARTIALLY_ACCEPTED";
        jdbc.update("UPDATE career_canvas_ai_proposals SET status=?,decided_at=? WHERE id=? AND account_id=?",
                status, now, proposalId, current.accountId());
        audit.append(current.accountId(), "CAREER_CANVAS_AI_PROPOSAL_DECIDED", "CAREER_CANVAS_AI_PROPOSAL", proposalId,
                "status=" + status + " accepted=" + accepted + " rejected=" + rejected);
        events.append(current.accountId(), sessionId, "proposal.decided", Map.of(
                "proposalId", proposalId, "status", status, "accepted", accepted,
                "rejected", rejected, "canvasVersion", canvas.version()));
        return new ProposalApplyResult(requireProposal(current.accountId(), sessionId, proposalId, false), canvas);
    }

    private ValidationView persistValidation(CurrentAccount current, SessionRow session, CanvasGraph graph,
            GraphNode node, String method, List<String> evidenceIds, JsonNode submission,
            AiResult<ValidationPayload> result) {
        Instant now = clock.now();
        String validationId = Ids.newId();
        String planId = jdbc.query("SELECT id FROM career_learning_plans WHERE goal_id=? AND account_id=? AND status IN ('ACTIVE','PAUSED') ORDER BY updated_at DESC LIMIT 1",
                (rs, n) -> rs.getString("id"), session.goalId(), current.accountId()).stream().findFirst().orElse(null);
        jdbc.update("INSERT INTO career_ability_validations(id,session_id,goal_id,plan_id,account_id,logical_node_id,canvas_version_id,method_code,status,submission_json,score_json,result_code,feedback_json,user_confirmed,prompt_version,schema_version,model_code,response_hash,created_at,evaluated_at,confirmed_at) VALUES(?,?,?,?,?,?,?,?,'EVALUATED',?,?,?,?,0,?,?,?,?,?,?,NULL)",
                validationId, session.id(), session.goalId(), planId, current.accountId(), node.logicalId(),
                graph.base().id(), method, json(submission), json(result.value().score()), result.value().result(),
                json(result.value().feedback()), CareerPlanningAiService.VALIDATION_PROMPT_VERSION,
                CareerPlanningAiService.VALIDATION_SCHEMA_VERSION, result.model(), result.responseHash(), now, now);
        for (String evidenceId : evidenceIds) {
            jdbc.update("INSERT INTO career_ability_validation_evidences(validation_id,evidence_id,account_id,created_at) VALUES(?,?,?,?)",
                    validationId, evidenceId, current.accountId(), now);
        }
        if (!"MASTERED".equals(node.status())) {
            graph.nodes().put(node.logicalId(), node.withStatus("PENDING_VALIDATION"));
            persistGraph(current.accountId(), session.goalId(), graph, "VALIDATION_STARTED",
                    "发起能力验证：" + node.title(), "USER", CareerPlanningAiService.VALIDATION_PROMPT_VERSION,
                    CareerPlanningAiService.VALIDATION_SCHEMA_VERSION, result.model(), result.responseHash());
        }
        jdbc.update("UPDATE career_planning_sessions SET phase_code='VALIDATION',updated_at=?,version_no=version_no+1 WHERE id=? AND account_id=?",
                now, session.id(), current.accountId());
        audit.append(current.accountId(), "CAREER_ABILITY_VALIDATION_EVALUATED", "CAREER_ABILITY_VALIDATION", validationId,
                "method=" + method + " result=" + result.value().result());
        events.append(current.accountId(), session.id(), "validation.evaluated", Map.of(
                "validationId", validationId, "nodeId", node.logicalId(),
                "result", result.value().result()));
        return requireValidation(current.accountId(), session.id(), validationId);
    }

    private List<ValidationView> persistValidationBatch(CurrentAccount current, PreparedValidationBatch prepared,
            String batchId, AiResult<BatchValidationPayload> result) {
        CanvasGraph latest = latestGraph(current.accountId(), prepared.session().goalId(), true);
        assertVersion(prepared.expectedVersion(), latest.base().version(),
                "CP_CANVAS_VERSION_CONFLICT", "批量验证期间职业能力画布已更新，请重新发起");
        Map<String, PreparedValidation> inputs = prepared.items().stream().collect(Collectors.toMap(
                value -> value.node().logicalId(), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Instant now = clock.now();
        String planId = jdbc.query("SELECT id FROM career_learning_plans WHERE goal_id=? AND account_id=? AND status IN ('ACTIVE','PAUSED') ORDER BY updated_at DESC LIMIT 1",
                (rs, n) -> rs.getString("id"), prepared.session().goalId(), current.accountId())
                .stream().findFirst().orElse(null);
        for (BatchValidationItem evaluated : result.value().items()) {
            PreparedValidation input = inputs.get(evaluated.nodeId());
            if (input == null) throw AppException.dependency("CP_VALIDATION_BATCH_RESULT_INVALID", "批量验证返回了未知节点");
            String validationId = Ids.newId();
            jdbc.update("INSERT INTO career_ability_validations(id,batch_id,session_id,goal_id,plan_id,account_id,logical_node_id,canvas_version_id,method_code,status,submission_json,score_json,result_code,feedback_json,user_confirmed,prompt_version,schema_version,model_code,response_hash,created_at,evaluated_at,confirmed_at) VALUES(?,?,?,?,?,?,?,?,?,'EVALUATED',?,?,?,?,0,?,?,?,?,?,?,NULL)",
                    validationId, batchId, prepared.session().id(), prepared.session().goalId(), planId,
                    current.accountId(), input.node().logicalId(), latest.base().id(), prepared.method(),
                    json(input.submission()), json(evaluated.score()), evaluated.result(), json(evaluated.feedback()),
                    CareerPlanningAiService.BATCH_VALIDATION_PROMPT_VERSION,
                    CareerPlanningAiService.BATCH_VALIDATION_SCHEMA_VERSION, result.model(), result.responseHash(), now, now);
            for (String evidenceId : input.evidenceIds()) {
                jdbc.update("INSERT INTO career_ability_validation_evidences(validation_id,evidence_id,account_id,created_at) VALUES(?,?,?,?)",
                        validationId, evidenceId, current.accountId(), now);
            }
            GraphNode node = latest.nodes().get(input.node().logicalId());
            if (node != null && !"MASTERED".equals(node.status())) {
                latest.nodes().put(node.logicalId(), node.withStatus("PENDING_VALIDATION"));
            }
        }
        persistGraph(current.accountId(), prepared.session().goalId(), latest, "VALIDATION_BATCH_STARTED",
                "批量发起 " + result.value().items().size() + " 项能力验证", "USER",
                CareerPlanningAiService.BATCH_VALIDATION_PROMPT_VERSION,
                CareerPlanningAiService.BATCH_VALIDATION_SCHEMA_VERSION, result.model(), result.responseHash());
        jdbc.update("UPDATE career_planning_sessions SET phase_code='VALIDATION',updated_at=?,version_no=version_no+1 WHERE id=? AND account_id=?",
                now, prepared.session().id(), current.accountId());
        audit.append(current.accountId(), "CAREER_ABILITY_VALIDATION_BATCH_EVALUATED",
                "CAREER_ABILITY_VALIDATION_BATCH", batchId, "count=" + result.value().items().size());
        events.append(current.accountId(), prepared.session().id(), "validation.batch-completed", Map.of(
                "batchId", batchId, "count", result.value().items().size()));
        return validations(current, prepared.session().id(), batchId);
    }

    private PreparedValidationBatch prepareValidationBatch(CurrentAccount current, String sessionId,
            StartValidationBatchCommand command) {
        SessionRow session = requireCanvasSession(current, sessionId);
        if (!session.aiConsent()) throw AppException.conflict("CP_AI_CONSENT_REQUIRED", "批量能力验证前需要明确授权 AI 评估");
        if (command == null || command.items() == null || command.items().size() < 2 || command.items().size() > 8) {
            throw AppException.user("CP_VALIDATION_BATCH_SIZE_INVALID", "批量验证必须包含 2 至 8 个节点");
        }
        String method = enumValue(command.method(), VALIDATION_METHODS,
                "CP_VALIDATION_METHOD_INVALID", "能力验证方式无效");
        CanvasGraph graph = latestGraph(current.accountId(), session.goalId(), false);
        int expectedVersion = command.expectedCanvasVersion() == null ? graph.base().version() : command.expectedCanvasVersion();
        assertVersion(expectedVersion, graph.base().version(),
                "CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新发起批量验证");
        Set<String> nodeIds = new LinkedHashSet<>();
        List<PreparedValidation> prepared = new ArrayList<>();
        for (StartValidationBatchItem item : command.items()) {
            String nodeId = required(item == null ? null : item.nodeId(),
                    "CP_VALIDATION_NODE_REQUIRED", "批量验证包含未选择的能力节点");
            if (!nodeIds.add(nodeId)) throw AppException.user("CP_VALIDATION_BATCH_DUPLICATE", "批量验证不能重复选择同一节点");
            GraphNode node = requireNode(graph, nodeId);
            if (Set.of("CAREER", "DOMAIN", "EVIDENCE").contains(node.type())) {
                throw AppException.user("CP_VALIDATION_NODE_INVALID", "只能批量验证技能、知识或实践任务节点");
            }
            List<String> evidenceIds = item.evidenceIds() == null ? List.of()
                    : item.evidenceIds().stream().filter(value -> clean(value) != null).map(String::trim).distinct().toList();
            List<LearningEvidenceView> evidence = evidenceIds.stream()
                    .map(id -> requireEvidence(current.accountId(), id)).toList();
            if (evidence.stream().anyMatch(value -> !nodeId.equals(value.nodeId()))) {
                throw AppException.user("CP_VALIDATION_EVIDENCE_MISMATCH", "批量验证证据不属于对应能力节点");
            }
            JsonNode submission = item.submission() == null ? mapper.createObjectNode() : item.submission();
            if (!submission.isObject() || submission.toString().length() > 12_000) {
                throw AppException.user("CP_VALIDATION_SUBMISSION_INVALID", "批量验证提交内容无效或过长");
            }
            if (evidenceIds.isEmpty() && clean(submission.path("summary").asText()) == null) {
                throw AppException.user("CP_VALIDATION_MATERIAL_REQUIRED", "每个节点都需要验证说明或至少一项证据");
            }
            prepared.add(new PreparedValidation(node, evidenceIds, evidence, submission.deepCopy()));
        }
        String requestId = required(command.requestId(), "CP_REQUEST_ID_REQUIRED", "批量验证请求标识不能为空");
        if (requestId.length() > 72 || !requestId.matches("[A-Za-z0-9._:-]+")) {
            throw AppException.user("CP_REQUEST_ID_INVALID", "批量验证请求标识格式无效");
        }
        return new PreparedValidationBatch(session, graph, method, expectedVersion, List.copyOf(prepared));
    }

    private ValidationView confirmValidationTx(CurrentAccount current, SessionRow session, String validationId,
            ConfirmValidationCommand command) {
        ValidationRow validation = requireValidationRow(current.accountId(), session.id(), validationId, true);
        if (!"EVALUATED".equals(validation.status())) {
            throw AppException.conflict("CP_VALIDATION_ALREADY_CONFIRMED", "该验证结果已经处理");
        }
        CanvasGraph graph = latestGraph(current.accountId(), session.goalId(), true);
        assertVersion(command == null ? null : command.expectedCanvasVersion(), graph.base().version(),
                "CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新确认验证结果");
        boolean accepted = command != null && command.accepted();
        Instant now = clock.now();
        String status = accepted ? "CONFIRMED" : "REJECTED";
        jdbc.update("UPDATE career_ability_validations SET status=?,user_confirmed=?,confirmed_at=? WHERE id=? AND account_id=?",
                status, accepted, now, validationId, current.accountId());
        GraphNode node = requireNode(graph, validation.nodeId());
        if (accepted && "PASSED".equals(validation.result())) {
            ObjectNode detail = node.detail().deepCopy();
            detail.put("lastValidationId", validationId);
            detail.put("masteredAt", now.toString());
            graph.nodes().put(node.logicalId(), new GraphNode(node.logicalId(), node.type(), "MASTERED",
                    node.title(), detail, node.sourceRefs(), node.x(), node.y(), node.locked(), node.sortOrder()));
            persistGraph(current.accountId(), session.goalId(), graph, "VALIDATION_CONFIRMED",
                    "验证通过并确认掌握：" + node.title(), "USER", null, null, null, null);
            jdbc.update("UPDATE career_learning_evidences SET verification_status='CONFIRMED',confirmed_at=? WHERE id IN (SELECT evidence_id FROM career_ability_validation_evidences WHERE validation_id=?)",
                    now, validationId);
        } else if (accepted && "NEEDS_WORK".equals(validation.result())) {
            addRemediationTask(current.accountId(), session.goalId(), node, validationId, now);
        }
        audit.append(current.accountId(), "CAREER_ABILITY_VALIDATION_CONFIRMED", "CAREER_ABILITY_VALIDATION", validationId,
                "accepted=" + accepted + " result=" + validation.result());
        events.append(current.accountId(), session.id(), "validation.confirmed", Map.of(
                "validationId", validationId, "accepted", accepted, "result", validation.result()));
        return requireValidation(current.accountId(), session.id(), validationId);
    }

    private LearningPlanView appendPlanEvent(String accountId, String sessionId,
            String eventType, String planId) {
        LearningPlanView view = planView(planId, accountId);
        events.append(accountId, sessionId, eventType, Map.of(
                "planId", view.id(), "status", view.status(), "version", view.version(),
                "taskCount", view.tasks().size()));
        return view;
    }

    private void appendCanvasEvent(String accountId, String sessionId,
            String eventType, CanvasView canvas) {
        events.append(accountId, sessionId, eventType, Map.of(
                "versionId", canvas.versionId(), "version", canvas.version(),
                "reason", canvas.reason(), "nodeCount", canvas.nodes().size()));
    }

    private void validateProposal(CanvasGraph graph, List<AiProposalItem> items, Set<String> allowedRefs) {
        Set<String> proposalKeys = new LinkedHashSet<>();
        for (AiProposalItem item : items) {
            if (!proposalKeys.add(item.key()) || !OPERATIONS.contains(item.operation())) {
                throw AppException.dependency("CP_AI_PROPOSAL_INVALID", "AI 差异建议包含重复或无效项目");
            }
        }
        for (AiProposalItem item : items) {
            if (!allowedRefs.containsAll(item.sourceRefs())) {
                throw AppException.dependency("CP_AI_SOURCE_INVALID", "AI 差异建议引用了未确认资料");
            }
            if (item.targetNodeId() != null) {
                GraphNode target = graph.nodes().get(item.targetNodeId());
                if (target == null || "CAREER".equals(target.type())) {
                    throw AppException.dependency("CP_AI_PROPOSAL_TARGET_INVALID", "AI 差异建议引用了无效节点");
                }
            }
            if (item.parentNodeId() != null && !item.parentNodeId().startsWith("PROPOSAL:")
                    && !graph.nodes().containsKey(item.parentNodeId())) {
                throw AppException.dependency("CP_AI_PROPOSAL_PARENT_INVALID", "AI 差异建议引用了无效父节点");
            }
            if ("ADD_RELATION".equals(item.operation())) {
                if (!"PREREQUISITE".equals(item.relationType())
                        || !proposalReferenceExists(graph, proposalKeys, item.fromNodeId())
                        || !proposalReferenceExists(graph, proposalKeys, item.toNodeId())
                        || item.fromNodeId().equals(item.toNodeId())) {
                    throw AppException.dependency("CP_AI_PROPOSAL_RELATION_INVALID", "AI 差异建议包含无效前置关系");
                }
            }
        }
    }

    private InferenceRequest validateInferenceRequest(CurrentAccount current, String sessionId,
            GenerateNodeInferenceCommand command) {
        SessionRow session = requireCanvasSession(current, sessionId);
        if (!session.aiConsent()) {
            throw AppException.conflict("CP_AI_CONSENT_REQUIRED", "节点推演前需要明确授权 AI");
        }
        if (command == null) throw AppException.user("CP_INFERENCE_REQUIRED", "节点推演参数不能为空");
        CanvasGraph graph = latestGraph(current.accountId(), session.goalId(), false);
        int expectedVersion = command.expectedVersion() == null ? graph.base().version() : command.expectedVersion();
        assertVersion(expectedVersion, graph.base().version(),
                "CP_CANVAS_VERSION_CONFLICT", "职业能力画布已更新，请重新载入");
        GraphNode target = requireNode(graph,
                required(command.targetNodeId(), "CP_INFERENCE_NODE_REQUIRED", "请选择需要推演的能力节点"));
        if ("EVIDENCE".equals(target.type())) {
            throw AppException.user("CP_INFERENCE_NODE_INVALID", "能力证据是推演终点，不能继续扩展");
        }
        String direction = enumValue(command.direction(), INFERENCE_DIRECTIONS,
                "CP_INFERENCE_DIRECTION_INVALID", "节点推演方向无效");
        String depth = enumValue(command.depth(), INFERENCE_DEPTHS,
                "CP_INFERENCE_DEPTH_INVALID", "节点推演深度无效");
        String parentId = treeParentId(graph, target.logicalId());
        if ("SIBLINGS".equals(direction) && parentId == null) {
            throw AppException.user("CP_INFERENCE_DIRECTION_INVALID", "职业根节点没有可横向扩展的同级节点");
        }
        if ("PREREQUISITES".equals(direction)
                && !Set.of("SKILL", "KNOWLEDGE", "TASK").contains(target.type())) {
            throw AppException.user("CP_INFERENCE_DIRECTION_INVALID", "只有技能、知识或任务节点可以补齐前置依赖");
        }
        String instruction = clean(command.instruction());
        if (instruction != null && instruction.length() > 1000) {
            throw AppException.user("CP_INFERENCE_INSTRUCTION_TOO_LONG", "补充要求不能超过 1000 字");
        }
        if (instruction == null) instruction = inferenceInstruction(direction, depth, target.title());
        Set<String> allowedRefs = graph.nodes().values().stream().flatMap(node -> node.sourceRefs().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new InferenceRequest(session, requireGoal(current.accountId(), session.goalId()), graph,
                target, parentId, direction, depth, instruction, expectedVersion, Set.copyOf(allowedRefs));
    }

    private void validateNodeInference(InferenceRequest request, List<AiProposalItem> items) {
        if (items.stream().anyMatch(item -> !Set.of("ADD", "ADD_RELATION").contains(item.operation()))) {
            throw AppException.dependency("CP_AI_INFERENCE_OPERATION_INVALID", "节点推演只能新增节点或前置关系");
        }
        List<AiProposalItem> additions = items.stream().filter(item -> "ADD".equals(item.operation())).toList();
        int maximum = "ONE_LEVEL".equals(request.depth()) ? 6 : 14;
        if (additions.isEmpty() || additions.size() > maximum) {
            throw AppException.dependency("CP_AI_INFERENCE_SIZE_INVALID", "节点推演新增数量不符合所选深度");
        }
        Map<String, AiProposalItem> byKey = additions.stream().collect(Collectors.toMap(
                AiProposalItem::key, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Set<String> existingTitles = request.graph().nodes().values().stream()
                .map(node -> node.type() + ':' + node.title().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (AiProposalItem item : additions) {
            if (!existingTitles.add(item.nodeType() + ':' + item.title().trim().toLowerCase(Locale.ROOT))) {
                throw AppException.dependency("CP_AI_INFERENCE_DUPLICATE", "节点推演包含现有或重复能力节点");
            }
            if ("ONE_LEVEL".equals(request.depth()) && item.parentNodeId().startsWith("PROPOSAL:")) {
                throw AppException.dependency("CP_AI_INFERENCE_DEPTH_INVALID", "单层推演不能继续嵌套下级");
            }
            String rootParent = proposalRootParent(item.parentNodeId(), byKey);
            boolean validScope = switch (request.direction()) {
                case "DOWNWARD", "TARGET_GAP" -> isAtOrBelow(request.graph(), rootParent,
                        request.target().logicalId());
                case "SIBLINGS", "PREREQUISITES" -> request.parentId().equals(rootParent);
                default -> false;
            };
            if (!validScope) {
                throw AppException.dependency("CP_AI_INFERENCE_SCOPE_INVALID", "节点推演超出了当前节点范围");
            }
        }
        List<AiProposalItem> relations = items.stream()
                .filter(item -> "ADD_RELATION".equals(item.operation())).toList();
        if ("PREREQUISITES".equals(request.direction()) && relations.isEmpty()) {
            throw AppException.dependency("CP_AI_INFERENCE_RELATION_REQUIRED", "前置依赖推演必须包含可确认的依赖关系");
        }
        for (AiProposalItem relation : relations) {
            if (!referenceExists(request.graph(), byKey, relation.fromNodeId())
                    || !referenceExists(request.graph(), byKey, relation.toNodeId())) {
                throw AppException.dependency("CP_AI_INFERENCE_RELATION_INVALID", "推演关系引用了不存在的节点");
            }
            if ("PREREQUISITES".equals(request.direction())
                    && !request.target().logicalId().equals(relation.toNodeId())) {
                throw AppException.dependency("CP_AI_INFERENCE_RELATION_INVALID", "前置依赖必须指向当前节点");
            }
        }
    }

    private static boolean proposalReferenceExists(CanvasGraph graph, Set<String> proposalKeys, String value) {
        if (value == null) return false;
        return value.startsWith("PROPOSAL:")
                ? proposalKeys.contains(value.substring("PROPOSAL:".length()))
                : graph.nodes().containsKey(value);
    }

    private static boolean referenceExists(CanvasGraph graph, Map<String, AiProposalItem> proposals,
            String value) {
        if (value == null) return false;
        return value.startsWith("PROPOSAL:")
                ? proposals.containsKey(value.substring("PROPOSAL:".length()))
                : graph.nodes().containsKey(value);
    }

    private static String proposalRootParent(String value, Map<String, AiProposalItem> additions) {
        String current = value;
        Set<String> seen = new LinkedHashSet<>();
        while (current != null && current.startsWith("PROPOSAL:")) {
            String key = current.substring("PROPOSAL:".length());
            if (!seen.add(key)) throw AppException.dependency("CP_AI_INFERENCE_CYCLE", "推演节点形成循环引用");
            AiProposalItem item = additions.get(key);
            if (item == null) throw AppException.dependency("CP_AI_INFERENCE_PARENT_INVALID", "推演节点引用了未知父节点");
            current = item.parentNodeId();
        }
        return current;
    }

    private static boolean isAtOrBelow(CanvasGraph graph, String nodeId, String ancestorId) {
        String current = nodeId;
        Set<String> seen = new LinkedHashSet<>();
        while (current != null && seen.add(current)) {
            if (ancestorId.equals(current)) return true;
            current = treeParentId(graph, current);
        }
        return false;
    }

    private static String treeParentId(CanvasGraph graph, String nodeId) {
        return graph.relations().stream().filter(relation -> "TREE_PARENT".equals(relation.type())
                && nodeId.equals(relation.fromNodeId())).map(GraphRelation::toNodeId).findFirst().orElse(null);
    }

    private static String inferenceInstruction(String direction, String depth, String title) {
        String action = switch (direction) {
            case "DOWNWARD" -> "向下扩展";
            case "PREREQUISITES" -> "补齐前置依赖";
            case "SIBLINGS" -> "横向扩展同级能力";
            default -> "根据目标职业检查缺口";
        };
        return action + "：“" + title + "”，生成" + ("ONE_LEVEL".equals(depth) ? "一层候选" : "完整发展分支");
    }

    private void applyItem(CanvasGraph graph, ProposalItemRow item, Map<String, String> addedIds) {
        JsonNode after = item.after();
        switch (item.operation()) {
            case "ADD" -> {
                String parentId = resolveProposalParent(item.parentNodeId(), addedIds);
                GraphNode parent = requireNode(graph, parentId);
                if ("EVIDENCE".equals(parent.type())) throw AppException.user("CP_CANVAS_PARENT_INVALID", "证据节点不能包含子节点");
                String type = enumValue(after.path("nodeType").asText(), NODE_TYPES,
                        "CP_CANVAS_NODE_TYPE_INVALID", "能力节点类型无效");
                if ("CAREER".equals(type)) throw AppException.user("CP_CANVAS_ROOT_INVALID", "不能通过差异建议新增职业根节点");
                String logicalId = Ids.newId();
                int order = graph.nodes().values().stream().mapToInt(GraphNode::sortOrder).max().orElse(0) + 1;
                String title = required(after.path("title").asText(), "CP_CANVAS_NODE_TITLE_REQUIRED", "节点名称不能为空");
                String status = proposalStatus(after.path("status").asText("NOT_STARTED"));
                JsonNode detail = sanitizedAiDetail(after.path("detail"), mapper.createObjectNode());
                List<String> refs = stringList(after.path("sourceRefs"));
                graph.nodes().put(logicalId, new GraphNode(logicalId, type, status, title, detail, refs,
                        Math.max(-20_000, parent.x() - 300), parent.y() + 80, false, order));
                graph.relations().add(new GraphRelation(Ids.newId(), logicalId, parentId, "TREE_PARENT"));
                addedIds.put(item.proposalKey(), logicalId);
            }
            case "UPDATE" -> {
                GraphNode node = requireNode(graph, item.targetNodeId());
                String title = clean(after.path("title").asText());
                String status = clean(after.path("status").asText());
                JsonNode detail = sanitizedAiDetail(after.path("detail"), node.detail());
                List<String> refs = after.path("sourceRefs").isArray() ? stringList(after.path("sourceRefs")) : node.sourceRefs();
                graph.nodes().put(node.logicalId(), new GraphNode(node.logicalId(), node.type(),
                        status == null ? node.status() : proposalStatus(status), title == null ? node.title() : title,
                        detail, refs, node.x(), node.y(), node.locked(), node.sortOrder()));
            }
            case "MOVE" -> {
                GraphNode node = requireNode(graph, item.targetNodeId());
                String parentId = resolveProposalParent(item.parentNodeId(), addedIds);
                GraphNode parent = requireNode(graph, parentId);
                if (node.logicalId().equals(parentId) || "EVIDENCE".equals(parent.type())) {
                    throw AppException.user("CP_CANVAS_PARENT_INVALID", "差异建议中的父节点无效");
                }
                graph.relations().removeIf(relation -> "TREE_PARENT".equals(relation.type())
                        && node.logicalId().equals(relation.fromNodeId()));
                graph.relations().add(new GraphRelation(Ids.newId(), node.logicalId(), parentId, "TREE_PARENT"));
            }
            case "DELETE" -> {
                GraphNode node = requireNode(graph, item.targetNodeId());
                Set<String> descendants = descendants(graph, node.logicalId());
                if (!new LinkedHashSet<>(item.impactNodeIds()).containsAll(descendants)) {
                    throw AppException.conflict("CP_PROPOSAL_IMPACT_CHANGED", "删除建议的影响范围已变化，请重新生成差异");
                }
                descendants.add(node.logicalId());
                graph.nodes().keySet().removeAll(descendants);
                graph.relations().removeIf(relation -> descendants.contains(relation.fromNodeId())
                        || descendants.contains(relation.toNodeId()));
            }
            case "ADD_RELATION" -> {
                String fromNodeId = resolveProposalReference(after.path("fromNodeId").asText(null), addedIds);
                String toNodeId = resolveProposalReference(after.path("toNodeId").asText(null), addedIds);
                requireNode(graph, fromNodeId);
                requireNode(graph, toNodeId);
                if (fromNodeId.equals(toNodeId)) {
                    throw AppException.user("CP_CANVAS_RELATION_INVALID", "前置依赖不能指向节点自身");
                }
                boolean duplicate = graph.relations().stream().anyMatch(relation ->
                        "PREREQUISITE".equals(relation.type())
                                && fromNodeId.equals(relation.fromNodeId())
                                && toNodeId.equals(relation.toNodeId()));
                if (duplicate) {
                    throw AppException.conflict("CP_CANVAS_RELATION_DUPLICATE", "该前置依赖已经存在");
                }
                graph.relations().add(new GraphRelation(Ids.newId(), fromNodeId, toNodeId, "PREREQUISITE"));
            }
            default -> throw AppException.user("CP_PROPOSAL_OPERATION_INVALID", "差异操作无效");
        }
    }

    private CanvasView persistGraph(String accountId, String goalId, CanvasGraph graph, String reason,
            String summary, String createdBy, String promptVersion, String schemaVersion,
            String model, String responseHash) {
        validateGraph(graph);
        String versionId = Ids.newId();
        int versionNo = graph.base().version() + 1;
        String hash = graphHash(graph);
        Instant now = clock.now();
        jdbc.update("INSERT INTO canvas_versions(id,goal_id,account_id,version_no,parent_version_id,reason_code,graph_hash,created_by,created_at,change_summary,prompt_version,schema_version,model_code,response_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                versionId, goalId, accountId, versionNo, graph.base().id(), reason, hash, createdBy, now,
                summary, promptVersion, schemaVersion, model, responseHash);
        for (GraphNode node : graph.nodes().values().stream().sorted(Comparator.comparingInt(GraphNode::sortOrder)).toList()) {
            jdbc.update("INSERT INTO canvas_nodes(id,version_id,logical_node_id,account_id,node_type,node_status,title,detail_json,source_refs_json,position_x,position_y,locked,sort_order,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Ids.newId(), versionId, node.logicalId(), accountId, node.type(), node.status(), node.title(),
                    json(node.detail()), json(node.sourceRefs()), node.x(), node.y(), node.locked(), node.sortOrder(), now);
        }
        for (GraphRelation relation : graph.relations()) {
            jdbc.update("INSERT INTO node_relations(id,version_id,account_id,from_logical_id,to_logical_id,relation_type,created_at) VALUES(?,?,?,?,?,?,?)",
                    Ids.newId(), versionId, accountId, relation.fromNodeId(), relation.toNodeId(), relation.type(), now);
        }
        jdbc.update("UPDATE career_planning_sessions SET phase_code='CANVAS',updated_at=?,version_no=version_no+1 WHERE current_goal_id=? AND account_id=? AND status='ACTIVE'",
                now, goalId, accountId);
        return canvasView(new VersionRow(versionId, goalId, accountId, versionNo, graph.base().id(), reason,
                summary, hash, createdBy, promptVersion, schemaVersion, model, responseHash, now));
    }

    private void updateNodeFromTask(String accountId, String goalId, String nodeId, String status, String title) {
        CanvasGraph graph = latestGraph(accountId, goalId, true);
        GraphNode node = graph.nodes().get(nodeId);
        if (node == null || "MASTERED".equals(node.status()) || status.equals(node.status())) return;
        graph.nodes().put(nodeId, node.withStatus(status));
        persistGraph(accountId, goalId, graph, "PLAN_TASK_STATUS_SYNC",
                "任务进度同步到能力节点：" + title, "SYSTEM", null, null, null, null);
    }

    private ValidationView validationView(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        List<String> evidenceIds = jdbc.query("SELECT evidence_id FROM career_ability_validation_evidences WHERE validation_id=? ORDER BY evidence_id",
                (inner, n) -> inner.getString("evidence_id"), id);
        return new ValidationView(id, rs.getString("batch_id"), rs.getString("session_id"), rs.getString("goal_id"), rs.getString("plan_id"),
                rs.getString("logical_node_id"), rs.getString("canvas_version_id"), rs.getString("method_code"),
                rs.getString("status"), read(rs.getString("submission_json")), read(rs.getString("score_json")),
                rs.getString("result_code"), read(rs.getString("feedback_json")), rs.getBoolean("user_confirmed"),
                evidenceIds, rs.getString("prompt_version"), rs.getString("schema_version"), rs.getString("model_code"),
                instant(rs, "created_at"), instant(rs, "evaluated_at"), instant(rs, "confirmed_at"));
    }

    private LearningPlanView planView(String planId, String accountId) {
        PlanRow row = jdbc.query("SELECT * FROM career_learning_plans WHERE id=? AND account_id=?",
                (rs, n) -> planRow(rs), planId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_PLAN_NOT_FOUND", "学习计划不存在"));
        List<PlanTaskView> tasks = jdbc.query("SELECT * FROM career_learning_plan_tasks WHERE plan_id=? AND account_id=? ORDER BY week_no,sort_order",
                (rs, n) -> taskView(rs), planId, accountId);
        List<LearningEvidenceView> evidences = jdbc.query("SELECT * FROM career_learning_evidences WHERE plan_id=? AND account_id=? AND archived_at IS NULL ORDER BY created_at",
                (rs, n) -> evidenceView(rs), planId, accountId);
        List<WeeklyReviewView> reviews = jdbc.query("SELECT * FROM career_learning_weekly_reviews WHERE plan_id=? AND account_id=? ORDER BY week_no",
                (rs, n) -> new WeeklyReviewView(rs.getString("id"), rs.getInt("week_no"),
                        rs.getString("completed_summary"), rs.getString("blockers_text"),
                        rs.getString("adjustment_text"), rs.getString("next_week_focus"),
                        instant(rs, "created_at"), instant(rs, "updated_at")), planId, accountId);
        return new LearningPlanView(row.id(), row.sessionId(), row.goalId(), row.canvasVersionId(), row.canvasVersion(),
                row.durationWeeks(), row.intensity(), row.weeklyHours(), row.learningDays(), row.startDate(),
                row.targetDate(), row.status(), row.version(), row.generationMethod(), row.promptVersion(),
                row.schemaVersion(), row.model(), tasks, evidences, reviews, row.createdAt(), row.updatedAt(),
                row.completedAt(), row.archivedAt(), row.currentRevisionId(),
                currentPlanRevision(planId, accountId));
    }

    private LearningPlanRevisionView planRevisionView(ResultSet rs) throws SQLException {
        return new LearningPlanRevisionView(rs.getString("id"), rs.getString("plan_id"),
                rs.getInt("revision_no"), rs.getString("parent_revision_id"),
                rs.getString("restored_from_revision_id"), rs.getString("reason_code"),
                rs.getString("plan_hash"), read(rs.getString("snapshot_json")),
                rs.getString("created_by"), instant(rs, "created_at"));
    }

    private ProposalView proposalView(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        List<ProposalItemView> items = jdbc.query("SELECT * FROM career_canvas_ai_proposal_items WHERE proposal_id=? ORDER BY sequence_no",
                (inner, n) -> proposalItemView(inner), id);
        return new ProposalView(id, rs.getString("session_id"), rs.getString("goal_id"),
                rs.getString("base_version_id"), rs.getInt("base_version_no"), rs.getString("status"),
                rs.getString("proposal_type"), rs.getString("target_logical_node_id"),
                rs.getString("direction_code"), rs.getString("depth_code"), rs.getString("task_id"),
                rs.getString("instruction_text"), rs.getString("prompt_version"), rs.getString("schema_version"),
                rs.getString("model_code"), items, instant(rs, "created_at"), instant(rs, "decided_at"));
    }

    private ProposalItemView proposalItemView(ResultSet rs) throws SQLException {
        return new ProposalItemView(rs.getString("id"), rs.getInt("sequence_no"), rs.getString("proposal_key"),
                rs.getString("operation_code"), rs.getString("target_logical_node_id"),
                rs.getString("parent_logical_node_id"), read(rs.getString("before_json")), read(rs.getString("after_json")),
                rs.getString("reason_text"), stringList(rs.getString("source_refs_json")),
                stringList(rs.getString("impact_node_ids_json")), rs.getString("decision_status"),
                rs.getString("rejection_reason"), instant(rs, "decided_at"));
    }

    private CanvasView canvasView(VersionRow version) {
        List<CanvasNodeView> nodes = jdbc.query("SELECT * FROM canvas_nodes WHERE version_id=? ORDER BY sort_order",
                (rs, n) -> new CanvasNodeView(rs.getString("logical_node_id"), rs.getString("node_type"),
                        rs.getString("node_status"), rs.getString("title"), read(rs.getString("detail_json")),
                        stringList(rs.getString("source_refs_json")), rs.getInt("position_x"), rs.getInt("position_y"),
                        rs.getBoolean("locked"), rs.getInt("sort_order")), version.id());
        List<CanvasRelationView> relations = jdbc.query("SELECT * FROM node_relations WHERE version_id=? ORDER BY id",
                (rs, n) -> new CanvasRelationView(rs.getString("id"), rs.getString("from_logical_id"),
                        rs.getString("to_logical_id"), rs.getString("relation_type")), version.id());
        return new CanvasView(version.id(), version.version(), version.parentVersionId(), version.reason(),
                version.summary(), version.graphHash(), version.promptVersion(), version.schemaVersion(),
                version.model(), nodes, relations, version.createdAt());
    }

    private CanvasGraph latestGraph(String accountId, String goalId, boolean lock) {
        String sql = "SELECT * FROM canvas_versions WHERE goal_id=? AND account_id=? ORDER BY version_no DESC LIMIT 1"
                + (lock ? " FOR UPDATE" : "");
        VersionRow version = jdbc.query(sql, (rs, n) -> versionRow(rs), goalId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.conflict("CP_CANVAS_NOT_FOUND", "职业能力画布尚未创建"));
        return graphByVersion(version);
    }

    private CanvasGraph graphByVersion(VersionRow version) {
        LinkedHashMap<String, GraphNode> nodes = new LinkedHashMap<>();
        jdbc.query("SELECT * FROM canvas_nodes WHERE version_id=? ORDER BY sort_order,created_at", rs -> {
            GraphNode node = new GraphNode(rs.getString("logical_node_id"), rs.getString("node_type"),
                    rs.getString("node_status"), rs.getString("title"), read(rs.getString("detail_json")),
                    stringList(rs.getString("source_refs_json")), rs.getInt("position_x"), rs.getInt("position_y"),
                    rs.getBoolean("locked"), rs.getInt("sort_order"));
            nodes.put(node.logicalId(), node);
        }, version.id());
        List<GraphRelation> relations = jdbc.query("SELECT * FROM node_relations WHERE version_id=? ORDER BY id",
                (rs, n) -> new GraphRelation(rs.getString("id"), rs.getString("from_logical_id"),
                        rs.getString("to_logical_id"), rs.getString("relation_type")), version.id());
        return new CanvasGraph(version, nodes, new ArrayList<>(relations));
    }

    private VersionRow requireVersion(String accountId, String goalId, int version) {
        return jdbc.query("SELECT * FROM canvas_versions WHERE goal_id=? AND account_id=? AND version_no=?",
                (rs, n) -> versionRow(rs), goalId, accountId, version).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_CANVAS_VERSION_NOT_FOUND", "画布版本不存在"));
    }

    private VersionRow versionRow(ResultSet rs) throws SQLException {
        return new VersionRow(rs.getString("id"), rs.getString("goal_id"), rs.getString("account_id"),
                rs.getInt("version_no"), rs.getString("parent_version_id"), rs.getString("reason_code"),
                rs.getString("change_summary"), rs.getString("graph_hash"), rs.getString("created_by"),
                rs.getString("prompt_version"), rs.getString("schema_version"), rs.getString("model_code"),
                rs.getString("response_hash"), instant(rs, "created_at"));
    }

    private SessionRow requireSession(CurrentAccount current, String sessionId) {
        return requireCanvasSession(current, sessionId, false);
    }

    private SessionRow requireCanvasSession(CurrentAccount current, String sessionId) {
        return requireCanvasSession(current, sessionId, false);
    }

    private SessionRow requireCanvasSession(CurrentAccount current, String sessionId, boolean lock) {
        assertEnabled();
        assertUser(current);
        String sql = "SELECT * FROM career_planning_sessions WHERE id=? AND account_id=?" + (lock ? " FOR UPDATE" : "");
        SessionRow session = jdbc.query(sql, (rs, n) -> new SessionRow(rs.getString("id"), rs.getString("account_id"),
                rs.getString("status"), rs.getString("phase_code"), rs.getBoolean("ai_consent"),
                rs.getString("current_profile_id"), rs.getString("current_recommendation_set_id"),
                rs.getString("current_goal_id"), rs.getInt("version_no")), sessionId, current.accountId())
                .stream().findFirst().orElseThrow(() -> AppException.user("CP_SESSION_NOT_FOUND", "职业规划会话不存在"));
        if (!"ACTIVE".equals(session.status())) throw AppException.conflict("CP_SESSION_ARCHIVED", "职业规划已归档");
        if (session.goalId() == null && !"PROFILE".equals(session.phase()) && !"RECOMMENDATIONS".equals(session.phase())) {
            throw AppException.conflict("CP_GOAL_NOT_CONFIRMED", "请先确认目标职业");
        }
        return session;
    }

    private ProposalView requireProposal(String accountId, String sessionId, String proposalId, boolean lock) {
        String sql = "SELECT * FROM career_canvas_ai_proposals WHERE id=? AND session_id=? AND account_id=?"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, n) -> proposalView(rs), proposalId, sessionId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_PROPOSAL_NOT_FOUND", "AI 差异建议不存在"));
    }

    private ProposalRow requireProposalRow(String accountId, String sessionId, String proposalId, boolean lock) {
        String sql = "SELECT * FROM career_canvas_ai_proposals WHERE id=? AND session_id=? AND account_id=?"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, n) -> new ProposalRow(rs.getString("id"), rs.getString("status"),
                rs.getInt("base_version_no"), rs.getString("model_code"), rs.getString("response_hash")),
                proposalId, sessionId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_PROPOSAL_NOT_FOUND", "AI 差异建议不存在"));
    }

    private List<ProposalItemRow> proposalItemRows(String proposalId, String accountId) {
        return jdbc.query("SELECT * FROM career_canvas_ai_proposal_items WHERE proposal_id=? AND account_id=? ORDER BY sequence_no",
                (rs, n) -> new ProposalItemRow(rs.getString("id"), rs.getString("proposal_key"),
                        rs.getString("operation_code"), rs.getString("target_logical_node_id"),
                        rs.getString("parent_logical_node_id"), read(rs.getString("after_json")),
                        stringList(rs.getString("impact_node_ids_json"))), proposalId, accountId);
    }

    private PlanRow requirePlan(String accountId, String sessionId, String planId, boolean lock) {
        String sql = "SELECT * FROM career_learning_plans WHERE id=? AND session_id=? AND account_id=?"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, n) -> planRow(rs), planId, sessionId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_PLAN_NOT_FOUND", "学习计划不存在"));
    }

    private PlanRow planRow(ResultSet rs) throws SQLException {
        return new PlanRow(rs.getString("id"), rs.getString("session_id"), rs.getString("goal_id"),
                rs.getString("canvas_version_id"), rs.getInt("canvas_version_no"), rs.getInt("duration_weeks"),
                rs.getString("intensity"), rs.getInt("weekly_hours"), integerList(rs.getString("learning_days_json")),
                date(rs, "start_date"), date(rs, "target_date"), rs.getString("status"), rs.getInt("version_no"),
                rs.getString("generation_method"), rs.getString("prompt_version"), rs.getString("schema_version"),
                rs.getString("model_code"), instant(rs, "created_at"), instant(rs, "updated_at"),
                instant(rs, "completed_at"), instant(rs, "archived_at"),
                rs.getString("current_revision_id"));
    }

    private TaskRow requireTask(String accountId, String planId, String taskId, boolean lock) {
        String sql = "SELECT * FROM career_learning_plan_tasks WHERE id=? AND plan_id=? AND account_id=?"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, n) -> taskRow(rs), taskId, planId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_PLAN_TASK_NOT_FOUND", "计划任务不存在"));
    }

    private TaskRow taskRow(ResultSet rs) throws SQLException {
        return new TaskRow(rs.getString("id"), rs.getString("plan_id"), rs.getString("logical_node_id"),
                rs.getString("title"), rs.getInt("week_no"), rs.getInt("estimated_minutes"),
                date(rs, "due_date"), rs.getString("status"), rs.getBoolean("evidence_required"),
                rs.getInt("version_no"));
    }

    private PlanTaskView taskView(ResultSet rs) throws SQLException {
        return new PlanTaskView(rs.getString("id"), rs.getString("logical_node_id"), rs.getString("task_type"),
                rs.getInt("week_no"), rs.getString("title"), rs.getString("description_text"),
                rs.getString("priority"), rs.getInt("estimated_minutes"), date(rs, "due_date"),
                rs.getString("status"), rs.getBoolean("evidence_required"), rs.getInt("sort_order"),
                rs.getInt("version_no"), instant(rs, "created_at"), instant(rs, "updated_at"),
                instant(rs, "completed_at"));
    }

    private LearningEvidenceView evidenceView(ResultSet rs) throws SQLException {
        return new LearningEvidenceView(rs.getString("id"), rs.getString("plan_id"), rs.getString("task_id"),
                rs.getString("logical_node_id"), rs.getString("source_type"), rs.getString("source_id"),
                rs.getString("title"), rs.getString("note_text"), rs.getString("verification_status"),
                instant(rs, "created_at"), instant(rs, "confirmed_at"));
    }

    private LearningEvidenceView requireEvidence(String accountId, String evidenceId) {
        return jdbc.query("SELECT * FROM career_learning_evidences WHERE id=? AND account_id=? AND archived_at IS NULL",
                (rs, n) -> evidenceView(rs), evidenceId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_EVIDENCE_NOT_FOUND", "能力证据不存在"));
    }

    private ValidationView requireValidation(String accountId, String sessionId, String validationId) {
        return jdbc.query("SELECT * FROM career_ability_validations WHERE id=? AND session_id=? AND account_id=?",
                (rs, n) -> validationView(rs), validationId, sessionId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_VALIDATION_NOT_FOUND", "能力验证不存在"));
    }

    private ValidationRow requireValidationRow(String accountId, String sessionId, String validationId, boolean lock) {
        String sql = "SELECT * FROM career_ability_validations WHERE id=? AND session_id=? AND account_id=?"
                + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, n) -> new ValidationRow(rs.getString("id"), rs.getString("logical_node_id"),
                rs.getString("status"), rs.getString("result_code")), validationId, sessionId, accountId)
                .stream().findFirst().orElseThrow(() -> AppException.user("CP_VALIDATION_NOT_FOUND", "能力验证不存在"));
    }

    private GoalRow requireGoal(String accountId, String goalId) {
        return jdbc.query("SELECT id,title,status FROM career_goals WHERE id=? AND account_id=?",
                (rs, n) -> new GoalRow(rs.getString("id"), rs.getString("title"), rs.getString("status")),
                goalId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_GOAL_NOT_FOUND", "职业目标不存在"));
    }

    private RecommendationRow requireRecommendation(String accountId, String sessionId, String recommendationId) {
        return jdbc.query("SELECT r.id,r.taxonomy_node_id,r.title FROM career_recommendations r JOIN career_recommendation_sets s ON s.id=r.set_id WHERE r.id=? AND r.account_id=? AND s.session_id=?",
                (rs, n) -> new RecommendationRow(rs.getString("id"), rs.getString("taxonomy_node_id"),
                        rs.getString("title")), recommendationId, accountId, sessionId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_RECOMMENDATION_NOT_FOUND", "新的目标职业不属于当前规划"));
    }

    private List<GraphNode> orderedPlanNodes(CanvasGraph graph) {
        Set<String> eligible = graph.nodes().values().stream()
                .filter(node -> Set.of("SKILL", "KNOWLEDGE", "TASK").contains(node.type()))
                .filter(node -> !Set.of("MASTERED", "PAUSED").contains(node.status()))
                .map(GraphNode::logicalId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> edges = new LinkedHashMap<>();
        eligible.forEach(id -> inDegree.put(id, 0));
        graph.relations().stream().filter(relation -> "PREREQUISITE".equals(relation.type())
                && eligible.contains(relation.fromNodeId()) && eligible.contains(relation.toNodeId()))
                .forEach(relation -> {
                    edges.computeIfAbsent(relation.fromNodeId(), ignored -> new ArrayList<>()).add(relation.toNodeId());
                    inDegree.compute(relation.toNodeId(), (id, value) -> value == null ? 1 : value + 1);
                });
        Comparator<String> comparator = Comparator.comparingInt((String id) -> nodeTypeRank(graph.nodes().get(id)))
                .thenComparingInt(id -> graph.nodes().get(id).sortOrder());
        List<String> ready = inDegree.entrySet().stream().filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey).sorted(comparator).collect(Collectors.toCollection(ArrayList::new));
        List<GraphNode> out = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.remove(0);
            out.add(graph.nodes().get(id));
            for (String next : edges.getOrDefault(id, List.of())) {
                int value = inDegree.compute(next, (key, count) -> count - 1);
                if (value == 0) {
                    ready.add(next);
                    ready.sort(comparator);
                }
            }
        }
        if (out.size() != eligible.size()) throw AppException.conflict("CP_CANVAS_DEPENDENCY_CYCLE", "画布依赖存在循环，无法生成计划");
        return out;
    }

    private void validateGraph(CanvasGraph graph) {
        List<GraphNode> roots = graph.nodes().values().stream().filter(node -> "CAREER".equals(node.type())).toList();
        if (roots.size() != 1 || !roots.get(0).locked()) throw AppException.conflict("CP_CANVAS_ROOT_INVALID", "职业能力树必须包含一个锁定根节点");
        Map<String, String> parents = new LinkedHashMap<>();
        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        for (GraphNode node : graph.nodes().values()) {
            if (!NODE_TYPES.contains(node.type()) || !NODE_STATUSES.contains(node.status())
                    || clean(node.title()) == null || !node.detail().isObject()) {
                throw AppException.user("CP_CANVAS_NODE_INVALID", "职业能力节点字段无效");
            }
        }
        for (GraphRelation relation : graph.relations()) {
            if (!Set.of("TREE_PARENT", "PREREQUISITE").contains(relation.type())
                    || !graph.nodes().containsKey(relation.fromNodeId()) || !graph.nodes().containsKey(relation.toNodeId())
                    || !keys.add(relation.type() + ':' + relation.fromNodeId() + ':' + relation.toNodeId())) {
                throw AppException.user("CP_CANVAS_RELATION_INVALID", "职业能力节点关系无效");
            }
            if ("TREE_PARENT".equals(relation.type())) {
                if (parents.put(relation.fromNodeId(), relation.toNodeId()) != null) {
                    throw AppException.user("CP_CANVAS_MULTIPLE_PARENTS", "每个节点只能有一个分类父节点");
                }
            } else dependencies.computeIfAbsent(relation.fromNodeId(), ignored -> new ArrayList<>()).add(relation.toNodeId());
        }
        String root = roots.get(0).logicalId();
        for (String id : graph.nodes().keySet()) {
            if (id.equals(root)) continue;
            Set<String> seen = new LinkedHashSet<>();
            String cursor = id;
            while (!cursor.equals(root)) {
                if (!seen.add(cursor)) throw AppException.conflict("CP_CANVAS_DEPENDENCY_CYCLE", "分类关系形成循环");
                cursor = parents.get(cursor);
                if (cursor == null) throw AppException.user("CP_CANVAS_PARENT_REQUIRED", "每个非根节点必须连接到职业根节点");
            }
        }
        if (hasCycle(graph.nodes().keySet(), dependencies)) {
            throw AppException.conflict("CP_CANVAS_DEPENDENCY_CYCLE", "前置依赖形成循环");
        }
    }

    private static boolean hasCycle(Set<String> nodes, Map<String, List<String>> edges) {
        Set<String> visited = new LinkedHashSet<>();
        Set<String> active = new LinkedHashSet<>();
        for (String node : nodes) if (cycle(node, edges, visited, active)) return true;
        return false;
    }

    private static boolean cycle(String node, Map<String, List<String>> edges,
            Set<String> visited, Set<String> active) {
        if (active.contains(node)) return true;
        if (!visited.add(node)) return false;
        active.add(node);
        for (String next : edges.getOrDefault(node, List.of())) if (cycle(next, edges, visited, active)) return true;
        active.remove(node);
        return false;
    }

    private Set<String> descendants(CanvasGraph graph, String parentId) {
        Map<String, List<String>> children = new LinkedHashMap<>();
        graph.relations().stream().filter(relation -> "TREE_PARENT".equals(relation.type()))
                .forEach(relation -> children.computeIfAbsent(relation.toNodeId(), ignored -> new ArrayList<>())
                        .add(relation.fromNodeId()));
        Set<String> found = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(children.getOrDefault(parentId, List.of()));
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (found.add(id)) queue.addAll(children.getOrDefault(id, List.of()));
        }
        return found;
    }

    private String insertInitialGraph(String accountId, String goalId, LinkedHashMap<String, GraphNode> nodes,
            List<GraphRelation> relations, Instant now, String reason, String summary) {
        VersionRow synthetic = new VersionRow(null, goalId, accountId, 0, null, reason, summary, "", "USER",
                null, null, null, null, now);
        CanvasGraph graph = new CanvasGraph(synthetic, nodes, relations);
        validateGraph(graph);
        String id = Ids.newId();
        String hash = graphHash(graph);
        jdbc.update("INSERT INTO canvas_versions(id,goal_id,account_id,version_no,parent_version_id,reason_code,graph_hash,created_by,created_at,change_summary,prompt_version,schema_version,model_code,response_hash) VALUES(?,?,?,1,NULL,?,?,'USER',?,?,NULL,NULL,NULL,NULL)",
                id, goalId, accountId, reason, hash, now, summary);
        for (GraphNode node : nodes.values()) jdbc.update(
                "INSERT INTO canvas_nodes(id,version_id,logical_node_id,account_id,node_type,node_status,title,detail_json,source_refs_json,position_x,position_y,locked,sort_order,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                Ids.newId(), id, node.logicalId(), accountId, node.type(), node.status(), node.title(),
                json(node.detail()), json(node.sourceRefs()), node.x(), node.y(), node.locked(), node.sortOrder(), now);
        for (GraphRelation relation : relations) jdbc.update(
                "INSERT INTO node_relations(id,version_id,account_id,from_logical_id,to_logical_id,relation_type,created_at) VALUES(?,?,?,?,?,?,?)",
                Ids.newId(), id, accountId, relation.fromNodeId(), relation.toNodeId(), relation.type(), now);
        return id;
    }

    private void reorderPlanTask(String planId, String accountId, String taskId, int sourceWeek,
            int targetWeek, Integer requestedOrder) {
        List<String> targetIds = jdbc.queryForList(
                "SELECT id FROM career_learning_plan_tasks WHERE plan_id=? AND account_id=? AND week_no=? AND id<>? ORDER BY sort_order,created_at,id",
                String.class, planId, accountId, targetWeek, taskId);
        int targetOrder = requestedOrder == null ? targetIds.size()
                : Math.max(0, Math.min(requestedOrder, targetIds.size()));
        targetIds.add(targetOrder, taskId);
        for (int index = 0; index < targetIds.size(); index++) {
            jdbc.update("UPDATE career_learning_plan_tasks SET sort_order=? WHERE id=? AND account_id=?",
                    index, targetIds.get(index), accountId);
        }
        if (sourceWeek != targetWeek) {
            List<String> sourceIds = jdbc.queryForList(
                    "SELECT id FROM career_learning_plan_tasks WHERE plan_id=? AND account_id=? AND week_no=? ORDER BY sort_order,created_at,id",
                    String.class, planId, accountId, sourceWeek);
            for (int index = 0; index < sourceIds.size(); index++) {
                jdbc.update("UPDATE career_learning_plan_tasks SET sort_order=? WHERE id=? AND account_id=?",
                        index, sourceIds.get(index), accountId);
            }
        }
    }

    private void insertPlanTask(String planId, String accountId, String nodeId, String taskType, int week,
            String title, String description, String priority, int minutes, LocalDate dueDate,
            boolean evidenceRequired, int order, Instant now) {
        jdbc.update("INSERT INTO career_learning_plan_tasks(id,plan_id,account_id,logical_node_id,task_type,week_no,title,description_text,priority,estimated_minutes,due_date,status,evidence_required,sort_order,version_no,created_at,updated_at,completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,'TODO',?,?,0,?,?,NULL)",
                Ids.newId(), planId, accountId, nodeId, taskType, week, limit(title, 255),
                limit(description, 2000), priority, minutes, dueDate, evidenceRequired, order, now, now);
    }

    private void addRemediationTask(String accountId, String goalId, GraphNode node,
            String validationId, Instant now) {
        String planId = jdbc.query("SELECT id FROM career_learning_plans WHERE goal_id=? AND account_id=? AND status='ACTIVE' ORDER BY updated_at DESC LIMIT 1",
                (rs, n) -> rs.getString("id"), goalId, accountId).stream().findFirst().orElse(null);
        if (planId == null) return;
        PlanRow plan = requirePlan(accountId, jdbc.queryForObject("SELECT session_id FROM career_learning_plans WHERE id=?", String.class, planId), planId, false);
        int order = count("SELECT COUNT(*) FROM career_learning_plan_tasks WHERE plan_id=?", planId) + 1;
        int week = Math.min(plan.durationWeeks(), Math.max(1, order / 3 + 1));
        insertPlanTask(planId, accountId, node.logicalId(), "REMEDIATION", week,
                "补强：" + node.title(), "根据验证 " + validationId + " 的缺口完成针对性练习并补充证据。",
                "HIGH", Math.max(60, estimatedMinutes(node) / 2), weekDueDate(plan.startDate(), week, plan.learningDays()),
                true, order, now);
        jdbc.update("UPDATE career_learning_plans SET version_no=version_no+1,updated_at=? WHERE id=?", now, planId);
        recordPlanRevision(planId, accountId, "REMEDIATION_TASK_ADDED", "SYSTEM", now);
    }

    private LearningPlanRevisionView recordPlanRevision(String planId, String accountId,
            String reason, String createdBy, Instant now) {
        PlanRow plan = jdbc.query("SELECT * FROM career_learning_plans WHERE id=? AND account_id=? FOR UPDATE",
                (rs, n) -> planRow(rs), planId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CP_PLAN_NOT_FOUND", "学习计划不存在"));
        int next = currentPlanRevision(planId, accountId) + 1;
        LearningPlanView current = planView(planId, accountId);
        ObjectNode snapshot = mapper.valueToTree(current);
        snapshot.remove("currentRevisionId");
        snapshot.remove("currentRevision");
        String snapshotJson = json(snapshot);
        String revisionId = Ids.newId();
        String planHash = sha256(snapshotJson);
        jdbc.update("INSERT INTO career_learning_plan_revisions(id,plan_id,account_id,revision_no,parent_revision_id,restored_from_revision_id,reason_code,plan_hash,snapshot_json,created_by,created_at) VALUES(?,?,?,?,?,NULL,?,?,?,?,?)",
                revisionId, planId, accountId, next, plan.currentRevisionId(), reason,
                planHash, snapshotJson, createdBy, now);
        jdbc.update("UPDATE career_learning_plans SET current_revision_id=? WHERE id=? AND account_id=?",
                revisionId, planId, accountId);
        return new LearningPlanRevisionView(revisionId, planId, next, plan.currentRevisionId(),
                null, reason, planHash, snapshot, createdBy, now);
    }

    private int currentPlanRevision(String planId, String accountId) {
        Integer value = jdbc.queryForObject("SELECT COALESCE(MAX(revision_no),0) FROM career_learning_plan_revisions WHERE plan_id=? AND account_id=?",
                Integer.class, planId, accountId);
        return value == null ? 0 : value;
    }

    private void validateEvidenceSource(String accountId, String type, String sourceId) {
        if ("USER_NOTE".equals(type)) return;
        String id = required(sourceId, "CP_EVIDENCE_SOURCE_REQUIRED", "请选择证据来源");
        int found = "CAREER_FILE".equals(type)
                ? count("SELECT COUNT(*) FROM career_library_files WHERE id=? AND account_id=? AND status='ACTIVE' AND processing_status='READY'", id, accountId)
                : count("SELECT COUNT(*) FROM career_library_records WHERE id=? AND account_id=? AND status='ACTIVE' AND confirmed=1", id, accountId);
        if (found == 0) throw AppException.forbidden("CP_EVIDENCE_SOURCE_FORBIDDEN", "证据来源不存在、未就绪或不属于当前账号");
    }

    private GraphNode requireNode(CanvasGraph graph, String nodeId) {
        GraphNode node = graph.nodes().get(nodeId);
        if (node == null) throw AppException.user("CP_CANVAS_NODE_NOT_FOUND", "职业能力节点不存在");
        return node;
    }

    private static String resolveProposalParent(String value, Map<String, String> addedIds) {
        String parent = required(value, "CP_CANVAS_PARENT_REQUIRED", "请选择父节点");
        if (!parent.startsWith("PROPOSAL:")) return parent;
        String resolved = addedIds.get(parent.substring("PROPOSAL:".length()));
        if (resolved == null) throw AppException.conflict("CP_PROPOSAL_PARENT_ORDER_INVALID", "新增节点引用了尚未采纳的父节点");
        return resolved;
    }

    private static String resolveProposalReference(String value, Map<String, String> addedIds) {
        String reference = required(value, "CP_CANVAS_RELATION_REQUIRED", "前置关系缺少节点引用");
        if (!reference.startsWith("PROPOSAL:")) return reference;
        String resolved = addedIds.get(reference.substring("PROPOSAL:".length()));
        if (resolved == null) {
            throw AppException.conflict("CP_PROPOSAL_REFERENCE_REJECTED", "关系依赖的新增节点未被采纳");
        }
        return resolved;
    }

    private static void assertAcceptedProposalReference(String value, Set<String> acceptedProposalKeys) {
        if (value == null || !value.startsWith("PROPOSAL:")) return;
        if (!acceptedProposalKeys.contains(value.substring("PROPOSAL:".length()))) {
            throw AppException.user("CP_PROPOSAL_REFERENCE_REJECTED", "采纳项依赖了已拒绝的新增节点，请调整选择");
        }
    }

    private JsonNode sanitizedAiDetail(JsonNode proposed, JsonNode existing) {
        ObjectNode result = existing != null && existing.isObject() ? existing.deepCopy() : mapper.createObjectNode();
        if (proposed != null && proposed.isObject()) proposed.fields().forEachRemaining(entry -> {
            if (!"notes".equals(entry.getKey())) result.set(entry.getKey(), entry.getValue());
        });
        return result;
    }

    private static String proposalStatus(String value) {
        return enumValue(value, EDITABLE_STATUSES, "CP_CANVAS_MASTERY_REQUIRES_VALIDATION",
                "AI 差异不能直接把能力标记为已掌握");
    }

    private String graphHash(CanvasGraph graph) {
        StringBuilder value = new StringBuilder();
        graph.nodes().values().stream().sorted(Comparator.comparing(GraphNode::logicalId)).forEach(node -> value
                .append("N|").append(node.logicalId()).append('|').append(node.type()).append('|')
                .append(node.status()).append('|').append(node.title()).append('|').append(json(node.detail()))
                .append('|').append(json(node.sourceRefs())).append('|').append(node.x()).append('|')
                .append(node.y()).append('|').append(node.locked()).append('|').append(node.sortOrder()).append('\n'));
        graph.relations().stream().sorted(Comparator.comparing(GraphRelation::type)
                .thenComparing(GraphRelation::fromNodeId).thenComparing(GraphRelation::toNodeId))
                .forEach(relation -> value.append("R|").append(relation.type()).append('|')
                        .append(relation.fromNodeId()).append('|').append(relation.toNodeId()).append('\n'));
        return sha256(value.toString());
    }

    private int estimatedMinutes(GraphNode node) {
        int hours = node.detail().path("estimatedHours").asInt(node.type().equals("TASK") ? 3 : 6);
        return Math.max(30, Math.min(2400, hours * 60));
    }

    private static String priority(GraphNode node) {
        String value = node.detail().path("importance").asText("MEDIUM").toUpperCase(Locale.ROOT);
        return Set.of("HIGH", "MEDIUM", "LOW").contains(value) ? value : "MEDIUM";
    }

    private static int nodeTypeRank(GraphNode node) {
        return switch (node.type()) { case "TASK" -> 0; case "SKILL" -> 1; default -> 2; };
    }

    private static String detailText(JsonNode detail, String field) {
        String value = clean(detail.path(field).asText());
        return value == null ? "完成节点学习内容，并产出可验证成果。" : value;
    }

    private static LocalDate weekDueDate(LocalDate start, int week, List<Integer> days) {
        int lastDay = days.stream().max(Integer::compareTo).orElse(7);
        return start.plusWeeks(week - 1L).plusDays(Math.max(0, lastDay - 1L));
    }

    private static List<Integer> normalizedDays(List<Integer> values) {
        List<Integer> days = values == null ? List.of() : values.stream().distinct().sorted().toList();
        if (days.isEmpty() || days.size() > 7 || days.stream().anyMatch(day -> day < 1 || day > 7)) {
            throw AppException.user("CP_PLAN_LEARNING_DAYS_INVALID", "请选择每周学习日");
        }
        return days;
    }

    private static void put(ObjectNode node, String key, String value) {
        if (value != null) node.put(key, value);
    }

    private JsonNode read(String value) {
        try { return mapper.readTree(value == null || value.isBlank() ? "{}" : value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private List<String> stringList(String value) {
        try { return mapper.readValue(value == null ? "[]" : value, STRING_LIST); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private List<String> stringList(JsonNode value) {
        if (!(value instanceof ArrayNode array)) return List.of();
        List<String> result = new ArrayList<>();
        array.forEach(node -> { if (clean(node.asText()) != null) result.add(node.asText().trim()); });
        return List.copyOf(result);
    }

    private List<Integer> integerList(String value) {
        try { return mapper.readValue(value == null ? "[]" : value, INTEGER_LIST); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static String enumValue(String value, Set<String> allowed, String reason, String message) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw AppException.user(reason, message);
        return normalized;
    }

    private static void assertVersion(Integer expected, int actual, String reason, String message) {
        if (expected != null && expected != actual) throw AppException.conflict(reason, message);
    }

    private void assertEnabled() {
        if (!enabled) throw AppException.user("CP_FEATURE_DISABLED", "AI 职业规划正在灰度验收中");
    }

    private static void assertUser(CurrentAccount current) {
        if (current.operator()) throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能查看用户职业规划正文");
    }

    private static String required(String value, String reason, String message) {
        String cleaned = clean(value);
        if (cleaned == null) throw AppException.user(reason, message);
        return cleaned;
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static String limit(String value, int max) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.substring(0, Math.min(cleaned.length(), max));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private record SessionRow(String id, String accountId, String status, String phase,
            boolean aiConsent, String profileId, String recommendationSetId, String goalId, int version) {}
    private record GoalRow(String id, String title, String status) {}
    private record RecommendationRow(String id, String taxonomyNodeId, String title) {}
    private record VersionRow(String id, String goalId, String accountId, int version,
            String parentVersionId, String reason, String summary, String graphHash,
            String createdBy, String promptVersion, String schemaVersion, String model,
            String responseHash, Instant createdAt) {}
    private record GraphNode(String logicalId, String type, String status, String title,
            JsonNode detail, List<String> sourceRefs, int x, int y, boolean locked, int sortOrder) {
        GraphNode withStatus(String value) {
            return new GraphNode(logicalId, type, value, title, detail, sourceRefs, x, y, locked, sortOrder);
        }
    }
    private record GraphRelation(String id, String fromNodeId, String toNodeId, String type) {}
    private record CanvasGraph(VersionRow base, LinkedHashMap<String, GraphNode> nodes,
            List<GraphRelation> relations) {}
    private record InferenceRequest(SessionRow session, GoalRow goal, CanvasGraph graph,
            GraphNode target, String parentId, String direction, String depth, String instruction,
            int expectedVersion, Set<String> allowedRefs) {}
    private record ProposalRow(String id, String status, int baseVersion, String model, String responseHash) {}
    private record ProposalItemRow(String id, String proposalKey, String operation,
            String targetNodeId, String parentNodeId, JsonNode after, List<String> impactNodeIds) {}
    private record PlanRow(String id, String sessionId, String goalId, String canvasVersionId,
            int canvasVersion, int durationWeeks, String intensity, int weeklyHours,
            List<Integer> learningDays, LocalDate startDate, LocalDate targetDate, String status,
            int version, String generationMethod, String promptVersion, String schemaVersion,
            String model, Instant createdAt, Instant updatedAt, Instant completedAt, Instant archivedAt,
            String currentRevisionId) {}
    private record TaskRow(String id, String planId, String nodeId, String title, int week,
            int estimatedMinutes, LocalDate dueDate, String status, boolean evidenceRequired, int version) {}
    private record ValidationRow(String id, String nodeId, String status, String result) {}
    private record PreparedValidation(GraphNode node, List<String> evidenceIds,
            List<LearningEvidenceView> evidence, JsonNode submission) {}
    private record PreparedValidationBatch(SessionRow session, CanvasGraph graph, String method,
            int expectedVersion, List<PreparedValidation> items) {}
}
