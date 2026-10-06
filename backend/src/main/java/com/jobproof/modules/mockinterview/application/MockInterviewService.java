package com.jobproof.modules.mockinterview.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.application.AiGatewayException;
import com.jobproof.modules.mockinterview.application.MockInterviewAiService.Evaluation;
import com.jobproof.modules.mockinterview.application.MockInterviewAiService.GeneratedQuestions;
import com.jobproof.modules.mockinterview.application.MockInterviewAiService.QuestionInput;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MockInterviewService {

    private static final Set<String> MODES = Set.of("TEXT", "VOICE");
    private static final Set<String> TYPES = Set.of("COMPREHENSIVE", "BEHAVIORAL", "PROFESSIONAL", "PRESSURE", "QUICK");
    private static final Set<String> DIFFICULTIES = Set.of("FOUNDATION", "STANDARD", "ADVANCED");
    private static final Set<String> FEEDBACK_MODES = Set.of("AFTER_EACH", "AFTER_SESSION");
    private static final Set<String> ACTIVE_STATUSES = Set.of("READY", "IN_PROGRESS", "ANSWERING", "ANALYZING", "FEEDBACK", "PAUSED", "OFFLINE", "TRANSCRIPTION_FAILED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final MockInterviewAiService ai;
    private final TransactionTemplate transactions;

    public MockInterviewService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock, MockInterviewAiService ai,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.ai = ai;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(CurrentAccount current) {
        assertSeeker(current);
        String accountId = current.accountId();
        int total = count("SELECT COUNT(*) FROM mock_interview_sessions WHERE account_id=?", accountId);
        int completed = count("SELECT COUNT(*) FROM mock_interview_sessions WHERE account_id=? AND status='COMPLETED'", accountId);
        int text = count("SELECT COUNT(*) FROM mock_interview_sessions WHERE account_id=? AND mode='TEXT'", accountId);
        int voice = count("SELECT COUNT(*) FROM mock_interview_sessions WHERE account_id=? AND mode='VOICE'", accountId);
        Integer average = jdbc.queryForObject(
                "SELECT CAST(AVG(overall_score) AS INTEGER) FROM mock_interview_reports WHERE account_id=?",
                Integer.class, accountId);
        List<SessionSummary> recent = list(current, null, null, null, 6);
        SessionSummary resumable = recent.stream().filter(item -> ACTIVE_STATUSES.contains(item.status())).findFirst().orElse(null);
        return new DashboardView(total, completed, text, voice, average, resumable, recent);
    }

    @Transactional
    public DraftView createDraft(CurrentAccount current) {
        assertSeeker(current);
        Instant now = clock.now();
        String id = Ids.newId();
        jdbc.update("INSERT INTO mock_interview_drafts(id,account_id,step_no,status,payload_json,version_no,created_at,updated_at) VALUES(?,?,1,'DRAFT','{}',0,?,?)",
                id, current.accountId(), now, now);
        return draft(current.accountId(), id);
    }

    @Transactional
    public DraftView saveDraft(CurrentAccount current, String draftId, DraftCommand command) {
        assertSeeker(current);
        DraftView existing = draft(current.accountId(), draftId);
        assertVersion(command.expectedVersion(), existing.version());
        int step = Math.max(1, Math.min(5, command.step()));
        int changed = jdbc.update("UPDATE mock_interview_drafts SET step_no=?,payload_json=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=? AND version_no=?",
                step, json(command.payload() == null ? Map.of() : command.payload()), clock.now(), draftId, current.accountId(), existing.version());
        requireUpdated(changed);
        return draft(current.accountId(), draftId);
    }

    @Transactional(readOnly = true)
    public DraftView getDraft(CurrentAccount current, String draftId) {
        assertSeeker(current);
        return draft(current.accountId(), draftId);
    }

    @Transactional
    public SessionView createSession(CurrentAccount current, CreateSessionCommand command) {
        assertSeeker(current);
        String accountId = current.accountId();
        String mode = enumValue(command.mode(), MODES, "TEXT", "MOCK_INTERVIEW_MODE_INVALID");
        String type = enumValue(command.interviewType(), TYPES, "COMPREHENSIVE", "MOCK_INTERVIEW_TYPE_INVALID");
        String difficulty = enumValue(command.difficulty(), DIFFICULTIES, "STANDARD", "MOCK_INTERVIEW_DIFFICULTY_INVALID");
        String feedbackMode = enumValue(command.feedbackMode(), FEEDBACK_MODES, "AFTER_SESSION", "MOCK_INTERVIEW_FEEDBACK_INVALID");
        String position = required(command.positionName(), "MOCK_INTERVIEW_POSITION_REQUIRED", "请选择目标岗位");
        int duration = Math.max(10, Math.min(90, command.durationMinutes()));
        int questionCount = Math.max(5, Math.min(15, command.questionCount()));
        if (command.resumeId() == null || command.resumeId().isBlank()) {
            throw AppException.user("MOCK_INTERVIEW_RESUME_REQUIRED", "请选择一份自己的简历");
        }
        Map<String, Object> resume = resumeSnapshot(accountId, command.resumeId());
        Map<String, Object> jd = jobMatchSnapshot(accountId, command.jobMatchId());
        Map<String, Object> materials = new LinkedHashMap<>();
        materials.put("careerRecordIds", command.careerRecordIds() == null ? List.of() : command.careerRecordIds());
        materials.put("careerFileIds", command.careerFileIds() == null ? List.of() : command.careerFileIds());
        materials.put("userNote", clean(command.userNote()));
        materials.put("jobMatchId", clean(command.jobMatchId()));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("mode", mode);
        settings.put("taxonomyNodeId", clean(command.taxonomyNodeId()));
        settings.put("taxonomyCategoryId", clean(command.taxonomyCategoryId()));
        settings.put("taxonomyGroupId", clean(command.taxonomyGroupId()));
        settings.put("interviewType", type);
        settings.put("difficulty", difficulty);
        settings.put("durationMinutes", duration);
        settings.put("questionCount", questionCount);
        settings.put("languageCode", blankTo(command.languageCode(), "zh-CN"));
        settings.put("feedbackMode", feedbackMode);
        settings.put("followUpEnabled", command.followUpEnabled());
        settings.put("consentConfirmed", command.consentConfirmed());
        if (!command.consentConfirmed()) {
            throw AppException.user("MOCK_INTERVIEW_CONSENT_REQUIRED", "开始前需要确认本次资料授权范围");
        }
        if (command.draftId() != null && !command.draftId().isBlank()) {
            draft(accountId, command.draftId());
        }

        Instant now = clock.now();
        String id = Ids.newId();
        String company = clean(command.companyName());
        String title = position + (company == null || company.isBlank() ? " · 模拟面试" : " · " + company);
        List<QuestionSeed> seeds;
        try {
            GeneratedQuestions generated = ai.generateQuestions(accountId, position, company, type, difficulty,
                    questionCount, resume, jd);
            seeds = generated.questions().stream().map(item ->
                    seed(item.type(), item.prompt(), item.sourceLabel(), item.sourceRefs())).toList();
            settings.put("questionGenerationMode", "AI_MODEL");
            settings.put("questionModel", generated.model());
            settings.put("questionChannelId", generated.channelId());
            settings.put("questionInputTokens", generated.inputTokens());
            settings.put("questionOutputTokens", generated.outputTokens());
        } catch (AiGatewayException exception) {
            seeds = questions(position, company, type, difficulty, questionCount, resume, jd);
            settings.put("questionGenerationMode", "BASIC_RULES");
            settings.put("aiNotice", "AI 通道暂不可用，本次使用基础题库；回答和进度仍会完整保存。");
        }
        jdbc.update("INSERT INTO mock_interview_sessions(id,account_id,draft_id,title,position_name,company_name,mode,interview_type,difficulty,duration_minutes,question_count,language_code,feedback_mode,follow_up_enabled,status,current_question_index,elapsed_seconds,resume_snapshot_json,jd_snapshot_json,materials_snapshot_json,settings_snapshot_json,version_no,created_at,updated_at,started_at,resumed_at,paused_at,completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'IN_PROGRESS',0,0,?,?,?,?,0,?,?,?,?,NULL,NULL)",
                id, accountId, clean(command.draftId()), title, position, company, mode, type, difficulty,
                duration, questionCount, blankTo(command.languageCode(), "zh-CN"), feedbackMode,
                command.followUpEnabled(), json(resume), json(jd), json(materials), json(settings), now, now, now, now);
        for (int i = 0; i < seeds.size(); i++) {
            QuestionSeed seed = seeds.get(i);
            jdbc.update("INSERT INTO mock_interview_questions(id,session_id,order_no,question_type,prompt_text,source_label,source_refs_json,status,created_at) VALUES(?,?,?,?,?,?,?,'PENDING',?)",
                    Ids.newId(), id, i + 1, seed.type(), seed.prompt(), seed.sourceLabel(), json(seed.sourceRefs()), now);
        }
        if (command.draftId() != null && !command.draftId().isBlank()) {
            jdbc.update("UPDATE mock_interview_drafts SET status='COMPLETED',step_no=5,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                    now, command.draftId(), accountId);
        }
        return session(current, id);
    }

    private Map<String, Object> jobMatchSnapshot(String accountId, String jobMatchId) {
        String cleanId = clean(jobMatchId);
        if (cleanId == null) return Map.of();
        List<Map<String, Object>> matches = jdbc.queryForList("""
                SELECT t.id,t.current_report_id,j.title,j.company_name,v.location,v.work_mode
                  FROM job_match_tasks t
                  JOIN jobs j ON j.id=t.job_id
                  JOIN job_versions v ON v.id=t.job_version_id
                 WHERE t.id=? AND t.account_id=?
                """, cleanId, accountId);
        if (matches.isEmpty()) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "岗位匹配报告不存在或无权用于模拟面试");
        }
        Map<String, Object> match = matches.get(0);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("jobMatchId", cleanId);
        snapshot.put("title", match.get("title"));
        snapshot.put("company", match.get("company_name"));
        snapshot.put("location", match.get("location"));
        snapshot.put("workMode", match.get("work_mode"));
        snapshot.put("requirements", jdbc.queryForList("SELECT id,category,requirement_text,priority_code,hard_gate,source_quote FROM job_requirements WHERE match_id=? ORDER BY sequence_no", cleanId));
        String reportId = clean(String.valueOf(match.get("current_report_id")));
        if (reportId != null && !"null".equals(reportId)) {
            String reportJson = jdbc.query("SELECT report_json FROM job_match_report_versions WHERE report_id=? AND account_id=? ORDER BY version_no DESC LIMIT 1",
                    (rs, n) -> rs.getString("report_json"), reportId, accountId).stream().findFirst().orElse(null);
            Map<String, Object> report = map(reportJson);
            Object ai = report.get("ai");
            if (ai instanceof Map<?, ?> aiMap) {
                snapshot.put("strengths", aiMap.get("strengths") == null ? List.of() : aiMap.get("strengths"));
                snapshot.put("gaps", aiMap.get("gaps") == null ? List.of() : aiMap.get("gaps"));
                snapshot.put("interviewTopics", aiMap.get("interviewTopics") == null ? List.of() : aiMap.get("interviewTopics"));
                snapshot.put("recommendation", aiMap.get("recommendation") == null ? Map.of() : aiMap.get("recommendation"));
            }
            snapshot.put("reportId", reportId);
        }
        return snapshot;
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> list(CurrentAccount current, String status, String mode, String type, int limit) {
        assertSeeker(current);
        StringBuilder sql = new StringBuilder("SELECT s.*,r.overall_score FROM mock_interview_sessions s LEFT JOIN mock_interview_reports r ON r.session_id=s.id WHERE s.account_id=?");
        List<Object> args = new ArrayList<>();
        args.add(current.accountId());
        if (status != null && !status.isBlank()) { sql.append(" AND s.status=?"); args.add(status.trim().toUpperCase(Locale.ROOT)); }
        if (mode != null && !mode.isBlank()) { sql.append(" AND s.mode=?"); args.add(mode.trim().toUpperCase(Locale.ROOT)); }
        if (type != null && !type.isBlank()) { sql.append(" AND s.interview_type=?"); args.add(type.trim().toUpperCase(Locale.ROOT)); }
        sql.append(" ORDER BY s.updated_at DESC");
        int safeLimit = Math.max(1, Math.min(100, limit));
        return jdbc.query(sql.toString(), (rs, rowNum) -> new SessionSummary(
                rs.getString("id"), rs.getString("title"), rs.getString("position_name"), rs.getString("company_name"),
                rs.getString("mode"), rs.getString("interview_type"), rs.getString("difficulty"), rs.getString("status"),
                rs.getInt("current_question_index"), rs.getInt("question_count"), (Integer) rs.getObject("overall_score"),
                instant(rs.getTimestamp("updated_at")), instant(rs.getTimestamp("completed_at")),
                listElapsed(rs.getInt("elapsed_seconds"), instant(rs.getTimestamp("resumed_at"))), rs.getInt("duration_minutes")),
                args.toArray()).stream().limit(safeLimit).toList();
    }

    @Transactional(readOnly = true)
    public SessionView get(CurrentAccount current, String sessionId) {
        assertSeeker(current);
        return session(current, sessionId);
    }

    @Transactional
    public AnswerView saveTextDraft(CurrentAccount current, String sessionId, String questionId, AnswerCommand command) {
        SessionRow session = requireActive(current, sessionId);
        QuestionView question = question(session.id(), questionId);
        return upsertAnswer(session, question, "TEXT", command.answer(), "DRAFT", command.expectedVersion(), Map.of(), Map.of());
    }

    @Transactional
    public SessionView submitAnswer(CurrentAccount current, String sessionId, String questionId, AnswerCommand command) {
        SessionRow row = requireActive(current, sessionId);
        QuestionView question = question(row.id(), questionId);
        String text = required(command.answer(), "MOCK_INTERVIEW_ANSWER_REQUIRED", "回答不能为空");
        Map<String, Integer> scores;
        Map<String, Object> feedback;
        try {
            Evaluation evaluation = ai.evaluateAnswer(current.accountId(), row.positionName(),
                    new QuestionInput(question.questionType(), question.prompt(), question.sourceRefs()), text,
                    row.followUpEnabled(), map(row.resumeSnapshotJson()), map(row.jdSnapshotJson()));
            scores = evaluation.scores();
            feedback = evaluation.feedback();
        } catch (AiGatewayException exception) {
            // No model, no score: counting characters is not an evaluation (BE-1).
            scores = Map.of();
            feedback = pendingFeedback();
        }
        upsertAnswer(row, question, row.mode(), text, "SUBMITTED", command.expectedVersion(), scores, feedback);
        Instant now = clock.now();
        jdbc.update("UPDATE mock_interview_questions SET status='COMPLETED' WHERE id=? AND session_id=?", questionId, sessionId);
        int next = Math.max(row.currentQuestionIndex(), question.orderNo());
        boolean complete = next >= row.questionCount();
        jdbc.update("UPDATE mock_interview_sessions SET current_question_index=?,status=?,version_no=version_no+1,updated_at=?,completed_at=? WHERE id=? AND account_id=?",
                next, complete ? "COMPLETED" : "IN_PROGRESS", now, complete ? now : null, sessionId, current.accountId());
        if (complete) stopClock(row, now);
        if (complete) generateReport(current.accountId(), sessionId);
        return session(current, sessionId);
    }

    @Transactional
    public AnswerView saveTranscript(CurrentAccount current, String sessionId, String questionId, AnswerCommand command) {
        SessionRow row = requireActive(current, sessionId);
        QuestionView question = question(row.id(), questionId);
        return upsertAnswer(row, question, "VOICE", command.answer(), "DRAFT", command.expectedVersion(), Map.of(), Map.of());
    }

    @Transactional
    public SessionView switchMode(CurrentAccount current, String sessionId, ModeCommand command) {
        SessionRow row = requireOwn(current, sessionId);
        String mode = enumValue(command.mode(), MODES, "TEXT", "MOCK_INTERVIEW_MODE_INVALID");
        if ("COMPLETED".equals(row.status()) || "ABANDONED".equals(row.status())) throw AppException.conflict("MOCK_INTERVIEW_TERMINAL", "已结束的面试不能切换模式");
        jdbc.update("UPDATE mock_interview_sessions SET mode=?,status='IN_PROGRESS',version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                mode, clock.now(), sessionId, current.accountId());
        return session(current, sessionId);
    }

    @Transactional
    public SessionView pause(CurrentAccount current, String sessionId) {
        SessionRow row = requireActive(current, sessionId);
        Instant now = clock.now();
        stopClock(row, now);
        jdbc.update("UPDATE mock_interview_sessions SET status='PAUSED',paused_at=?,updated_at=?,version_no=version_no+1 WHERE id=? AND account_id=?",
                now, now, row.id(), current.accountId());
        return session(current, sessionId);
    }

    @Transactional
    public SessionView resume(CurrentAccount current, String sessionId) {
        SessionRow row = requireOwn(current, sessionId);
        if (!"PAUSED".equals(row.status()) && !"OFFLINE".equals(row.status()) && !"TRANSCRIPTION_FAILED".equals(row.status())) {
            throw AppException.conflict("MOCK_INTERVIEW_NOT_RESUMABLE", "当前面试不在可恢复状态");
        }
        Instant now = clock.now();
        jdbc.update("UPDATE mock_interview_sessions SET status='IN_PROGRESS',paused_at=NULL,resumed_at=?,updated_at=?,version_no=version_no+1 WHERE id=? AND account_id=?",
                now, now, row.id(), current.accountId());
        return session(current, sessionId);
    }

    @Transactional
    public ReportView complete(CurrentAccount current, String sessionId) {
        SessionRow row = requireOwn(current, sessionId);
        if ("ABANDONED".equals(row.status())) throw AppException.conflict("MOCK_INTERVIEW_ABANDONED", "已放弃的记录不能生成报告");
        Instant now = clock.now();
        stopClock(row, now);
        jdbc.update("UPDATE mock_interview_sessions SET status='COMPLETED',completed_at=?,updated_at=?,version_no=version_no+1 WHERE id=? AND account_id=?",
                now, now, row.id(), current.accountId());
        generateReport(current.accountId(), sessionId);
        return report(current, sessionId);
    }

    @Transactional
    public SessionView abandon(CurrentAccount current, String sessionId) {
        SessionRow row = requireOwn(current, sessionId);
        if ("COMPLETED".equals(row.status())) throw AppException.conflict("MOCK_INTERVIEW_COMPLETED", "已完成的记录不能放弃");
        Instant now = clock.now();
        stopClock(row, now);
        jdbc.update("UPDATE mock_interview_sessions SET status='ABANDONED',updated_at=?,version_no=version_no+1 WHERE id=? AND account_id=?",
                now, row.id(), current.accountId());
        return session(current, sessionId);
    }

    /**
     * Evaluates answers submitted while the model was unavailable. Runs outside a transaction (model
     * calls are slow); each evaluated answer is saved on its own, then the report is rebuilt.
     */
    public EvaluationResult evaluatePending(CurrentAccount current, String sessionId) {
        SessionRow row = requireOwn(current, sessionId);
        if ("ABANDONED".equals(row.status())) throw AppException.conflict("MOCK_INTERVIEW_ABANDONED", "已放弃的记录不能评估");
        List<ReviewRow> pending = questionsWithAnswers(sessionId).stream()
                .filter(item -> item.answer() != null && "SUBMITTED".equals(item.answer().status()) && item.scores().isEmpty())
                .toList();
        int evaluated = 0;
        boolean available = true;
        for (ReviewRow item : pending) {
            Evaluation evaluation;
            try {
                evaluation = ai.evaluateAnswer(current.accountId(), row.positionName(),
                        new QuestionInput(item.question().questionType(), item.question().prompt(), item.question().sourceRefs()),
                        item.answer().answer(), row.followUpEnabled(), map(row.resumeSnapshotJson()), map(row.jdSnapshotJson()));
            } catch (AiGatewayException exception) {
                available = false;
                break;
            }
            int changed = jdbc.update("UPDATE mock_interview_answers SET score_json=?,feedback_json=?,version_no=version_no+1,updated_at=? WHERE id=? AND version_no=?",
                    json(evaluation.scores()), json(evaluation.feedback()), clock.now(), item.answer().id(), item.answer().version());
            if (changed == 1) evaluated++;
        }
        if (evaluated > 0) {
            transactions.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM mock_interview_reports WHERE session_id=?", sessionId);
                if ("COMPLETED".equals(row.status())) generateReport(current.accountId(), sessionId);
            });
        }
        return new EvaluationResult(evaluated, pending.size() - evaluated, available);
    }

    @Transactional
    public ReportView report(CurrentAccount current, String sessionId) {
        assertSeeker(current);
        SessionRow session = requireOwn(current, sessionId);
        if (!"COMPLETED".equals(session.status())) throw AppException.conflict("MOCK_INTERVIEW_REPORT_NOT_READY", "面试完成后才会生成报告");
        // Reports dropped by a re-evaluation or by V69 are rebuilt from the stored answers.
        generateReport(current.accountId(), sessionId);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM mock_interview_reports WHERE session_id=? AND account_id=?",
                sessionId, current.accountId());
        List<QuestionReview> reviews = questionsWithAnswers(sessionId).stream().map(item -> new QuestionReview(
                item.question(), item.answer(), item.feedback(), item.scores())).toList();
        int answered = (int) reviews.stream().filter(item -> item.answer() != null && "SUBMITTED".equals(item.answer().status())).count();
        int evaluated = number(row.get("evaluated_count"));
        return new ReportView(String.valueOf(row.get("id")), summary(session),
                row.get("overall_score") == null ? null : number(row.get("overall_score")),
                intMap(String.valueOf(row.get("dimensions_json"))), map(String.valueOf(row.get("summary_json"))),
                stringList(String.valueOf(row.get("recommendations_json"))), reviews,
                instant(row.get("generated_at")), evaluated, Math.max(0, answered - evaluated));
    }

    @Transactional
    public SessionView retry(CurrentAccount current, String sessionId) {
        SessionRow source = requireOwn(current, sessionId);
        Instant now = clock.now();
        String id = Ids.newId();
        jdbc.update("INSERT INTO mock_interview_sessions(id,account_id,draft_id,title,position_name,company_name,mode,interview_type,difficulty,duration_minutes,question_count,language_code,feedback_mode,follow_up_enabled,status,current_question_index,elapsed_seconds,resume_snapshot_json,jd_snapshot_json,materials_snapshot_json,settings_snapshot_json,version_no,created_at,updated_at,started_at,resumed_at,paused_at,completed_at) SELECT ?,account_id,NULL,title,position_name,company_name,mode,interview_type,difficulty,duration_minutes,question_count,language_code,feedback_mode,follow_up_enabled,'IN_PROGRESS',0,0,resume_snapshot_json,jd_snapshot_json,materials_snapshot_json,settings_snapshot_json,0,?,?,?,?,NULL,NULL FROM mock_interview_sessions WHERE id=? AND account_id=?",
                id, now, now, now, now, sessionId, current.accountId());
        List<QuestionView> sourceQuestions = questionList(sessionId);
        for (QuestionView question : sourceQuestions) {
            jdbc.update("INSERT INTO mock_interview_questions(id,session_id,order_no,question_type,prompt_text,source_label,source_refs_json,status,created_at) VALUES(?,?,?,?,?,?,?,'PENDING',?)",
                    Ids.newId(), id, question.orderNo(), question.questionType(), question.prompt(), question.sourceLabel(), json(question.sourceRefs()), now);
        }
        return session(current, id);
    }

    private SessionView session(CurrentAccount current, String sessionId) {
        SessionRow row = requireOwn(current, sessionId);
        List<QuestionView> questions = questionList(sessionId);
        List<AnswerView> answers = answerList(sessionId);
        QuestionView currentQuestion = questions.stream().filter(item -> item.orderNo() == row.currentQuestionIndex() + 1).findFirst().orElse(null);
        return new SessionView(summary(row), currentQuestion, questions, answers,
                map(row.resumeSnapshotJson()), map(row.jdSnapshotJson()), map(row.materialsSnapshotJson()), map(row.settingsSnapshotJson()));
    }

    private SessionSummary summary(SessionRow row) {
        Integer score = null;
        try { score = jdbc.queryForObject("SELECT overall_score FROM mock_interview_reports WHERE session_id=?", Integer.class, row.id()); }
        catch (EmptyResultDataAccessException ignored) { }
        return new SessionSummary(row.id(), row.title(), row.positionName(), row.companyName(), row.mode(), row.interviewType(),
                row.difficulty(), row.status(), row.currentQuestionIndex(), row.questionCount(), score, row.updatedAt(), row.completedAt(),
                elapsedSeconds(row), row.durationMinutes());
    }

    private SessionRow requireActive(CurrentAccount current, String sessionId) {
        SessionRow row = requireOwn(current, sessionId);
        if (!ACTIVE_STATUSES.contains(row.status())) throw AppException.conflict("MOCK_INTERVIEW_NOT_ACTIVE", "当前面试不可继续作答");
        return row;
    }

    private SessionRow requireOwn(CurrentAccount current, String sessionId) {
        assertSeeker(current);
        try {
            return jdbc.queryForObject("SELECT * FROM mock_interview_sessions WHERE id=? AND account_id=?", (rs, rowNum) -> new SessionRow(
                    rs.getString("id"), rs.getString("account_id"), rs.getString("title"), rs.getString("position_name"),
                    rs.getString("company_name"), rs.getString("mode"), rs.getString("interview_type"), rs.getString("difficulty"),
                    rs.getInt("duration_minutes"), rs.getInt("question_count"), rs.getString("language_code"),
                    rs.getString("feedback_mode"), rs.getBoolean("follow_up_enabled"), rs.getString("status"),
                    rs.getInt("current_question_index"), rs.getInt("elapsed_seconds"), rs.getString("resume_snapshot_json"),
                    rs.getString("jd_snapshot_json"), rs.getString("materials_snapshot_json"), rs.getString("settings_snapshot_json"),
                    rs.getInt("version_no"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")),
                    instant(rs.getTimestamp("completed_at")), instant(rs.getTimestamp("resumed_at"))), sessionId, current.accountId());
        } catch (EmptyResultDataAccessException exception) {
            throw AppException.user("MOCK_INTERVIEW_NOT_FOUND", "模拟面试不存在");
        }
    }

    private DraftView draft(String accountId, String draftId) {
        try {
            return jdbc.queryForObject("SELECT * FROM mock_interview_drafts WHERE id=? AND account_id=?", (rs, rowNum) -> new DraftView(
                    rs.getString("id"), rs.getInt("step_no"), rs.getString("status"), map(rs.getString("payload_json")),
                    rs.getInt("version_no"), instant(rs.getTimestamp("updated_at"))), draftId, accountId);
        } catch (EmptyResultDataAccessException exception) {
            throw AppException.user("MOCK_INTERVIEW_DRAFT_NOT_FOUND", "面试草稿不存在");
        }
    }

    private QuestionView question(String sessionId, String questionId) {
        try {
            return jdbc.queryForObject("SELECT * FROM mock_interview_questions WHERE id=? AND session_id=?", (rs, rowNum) -> new QuestionView(
                    rs.getString("id"), rs.getInt("order_no"), rs.getString("question_type"), rs.getString("prompt_text"),
                    rs.getString("source_label"), stringList(rs.getString("source_refs_json")), rs.getString("status")), questionId, sessionId);
        } catch (EmptyResultDataAccessException exception) {
            throw AppException.user("MOCK_INTERVIEW_QUESTION_NOT_FOUND", "面试题不存在");
        }
    }

    private List<QuestionView> questionList(String sessionId) {
        return jdbc.query("SELECT * FROM mock_interview_questions WHERE session_id=? ORDER BY order_no", (rs, rowNum) -> new QuestionView(
                rs.getString("id"), rs.getInt("order_no"), rs.getString("question_type"), rs.getString("prompt_text"),
                rs.getString("source_label"), stringList(rs.getString("source_refs_json")), rs.getString("status")), sessionId);
    }

    private List<AnswerView> answerList(String sessionId) {
        return jdbc.query("SELECT * FROM mock_interview_answers WHERE session_id=? ORDER BY created_at", (rs, rowNum) -> answer(rs), sessionId);
    }

    private AnswerView upsertAnswer(SessionRow session, QuestionView question, String mode, String text, String status,
            Integer expectedVersion, Map<String, Integer> scores, Map<String, Object> feedback) {
        AnswerView existing = null;
        try { existing = jdbc.queryForObject("SELECT * FROM mock_interview_answers WHERE session_id=? AND question_id=?", (rs, n) -> answer(rs), session.id(), question.id()); }
        catch (EmptyResultDataAccessException ignored) { }
        Instant now = clock.now();
        String cleanText = text == null ? "" : text.trim();
        if (existing == null) {
            assertVersion(expectedVersion, 0);
            String id = Ids.newId();
            try {
                jdbc.update("INSERT INTO mock_interview_answers(id,session_id,question_id,answer_mode,answer_text,transcript_text,status,score_json,feedback_json,version_no,created_at,updated_at,submitted_at) VALUES(?,?,?,?,?,?,?, ?,?,0,?,?,?)",
                        id, session.id(), question.id(), mode, cleanText, "VOICE".equals(mode) ? cleanText : null, status,
                        json(scores), json(feedback), now, now, "SUBMITTED".equals(status) ? now : null);
            } catch (DuplicateKeyException exception) {
                throw AppException.conflict("VERSION_CONFLICT", "回答已在其他页面保存，请刷新后重试");
            }
        } else {
            assertVersion(expectedVersion, existing.version());
            int changed = jdbc.update("UPDATE mock_interview_answers SET answer_mode=?,answer_text=?,transcript_text=?,status=?,score_json=?,feedback_json=?,version_no=version_no+1,updated_at=?,submitted_at=? WHERE id=? AND version_no=?",
                    mode, cleanText, "VOICE".equals(mode) ? cleanText : existing.transcript(), status, json(scores), json(feedback), now,
                    "SUBMITTED".equals(status) ? now : null, existing.id(), existing.version());
            requireUpdated(changed);
        }
        return jdbc.queryForObject("SELECT * FROM mock_interview_answers WHERE session_id=? AND question_id=?", (rs, n) -> answer(rs), session.id(), question.id());
    }

    private AnswerView answer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AnswerView(rs.getString("id"), rs.getString("question_id"), rs.getString("answer_mode"),
                rs.getString("answer_text"), rs.getString("transcript_text"), rs.getString("status"),
                intMap(rs.getString("score_json")), map(rs.getString("feedback_json")), rs.getInt("version_no"),
                instant(rs.getTimestamp("updated_at")), instant(rs.getTimestamp("submitted_at")));
    }

    /**
     * Builds the report from AI-evaluated answers only (H-5): dimension averages, strengths and risks the
     * model wrote for this candidate, and its follow-up questions as practice prompts. Nothing is
     * written in when no answer was evaluated.
     */
    private void generateReport(String accountId, String sessionId) {
        if (count("SELECT COUNT(*) FROM mock_interview_reports WHERE session_id=?", sessionId) > 0) return;
        List<ReviewRow> evaluated = questionsWithAnswers(sessionId).stream()
                .filter(item -> item.answer() != null && !item.scores().isEmpty()).toList();
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        for (String key : List.of("structure", "relevance", "evidence", "expression", "professional")) {
            List<Integer> values = evaluated.stream().map(row -> row.scores().get(key)).filter(Objects::nonNull).toList();
            if (!values.isEmpty()) dimensions.put(key, average(values, 0));
        }
        Integer overall = dimensions.isEmpty() ? null : average(new ArrayList<>(dimensions.values()), 0);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("strengths", distinctFeedback(evaluated, "strengths", 4));
        summary.put("risks", distinctFeedback(evaluated, "improvements", 4));
        List<String> recommendations = evaluated.stream()
                .map(row -> row.feedback().get("suggestedFollowUp"))
                .filter(value -> value instanceof String text && !text.isBlank())
                .map(String::valueOf).distinct().limit(3).toList();
        jdbc.update("INSERT INTO mock_interview_reports(id,account_id,session_id,overall_score,evaluated_count,dimensions_json,summary_json,recommendations_json,generated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                Ids.newId(), accountId, sessionId, overall, evaluated.size(), json(dimensions), json(summary),
                json(recommendations), clock.now());
    }

    private static List<String> distinctFeedback(List<ReviewRow> rows, String key, int limit) {
        return rows.stream()
                .flatMap(row -> row.feedback().get(key) instanceof List<?> list ? list.stream() : java.util.stream.Stream.empty())
                .map(String::valueOf).filter(text -> !text.isBlank()).distinct().limit(limit).toList();
    }

    private static Map<String, Object> pendingFeedback() {
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("generationMode", "PENDING");
        feedback.put("modelNotice", "AI 通道暂不可用，本题尚未评估；回答已保存，AI 恢复后可在报告中重新评估。");
        return feedback;
    }

    /** Adds the current active stretch to elapsed_seconds and stops the clock. */
    private void stopClock(SessionRow row, Instant now) {
        if (row.resumedAt() == null) return;
        long seconds = Math.max(0, java.time.Duration.between(row.resumedAt(), now).getSeconds());
        jdbc.update("UPDATE mock_interview_sessions SET elapsed_seconds=elapsed_seconds+?,resumed_at=NULL WHERE id=?",
                (int) Math.min(Integer.MAX_VALUE, seconds), row.id());
    }

    private int listElapsed(int accumulated, Instant resumedAt) {
        long running = resumedAt == null ? 0 : Math.max(0, java.time.Duration.between(resumedAt, clock.now()).getSeconds());
        return (int) Math.min(Integer.MAX_VALUE, accumulated + running);
    }

    private int elapsedSeconds(SessionRow row) {
        long running = row.resumedAt() == null ? 0
                : Math.max(0, java.time.Duration.between(row.resumedAt(), clock.now()).getSeconds());
        return (int) Math.min(Integer.MAX_VALUE, row.elapsedSeconds() + running);
    }

    private List<ReviewRow> questionsWithAnswers(String sessionId) {
        return jdbc.query("SELECT q.id,q.order_no,q.question_type,q.prompt_text,q.source_label,q.source_refs_json,q.status AS question_status,a.id AS answer_id,a.answer_mode,a.answer_text,a.transcript_text,a.status AS answer_status,a.score_json,a.feedback_json,a.version_no,a.updated_at,a.submitted_at FROM mock_interview_questions q LEFT JOIN mock_interview_answers a ON a.question_id=q.id AND a.session_id=q.session_id WHERE q.session_id=? ORDER BY q.order_no", (rs, rowNum) -> {
            QuestionView question = new QuestionView(rs.getString("id"), rs.getInt("order_no"), rs.getString("question_type"), rs.getString("prompt_text"), rs.getString("source_label"), stringList(rs.getString("source_refs_json")), rs.getString("question_status"));
            AnswerView answer = rs.getString("answer_id") == null ? null : new AnswerView(rs.getString("answer_id"), rs.getString("id"), rs.getString("answer_mode"), rs.getString("answer_text"), rs.getString("transcript_text"), rs.getString("answer_status"), intMap(rs.getString("score_json")), map(rs.getString("feedback_json")), rs.getInt("version_no"), instant(rs.getTimestamp("updated_at")), instant(rs.getTimestamp("submitted_at")));
            return new ReviewRow(question, answer, answer == null ? Map.of() : answer.feedback(), answer == null ? Map.of() : answer.scores());
        }, sessionId);
    }

    private List<QuestionSeed> questions(String position, String company, String type, String difficulty, int count,
            Map<String, Object> resume, Map<String, Object> jd) {
        String companyText = company == null || company.isBlank() ? "目标团队" : company;
        String project = firstNonBlank(resume.get("projects"), resume.get("experience"), "一段最能代表你的经历");
        List<QuestionSeed> bank = new ArrayList<>();
        bank.add(seed("INTRO", "请做一个 2 分钟左右的自我介绍，并说明你与" + position + "岗位最相关的优势。", "简历 · 个人经历", List.of("RESUME")));
        bank.add(seed("MOTIVATION", "为什么选择" + companyText + "的" + position + "岗位？请结合你的长期方向回答。", jd.isEmpty() ? "目标岗位" : "岗位 JD", jd.isEmpty() ? List.of("POSITION") : List.of("JD")));
        bank.add(seed("PROJECT", "请挑选" + project + "，说明目标、你的职责、关键行动和最终结果。", "简历 · 项目或经历", List.of("RESUME")));
        bank.add(seed("BEHAVIORAL", "讲一次你在信息不完整或时间紧张的情况下做出关键判断的经历。", "通用行为题", List.of("RESUME", "CAREER_LIBRARY")));
        bank.add(seed("PROFESSIONAL", "针对" + position + "的核心职责，你会如何拆解问题并验证方案有效性？", jd.isEmpty() ? "岗位能力模型" : "岗位 JD", jd.isEmpty() ? List.of("POSITION") : List.of("JD")));
        bank.add(seed("COLLABORATION", "请举例说明你如何处理跨团队分歧，并推动事情按计划交付。", "简历 · 协作经历", List.of("RESUME")));
        bank.add(seed("PRESSURE", "如果项目上线后核心指标没有达到预期，你会如何排查、沟通并制定下一步？", "压力追问", List.of("JD")));
        bank.add(seed("REFLECTION", "回顾最近一年，你主动改进过哪项能力？采取了什么行动，结果如何？", "求职资料库", List.of("CAREER_LIBRARY")));
        bank.add(seed("PROFESSIONAL", "请说明你对" + position + "岗位中最重要的一项专业能力的理解，并给出实践证据。", "岗位能力模型", List.of("JD", "RESUME")));
        bank.add(seed("CAREER", "你希望入职后的前三个月完成什么目标？会如何确认自己正在创造价值？", "岗位目标", List.of("POSITION")));
        bank.add(seed("FOLLOW_UP", "在你刚才提到的经历中，哪一个决定最难？如果重来一次会改变什么？", "动态追问", List.of("ANSWER")));
        bank.add(seed("EVIDENCE", "选择一项你最有把握的成果，说明证据来源、你的贡献边界和可复核方式。", "简历 · 成果证据", List.of("RESUME", "CAREER_LIBRARY")));
        bank.add(seed("LEARNING", "遇到完全陌生的问题时，你通常如何快速学习并形成可靠判断？", "通用能力", List.of("RESUME")));
        bank.add(seed("RISK", "在这个岗位上你认为最容易被忽略的风险是什么？你会如何提前发现？", "岗位 JD", List.of("JD")));
        bank.add(seed("CLOSING", "还有哪项经历或能力是我们没有问到，但你认为对这个岗位非常重要？", "总结题", List.of("RESUME")));
        if ("BEHAVIORAL".equals(type)) bank.sort((left, right) -> behavioralRank(left.type()) - behavioralRank(right.type()));
        if ("PROFESSIONAL".equals(type)) bank.sort((left, right) -> professionalRank(left.type()) - professionalRank(right.type()));
        if ("PRESSURE".equals(type)) bank.sort((left, right) -> pressureRank(left.type()) - pressureRank(right.type()));
        if ("ADVANCED".equals(difficulty)) {
            bank.set(4, seed("PROFESSIONAL", bank.get(4).prompt() + "请同时说明取舍依据、失败信号和回滚条件。", bank.get(4).sourceLabel(), bank.get(4).sourceRefs()));
        }
        return bank.subList(0, Math.min(count, bank.size()));
    }

    private Map<String, Object> resumeSnapshot(String accountId, String resumeId) {
        try {
            return jdbc.queryForObject("SELECT * FROM resume_masters WHERE id=? AND account_id=?", (rs, rowNum) -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", rs.getString("id")); value.put("title", rs.getString("title")); value.put("source", rs.getString("source"));
                value.put("education", rs.getString("education_json")); value.put("experience", rs.getString("experience_json"));
                value.put("projects", rs.getString("projects_json")); value.put("skills", rs.getString("skills_json"));
                value.put("certificates", rs.getString("certificates_json")); value.put("summary", rs.getString("self_intro"));
                value.put("version", rs.getInt("version_no"));
                return value;
            }, resumeId, accountId);
        } catch (EmptyResultDataAccessException exception) {
            throw AppException.user("MOCK_INTERVIEW_RESUME_NOT_FOUND", "所选简历不存在或不属于当前账号");
        }
    }

    private static QuestionSeed seed(String type, String prompt, String source, List<String> refs) { return new QuestionSeed(type, prompt, source, refs); }
    private static int behavioralRank(String value) { return Set.of("BEHAVIORAL", "COLLABORATION", "REFLECTION", "LEARNING").contains(value) ? 0 : 1; }
    private static int professionalRank(String value) { return Set.of("PROFESSIONAL", "PROJECT", "EVIDENCE", "RISK").contains(value) ? 0 : 1; }
    private static int pressureRank(String value) { return Set.of("PRESSURE", "RISK", "BEHAVIORAL", "FOLLOW_UP").contains(value) ? 0 : 1; }
    private static int average(List<Integer> values, int fallback) { return values.isEmpty() ? fallback : (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(fallback)); }
    private int count(String sql, Object... args) { Integer result = jdbc.queryForObject(sql, Integer.class, args); return result == null ? 0 : result; }
    private static int number(Object value) { return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value)); }
    private static Instant instant(Object value) { if (value == null) return null; if (value instanceof java.sql.Timestamp ts) return ts.toInstant(); if (value instanceof Instant instant) return instant; return Instant.parse(String.valueOf(value)); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String blankTo(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String required(String value, String reason, String message) { if (value == null || value.isBlank()) throw AppException.user(reason, message); return value.trim(); }
    private static String enumValue(String value, Set<String> allowed, String fallback, String reason) { String result = blankTo(value, fallback).toUpperCase(Locale.ROOT); if (!allowed.contains(result)) throw AppException.user(reason, "不支持的选项：" + result); return result; }
    private static void assertVersion(Integer expected, int actual) { if (expected != null && expected != actual) throw AppException.conflict("VERSION_CONFLICT", "数据已在其他页面更新，请刷新后重试"); }
    private static void requireUpdated(int changed) { if (changed != 1) throw AppException.conflict("VERSION_CONFLICT", "数据已在其他页面更新，请刷新后重试"); }
    private static void assertSeeker(CurrentAccount current) { if (current == null || !"SEEKER".equals(current.role())) throw AppException.forbidden("SEEKER_ONLY", "仅求职者可以使用模拟面试"); }
    private static String firstNonBlank(Object... values) { for (Object value : values) { if (value != null && !String.valueOf(value).isBlank()) { String text = String.valueOf(value).replaceAll("[\\[\\]{}\\\"]", "").trim(); return text.length() > 28 ? text.substring(0, 28) + "…" : text; } } return "一段最能代表你的经历"; }

    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private Map<String, Object> map(String value) { if (value == null || value.isBlank()) return Map.of(); try { return mapper.readValue(value, new TypeReference<>() {}); } catch (Exception exception) { return Map.of(); } }
    private Map<String, Integer> intMap(String value) { if (value == null || value.isBlank()) return Map.of(); try { return mapper.readValue(value, new TypeReference<>() {}); } catch (Exception exception) { return Map.of(); } }
    private List<String> stringList(String value) { if (value == null || value.isBlank()) return List.of(); try { return mapper.readValue(value, new TypeReference<>() {}); } catch (Exception exception) { return List.of(); } }

    public record DraftCommand(int step, Map<String, Object> payload, Integer expectedVersion) {}
    public record CreateSessionCommand(String draftId, String resumeId, String taxonomyNodeId,
            String taxonomyCategoryId, String taxonomyGroupId, List<String> careerRecordIds,
            List<String> careerFileIds, String positionName, String companyName, String userNote, String mode,
            String interviewType, String difficulty, int durationMinutes, int questionCount, String languageCode,
            String feedbackMode, boolean followUpEnabled, boolean consentConfirmed, String jobMatchId) {}
    public record AnswerCommand(String answer, Integer expectedVersion) {}
    public record ModeCommand(String mode) {}
    /** {@code averageScore} is null until at least one report has an AI-evaluated score. */
    public record DashboardView(int total, int completed, int textCount, int voiceCount, Integer averageScore,
            SessionSummary resumable, List<SessionSummary> recent) {}
    public record DraftView(String id, int step, String status, Map<String, Object> payload, int version, Instant updatedAt) {}
    /** {@code elapsedSeconds}: active interview time measured by the server (pauses excluded). */
    public record SessionSummary(String id, String title, String positionName, String companyName, String mode,
            String interviewType, String difficulty, String status, int answeredCount, int questionCount,
            Integer score, Instant updatedAt, Instant completedAt, Integer elapsedSeconds, Integer durationMinutes) {}
    public record QuestionView(String id, int orderNo, String questionType, String prompt, String sourceLabel,
            List<String> sourceRefs, String status) {}
    public record AnswerView(String id, String questionId, String mode, String answer, String transcript, String status,
            Map<String, Integer> scores, Map<String, Object> feedback, int version, Instant updatedAt, Instant submittedAt) {}
    public record SessionView(SessionSummary session, QuestionView currentQuestion, List<QuestionView> questions,
            List<AnswerView> answers, Map<String, Object> resumeSnapshot, Map<String, Object> jdSnapshot,
            Map<String, Object> materialsSnapshot, Map<String, Object> settingsSnapshot) {}
    public record QuestionReview(QuestionView question, AnswerView answer, Map<String, Object> feedback,
            Map<String, Integer> scores) {}
    /**
     * Scores and dimensions come only from AI-evaluated answers; {@code overallScore} is null while none
     * are evaluated, and {@code pendingCount} answers can be evaluated once the model is available.
     */
    public record ReportView(String id, SessionSummary session, Integer overallScore, Map<String, Integer> dimensions,
            Map<String, Object> summary, List<String> recommendations, List<QuestionReview> questions, Instant generatedAt,
            int evaluatedCount, int pendingCount) {}
    public record EvaluationResult(int evaluated, int pending, boolean aiAvailable) {}

    private record SessionRow(String id, String accountId, String title, String positionName, String companyName,
            String mode, String interviewType, String difficulty, int durationMinutes, int questionCount,
            String languageCode, String feedbackMode, boolean followUpEnabled, String status, int currentQuestionIndex,
            int elapsedSeconds, String resumeSnapshotJson, String jdSnapshotJson, String materialsSnapshotJson,
            String settingsSnapshotJson, int version, Instant createdAt, Instant updatedAt, Instant completedAt,
            Instant resumedAt) {}
    private record QuestionSeed(String type, String prompt, String sourceLabel, List<String> sourceRefs) {}
    private record ReviewRow(QuestionView question, AnswerView answer, Map<String, Object> feedback,
            Map<String, Integer> scores) {}
}
