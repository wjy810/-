package com.jobproof.modules.jobmatch.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.airesume.application.AiQuotaService;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService;
import com.jobproof.modules.airesume.application.AiResumeChangeSetService.GeneratedChange;
import com.jobproof.modules.airesume.application.AiResumeChangeSetService.GeneratedItem;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.career.application.CareerFileService;
import com.jobproof.modules.jobmatch.domain.JobMatchModels.EvidenceCandidate;
import com.jobproof.modules.jobmatch.domain.JobMatchModels.ParsedJd;
import com.jobproof.modules.jobmatch.domain.JobMatchModels.RequirementDraft;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobMatchService {
    public static final String AUTH_POLICY = "job-match-authorization-v1";
    public static final List<String> SENSITIVE_FIELDS = List.of("phone", "email", "fullAddress", "identityNumber",
            "age", "gender", "maritalStatus", "ethnicity", "photoRef", "photo", "avatar");
    private static final Pattern LATIN_TOKEN = Pattern.compile("[a-z0-9+#.]+");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final TaskService tasks;
    private final JobMatchTextService text;
    private final CareerFileService careerFiles;
    private final AiQuotaService quota;
    private final AiResumeWorkbenchService workbench;
    private final AuditService audit;
    private final boolean enabled;

    public JobMatchService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock, TaskService tasks,
            JobMatchTextService text, CareerFileService careerFiles, AiQuotaService quota,
            AiResumeWorkbenchService workbench, AuditService audit,
            @Value("${jobproof.job-match.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.tasks = tasks;
        this.text = text;
        this.careerFiles = careerFiles;
        this.quota = quota;
        this.workbench = workbench;
        this.audit = audit;
        this.enabled = enabled;
    }

    public CapabilitiesView capabilities(CurrentAccount current) {
        assertEnabled();
        assertUser(current);
        return new CapabilitiesView(true, text.ocrAvailable(), List.of("TEXT", "FILE", "IMAGE", "URL"),
                List.of("SITE_RESUME", "LOCAL_PDF", "LOCAL_DOCX"), 10 * 1024 * 1024,
                300, 10_000, quota.current(current.accountId()));
    }

    @Transactional
    public MatchView create(CurrentAccount current, CreateCommand command) {
        assertEnabled();
        assertUser(current);
        String sourceType = enumValue(command == null ? null : command.sourceType(),
                Set.of("TEXT", "FILE", "IMAGE", "URL"), "TEXT");
        String sourceText = clean(command == null ? null : command.text());
        if (sourceText == null) throw AppException.user("JD_TEXT_REQUIRED", "请输入岗位描述");
        return createFromText(current, sourceType, sourceText, command == null ? null : command.requestId());
    }

    @Transactional
    public MatchView createFromUpload(CurrentAccount current, String filename, byte[] content, String requestId) {
        assertEnabled();
        assertUser(current);
        String extracted = text.extract(filename, content);
        String sourceType = filename != null && filename.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp)$")
                ? "IMAGE" : "FILE";
        return createFromText(current, sourceType, extracted, requestId);
    }

    @Transactional
    public MatchView createFromUrl(CurrentAccount current, String url, String requestId) {
        assertEnabled();
        assertUser(current);
        return createFromText(current, "URL", text.fetch(url), requestId);
    }

    private MatchView createFromText(CurrentAccount current, String sourceType, String rawText, String requestId) {
        String accountId = current.accountId();
        String createRequestId = clean(requestId);
        if (createRequestId != null) {
            String existingId = jdbc.query("SELECT id FROM job_match_tasks WHERE account_id=? AND create_request_id=?",
                    (rs, n) -> rs.getString(1), accountId, createRequestId)
                    .stream().findFirst().orElse(null);
            if (existingId != null) return get(current, existingId);
        }
        String normalized = normalize(rawText);
        String hash = sha256(normalized);
        Instant now = clock.now();
        String snapshotId = jdbc.query("SELECT id FROM jd_snapshots WHERE account_id=? AND normalized_hash=?",
                (rs, n) -> rs.getString(1), accountId, hash).stream().findFirst().orElse(null);
        if (snapshotId == null) {
            snapshotId = Ids.newId();
            jdbc.update("INSERT INTO jd_snapshots(id,account_id,normalized_hash,original_text,normalized_text,created_at) VALUES(?,?,?,?,?,?)",
                    snapshotId, accountId, hash, rawText, normalized, now);
        }
        String matchId = Ids.newId();
        String jobId = Ids.newId();
        String versionId = Ids.newId();
        String idem = clean(requestId) == null ? "JD_PARSE:" + matchId : "JD_PARSE:" + clean(requestId);
        TaskView parseTask = tasks.create(accountId, TaskTypes.JD_PARSE, idem,
                json(Map.of("matchId", matchId, "sourceType", sourceType)));
        jdbc.update("INSERT INTO jobs(id,account_id,current_snapshot_id,company_name,title,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",
                jobId, accountId, snapshotId, null, null, now, now);
        jdbc.update("INSERT INTO job_versions(id,job_id,account_id,snapshot_id,parse_task_id,status,title,company_name,duties,hard_skills_json,general_skills_json,plus_skills_json,experience_requirement,experience_hard,education_requirement,location,work_mode,mixed_jobs,possible_duplicate_job_id,candidate_json,version_no,created_at,updated_at,confirmed_at,responsibilities_json,required_qualifications_json,preferred_qualifications_json,other_sections_json) VALUES(?,?,?,?,?,'PARSING',NULL,NULL,NULL,'[]','[]','[]',NULL,0,NULL,NULL,NULL,0,NULL,'{}',0,?,?,NULL,'[]','[]','[]','{}')",
                versionId, jobId, accountId, snapshotId, parseTask.id(), now, now);
        jdbc.update("INSERT INTO job_match_tasks(id,account_id,job_id,job_version_id,status,progress_percent,checkpoint_code,version_no,created_at,updated_at,create_request_id) VALUES(?,?,?,?, 'DRAFT',5,'PARSE_JD',0,?,?,?)",
                matchId, accountId, jobId, versionId, now, now, createRequestId);
        try {
            ParsedJd parsed = text.parse(normalized);
            writeParsed(accountId, matchId, jobId, versionId, parsed, now);
            tasks.markSucceeded(parseTask.id(), "jd-structure-v2", json(Map.of("matchId", matchId)));
            audit.append(accountId, "JOB_MATCH_JD_PARSED", "JOB_MATCH", matchId,
                    "requirements=" + parsed.requirements().size() + " source=" + sourceType);
        } catch (AppException exception) {
            jdbc.update("UPDATE job_versions SET status='PARSE_FAILED',candidate_json=?,updated_at=? WHERE id=?",
                    json(Map.of("sourceType", sourceType, "recognizedTextLength", normalized.length(),
                            "errorCode", exception.reason())), now, versionId);
            jdbc.update("UPDATE job_match_tasks SET status='JD_PARSE_FAILED',error_code=?,version_no=version_no+1,updated_at=? WHERE id=?",
                    exception.reason(), now, matchId);
            tasks.markFailed(parseTask.id(), exception.reason());
        }
        return get(current, matchId);
    }

    private void writeParsed(String accountId, String matchId, String jobId, String versionId, ParsedJd parsed,
            Instant now) {
        jdbc.update("UPDATE jobs SET company_name=?,title=?,updated_at=? WHERE id=? AND account_id=?",
                blank(parsed.company()), parsed.title(), now, jobId, accountId);
        List<String> required = parsed.requirements().stream().filter(item -> !"BONUS".equals(item.category()))
                .map(RequirementDraft::text).toList();
        List<String> preferred = parsed.requirements().stream().filter(item -> "BONUS".equals(item.category()))
                .map(RequirementDraft::text).toList();
        jdbc.update("UPDATE job_versions SET status='CONFIRMED',title=?,company_name=?,duties=?,location=?,work_mode=?,candidate_json=?,responsibilities_json=?,required_qualifications_json=?,preferred_qualifications_json=?,updated_at=?,confirmed_at=? WHERE id=? AND account_id=?",
                parsed.title(), blank(parsed.company()), String.join("\n", parsed.responsibilities()),
                blank(parsed.location()), parsed.workMode(), json(Map.of("confidence", parsed.confidence(),
                        "conflicts", parsed.conflicts())), json(parsed.responsibilities()), json(required),
                json(preferred), now, now, versionId, accountId);
        jdbc.update("DELETE FROM job_requirements WHERE match_id=?", matchId);
        int sequence = 0;
        for (RequirementDraft requirement : parsed.requirements()) {
            jdbc.update("INSERT INTO job_requirements(id,match_id,account_id,sequence_no,category,requirement_text,priority_code,hard_gate,source_locator,source_quote,confidence,user_corrected,version_no,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,0,0,?,?)",
                    Ids.newId(), matchId, accountId, ++sequence, requirement.category(), requirement.text(),
                    requirement.priority(), requirement.hardGate(), requirement.locator(), requirement.quote(),
                    requirement.confidence(), now, now);
        }
        String state = parsed.conflicts().isEmpty() ? "JD_PARSED" : "DRAFT";
        jdbc.update("UPDATE job_match_tasks SET status=?,progress_percent=20,checkpoint_code='JD_PARSED',error_code=NULL,version_no=version_no+1,updated_at=? WHERE id=?",
                state, now, matchId);
    }

    @Transactional
    public MatchView updateJd(CurrentAccount current, String matchId, JdStructureCommand command) {
        MatchRow row = owned(current, matchId, true);
        Versions.assertExpected(command.expectedVersion(), row.version());
        if (command.requirements() == null || command.requirements().size() < 3) {
            throw AppException.user("JD_REQUIREMENTS_INCOMPLETE", "请至少保留 3 条岗位要求");
        }
        Instant now = clock.now();
        jdbc.update("UPDATE jobs SET title=?,company_name=?,updated_at=? WHERE id=? AND account_id=?",
                required(command.title(), "JD_TITLE_REQUIRED", "请填写岗位名称"), blank(command.company()), now,
                row.jobId(), current.accountId());
        jdbc.update("UPDATE job_versions SET title=?,company_name=?,location=?,work_mode=?,status='CONFIRMED',candidate_json='{}',version_no=version_no+1,updated_at=?,confirmed_at=? WHERE id=? AND account_id=?",
                command.title().trim(), blank(command.company()), blank(command.location()),
                blankTo(command.workMode(), "ONSITE"), now, now, row.jobVersionId(), current.accountId());
        jdbc.update("DELETE FROM job_requirements WHERE match_id=?", matchId);
        int sequence = 0;
        for (RequirementWrite requirement : command.requirements()) {
            String value = required(requirement.text(), "JD_REQUIREMENT_REQUIRED", "岗位要求不能为空");
            jdbc.update("INSERT INTO job_requirements(id,match_id,account_id,sequence_no,category,requirement_text,priority_code,hard_gate,source_locator,source_quote,confidence,user_corrected,version_no,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,1,0,?,?)",
                    Ids.newId(), matchId, current.accountId(), ++sequence,
                    blankTo(requirement.category(), "SKILL"), value,
                    blankTo(requirement.priority(), "IMPORTANT"), requirement.hardGate(),
                    blankTo(requirement.sourceLocator(), "user-corrected:" + sequence),
                    blankTo(requirement.sourceQuote(), value), 100, now, now);
        }
        jdbc.update("UPDATE job_match_tasks SET status='JD_PARSED',progress_percent=20,checkpoint_code='JD_CONFIRMED',error_code=NULL,version_no=version_no+1,updated_at=? WHERE id=?",
                now, matchId);
        return get(current, matchId);
    }

    @Transactional(readOnly = true)
    public List<ResumeOption> resumeOptions(CurrentAccount current, String matchId) {
        owned(current, matchId, false);
        return jdbc.query("""
                SELECT m.id master_id,m.title,b.id branch_id,b.current_revision_id revision_id,
                       b.language_code,m.updated_at,r.content_hash
                  FROM resume_masters m
                  JOIN resume_branches b ON b.master_id=m.id AND b.status='ACTIVE'
                  JOIN resume_revisions r ON r.id=b.current_revision_id
                 WHERE m.account_id=? AND m.archived_at IS NULL
                 ORDER BY m.updated_at DESC
                """, (rs, n) -> new ResumeOption(rs.getString("master_id"), rs.getString("branch_id"),
                        rs.getString("revision_id"), rs.getString("title"), rs.getString("language_code"),
                        rs.getString("content_hash"), rs.getTimestamp("updated_at").toInstant()),
                current.accountId());
    }

    @Transactional
    public MatchView selectResume(CurrentAccount current, String matchId, ResumeSelectionCommand command) {
        MatchRow row = owned(current, matchId, true);
        Versions.assertExpected(command.expectedVersion(), row.version());
        if (!("JD_PARSED".equals(row.status()) || "RESUME_CONFIRMED".equals(row.status()))) {
            throw AppException.conflict("JOB_MATCH_RESUME_STAGE_INVALID", "请先确认岗位要求");
        }
        ResumeRef resume = jdbc.query("""
                SELECT r.master_id,r.branch_id,r.id revision_id
                  FROM resume_revisions r
                 WHERE r.id=? AND r.account_id=?
                """, (rs, n) -> new ResumeRef(rs.getString("master_id"), rs.getString("branch_id"),
                        rs.getString("revision_id")), command.resumeRevisionId(), current.accountId())
                .stream().findFirst().orElseThrow(() -> AppException.user("RESUME_REVISION_NOT_FOUND", "所选简历版本不存在"));
        jdbc.update("UPDATE job_match_tasks SET resume_master_id=?,resume_branch_id=?,resume_revision_id=?,resume_import_id=?,status='RESUME_CONFIRMED',progress_percent=40,checkpoint_code='RESUME_CONFIRMED',version_no=version_no+1,updated_at=? WHERE id=?",
                resume.masterId(), resume.branchId(), resume.revisionId(), blank(command.resumeImportId()),
                clock.now(), matchId);
        audit.append(current.accountId(), "JOB_MATCH_RESUME_CONFIRMED", "JOB_MATCH", matchId,
                "revision=" + resume.revisionId());
        return get(current, matchId);
    }

    @Transactional(readOnly = true)
    public List<EvidenceCandidate> evidenceRecommendations(CurrentAccount current, String matchId) {
        MatchRow row = owned(current, matchId, false);
        if (row.resumeRevisionId() == null) throw AppException.conflict("JOB_MATCH_RESUME_REQUIRED", "请先选择简历");
        Set<String> terms = requirementTerms(matchId);
        List<EvidenceCandidate> result = new ArrayList<>();
        jdbc.query("SELECT id,title,description_text,core_outcome,strength,record_type FROM career_library_records WHERE account_id=? AND status='ACTIVE' AND confirmed=1 ORDER BY updated_at DESC",
                rs -> {
                    String excerpt = join("；", rs.getString("description_text"), rs.getString("core_outcome"));
                    int relevance = relevance(terms, rs.getString("title") + " " + excerpt);
                    result.add(new EvidenceCandidate("CAREER_RECORD", rs.getString("id"), rs.getString("title"),
                            excerpt, "record:" + rs.getString("id"), blankTo(rs.getString("strength"), "MEDIUM"),
                            relevance, relevance >= 35));
                }, current.accountId());
        jdbc.query("SELECT id,display_name,category,original_filename FROM career_library_files WHERE account_id=? AND status='ACTIVE' AND processing_status='READY' ORDER BY updated_at DESC",
                rs -> {
                    String title = rs.getString("display_name");
                    int relevance = relevance(terms, title + " " + rs.getString("original_filename"));
                    result.add(new EvidenceCandidate("CAREER_FILE", rs.getString("id"), title, rs.getString("category"),
                            "file:" + rs.getString("id"), "MEDIUM", relevance, relevance >= 35));
                }, current.accountId());
        return result.stream().sorted(Comparator.comparingInt(EvidenceCandidate::relevance).reversed())
                .limit(100).toList();
    }

    @Transactional
    public MatchView authorizeEvidence(CurrentAccount current, String matchId, AuthorizationCommand command) {
        MatchRow row = owned(current, matchId, true);
        Versions.assertExpected(command.expectedVersion(), row.version());
        if (!"RESUME_CONFIRMED".equals(row.status()) && !"EVIDENCE_AUTHORIZED".equals(row.status())) {
            throw AppException.conflict("JOB_MATCH_EVIDENCE_STAGE_INVALID", "请先确认用于匹配的简历");
        }
        String mode = enumValue(command.mode(), Set.of("AUTO", "MANUAL", "NONE"), "NONE");
        List<EvidenceCandidate> available = evidenceRecommendations(current, matchId);
        Set<String> selected = new LinkedHashSet<>();
        if ("AUTO".equals(mode)) available.stream().filter(EvidenceCandidate::recommended)
                .forEach(item -> selected.add(item.sourceType() + ":" + item.sourceId()));
        if (command.recordIds() != null) command.recordIds().forEach(id -> selected.add("CAREER_RECORD:" + id));
        if (command.fileIds() != null) command.fileIds().forEach(id -> selected.add("CAREER_FILE:" + id));
        if ("NONE".equals(mode)) selected.clear();
        Instant now = clock.now();
        jdbc.update("UPDATE job_match_authorizations SET status='REVOKED',revoked_at=? WHERE match_id=? AND status='ACTIVE'",
                now, matchId);
        String authorizationId = Ids.newId();
        Instant expires = switch (blankTo(command.scope(), "CURRENT_MATCH")) {
            case "SEVEN_DAYS" -> now.plus(7, ChronoUnit.DAYS);
            case "THIRTY_DAYS" -> now.plus(30, ChronoUnit.DAYS);
            default -> null;
        };
        jdbc.update("INSERT INTO job_match_authorizations(id,match_id,account_id,scope_code,included_sources_json,excluded_fields_json,policy_version,status,remember_preference,expires_at,created_at,revoked_at) VALUES(?,?,?,?,?,?,?,'ACTIVE',?,?,?,NULL)",
                authorizationId, matchId, current.accountId(), blankTo(command.scope(), "CURRENT_MATCH"),
                json(selected), json(SENSITIVE_FIELDS), AUTH_POLICY, command.rememberPreference(), expires, now);
        jdbc.update("DELETE FROM job_match_evidence_items WHERE match_id=?", matchId);
        Map<String, EvidenceCandidate> byKey = new LinkedHashMap<>();
        available.forEach(item -> byKey.put(item.sourceType() + ":" + item.sourceId(), item));
        for (String key : selected) {
            EvidenceCandidate item = byKey.get(key);
            if (item == null) throw AppException.forbidden("EVIDENCE_FORBIDDEN", "所选资料不存在或无权访问");
            Map<String, Object> snapshot = Map.of("title", item.title(), "excerpt", blankTo(item.excerpt(), ""),
                    "locator", blankTo(item.locator(), ""), "strength", item.strength());
            String snapshotJson = json(snapshot);
            jdbc.update("INSERT INTO job_match_evidence_items(id,match_id,authorization_id,account_id,source_type,source_id,title,excerpt,locator,strength,relevance,snapshot_hash,snapshot_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Ids.newId(), matchId, authorizationId, current.accountId(), item.sourceType(), item.sourceId(),
                    item.title(), blank(item.excerpt()), blank(item.locator()), item.strength(), item.relevance(),
                    sha256(snapshotJson), snapshotJson, now);
        }
        jdbc.update("UPDATE job_match_tasks SET authorization_id=?,evidence_mode=?,status='EVIDENCE_AUTHORIZED',progress_percent=55,checkpoint_code='EVIDENCE_AUTHORIZED',version_no=version_no+1,updated_at=? WHERE id=?",
                authorizationId, mode, now, matchId);
        audit.append(current.accountId(), "JOB_MATCH_EVIDENCE_AUTHORIZED", "JOB_MATCH", matchId,
                "mode=" + mode + " selected=" + selected.size() + " sensitive_excluded=true");
        return get(current, matchId);
    }

    @Transactional(readOnly = true)
    public RedactionPreview redactionPreview(CurrentAccount current, String matchId) {
        MatchRow row = owned(current, matchId, false);
        List<Map<String, Object>> included = row.authorizationId() == null ? List.of()
                : jdbc.queryForList("SELECT source_type,source_id,title,strength,relevance FROM job_match_evidence_items WHERE match_id=? AND authorization_id=? ORDER BY relevance DESC",
                        matchId, row.authorizationId());
        return new RedactionPreview(matchId, row.resumeRevisionId(), included, SENSITIVE_FIELDS,
                "联系方式、详细地址、身份证及敏感属性不会发送给模型");
    }

    @Transactional
    public MatchView revokeAuthorization(CurrentAccount current, String matchId, MutationCommand command) {
        MatchRow row = owned(current, matchId, true);
        assertMutationVersion(command, row);
        if (row.authorizationId() == null) return get(current, matchId);
        Instant now = clock.now();
        jdbc.update("UPDATE job_match_authorizations SET status='REVOKED',revoked_at=? WHERE id=? AND match_id=? AND account_id=? AND status='ACTIVE'",
                now, row.authorizationId(), matchId, current.accountId());
        jdbc.update("UPDATE job_match_tasks SET authorization_id=NULL,evidence_mode=NULL,status='RESUME_CONFIRMED',progress_percent=40,checkpoint_code='AUTHORIZATION_REVOKED',version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                now, matchId, current.accountId());
        audit.append(current.accountId(), "JOB_MATCH_AUTHORIZATION_REVOKED", "JOB_MATCH", matchId,
                "authorization=" + row.authorizationId());
        return get(current, matchId);
    }

    @Transactional
    public AnalysisStart analyze(CurrentAccount current, String matchId, AnalyzeCommand command) {
        MatchRow row = owned(current, matchId, true);
        String requestId = required(command.requestId(), "JOB_MATCH_REQUEST_ID_REQUIRED", "缺少稳定请求编号");
        if (requestId.equals(row.analysisRequestId()) && row.analysisTaskId() != null) {
            return new AnalysisStart(get(current, matchId), tasks.getOwned(current.accountId(), row.analysisTaskId()),
                    quota.current(current.accountId()));
        }
        Versions.assertExpected(command.expectedVersion(), row.version());
        if (row.analysisTaskId() != null && Set.of("ANALYZING", "NEEDS_CLARIFICATION").contains(row.status())) {
            return new AnalysisStart(get(current, matchId), tasks.getOwned(current.accountId(), row.analysisTaskId()),
                    quota.current(current.accountId()));
        }
        if (!Set.of("EVIDENCE_AUTHORIZED", "ANALYSIS_PAUSED", "NEEDS_CLARIFICATION", "COMPLETED").contains(row.status())) {
            throw AppException.conflict("JOB_MATCH_NOT_READY", "请先完成岗位、简历和证据范围确认");
        }
        AiQuotaService.Reservation reservation = quota.reserve(current.accountId(), "JOB_MATCH:" + requestId,
                "JOB_MATCH_ANALYSIS", 1);
        TaskView task = tasks.create(current.accountId(), TaskTypes.JOB_MATCH,
                "JOB_MATCH:" + requestId, json(Map.of("matchId", matchId, "requestId", requestId)));
        Instant now = clock.now();
        jdbc.update("UPDATE job_match_tasks SET status='ANALYZING',analysis_task_id=?,analysis_request_id=?,quota_reservation_id=?,progress_percent=58,checkpoint_code='FREEZE_INPUTS',output_options_json=?,error_code=NULL,version_no=version_no+1,updated_at=? WHERE id=?",
                task.id(), requestId, reservation.id(), json(command.outputOptions() == null ? Map.of() : command.outputOptions()),
                now, matchId);
        audit.append(current.accountId(), "JOB_MATCH_ANALYSIS_STARTED", "JOB_MATCH", matchId,
                "task=" + task.id() + " request=" + requestId);
        return new AnalysisStart(get(current, matchId), task, quota.current(current.accountId()));
    }

    @Transactional
    public MatchView answerClarifications(CurrentAccount current, String matchId, ClarificationCommand command) {
        MatchRow row = owned(current, matchId, true);
        Versions.assertExpected(command.expectedVersion(), row.version());
        if (!"NEEDS_CLARIFICATION".equals(row.status())) {
            throw AppException.conflict("CLARIFICATION_NOT_REQUIRED", "当前分析没有待确认问题");
        }
        Instant now = clock.now();
        for (ClarificationAnswer answer : command.answers() == null ? List.<ClarificationAnswer>of() : command.answers()) {
            String answerCode = required(answer.answerCode(), "CLARIFICATION_ANSWER_REQUIRED", "请选择答案");
            String status = "暂不确认".equals(answerCode) ? "DEFERRED" : "ANSWERED";
            int updated = jdbc.update("UPDATE job_match_clarifications SET answer_code=?,answer_note=?,status=?,version_no=version_no+1,answered_at=? WHERE id=? AND match_id=? AND account_id=? AND status='PENDING'",
                    answerCode, blank(answer.note()), status, now, answer.id(), matchId, current.accountId());
            if (updated == 0) throw AppException.user("CLARIFICATION_NOT_FOUND", "待确认问题不存在");
        }
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM job_match_clarifications WHERE match_id=? AND status='PENDING'",
                Integer.class, matchId);
        if (pending != null && pending > 0 && !command.continueWithPending()) {
            throw AppException.user("CLARIFICATION_PENDING", "仍有待确认问题，可选择稍后确认或明确继续");
        }
        if (pending != null && pending > 0) {
            jdbc.update("UPDATE job_match_clarifications SET answer_code='暂不确认',status='DEFERRED',version_no=version_no+1,answered_at=? WHERE match_id=? AND account_id=? AND status='PENDING'",
                    now, matchId, current.accountId());
        }
        String request = row.analysisRequestId() + ":clarification:" + (row.version() + 1);
        TaskView task = tasks.create(current.accountId(), TaskTypes.JOB_MATCH, request,
                json(Map.of("matchId", matchId, "requestId", row.analysisRequestId(), "resume", true)));
        jdbc.update("UPDATE job_match_tasks SET status='ANALYZING',analysis_task_id=?,progress_percent=72,checkpoint_code='CLARIFICATION_CONFIRMED',version_no=version_no+1,updated_at=? WHERE id=?",
                task.id(), now, matchId);
        return get(current, matchId);
    }

    @Transactional
    public MatchView resumeAnalysis(CurrentAccount current, String matchId, MutationCommand command) {
        MatchRow row = owned(current, matchId, true);
        assertMutationVersion(command, row);
        if (!"ANALYSIS_PAUSED".equals(row.status())) return get(current, matchId);
        String request = row.analysisRequestId() + ":resume:" + (row.version() + 1);
        AiQuotaService.Reservation reservation = quota.reserve(current.accountId(),
                "JOB_MATCH:" + request, "JOB_MATCH_ANALYSIS", 1);
        TaskView task = tasks.create(current.accountId(), TaskTypes.JOB_MATCH, request,
                json(Map.of("matchId", matchId, "requestId", row.analysisRequestId(), "resume", true)));
        jdbc.update("UPDATE job_match_tasks SET status='ANALYZING',analysis_task_id=?,quota_reservation_id=?,error_code=NULL,version_no=version_no+1,updated_at=? WHERE id=?",
                task.id(), reservation.id(), clock.now(), matchId);
        return get(current, matchId);
    }

    @Transactional
    public MatchView cancel(CurrentAccount current, String matchId, MutationCommand command) {
        MatchRow row = owned(current, matchId, true);
        assertMutationVersion(command, row);
        if (row.analysisTaskId() != null) {
            try { tasks.cancel(current.accountId(), row.analysisTaskId()); } catch (RuntimeException ignored) {}
        }
        if (row.quotaReservationId() != null) quota.release(current.accountId(), row.quotaReservationId());
        jdbc.update("UPDATE job_match_tasks SET status='CANCELLED',checkpoint_code='CANCELLED',version_no=version_no+1,updated_at=? WHERE id=?",
                clock.now(), matchId);
        return get(current, matchId);
    }

    @Transactional
    public ClaimView feedback(CurrentAccount current, String matchId, String claimId, FeedbackCommand command) {
        owned(current, matchId, true);
        ClaimView before = claim(matchId, claimId);
        if (command != null && clean(command.requestId()) != null && command.requestId().equals(before.feedbackRequestId())) {
            return before;
        }
        if (command != null && command.expectedVersion() != null) {
            Versions.assertExpected(command.expectedVersion(), before.version());
        }
        String feedback = enumValue(command.feedback(), Set.of("ACCURATE", "INACCURATE", "IGNORE"), "IGNORE");
        int updated = jdbc.update("UPDATE job_match_claims SET feedback_status=?,feedback_request_id=?,version_no=version_no+1,updated_at=? WHERE id=? AND match_id=? AND version_no=?",
                feedback, clean(command.requestId()), clock.now(), claimId, matchId, before.version());
        if (updated == 0) throw AppException.user("JOB_MATCH_CLAIM_NOT_FOUND", "报告结论不存在");
        return claim(matchId, claimId);
    }

    @Transactional(readOnly = true)
    public ClaimView getClaim(CurrentAccount current, String matchId, String claimId) {
        owned(current, matchId, false);
        return claim(matchId, claimId);
    }

    @Transactional
    public ImprovementView updateImprovement(CurrentAccount current, String matchId, String taskId,
            ImprovementCommand command) {
        owned(current, matchId, true);
        String status = enumValue(command.status(), Set.of("TODO", "IN_PROGRESS", "DONE", "SKIPPED"), "TODO");
        int updated = jdbc.update("UPDATE job_match_improvement_tasks SET status=?,version_no=version_no+1,updated_at=? WHERE id=? AND match_id=? AND account_id=? AND version_no=?",
                status, clock.now(), taskId, matchId, current.accountId(), command.expectedVersion());
        if (updated == 0) throw AppException.conflict("VERSION_CONFLICT", "学习任务已经变化，请刷新后重试");
        return jdbc.query("SELECT * FROM job_match_improvement_tasks WHERE id=? AND match_id=?",
                this::improvement, taskId, matchId).stream().findFirst()
                .orElseThrow(() -> AppException.user("JOB_MATCH_TASK_NOT_FOUND", "学习任务不存在"));
    }

    @Transactional
    public OptimizationLink createOptimization(CurrentAccount current, String matchId, MutationCommand command) {
        MatchRow row = owned(current, matchId, false);
        assertMutationVersion(command, row);
        if (row.resumeMasterId() == null || row.currentReportId() == null) {
            throw AppException.conflict("JOB_MATCH_REPORT_REQUIRED", "报告完成后才能优化简历");
        }
        var conversation = workbench.ensureForResume(current, row.resumeMasterId());
        List<Map<String, Object>> advice = jdbc.queryForList("SELECT target_path,before_value_json,proposed_value_json,reason_text,source_refs_json FROM match_advice_items WHERE report_id=? AND account_id=? AND status='PENDING' ORDER BY sequence_no",
                row.currentReportId(), current.accountId());
        List<GeneratedItem> items = new ArrayList<>();
        for (Map<String, Object> value : advice) {
            String path = blank(String.valueOf(value.get("target_path")));
            String proposed = scalarJson(value.get("proposed_value_json"));
            if (path == null || proposed == null || proposed.isBlank()) continue;
            String before = scalarJson(value.get("before_value_json"));
            List<Map<String, String>> facts = sourceFacts(value.get("source_refs_json"));
            items.add(new GeneratedItem(moduleForPath(path), path, "REPLACE_TEXT", blankTo(before, ""), proposed,
                    blankTo(blank(String.valueOf(value.get("reason_text"))), "对齐岗位要求并提高证据清晰度"),
                    facts, Map.of("source", "JOB_MATCH_REPORT", "reportId", row.currentReportId()), false));
        }
        if (!items.isEmpty()) {
            GeneratedChange generated = new GeneratedChange(
                    "已根据岗位匹配报告生成 " + items.size() + " 条逐条修改。每条都可单独接受、编辑或拒绝，接受后才会写入正式简历。",
                    "RESUME_CHANGE", List.copyOf(items), List.of(), "job-match-report-v2", 0, 0,
                    Map.of("jobMatchId", matchId, "reportId", row.currentReportId()));
            workbench.attachExternalChanges(current, conversation.id(), generated, "JOB_MATCH_REPORT", row.currentReportId());
        }
        return new OptimizationLink(conversation.id(), row.resumeMasterId(), row.resumeRevisionId(), matchId,
                "/ai-resume/" + conversation.id() + "?jobMatchId=" + matchId);
    }

    private String scalarJson(Object raw) {
        if (raw == null) return "";
        JsonNode node = readNode(String.valueOf(raw));
        return node.isTextual() ? node.asText() : node.toString();
    }

    private List<Map<String, String>> sourceFacts(Object raw) {
        JsonNode node = readNode(raw == null ? "[]" : String.valueOf(raw));
        List<Map<String, String>> result = new ArrayList<>();
        if (node.isArray()) for (JsonNode value : node) {
            String ref = value.isTextual() ? value.asText() : value.toString();
            if (!ref.isBlank()) result.add(Map.of("source", "jobMatch", "quote", ref));
        }
        if (result.isEmpty()) result.add(Map.of("source", "jobMatch", "quote", "岗位匹配报告中的已确认简历事实"));
        return List.copyOf(result);
    }

    private static String moduleForPath(String path) {
        if (path.startsWith("/summary")) return "SUMMARY";
        if (path.startsWith("/education")) return "EDUCATION";
        if (path.startsWith("/experiences")) return "EXPERIENCE";
        if (path.startsWith("/projects")) return "PROJECTS";
        if (path.startsWith("/organizations")) return "ORGANIZATIONS";
        if (path.startsWith("/skills")) return "SKILLS";
        if (path.startsWith("/certificates")) return "CERTIFICATES";
        if (path.startsWith("/honors")) return "HONORS";
        return "LANGUAGES";
    }

    /**
     * Taxonomy directions whose name or alias names a skill from the frozen resume's skills section.
     * Only directions with at least one shared skill are returned, most shared first; the target
     * direction itself is left out. No score is derived: the shared skills are the whole basis.
     */
    @Transactional(readOnly = true)
    public SimilarDirectionsView careerDirections(CurrentAccount current, String matchId) {
        MatchRow row = owned(current, matchId, false);
        List<String> skills = row.resumeRevisionId() == null ? List.of() : resumeSkills(row);
        if (skills.isEmpty()) return new SimilarDirectionsView(List.of(), List.of());
        String target = compactLower(jdbc.query("SELECT title FROM jobs WHERE id=?", (rs, n) -> rs.getString(1),
                row.jobId()).stream().findFirst().orElse(""));
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        jdbc.query("SELECT node_id,alias_name FROM job_taxonomy_aliases", rs -> {
            aliases.computeIfAbsent(rs.getString("node_id"), ignored -> new ArrayList<>()).add(rs.getString("alias_name"));
        });
        List<CareerDirection> directions = new ArrayList<>();
        jdbc.query("""
                SELECT n.id,n.display_name,c.display_name category_name
                  FROM job_taxonomy_nodes n
                  JOIN job_taxonomy_nodes g ON g.id=n.parent_id
                  JOIN job_taxonomy_nodes c ON c.id=g.parent_id
                 WHERE n.node_level='JOB' AND n.status='PUBLISHED'
                 ORDER BY c.sort_order,n.sort_order,n.display_name
                """, rs -> {
                    String name = rs.getString("display_name");
                    if (!target.isEmpty() && target.contains(compactLower(name))) return;
                    List<String> terms = new ArrayList<>(List.of(name.split("/")));
                    terms.addAll(aliases.getOrDefault(rs.getString("id"), List.of()));
                    List<String> shared = skills.stream()
                            .filter(skill -> terms.stream().anyMatch(term -> namesSkill(term, skill))).toList();
                    if (!shared.isEmpty()) {
                        directions.add(new CareerDirection(rs.getString("id"), name, rs.getString("category_name"), shared));
                    }
                });
        List<CareerDirection> ranked = directions.stream()
                .sorted(Comparator.comparingInt((CareerDirection item) -> item.sharedSkills().size()).reversed())
                .limit(12).toList();
        return new SimilarDirectionsView(skills, ranked);
    }

    /** Skill names from the skills section of the match's frozen resume revision, in resume order. */
    private List<String> resumeSkills(MatchRow row) {
        String content = jdbc.query("SELECT content_json FROM resume_revisions WHERE id=? AND account_id=?",
                (rs, n) -> rs.getString(1), row.resumeRevisionId(), row.accountId()).stream().findFirst().orElse(null);
        JsonNode sections = readNode(content).path("skills");
        Map<String, String> unique = new LinkedHashMap<>();
        if (sections.isArray()) {
            for (JsonNode entry : sections) {
                if (entry.isTextual()) addSkills(unique, entry.asText());
                addSkills(unique, entry.path("name").asText(""));
                for (JsonNode item : entry.path("items")) addSkills(unique, item.asText(""));
            }
        }
        return unique.values().stream().limit(60).toList();
    }

    private static void addSkills(Map<String, String> unique, String value) {
        for (String part : value.split("[、,，;；|\\n]+")) {
            String skill = part.trim();
            if (!skill.isEmpty() && skill.length() <= 40) unique.putIfAbsent(compactLower(skill), skill);
        }
    }

    /**
     * Whether a direction term (a taxonomy name part or alias) names the skill. Chinese terms match
     * when the skill contains them ("数据挖掘与分析" → 数据挖掘); Latin terms must equal a whole token so
     * "Go" never matches "Django".
     */
    static boolean namesSkill(String term, String skill) {
        String t = compactLower(term);
        String s = compactLower(skill);
        if (t.isEmpty() || s.isEmpty()) return false;
        if (t.equals(s)) return true;
        if (t.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)) {
            return t.codePointCount(0, t.length()) >= 2 && s.contains(t);
        }
        Matcher tokens = LATIN_TOKEN.matcher(s);
        while (tokens.find()) {
            String token = tokens.group().replaceAll("\\.+$", "");
            if (token.equals(t)) return true;
        }
        return false;
    }

    private static String compactLower(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(CurrentAccount current) {
        assertEnabled();
        assertUser(current);
        List<MatchSummary> items = jdbc.query("""
                SELECT t.*,j.title,j.company_name,m.title resume_title,r.total_score,r.confidence
                  FROM job_match_tasks t JOIN jobs j ON j.id=t.job_id
                  LEFT JOIN resume_masters m ON m.id=t.resume_master_id
                  LEFT JOIN match_reports r ON r.id=t.current_report_id
                 WHERE t.account_id=? AND t.archived_at IS NULL ORDER BY t.updated_at DESC
                """, this::summary, current.accountId());
        int completed = (int) items.stream().filter(item -> "COMPLETED".equals(item.status())).count();
        int pending = (int) items.stream().filter(item -> !Set.of("COMPLETED", "CANCELLED").contains(item.status())).count();
        java.util.OptionalDouble scored = items.stream().filter(item -> item.score() != null)
                .mapToInt(item -> item.score()).average();
        Integer average = scored.isPresent() ? (int) Math.round(scored.getAsDouble()) : null;
        int optimized = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT r.match_id)
                  FROM match_advice_items a
                  JOIN match_reports r ON r.id=a.report_id
                 WHERE a.account_id=? AND a.status='APPLIED' AND r.match_id IS NOT NULL
                """, Integer.class, current.accountId());
        return new DashboardView(items.size(), completed, pending, average, optimized, items.stream().limit(6).toList());
    }

    @Transactional(readOnly = true)
    public List<MatchSummary> history(CurrentAccount current, String status, String query, boolean archived) {
        assertEnabled();
        assertUser(current);
        StringBuilder sql = new StringBuilder("""
                SELECT t.*,j.title,j.company_name,m.title resume_title,r.total_score,r.confidence
                  FROM job_match_tasks t JOIN jobs j ON j.id=t.job_id
                  LEFT JOIN resume_masters m ON m.id=t.resume_master_id
                  LEFT JOIN match_reports r ON r.id=t.current_report_id WHERE t.account_id=?
                """);
        List<Object> args = new ArrayList<>();
        args.add(current.accountId());
        if (!archived) sql.append(" AND t.archived_at IS NULL");
        if (clean(status) != null) { sql.append(" AND t.status=?"); args.add(status.trim()); }
        if (clean(query) != null) { sql.append(" AND (LOWER(j.title) LIKE ? OR LOWER(j.company_name) LIKE ?)"); String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%"; args.add(like); args.add(like); }
        sql.append(" ORDER BY t.updated_at DESC");
        return jdbc.query(sql.toString(), this::summary, args.toArray());
    }

    @Transactional(readOnly = true)
    public HistoryPage historyPage(CurrentAccount current, String status, String query, boolean archived,
            int page, int size) {
        assertEnabled();
        assertUser(current);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(10, Math.min(100, size));
        StringBuilder where = new StringBuilder(" WHERE t.account_id=?");
        List<Object> args = new ArrayList<>();
        args.add(current.accountId());
        if (!archived) where.append(" AND t.archived_at IS NULL");
        if (clean(status) != null) { where.append(" AND t.status=?"); args.add(status.trim()); }
        if (clean(query) != null) {
            where.append(" AND (LOWER(j.title) LIKE ? OR LOWER(j.company_name) LIKE ? OR LOWER(COALESCE(m.title,'')) LIKE ?)");
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(like); args.add(like); args.add(like);
        }
        String joins = " FROM job_match_tasks t JOIN jobs j ON j.id=t.job_id LEFT JOIN resume_masters m ON m.id=t.resume_master_id LEFT JOIN match_reports r ON r.id=t.current_report_id";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + joins + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);
        List<MatchSummary> items = jdbc.query("SELECT t.*,j.title,j.company_name,m.title resume_title,r.total_score,r.confidence"
                + joins + where + " ORDER BY t.updated_at DESC LIMIT ? OFFSET ?", this::summary, pageArgs.toArray());
        return new HistoryPage(items, safePage, safeSize, total, (int) Math.ceil(total / (double) safeSize));
    }

    @Transactional(readOnly = true)
    public MatchView get(CurrentAccount current, String matchId) {
        MatchRow row = owned(current, matchId, false);
        Map<String, Object> job = jdbc.queryForMap("SELECT title,company_name FROM jobs WHERE id=?", row.jobId());
        Map<String, Object> version = jdbc.queryForMap("SELECT location,work_mode,candidate_json FROM job_versions WHERE id=?", row.jobVersionId());
        List<RequirementView> requirements = jdbc.query("SELECT * FROM job_requirements WHERE match_id=? ORDER BY sequence_no",
                this::requirement, matchId);
        ResumeOption selectedResume = row.resumeRevisionId() == null ? null : jdbc.query("""
                SELECT m.id master_id,m.title,b.id branch_id,r.id revision_id,b.language_code,r.content_hash,m.updated_at
                  FROM resume_revisions r JOIN resume_branches b ON b.id=r.branch_id
                  JOIN resume_masters m ON m.id=r.master_id WHERE r.id=? AND r.account_id=?
                """, (rs, n) -> new ResumeOption(rs.getString("master_id"), rs.getString("branch_id"),
                        rs.getString("revision_id"), rs.getString("title"), rs.getString("language_code"),
                        rs.getString("content_hash"), rs.getTimestamp("updated_at").toInstant()),
                row.resumeRevisionId(), current.accountId()).stream().findFirst().orElse(null);
        List<ClarificationView> clarifications = jdbc.query("SELECT * FROM job_match_clarifications WHERE match_id=? ORDER BY sequence_no",
                this::clarification, matchId);
        return new MatchView(row.id(), row.status(), row.version(), row.progress(), row.checkpoint(), row.errorCode(),
                row.jobId(), row.jobVersionId(), String.valueOf(job.get("title")), blank(String.valueOf(job.get("company_name"))),
                blank(String.valueOf(version.get("location"))), blank(String.valueOf(version.get("work_mode"))),
                requirements, selectedResume, row.resumeImportId(), row.evidenceMode(), row.authorizationId(),
                row.currentReportId(), row.analysisTaskId(), clarifications, row.createdAt(), row.updatedAt());
    }

    @Transactional(readOnly = true)
    public ReportView report(CurrentAccount current, String matchId) {
        MatchRow row = owned(current, matchId, false);
        if (row.currentReportId() == null) throw AppException.conflict("JOB_MATCH_REPORT_NOT_READY", "岗位匹配报告尚未生成");
        String reportJson = jdbc.queryForObject("SELECT report_json FROM job_match_report_versions WHERE report_id=? ORDER BY version_no DESC LIMIT 1",
                String.class, row.currentReportId());
        Map<String, Object> report = readMap(reportJson);
        List<ClaimView> claims = jdbc.query("SELECT * FROM job_match_claims WHERE report_id=? ORDER BY created_at,id",
                this::claim, row.currentReportId());
        List<ImprovementView> plan = jdbc.query("SELECT * FROM job_match_improvement_tasks WHERE report_id=? ORDER BY phase_code,priority_code,created_at",
                this::improvement, row.currentReportId());
        List<EvidenceSourceView> sources = jdbc.query("SELECT id,source_type,title FROM job_match_evidence_items WHERE match_id=? AND account_id=? ORDER BY created_at,id",
                (rs, n) -> new EvidenceSourceView(rs.getString("id"), rs.getString("source_type"), rs.getString("title")),
                matchId, row.accountId());
        return new ReportView(row.currentReportId(), matchId, report, claims, plan, sources,
                row.status(), row.updatedAt());
    }

    @Transactional(readOnly = true)
    public List<ReportVersionView> reportVersions(CurrentAccount current, String matchId) {
        MatchRow row = owned(current, matchId, false);
        return jdbc.query("SELECT * FROM job_match_report_versions WHERE match_id=? AND account_id=? ORDER BY version_no DESC",
                (rs, n) -> {
                    Map<String, Object> document = readMap(rs.getString("report_json"));
                    Map<String, Object> ai = mapValue(document.get("ai"));
                    Map<String, Object> recommendation = mapValue(ai.get("recommendation"));
                    return new ReportVersionView(rs.getString("id"), rs.getString("report_id"),
                            rs.getInt("version_no"), intValue(document.get("score")),
                            intValue(document.get("confidence")), blank(String.valueOf(recommendation.get("code"))),
                            rs.getString("report_id").equals(row.currentReportId()),
                            rs.getTimestamp("created_at").toInstant());
                }, matchId, current.accountId());
    }

    @Transactional(readOnly = true)
    public ReportComparisonView compareReportVersions(CurrentAccount current, String matchId, int fromVersion,
            int toVersion) {
        owned(current, matchId, false);
        Map<String, Object> from = reportDocument(current.accountId(), matchId, fromVersion);
        Map<String, Object> to = reportDocument(current.accountId(), matchId, toVersion);
        Map<String, Object> fromAi = mapValue(from.get("ai"));
        Map<String, Object> toAi = mapValue(to.get("ai"));
        List<ReportDelta> changes = List.of(
                delta("综合匹配度", intValue(from.get("score")), intValue(to.get("score"))),
                delta("可信度", intValue(from.get("confidence")), intValue(to.get("confidence"))),
                delta("匹配优势", listValue(fromAi.get("strengths")).size(), listValue(toAi.get("strengths")).size()),
                delta("待处理缺口", listValue(fromAi.get("gaps")).size(), listValue(toAi.get("gaps")).size()),
                delta("简历修改建议", listValue(fromAi.get("resumeSuggestions")).size(), listValue(toAi.get("resumeSuggestions")).size()));
        return new ReportComparisonView(fromVersion, toVersion, changes,
                listValue(fromAi.get("resumeSuggestions")), listValue(toAi.get("resumeSuggestions")));
    }

    private Map<String, Object> reportDocument(String accountId, String matchId, int version) {
        String value = jdbc.query("SELECT report_json FROM job_match_report_versions WHERE match_id=? AND account_id=? AND version_no=?",
                (rs, n) -> rs.getString("report_json"), matchId, accountId, version).stream().findFirst()
                .orElseThrow(() -> AppException.user("JOB_MATCH_REPORT_VERSION_NOT_FOUND", "报告版本不存在"));
        return readMap(value);
    }

    private static ReportDelta delta(String label, int before, int after) {
        return new ReportDelta(label, before, after, after - before);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listValue(Object value) {
        return value instanceof List<?> list ? list.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList() : List.of();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (RuntimeException ignored) { return 0; }
    }

    MatchRow internalOwned(String accountId, String matchId, boolean lock) {
        String sql = "SELECT * FROM job_match_tasks WHERE id=? AND account_id=?" + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, this::row, matchId, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.forbidden("OBJECT_FORBIDDEN", "岗位匹配任务不存在或无权访问"));
    }

    Map<String, Object> internalInput(MatchRow row) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("matchId", row.id());
        input.put("job", jdbc.queryForMap("SELECT j.title,j.company_name,v.location,v.work_mode,s.normalized_text FROM jobs j JOIN job_versions v ON v.id=? JOIN jd_snapshots s ON s.id=v.snapshot_id WHERE j.id=?", row.jobVersionId(), row.jobId()));
        input.put("requirements", jdbc.queryForList("SELECT id,category,requirement_text,priority_code,hard_gate,source_locator,source_quote FROM job_requirements WHERE match_id=? ORDER BY sequence_no", row.id()));
        input.put("resume", jdbc.queryForMap("SELECT id,content_json,content_hash FROM resume_revisions WHERE id=? AND account_id=?", row.resumeRevisionId(), row.accountId()));
        input.put("evidence", jdbc.queryForList("SELECT id,source_type,source_id,title,excerpt,locator,strength,relevance FROM job_match_evidence_items WHERE match_id=? AND authorization_id=? ORDER BY relevance DESC", row.id(), row.authorizationId()));
        input.put("clarifications", jdbc.queryForList("SELECT requirement_id,question_text,answer_code,answer_note,status FROM job_match_clarifications WHERE match_id=? AND status IN ('ANSWERED','DEFERRED') ORDER BY sequence_no", row.id()));
        input.put("excludedFields", SENSITIVE_FIELDS);
        return input;
    }

    private MatchRow owned(CurrentAccount current, String matchId, boolean lock) {
        assertEnabled();
        assertUser(current);
        return internalOwned(current.accountId(), matchId, lock);
    }

    private MatchRow row(ResultSet rs, int n) throws SQLException {
        return new MatchRow(rs.getString("id"), rs.getString("account_id"), rs.getString("job_id"),
                rs.getString("job_version_id"), rs.getString("status"), rs.getString("resume_master_id"),
                rs.getString("resume_branch_id"), rs.getString("resume_revision_id"), rs.getString("resume_import_id"),
                rs.getString("authorization_id"), rs.getString("current_report_id"), rs.getString("analysis_task_id"),
                rs.getString("analysis_request_id"), rs.getString("quota_reservation_id"), rs.getString("evidence_mode"),
                rs.getInt("progress_percent"), rs.getString("checkpoint_code"), rs.getString("error_code"),
                rs.getInt("version_no"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private RequirementView requirement(ResultSet rs, int n) throws SQLException { return new RequirementView(rs.getString("id"), rs.getInt("sequence_no"), rs.getString("category"), rs.getString("requirement_text"), rs.getString("priority_code"), rs.getBoolean("hard_gate"), rs.getString("source_locator"), rs.getString("source_quote"), rs.getInt("confidence"), rs.getBoolean("user_corrected")); }
    private ClarificationView clarification(ResultSet rs, int n) throws SQLException { return new ClarificationView(rs.getString("id"), rs.getString("requirement_id"), rs.getInt("sequence_no"), rs.getString("question_text"), readList(rs.getString("options_json")), readMap(rs.getString("evidence_context_json")), rs.getString("answer_code"), rs.getString("answer_note"), rs.getString("status"), rs.getInt("version_no")); }
    private ClaimView claim(ResultSet rs, int n) throws SQLException { return new ClaimView(rs.getString("id"), rs.getString("requirement_id"), rs.getString("conclusion_type"), (Integer) rs.getObject("score"), rs.getInt("confidence"), readList(rs.getString("evidence_ids_json")), rs.getString("reasoning_summary"), rs.getString("feedback_status"), rs.getString("feedback_request_id"), rs.getInt("version_no")); }
    private ClaimView claim(String matchId, String claimId) { return jdbc.query("SELECT * FROM job_match_claims WHERE id=? AND match_id=?", this::claim, claimId, matchId).stream().findFirst().orElseThrow(() -> AppException.user("JOB_MATCH_CLAIM_NOT_FOUND", "报告结论不存在")); }
    private ImprovementView improvement(ResultSet rs, int n) throws SQLException { return new ImprovementView(rs.getString("id"), rs.getString("gap_code"), rs.getString("phase_code"), rs.getString("title"), rs.getString("task_text"), rs.getString("expected_output"), rs.getString("acceptance_criteria"), rs.getInt("estimated_hours"), rs.getString("priority_code"), rs.getString("status"), rs.getInt("version_no")); }
    private MatchSummary summary(ResultSet rs, int n) throws SQLException { return new MatchSummary(rs.getString("id"), rs.getString("title"), rs.getString("company_name"), rs.getString("resume_title"), rs.getString("status"), (Integer) rs.getObject("total_score"), rs.getString("confidence"), rs.getInt("progress_percent"), rs.getTimestamp("updated_at").toInstant()); }

    private Set<String> requirementTerms(String matchId) {
        Set<String> result = new LinkedHashSet<>();
        jdbc.queryForList("SELECT requirement_text FROM job_requirements WHERE match_id=?", String.class, matchId)
                .forEach(text -> {
                    for (String token : text.split("[^\\p{L}\\p{N}+#.]+")) if (token.length() >= 2) result.add(token.toLowerCase(Locale.ROOT));
                });
        return result;
    }
    private static int relevance(Set<String> terms, String text) { String lower = blankTo(text, "").toLowerCase(Locale.ROOT); int hits = 0; for (String term : terms) if (lower.contains(term)) hits++; return Math.min(100, hits * 18); }
    private static String normalize(String value) { return blankTo(value, "").replace("\r\n", "\n").replace('\r', '\n').replaceAll("[ \\t]+", " ").trim(); }
    private static String join(String separator, String... values) { List<String> result = new ArrayList<>(); for (String value : values) if (clean(value) != null) result.add(value.trim()); return String.join(separator, result); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Map<String, Object> readMap(String value) { try { return value == null ? new LinkedHashMap<>() : mapper.readValue(value, new TypeReference<>() {}); } catch (Exception e) { return new LinkedHashMap<>(); } }
    private JsonNode readNode(String value) { try { return value == null ? mapper.nullNode() : mapper.readTree(value); } catch (Exception e) { return mapper.getNodeFactory().textNode(value == null ? "" : value); } }
    private List<Object> readList(String value) { try { return value == null ? List.of() : mapper.readValue(value, new TypeReference<>() {}); } catch (Exception e) { return List.of(); } }
    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String required(String value, String code, String message) { if (clean(value) == null) throw AppException.user(code, message); return value.trim(); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String blank(String value) { return value == null || value.isBlank() || "null".equals(value) ? null : value.trim(); }
    private static String blankTo(String value, String fallback) { return clean(value) == null ? fallback : value.trim(); }
    private static String enumValue(String value, Set<String> allowed, String fallback) { String result = clean(value) == null ? fallback : value.trim().toUpperCase(Locale.ROOT); if (!allowed.contains(result)) throw AppException.user("JOB_MATCH_VALUE_INVALID", "不支持的选项：" + result); return result; }
    private static void assertMutationVersion(MutationCommand command, MatchRow row) {
        if (command != null && command.expectedVersion() != null) Versions.assertExpected(command.expectedVersion(), row.version());
    }
    private void assertEnabled() { if (!enabled) throw AppException.forbidden("JOB_MATCH_DISABLED", "岗位匹配功能尚未开放"); }
    private static void assertUser(CurrentAccount current) { if (!("SEEKER".equals(current.role()) || "ADMIN".equals(current.role()))) throw AppException.forbidden("JOB_MATCH_ROLE_REQUIRED", "当前账号不能使用岗位匹配"); }

    public record CreateCommand(String sourceType, String text, String requestId) {}
    public record RequirementWrite(String id, String category, String text, String priority, boolean hardGate, String sourceLocator, String sourceQuote) {}
    public record JdStructureCommand(String title, String company, String location, String workMode, List<RequirementWrite> requirements, Integer expectedVersion, String requestId) {}
    public record ResumeSelectionCommand(String resumeRevisionId, String resumeImportId, Integer expectedVersion, String requestId) {}
    public record AuthorizationCommand(String mode, List<String> recordIds, List<String> fileIds, String scope, boolean rememberPreference, Integer expectedVersion, String requestId) {}
    public record AnalyzeCommand(String requestId, Map<String, Object> outputOptions, Integer expectedVersion) {}
    public record ClarificationAnswer(String id, String answerCode, String note) {}
    public record ClarificationCommand(List<ClarificationAnswer> answers, boolean continueWithPending, Integer expectedVersion, String requestId) {}
    public record FeedbackCommand(String feedback, String requestId, Integer expectedVersion) {}
    public record ImprovementCommand(String status, int expectedVersion, String requestId) {}
    public record MutationCommand(String requestId, Integer expectedVersion) {}
    public record CapabilitiesView(boolean enabled, boolean ocrAvailable, List<String> jdSources, List<String> resumeSources, int maxFileBytes, int minJdChars, int maxJdChars, AiQuotaService.QuotaView quota) {}
    public record ResumeOption(String masterId, String branchId, String revisionId, String title, String languageCode, String contentHash, Instant updatedAt) {}
    public record RequirementView(String id, int sequence, String category, String text, String priority, boolean hardGate, String sourceLocator, String sourceQuote, int confidence, boolean userCorrected) {}
    public record ClarificationView(String id, String requirementId, int sequence, String question, List<Object> options, Map<String, Object> evidenceContext, String answerCode, String answerNote, String status, int version) {}
    public record MatchView(String id, String status, int version, int progress, String checkpoint, String errorCode, String jobId, String jobVersionId, String title, String company, String location, String workMode, List<RequirementView> requirements, ResumeOption resume, String resumeImportId, String evidenceMode, String authorizationId, String reportId, String analysisTaskId, List<ClarificationView> clarifications, Instant createdAt, Instant updatedAt) {}
    public record AnalysisStart(MatchView match, TaskView task, AiQuotaService.QuotaView quota) {}
    public record RedactionPreview(String matchId, String resumeRevisionId, List<Map<String, Object>> includedSources, List<String> excludedFields, String notice) {}
    public record ClaimView(String id, String requirementId, String conclusionType, Integer score, int confidence, List<Object> evidenceIds, String reasoning, String feedback, String feedbackRequestId, int version) {}
    public record ImprovementView(String id, String gapCode, String phase, String title, String task, String expectedOutput, String acceptanceCriteria, int estimatedHours, String priority, String status, int version) {}
    /** evidenceSources names the authorized items that claims and evidenceIds refer to. */
    public record ReportView(String id, String matchId, Map<String, Object> report, List<ClaimView> claims, List<ImprovementView> learningPlan, List<EvidenceSourceView> evidenceSources, String status, Instant updatedAt) {}
    public record MatchSummary(String id, String title, String company, String resumeTitle, String status, Integer score, String confidence, int progress, Instant updatedAt) {}
    /** averageScore is null until a report has a score; pending counts matches still in progress. */
    public record DashboardView(int total, int completed, int pending, Integer averageScore, int optimized, List<MatchSummary> recent) {}
    public record HistoryPage(List<MatchSummary> items, int page, int size, long total, int totalPages) {}
    public record ReportVersionView(String id, String reportId, int version, int score, int confidence,
            String recommendation, boolean current, Instant createdAt) {}
    public record ReportDelta(String label, int before, int after, int delta) {}
    public record ReportComparisonView(int fromVersion, int toVersion, List<ReportDelta> changes,
            List<Map<String, Object>> beforeSuggestions, List<Map<String, Object>> afterSuggestions) {}
    public record OptimizationLink(String conversationId, String resumeMasterId, String resumeRevisionId, String jobMatchId, String path) {}
    public record CareerDirection(String taxonomyNodeId, String title, String category, List<String> sharedSkills) {}
    public record SimilarDirectionsView(List<String> resumeSkills, List<CareerDirection> directions) {}
    public record EvidenceSourceView(String id, String sourceType, String title) {}
    public record MatchRow(String id, String accountId, String jobId, String jobVersionId, String status, String resumeMasterId, String resumeBranchId, String resumeRevisionId, String resumeImportId, String authorizationId, String currentReportId, String analysisTaskId, String analysisRequestId, String quotaReservationId, String evidenceMode, int progress, String checkpoint, String errorCode, int version, Instant createdAt, Instant updatedAt) {}
    private record ResumeRef(String masterId, String branchId, String revisionId) {}
}
