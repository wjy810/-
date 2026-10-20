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
        ExportView existing = jdbc.query("SELECT * FROM job_match_exports WHERE account_id=? AND match_id=? AND request_id=?",
                this::view, current.accountId(), matchId, requestId).stream().findFirst().orElse(null);
        if (existing != null) {
            boolean usable = "RUNNING".equals(existing.status()) || ("COMPLETED".equals(existing.status())
                    && existing.expiresAt() != null && existing.expiresAt().isAfter(clock.now()));
            if (usable) return existing;
            // A replayed request whose file has expired: build a fresh one rather than hand back a dead link.
            requestId = requestId.substring(0, Math.min(80, requestId.length())) + ":" + Ids.newId();
        }
        TaskView task = tasks.create(current.accountId(), TaskTypes.JOB_MATCH_EXPORT,
                "JOB_MATCH_EXPORT:" + requestId, json(Map.of("matchId", matchId, "format", format)));

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
        String title = match.title() + " 岗位匹配报告";
        List<Section> sections = sections(match, report);
        if ("PDF".equals(format)) {
            return ResumePdfRenderer.renderSections(title, "JobProof 岗位匹配报告 · 已隐藏联系方式", "Job match report",
                    sections.stream().map(section -> new ResumePdfRenderer.PlainSection(section.heading(),
                            String.join("\n", section.lines()))).toList());
        }
        return docx(title, sections);
    }

    /** One heading and its lines; a line starting with "N. " opens an item, "   " continues it. */
    record Section(String heading, List<String> lines) {}

    /**
     * Everything the v3 report page shows, in reading order: the recommendation and its reason, the five
     * dimensions, each hard requirement, the verdict on every requirement and what backs it, strengths,
     * gaps, resume suggestions, interview topics and the learning plan with its progress.
     */
    static List<Section> sections(JobMatchService.MatchView match, JobMatchService.ReportView report) {
        Map<String, Object> document = report.report();
        Map<String, Object> ai = map(document.get("ai"));
        Map<String, Object> scoring = map(document.get("scoring"));
        List<Map<String, Object>> requirements = list(document.get("requirements"));
        Map<String, String> requirementTitles = requirementTitles(requirements);
        boolean preview = Boolean.TRUE.equals(document.get("preview"));
        List<Section> sections = new ArrayList<>();

        List<String> overview = new ArrayList<>();
        overview.add("目标岗位：" + match.title() + (match.company() == null ? "" : " · " + match.company()));
        overview.add("投递简历：" + (match.resume() == null ? "未命名简历" : match.resume().title()));
        if (document.get("generatedAt") instanceof String generated && generated.length() >= 16) {
            overview.add("生成时间：" + generated.substring(0, 10) + " " + generated.substring(11, 16) + "（UTC）");
        }
        overview.add("综合匹配度：" + number(document.get("score")) + " 分（满分 100）");
        String cited = scoring.containsKey("citedCount") && scoring.containsKey("requirementCount")
                ? "，" + number(scoring.get("requirementCount")) + " 条要求中 " + number(scoring.get("citedCount")) + " 条有依据" : "";
        overview.add("可信度：" + confidenceText(document.get("confidence")) + cited);
        overview.add("评分方式：" + (!scoring.containsKey("method") ? "旧版评分"
                : "RULE_PREVIEW".equals(scoring.get("method")) ? "规则预估（按关键词估算，AI 未参与）"
                : "逐条判断岗位要求，按 必须 3 / 重要 2 / 加分 1 加权"));
        if (preview) overview.add("这是规则预估，结论仅供参考；AI 分析完成后会得到逐条判断的报告。");
        overview.add("分析摘要：" + summaryText(ai.get("summary")));
        sections.add(new Section("概览", overview));

        Map<String, Object> recommendation = map(document.get("recommendation"));
        if (!recommendation.isEmpty()) {
            List<String> lines = new ArrayList<>();
            lines.add("建议：" + RECOMMENDATIONS.getOrDefault(String.valueOf(recommendation.get("code")), "待评估"));
            if (recommendation.get("rationale") instanceof String rationale && !rationale.isBlank()) lines.add("理由：" + displayText(rationale));
            if (Boolean.TRUE.equals(recommendation.get("capped"))) lines.add("AI 给出的建议比分数所允许的更乐观，已按分数调整。");
            sections.add(new Section("投递建议", lines));
        }

        List<Map<String, Object>> gates = list(document.get("hardGates"));
        Map<String, Object> dimensions = map(document.get("dimensions"));
        if (!dimensions.isEmpty() || !gates.isEmpty()) {
            List<String> lines = new ArrayList<>();
            long passed = gates.stream().filter(gate -> "PASSED".equals(gate.get("status"))).count();
            lines.add("硬性要求：" + (gates.isEmpty() ? "无硬性要求" : passed + " / " + gates.size() + " 条已满足"));
            for (Map.Entry<String, String> dimension : DIMENSIONS.entrySet()) {
                Object value = dimensions.get(dimension.getKey());
                lines.add(dimension.getValue() + "：" + (value instanceof Number score ? score.intValue() + " 分" : "无相关要求"));
            }
            sections.add(new Section("维度得分", lines));
        }

        if (!gates.isEmpty()) {
            List<String> lines = new ArrayList<>();
            int index = 0;
            for (Map<String, Object> gate : gates) {
                lines.add(++index + ". " + displayText(string(gate.get("text"))) + " — "
                        + GATE_STATUSES.getOrDefault(String.valueOf(gate.get("status")), "待核实"));
                if (gate.get("explanation") instanceof String explanation && !explanation.isBlank()) {
                    lines.add("   " + displayText(explanation));
                }
            }
            sections.add(new Section("硬性要求", lines));
        }

        if (!requirements.isEmpty() && requirements.get(0).containsKey("verdict")) {
            List<String> lines = new ArrayList<>();
            int index = 0;
            for (Map<String, Object> requirement : requirements) {
                String priority = PRIORITIES.getOrDefault(String.valueOf(requirement.get("priority")), "重要");
                lines.add(++index + ". [" + priority + (Boolean.TRUE.equals(requirement.get("hardGate")) ? " · 硬性" : "") + "] "
                        + displayText(string(requirement.get("text"))));
                lines.add("   判定：" + VERDICTS.getOrDefault(String.valueOf(requirement.get("verdict")), "无法判断")
                        + "（" + number(requirement.get("value")) + " 分）" + basis(requirement));
                if (requirement.get("explanation") instanceof String explanation && !explanation.isBlank()) {
                    lines.add("   说明：" + displayText(explanation));
                }
            }
            sections.add(new Section("逐条判定", lines));
        }

        sections.add(new Section("匹配优势", items(list(document.containsKey("strengths") ? document.get("strengths") : ai.get("strengths")),
                "title", "explanation", requirementTitles)));
        sections.add(new Section("能力与材料缺口", items(list(document.containsKey("gaps") ? document.get("gaps") : ai.get("gaps")),
                "title", "impact", requirementTitles)));

        List<Map<String, Object>> suggestions = list(ai.get("resumeSuggestions"));
        if (!suggestions.isEmpty()) {
            List<String> lines = new ArrayList<>();
            int index = 0;
            for (Map<String, Object> suggestion : suggestions) {
                lines.add(++index + ". " + resumeSection(String.valueOf(suggestion.get("targetPath"))));
                lines.add("   建议写成：" + displayText(string(suggestion.get("proposedValue"))));
                if (suggestion.get("reason") instanceof String reason && !reason.isBlank()) lines.add("   理由：" + displayText(reason));
            }
            sections.add(new Section("简历修改建议", lines));
        }

        List<String> topics = new ArrayList<>();
        for (Object topic : ai.get("interviewTopics") instanceof List<?> values ? values : List.of()) {
            String text = topic instanceof Map<?, ?> value ? string(value.get("topic") != null ? value.get("topic") : value.get("title"))
                    : String.valueOf(topic);
            if (text != null && !text.isBlank() && !"暂未生成".equals(text)) topics.add(topics.size() + 1 + ". " + displayText(text));
        }
        if (!topics.isEmpty()) sections.add(new Section("面试准备主题", topics));

        if (!report.learningPlan().isEmpty()) {
            List<String> lines = new ArrayList<>();
            int index = 0;
            long done = report.learningPlan().stream().filter(task -> "DONE".equals(task.status())).count();
            lines.add("已完成 " + done + " / " + report.learningPlan().size() + " 项");
            for (JobMatchService.ImprovementView task : report.learningPlan()) {
                lines.add(++index + ". " + task.title() + "（" + TASK_STATUSES.getOrDefault(task.status(), "待完成") + " · "
                        + task.estimatedHours() + " 小时）");
                lines.add("   任务：" + task.task());
                lines.add("   产物：" + task.expectedOutput());
                lines.add("   验收：" + task.acceptanceCriteria());
            }
            sections.add(new Section("提升计划", lines));
        }

        sections.add(new Section("说明", List.of("匹配结果用于求职准备，不代表录用概率。报告默认隐藏联系方式和个人敏感资料。")));
        return sections;
    }

    private static final Map<String, String> RECOMMENDATIONS = Map.of("RECOMMENDED", "推荐投递", "CONDITIONAL", "准备后投递",
            "PREPARE_FIRST", "先补强再投递", "CAUTION", "谨慎评估", "NOT_RECOMMENDED", "暂不建议投递");
    private static final Map<String, String> DIMENSIONS = orderedMap("coreSkills", "核心技能", "experience", "经验",
            "qualification", "学历与限制", "evidence", "证据强度");
    private static final Map<String, String> GATE_STATUSES = Map.of("PASSED", "已满足", "PARTIAL", "部分满足",
            "FAILED", "暂未找到证据", "UNKNOWN", "无法判断");
    private static final Map<String, String> VERDICTS = Map.of("MET", "符合", "PARTIAL", "部分符合",
            "UNCLEAR", "无法判断", "NOT_FOUND", "暂未发现证据");
    private static final Map<String, String> PRIORITIES = Map.of("MUST", "必须", "IMPORTANT", "重要", "BONUS", "加分",
            "RESPONSIBILITY", "职责");
    private static final Map<String, String> TASK_STATUSES = Map.of("DONE", "已完成", "IN_PROGRESS", "进行中",
            "SKIPPED", "已跳过", "TODO", "待完成", "PENDING", "待完成");

    private static Map<String, String> orderedMap(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    /** What backs a verdict, in the user's words. */
    private static String basis(Map<String, Object> requirement) {
        List<String> sources = new ArrayList<>();
        if (requirement.get("resumeRefs") instanceof List<?> refs && !refs.isEmpty()) sources.add("简历");
        if (requirement.get("evidenceIds") instanceof List<?> ids && !ids.isEmpty()) sources.add("授权资料");
        if (Boolean.TRUE.equals(requirement.get("userConfirmed"))) sources.add("你的确认");
        return sources.isEmpty() ? "" : " · 依据：" + String.join("、", sources);
    }

    static String resumeSection(String path) {
        if (path.startsWith("/summary")) return "个人简介";
        if (path.startsWith("/experiences")) return "工作或实习经历";
        if (path.startsWith("/projects")) return "项目经历";
        if (path.startsWith("/education")) return "教育经历";
        if (path.startsWith("/skills")) return "专业技能";
        if (path.startsWith("/organizations")) return "社团与组织经历";
        if (path.startsWith("/certificates")) return "证书";
        if (path.startsWith("/honors")) return "荣誉";
        return "简历内容";
    }

    private static List<String> items(List<Map<String, Object>> items, String titleKey, String detailKey,
            Map<String, String> requirementTitles) {
        if (items.isEmpty()) return List.of("暂无通过事实校验的内容。");
        List<String> lines = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> item : items) {
            lines.add(++index + ". " + sectionTitle(item, titleKey, requirementTitles));
            lines.add("   " + string(item.get(detailKey)));
            if (item.get("actions") instanceof List<?> actions) {
                for (Object action : actions) {
                    String text = action instanceof Map<?, ?> value ? String.valueOf(value.get("title") != null ? value.get("title") : value.get("action"))
                            : String.valueOf(action);
                    if (!text.isBlank() && !"null".equals(text)) lines.add("   · " + displayText(text));
                }
            }
        }
        return lines;
    }

    /** v3 reports store HIGH / MEDIUM / LOW; older ones a number. */
    static String confidenceText(Object value) {
        String level = JobMatchService.confidenceLevel(value);
        return switch (level) { case "HIGH" -> "高"; case "MEDIUM" -> "中"; default -> "低"; };
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

    private byte[] docx(String title, List<Section> sections) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFRun titleRun = document.createParagraph().createRun();
            titleRun.setText(title); titleRun.setBold(true); titleRun.setFontSize(20);
            titleRun.setFontFamily("Microsoft YaHei");
            for (Section section : sections) {
                XWPFParagraph heading = document.createParagraph();
                heading.setSpacingBefore(240);
                XWPFRun headingRun = heading.createRun();
                headingRun.setText(section.heading()); headingRun.setBold(true); headingRun.setFontSize(13);
                headingRun.setFontFamily("Microsoft YaHei"); headingRun.setColor("1D4ED8");
                for (String line : section.lines()) {
                    XWPFParagraph paragraph = document.createParagraph();
                    boolean continuation = line.startsWith("   ");
                    if (continuation) paragraph.setIndentationLeft(360);
                    XWPFRun run = paragraph.createRun();
                    run.setText(continuation ? line.strip() : line); run.setFontSize(10);
                    run.setFontFamily("Microsoft YaHei");
                    if (!continuation && line.matches("^\\d+\\.\\s.+")) run.setBold(true);
                }
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
