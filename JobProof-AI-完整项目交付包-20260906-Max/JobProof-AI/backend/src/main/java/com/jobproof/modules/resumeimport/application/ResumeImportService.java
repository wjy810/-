package com.jobproof.modules.resumeimport.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.career.application.CareerFileService;
import com.jobproof.modules.resumeimport.domain.ResumeContentParser;
import com.jobproof.modules.resumeimport.domain.ResumeTextExtractor;
import com.jobproof.modules.resume.domain.ResumeStructuredContent;
import com.jobproof.modules.resume.infra.ResumeMasterEntity;
import com.jobproof.modules.resume.infra.ResumeMasterJpaRepository;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.export.AccountExportContributor;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeImportService implements DeletionModuleHandler, AccountExportContributor {
    private static final int MAX_PASTED_CHARS = 200_000;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final TaskService tasks;
    private final CareerFileService files;
    private final ResumeMasterJpaRepository masters;
    private final AiResumeWorkbenchService workbench;
    private final AuditService audit;

    public ResumeImportService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock, TaskService tasks,
            CareerFileService files, ResumeMasterJpaRepository masters, AiResumeWorkbenchService workbench,
            AuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.tasks = tasks;
        this.files = files;
        this.masters = masters;
        this.workbench = workbench;
        this.audit = audit;
    }

    @Override
    public String moduleCode() { return "resume-import"; }

    @Override
    public String moduleKey() { return "resumeImports"; }

    @Transactional
    public ImportStartView create(CurrentAccount current, CreateCommand command) {
        assertSeeker(current);
        String careerFileId = clean(command == null ? null : command.careerFileId());
        String pastedText = clean(command == null ? null : command.pastedText());
        if ((careerFileId == null) == (pastedText == null)) {
            throw AppException.user("RESUME_IMPORT_SOURCE_INVALID", "careerFileId 与 pastedText 必须二选一");
        }
        if (pastedText != null && pastedText.length() > MAX_PASTED_CHARS) {
            throw AppException.user("RESUME_IMPORT_TEXT_TOO_LARGE", "粘贴文本不能超过 200000 字符");
        }
        String filename = null;
        String sourceType;
        if (careerFileId != null) {
            CareerFileService.FileView file = files.get(current, careerFileId);
            files.readReadyResume(current.accountId(), careerFileId);
            filename = file.originalFilename();
            sourceType = "CAREER_FILE";
        } else {
            sourceType = "PASTED_TEXT";
        }
        String id = Ids.newId();
        Instant now = clock.now();
        jdbc.update("INSERT INTO resume_import_sessions(id,account_id,source_type,career_file_id,source_text,source_filename,status,structured_draft_json,source_map_json,confidence_json,parse_task_id,error_code,result_master_id,result_branch_id,result_revision_id,version_no,created_at,updated_at,confirmed_at) VALUES(?,?,?,?,?,?,'PENDING',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,0,?,?,NULL)",
                id, current.accountId(), sourceType, careerFileId, pastedText, filename, now, now);
        TaskView task = tasks.create(current.accountId(), TaskTypes.RESUME_IMPORT_PARSE,
                "RESUME_IMPORT:" + id, json(Map.of("resumeImportId", id)));
        jdbc.update("UPDATE resume_import_sessions SET parse_task_id=?,status='PARSING',updated_at=? WHERE id=?",
                task.id(), now, id);
        audit.append(current.accountId(), "RESUME_IMPORT_STARTED", "RESUME_IMPORT", id,
                "source=" + sourceType + " task=" + task.id());
        return new ImportStartView(require(current.accountId(), id), task);
    }

    @Transactional
    public void process(String taskId, String accountId, String importId) {
        ImportRow row = row(accountId, importId, true);
        if (!"PARSING".equals(row.status()) && !"PENDING".equals(row.status())) return;
        try {
            String text;
            if ("CAREER_FILE".equals(row.sourceType())) {
                CareerFileService.Binary binary = files.readReadyResume(accountId, row.careerFileId());
                text = ResumeTextExtractor.extract(binary.contentType(), binary.body());
            } else {
                text = row.sourceText();
            }
            if (meaningfulLength(text) < 80) {
                fail(row.id(), "SCANNED_OR_EMPTY_RESUME");
                tasks.markFailed(taskId, "SCANNED_OR_EMPTY_RESUME");
                return;
            }
            ResumeContentParser.Parsed parsed = ResumeContentParser.parse(text);
            if (!parsed.confirmable()) {
                fail(row.id(), "RESUME_STRUCTURE_INSUFFICIENT");
                tasks.markFailed(taskId, "RESUME_STRUCTURE_INSUFFICIENT");
                return;
            }
            Instant now = clock.now();
            jdbc.update("UPDATE resume_import_sessions SET status='READY_FOR_CONFIRMATION',structured_draft_json=?,source_map_json=?,confidence_json=?,error_code=NULL,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                    json(parsed.content()), json(parsed.sourceMap()), json(parsed.confidence()), now, row.id(), accountId);
            tasks.markSucceeded(taskId, "resume-import-v1", json(Map.of("resumeImportId", row.id())));
        } catch (RuntimeException exception) {
            fail(row.id(), reason(exception));
            tasks.markFailed(taskId, reason(exception));
        }
    }

    @Transactional(readOnly = true)
    public ImportView get(CurrentAccount current, String id) {
        assertSeeker(current);
        return require(current.accountId(), id);
    }

    @Transactional
    public ImportView update(CurrentAccount current, String id, UpdateCommand command) {
        assertSeeker(current);
        ImportRow row = row(current.accountId(), id, true);
        if (!"READY_FOR_CONFIRMATION".equals(row.status())) {
            throw AppException.conflict("RESUME_IMPORT_NOT_EDITABLE", "简历解析完成后才能校正结构化字段");
        }
        Versions.assertExpected(command.expectedVersion(), row.version());
        Map<String, Object> content = normalize(command.structuredDraft());
        if (ResumeContentParser.substantiveCount(content) < 1) {
            throw AppException.user("RESUME_IMPORT_CONTENT_REQUIRED", "教育、经历、项目或技能至少保留一项");
        }
        jdbc.update("UPDATE resume_import_sessions SET structured_draft_json=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                json(content), clock.now(), id, current.accountId());
        return require(current.accountId(), id);
    }

    @Transactional
    public ImportView confirm(CurrentAccount current, String id, ConfirmCommand command) {
        assertSeeker(current);
        ImportRow row = row(current.accountId(), id, true);
        if ("CONFIRMED".equals(row.status())) return require(current.accountId(), id);
        if (!"READY_FOR_CONFIRMATION".equals(row.status())) {
            throw AppException.conflict("RESUME_IMPORT_NOT_CONFIRMABLE", "简历尚未完成解析与校正");
        }
        Versions.assertExpected(command == null ? null : command.expectedVersion(), row.version());
        Map<String, Object> content = normalize(readMap(row.structuredDraftJson()));
        if (ResumeContentParser.substantiveCount(content) < 1) {
            throw AppException.user("RESUME_IMPORT_CONTENT_REQUIRED", "教育、经历、项目或技能至少包含一项才能确认");
        }
        Instant now = clock.now();
        ResumeMasterEntity master = new ResumeMasterEntity();
        master.setId(Ids.newId());
        master.setAccountId(current.accountId());
        master.setTitle(title(command == null ? null : command.title(), row.sourceFilename()));
        master.setStatus("DRAFT");
        master.setSource("IMPORT");
        master.setTemplateCode(null);
        JsonNode node = mapper.valueToTree(content);
        master.setContentSchemaVersion("resume-content-v3");
        master.setContentJson(json(content));
        master.setEducationJson(blank(ResumeStructuredContent.text(node, "education")));
        master.setExperienceJson(blank(ResumeStructuredContent.text(node, "experience")));
        master.setProjectsJson(blank(ResumeStructuredContent.text(node, "projects")));
        master.setSkillsJson(blank(ResumeStructuredContent.text(node, "skills")));
        master.setCertificatesJson(blank(ResumeStructuredContent.text(node, "certificates")));
        master.setSelfIntro(blank(node.path("summary").asText("")));
        master.setKeyOutcomesJson("[]");
        master.setVersionNo(0);
        master.setCreatedAt(now);
        master.setUpdatedAt(now);
        masters.saveAndFlush(master);

        AiResumeWorkbenchService.ConversationView conversation = workbench.ensureForResume(current, master.getId());
        String branchId = conversation.activeBranchId();
        String revisionId = jdbc.queryForObject("SELECT current_revision_id FROM resume_branches WHERE id=?",
                String.class, branchId);
        jdbc.update("UPDATE resume_import_sessions SET status='CONFIRMED',source_text=NULL,result_master_id=?,result_branch_id=?,result_revision_id=?,version_no=version_no+1,confirmed_at=?,updated_at=? WHERE id=? AND account_id=?",
                master.getId(), branchId, revisionId, now, now, id, current.accountId());
        audit.append(current.accountId(), "RESUME_IMPORT_CONFIRMED", "RESUME_IMPORT", id,
                "master=" + master.getId() + " revision=" + revisionId + " original_immutable=true");
        return require(current.accountId(), id);
    }

    @Override
    @Transactional
    public Result onAccountDeletion(String accountId) {
        try {
            jdbc.update("DELETE FROM resume_import_sessions WHERE account_id=?", accountId);
            return Result.succeeded("简历导入会话及未确认原文已清理");
        } catch (RuntimeException exception) {
            return Result.failed("简历导入会话清理失败，删除申请保持可重试");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> contribute(String accountId) {
        return Map.of("sessions", jdbc.queryForList(
                "SELECT * FROM resume_import_sessions WHERE account_id=? ORDER BY created_at", accountId));
    }

    private ImportView require(String accountId, String id) {
        ImportRow row = row(accountId, id, false);
        TaskView task = row.parseTaskId() == null ? null : tasks.getOwned(accountId, row.parseTaskId());
        return new ImportView(row.id(), row.sourceType(), row.careerFileId(), row.sourceFilename(), row.status(),
                readNode(row.structuredDraftJson()), readNode(row.sourceMapJson()), readNode(row.confidenceJson()),
                task, row.errorCode(), row.resultMasterId(), row.resultBranchId(), row.resultRevisionId(),
                row.version(), row.createdAt(), row.updatedAt(), row.confirmedAt());
    }

    private ImportRow row(String accountId, String id, boolean lock) {
        String sql = "SELECT * FROM resume_import_sessions WHERE id=? AND account_id=?" + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, n) -> new ImportRow(rs.getString("id"), rs.getString("source_type"),
                rs.getString("career_file_id"), rs.getString("source_text"), rs.getString("source_filename"),
                rs.getString("status"), rs.getString("structured_draft_json"), rs.getString("source_map_json"),
                rs.getString("confidence_json"), rs.getString("parse_task_id"), rs.getString("error_code"),
                rs.getString("result_master_id"), rs.getString("result_branch_id"), rs.getString("result_revision_id"),
                rs.getInt("version_no"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toInstant()), id, accountId)
                .stream().findFirst().orElseThrow(() -> AppException.user("RESUME_IMPORT_NOT_FOUND", "简历导入会话不存在"));
    }

    private void fail(String id, String error) {
        jdbc.update("UPDATE resume_import_sessions SET status='FAILED',error_code=?,version_no=version_no+1,updated_at=? WHERE id=?",
                error, clock.now(), id);
    }

    private Map<String, Object> normalize(Map<String, Object> value) {
        Map<String, Object> result = new LinkedHashMap<>(value == null ? Map.of() : value);
        result.put("schemaVersion", "resume-content-v3");
        if (!(result.get("basics") instanceof Map<?, ?>)) result.put("basics", new LinkedHashMap<>());
        if (!(result.get("intentions") instanceof Map<?, ?>)) result.put("intentions", new LinkedHashMap<>());
        if (!(result.get("summary") instanceof String)) result.put("summary", "");
        for (String key : List.of("education", "experiences", "projects", "organizations", "skills",
                "certificates", "honors", "languages", "evidence")) {
            if (!(result.get(key) instanceof List<?>)) result.put(key, List.of());
        }
        if (!result.containsKey("photoFileId")) result.put("photoFileId", null);
        return result;
    }

    private Map<String, Object> readMap(String value) {
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw AppException.conflict("RESUME_IMPORT_DRAFT_INVALID", "结构化简历草稿无效"); }
    }
    private JsonNode readNode(String value) { try { return value == null ? null : mapper.readTree(value); } catch (Exception exception) { return null; } }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private static int meaningfulLength(String value) { return value == null ? 0 : value.replaceAll("\\s+", "").length(); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String title(String requested, String filename) { if (requested != null && !requested.isBlank()) return requested.trim(); if (filename == null || filename.isBlank()) return "导入简历"; return filename.replaceFirst("(?i)\\.(pdf|docx)$", "") + "（导入）"; }
    private static String reason(RuntimeException exception) { String message = exception.getMessage(); if (message != null && message.matches("[A-Z0-9_]{4,64}")) return message; return "RESUME_IMPORT_PARSE_FAILED"; }
    private static void assertSeeker(CurrentAccount current) { if (!"SEEKER".equals(current.role())) throw AppException.forbidden("SEEKER_REQUIRED", "只有求职者可以导入简历"); }

    public record CreateCommand(String careerFileId, String pastedText) {}
    public record UpdateCommand(Map<String, Object> structuredDraft, Integer expectedVersion) {}
    public record ConfirmCommand(String title, Integer expectedVersion) {}
    public record ImportStartView(ImportView importSession, TaskView task) {}
    public record ImportView(String id, String sourceType, String careerFileId, String sourceFilename, String status,
            JsonNode structuredDraft, JsonNode sourceMap, JsonNode confidence, TaskView task, String errorCode,
            String resultMasterId, String resultBranchId, String resultRevisionId, int version,
            Instant createdAt, Instant updatedAt, Instant confirmedAt) {}
    private record ImportRow(String id, String sourceType, String careerFileId, String sourceText,
            String sourceFilename, String status, String structuredDraftJson, String sourceMapJson,
            String confidenceJson, String parseTaskId, String errorCode, String resultMasterId,
            String resultBranchId, String resultRevisionId, int version, Instant createdAt,
            Instant updatedAt, Instant confirmedAt) {}
}
