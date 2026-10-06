package com.jobproof.modules.jobmatch.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.resume.domain.ResumeDocxRenderer;
import com.jobproof.modules.resume.domain.ResumePdfRenderer;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileEntity;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobMatchExportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final JobMatchService matches;
    private final TaskService tasks;
    private final ObjectStoragePort storage;
    private final PrivateFileJpaRepository files;
    private final AuditService audit;

    public JobMatchExportService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock,
            JobMatchService matches, TaskService tasks, ObjectStoragePort storage,
            PrivateFileJpaRepository files, AuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.matches = matches;
        this.tasks = tasks;
        this.storage = storage;
        this.files = files;
        this.audit = audit;
    }

    @Transactional
    public ExportView create(CurrentAccount current, String matchId, ExportCommand command) {
        JobMatchService.MatchView match = matches.get(current, matchId);
        if (command != null && command.expectedVersion() != null) Versions.assertExpected(command.expectedVersion(), match.version());
        JobMatchService.ReportView report = matches.report(current, matchId);
        String format = enumFormat(command == null ? null : command.format());
        String requestId = required(command == null ? null : command.requestId());
        TaskView task = tasks.create(current.accountId(), TaskTypes.JOB_MATCH_EXPORT,
                "JOB_MATCH_EXPORT:" + requestId, json(Map.of("matchId", matchId, "format", format)));
        ExportView existing = jdbc.query("SELECT * FROM job_match_exports WHERE account_id=? AND match_id=? AND request_id=?",
                this::view, current.accountId(), matchId, requestId).stream().findFirst().orElse(null);
        if (existing != null) return existing;

        Instant now = clock.now();
        String id = Ids.newId();
        String filename = safeFilename(match.title()) + "-岗位匹配报告." + format.toLowerCase(Locale.ROOT);
        jdbc.update("INSERT INTO job_match_exports(id,match_id,report_id,account_id,task_id,format_code,sections_json,redacted,language_code,filename,private_file_id,status,error_code,expires_at,created_at,completed_at,request_id,request_version) VALUES(?,?,?,?,?,?,?,?,?,?,NULL,'RUNNING',NULL,NULL,?,NULL,?,?)",
                id, matchId, report.id(), current.accountId(), task.id(), format,
                json(command == null || command.sections() == null ? List.of("ALL") : command.sections()),
                command == null || command.redacted(), command == null ? "zh-CN" : blankTo(command.language(), "zh-CN"),
                filename, now, requestId, match.version());
        try {
            byte[] content = render(format, match, report);
            String contentType = contentType(format);
            String fileId = store(current.accountId(), id, format, contentType, content, now);
            Instant expires = now.plus(15, ChronoUnit.MINUTES);
            jdbc.update("UPDATE job_match_exports SET private_file_id=?,status='COMPLETED',expires_at=?,completed_at=? WHERE id=?",
                    fileId, expires, now, id);
            tasks.markSucceeded(task.id(), "job-match-export-v1", json(Map.of("exportId", id, "fileId", fileId)));
            audit.append(current.accountId(), "JOB_MATCH_REPORT_EXPORTED", "JOB_MATCH", matchId,
                    "format=" + format + " redacted=true");
            return require(current.accountId(), id);
        } catch (RuntimeException exception) {
            jdbc.update("UPDATE job_match_exports SET status='FAILED',error_code=?,completed_at=? WHERE id=?",
                    "JOB_MATCH_EXPORT_FAILED", clock.now(), id);
            tasks.markFailed(task.id(), "JOB_MATCH_EXPORT_FAILED");
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public ExportView get(CurrentAccount current, String exportId) {
        return require(current.accountId(), exportId);
    }

    @Transactional(readOnly = true)
    public Download download(CurrentAccount current, String exportId) {
        ExportView export = require(current.accountId(), exportId);
        if (!"COMPLETED".equals(export.status()) || export.privateFileId() == null) {
            throw AppException.conflict("JOB_MATCH_EXPORT_NOT_READY", "报告导出尚未完成");
        }
        if (export.expiresAt() == null || !export.expiresAt().isAfter(clock.now())) {
            throw AppException.user("JOB_MATCH_EXPORT_EXPIRED", "报告下载地址已过期，请重新生成");
        }
        PrivateFileEntity file = files.findById(export.privateFileId())
                .orElseThrow(() -> AppException.user("FILE_NOT_FOUND", "导出文件不存在"));
        if (!current.accountId().equals(file.getOwnerId())) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能下载他人的岗位匹配报告");
        }
        return new Download(export.filename(), file.getContentType(), storage.get(file.getObjectKey()));
    }

    private byte[] render(String format, JobMatchService.MatchView match, JobMatchService.ReportView report) {
        if ("JSON".equals(format)) {
            try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        String body = text(match, report);
        if ("PDF".equals(format)) return ResumePdfRenderer.render(match.title() + " 岗位匹配报告", body);
        return docx(match.title() + " 岗位匹配报告", body);
    }

    private String text(JobMatchService.MatchView match, JobMatchService.ReportView report) {
        Map<String, Object> document = report.report();
        Map<String, Object> ai = map(document.get("ai"));
        StringBuilder out = new StringBuilder();
        line(out, "目标岗位", match.title() + (match.company() == null ? "" : " · " + match.company()));
        line(out, "投递简历", match.resume() == null ? "未命名简历" : match.resume().title());
        line(out, "综合匹配度", number(document.get("score")) + "%");
        line(out, "可信度", number(document.get("confidence")) + "%");
        line(out, "硬性资格", Boolean.TRUE.equals(document.get("hardGatePassed")) ? "通过" : "存在待核实或未满足项");
        Map<String, String> requirementTitles = requirementTitles(list(document.get("requirements")));
        out.append("\n分析摘要\n").append(summaryText(ai.get("summary"))).append("\n");
        section(out, "匹配优势", list(ai.get("strengths")), "title", "explanation", requirementTitles);
        section(out, "能力与材料缺口", list(ai.get("gaps")), "title", "impact", requirementTitles);
        if (!report.learningPlan().isEmpty()) {
            out.append("\n提升计划\n");
            int index = 0;
            for (JobMatchService.ImprovementView task : report.learningPlan()) {
                out.append(++index).append(". ").append(task.title()).append("（").append(task.estimatedHours()).append(" 小时）\n")
                        .append("   任务：").append(task.task()).append("\n")
                        .append("   产物：").append(task.expectedOutput()).append("\n")
                        .append("   验收：").append(task.acceptanceCriteria()).append("\n");
            }
        }
        out.append("\n说明：匹配结果用于求职准备，不代表录用概率。报告默认隐藏联系方式和个人敏感资料。\n");
        return out.toString();
    }

    private static void section(StringBuilder out, String heading, List<Map<String, Object>> items,
            String titleKey, String detailKey, Map<String, String> requirementTitles) {
        out.append("\n").append(heading).append("\n");
        if (items.isEmpty()) { out.append("暂无通过事实校验的内容。\n"); return; }
        int index = 0;
        for (Map<String, Object> item : items) {
            out.append(++index).append(". ").append(sectionTitle(item, titleKey, requirementTitles)).append("\n")
                    .append("   ").append(string(item.get(detailKey))).append("\n");
        }
    }

    static String summaryText(Object value) {
        if (value instanceof String text) return text.isBlank() ? "暂未生成" : displayText(text);
        if (value instanceof Map<?, ?> summary) {
            for (String key : List.of("assessment", "headline", "overview", "rationale")) {
                Object candidate = summary.get(key);
                if (candidate instanceof String text && !text.isBlank()) return displayText(text);
            }
        }
        return "暂未生成";
    }

    static String sectionTitle(Map<String, Object> item, String titleKey,
            Map<String, String> requirementTitles) {
        Object direct = item.get(titleKey);
        if (direct instanceof String text && !text.isBlank()) return displayText(text);
        Object requirementId = item.get("requirementId");
        if (requirementId != null) {
            String requirement = requirementTitles.get(String.valueOf(requirementId));
            if (requirement != null && !requirement.isBlank()) return displayText(requirement);
        }
        return switch (String.valueOf(item.getOrDefault("type", ""))) {
            case "CAPABILITY_GAP" -> "能力缺口";
            case "VERIFICATION_GAP", "EVIDENCE_GAP" -> "证据缺口";
            case "EXPRESSION_GAP" -> "表达缺口";
            case "OBJECTIVE_CONSTRAINT" -> "客观限制";
            case "BONUS_ITEM" -> "加分项";
            default -> "待补充说明";
        };
    }

    private static Map<String, String> requirementTitles(List<Map<String, Object>> requirements) {
        Map<String, String> titles = new LinkedHashMap<>();
        for (Map<String, Object> requirement : requirements) {
            Object id = requirement.get("requirementId");
            Object text = requirement.get("text");
            if (id != null && text instanceof String value && !value.isBlank()) {
                titles.put(String.valueOf(id), value.trim());
            }
        }
        return titles;
    }

    private byte[] docx(String title, String body) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            XWPFRun titleRun = heading.createRun();
            titleRun.setText(title); titleRun.setBold(true); titleRun.setFontSize(20);
            titleRun.setFontFamily("Microsoft YaHei");
            for (String line : body.split("\n", -1)) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(line); run.setFontSize(10); run.setFontFamily("Microsoft YaHei");
            }
            document.write(output);
            return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private String store(String accountId, String exportId, String format, String contentType, byte[] content, Instant now) {
        String fileId = Ids.newId();
        String key = "private/" + accountId + "/job-match/" + exportId + "." + format.toLowerCase(Locale.ROOT);
        storage.put(key, content);
        PrivateFileEntity file = new PrivateFileEntity();
        file.setId(fileId); file.setOwnerId(accountId); file.setObjectKey(key); file.setContentType(contentType);
        file.setSizeBytes(content.length); file.setCreatedAt(now); files.save(file);
        return fileId;
    }

    private ExportView require(String accountId, String exportId) {
        return jdbc.query("SELECT * FROM job_match_exports WHERE id=? AND account_id=?", this::view, exportId, accountId)
                .stream().findFirst().orElseThrow(() -> AppException.forbidden("OBJECT_FORBIDDEN", "导出记录不存在或无权访问"));
    }
    private ExportView view(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ExportView(rs.getString("id"), rs.getString("match_id"), rs.getString("report_id"),
                rs.getString("task_id"), rs.getString("format_code"), rs.getString("filename"),
                rs.getString("private_file_id"), rs.getString("status"), rs.getString("error_code"),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant());
    }
    private static String contentType(String format) { return switch (format) { case "PDF" -> "application/pdf"; case "DOCX" -> ResumeDocxRenderer.CONTENT_TYPE; default -> "application/json"; }; }
    private static String enumFormat(String value) { String format = blankTo(value, "PDF").toUpperCase(Locale.ROOT); if (!List.of("PDF", "DOCX", "JSON").contains(format)) throw AppException.user("JOB_MATCH_EXPORT_FORMAT_INVALID", "只支持 PDF、DOCX 或 JSON"); return format; }
    private static String required(String value) { if (value == null || value.isBlank()) throw AppException.user("JOB_MATCH_REQUEST_ID_REQUIRED", "缺少导出请求编号"); return value.trim(); }
    private static String blankTo(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String safeFilename(String value) { String safe = blankTo(value, "岗位匹配").replaceAll("[\\\\/:*?\"<>|]", "-"); return safe.substring(0, Math.min(80, safe.length())); }
    private static void line(StringBuilder out, String label, String value) { out.append(label).append("：").append(value).append("\n"); }
    private static int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private static String string(Object value) { if (value == null) return "暂未生成"; if (value instanceof String text) return text.isBlank() ? "暂未生成" : displayText(text); return String.valueOf(value); }
    private static String displayText(String value) {
        return value.trim()
                .replace("ruleResult 判定", "规则引擎判定")
                .replace("ruleResult 仍为", "规则结果仍为")
                .replace("ruleResult=", "规则结果为")
                .replace("ruleResult", "规则结果")
                .replace("NOT_FOUND", "暂未发现证据")
                .replace("evidence", "证据")
                .replaceAll("已授权\\s+证据", "已授权证据")
                .replaceAll("授权\\s+证据", "授权证据")
                .replaceAll("规则结果仍为\\s+暂未发现证据", "规则结果仍为暂未发现证据");
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> list(Object value) { return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of(); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException(exception); } }

    public record ExportCommand(String format, List<String> sections, boolean redacted, String language, String requestId, Integer expectedVersion) {}
    public record ExportView(String id, String matchId, String reportId, String taskId, String format, String filename,
            String privateFileId, String status, String errorCode, Instant expiresAt, Instant createdAt, Instant completedAt) {}
    public record Download(String filename, String contentType, byte[] body) {}
}
