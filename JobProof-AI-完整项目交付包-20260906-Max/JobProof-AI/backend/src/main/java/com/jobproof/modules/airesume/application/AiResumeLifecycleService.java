package com.jobproof.modules.airesume.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.airesume.application.AiGenerationAttemptService.Handle;
import com.jobproof.modules.airesume.application.AiGenerationAttemptService.TaskClass;
import com.jobproof.modules.aigateway.application.AiGatewayException;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Message;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Request;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Response;
import com.jobproof.modules.resume.application.ResumeAiCandidateService;
import com.jobproof.modules.resume.infra.ResumeMasterEntity;
import com.jobproof.modules.resume.infra.ResumeMasterJpaRepository;
import com.jobproof.modules.resume.domain.ResumeStructuredContent;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiResumeLifecycleService {

    private static final String SCHEMA = "resume-content-v3";
    private static final Set<String> BRANCH_LANGUAGES = Set.of("zh-CN", "en-US");
    private static final List<String> TRANSLATABLE_FIELDS = List.of("intentions", "summary", "education",
            "experiences", "projects", "organizations", "skills", "certificates", "honors", "languages");
    private static final Pattern NUMBER_OR_DATE = Pattern.compile("(?iu)(?:19|20)\\d{2}(?:[-/.年]\\d{1,2}(?:[-/.月]\\d{1,2}日?)?)?|(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?%?");
    private static final String TRANSLATION_PROMPT = """
            你是 JobProof 简历翻译器。只翻译输入 JSON 中的 confirmedFacts，不得删减或增加事实、数字、公司、学校、岗位、技能和日期。
            目标语言由 targetLanguage 指定。公司、学校、项目等专有名称没有输入提供的官方译名时保留原文，并放入 unconfirmedProperNames。
            只输出 JSON：{"translated":{与 confirmedFacts 完全相同的字段},"unconfirmedProperNames":["原文名称"]}。不要 Markdown。
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final ResumeMasterJpaRepository masters;
    private final AiGatewayService gateway;
    private final ResumeAiCandidateService aiCandidates;
    private final AiQuotaService quota;
    private final AiGenerationAttemptService attempts;
    private final String defaultModel;

    public AiResumeLifecycleService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock,
            ResumeMasterJpaRepository masters, AiGatewayService gateway,
            ResumeAiCandidateService aiCandidates, AiQuotaService quota, AiGenerationAttemptService attempts,
            @Value("${jobproof.ai.resume-model:qwen-plus}") String defaultModel) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.masters = masters;
        this.gateway = gateway;
        this.aiCandidates = aiCandidates;
        this.quota = quota;
        this.attempts = attempts;
        this.defaultModel = defaultModel;
    }

    @Transactional(readOnly = true)
    public List<RevisionView> revisions(CurrentAccount current, String conversationId) {
        ConversationOwner owner = owner(current, conversationId);
        return jdbc.query("SELECT id,branch_id,revision_no,source,source_object_id,content_json,content_hash,created_at FROM resume_revisions WHERE account_id=? AND master_id=? ORDER BY created_at DESC",
                (rs, n) -> new RevisionView(rs.getString("id"), rs.getString("branch_id"),
                        rs.getInt("revision_no"), rs.getString("source"), rs.getString("source_object_id"),
                        readNode(rs.getString("content_json")), rs.getString("content_hash"),
                        rs.getTimestamp("created_at").toInstant()),
                owner.accountId(), owner.masterId());
    }

    @Transactional(readOnly = true)
    public List<BranchView> branches(CurrentAccount current, String conversationId) {
        ConversationOwner owner = owner(current, conversationId);
        return jdbc.query("SELECT b.*,p.current_revision_id AS parent_revision_id FROM resume_branches b LEFT JOIN resume_branches p ON p.id=b.parent_branch_id WHERE b.account_id=? AND b.master_id=? AND b.status<>'ARCHIVED' ORDER BY b.created_at",
                (rs, n) -> branchView(rs, owner.branchId()), owner.accountId(), owner.masterId());
    }

    @Transactional
    public BranchView createLanguageBranch(CurrentAccount current, String conversationId, String title,
            String languageCode) {
        ConversationOwner owner = owner(current, conversationId);
        String language = languageCode == null ? "" : languageCode.trim();
        if (!BRANCH_LANGUAGES.contains(language)) {
            throw AppException.user("AI_BRANCH_LANGUAGE_INVALID", "语言分支首版仅支持 zh-CN 或 en-US");
        }
        BranchRow parent = requireBranch(owner, owner.branchId());
        if (language.equals(parent.languageCode())) {
            throw AppException.conflict("AI_BRANCH_LANGUAGE_CONFLICT", "目标语言与当前分支相同");
        }
        String fallback = "en-US".equals(language) ? parent.title() + " · English" : parent.title() + " · 中文";
        BranchView created = createBranch(owner, parent, "LANGUAGE", cleanTitle(title, fallback), language,
                parent.jobVersionId(), true, content -> {});
        jdbc.update("UPDATE resume_branches SET review_metadata_json=? WHERE id=?",
                json(Map.of("translationStatus", "NOT_STARTED", "targetLanguage", language)), created.id());
        return branch(created.id(), owner, owner.branchId());
    }

    @Transactional(noRollbackFor = AppException.class)
    public BranchView translateLanguageBranch(CurrentAccount current, String conversationId, String branchId,
            String requestId) {
        ConversationOwner owner = owner(current, conversationId);
        BranchRow branch = requireBranchForUpdate(owner, branchId);
        if (!"LANGUAGE".equals(branch.branchType())) {
            throw AppException.user("AI_TRANSLATION_BRANCH_REQUIRED", "只能翻译语言分支");
        }
        if (!"REVIEWING".equals(branch.status())) {
            throw AppException.conflict("AI_TRANSLATION_STATE_CONFLICT", "该语言分支当前不需要翻译");
        }
        String cleanRequestId = required(requestId, "AI_REQUEST_ID_INVALID", "翻译请求标识必填");
        if (cleanRequestId.length() > 128) {
            throw AppException.user("AI_REQUEST_ID_INVALID", "翻译请求标识最长 128 个字符");
        }
        Integer consent = jdbc.queryForObject("SELECT COUNT(*) FROM ai_user_consents WHERE account_id=? AND consent_type='AI_RESUME_WORKBENCH' AND status='GRANTED'",
                Integer.class, owner.accountId());
        if (consent == null || consent == 0) {
            throw AppException.conflict("AI_CONSENT_REQUIRED", "请先确认 AI 简历授权");
        }
        ResumeAiCandidateService.Availability availability = aiCandidates.availability(current, defaultModel);
        if (!availability.available()) {
            throw AppException.conflict(availability.reason(), "没有可用 AI 通道，语言分支仍保持待翻译状态");
        }
        Handle attempt = attempts.start(owner.accountId(), conversationId, cleanRequestId,
                "RESUME_TRANSLATION", TaskClass.BACKGROUND);
        AiQuotaService.Reservation reservation = null;
        try {
            reservation = quota.reserve(owner.accountId(),
                    "translation:" + branch.id() + ":" + cleanRequestId, "RESUME_TRANSLATION", 1);
            RevisionContent source = requireRevision(owner, branch.currentRevisionId());
            Map<String, Object> sourceContent = readMap(source.contentJson());
            Map<String, Object> facts = new LinkedHashMap<>();
            for (String field : TRANSLATABLE_FIELDS) {
                Object value = sourceContent.get(field);
                if (hasContent(value)) facts.put(field, value);
            }
            if (facts.isEmpty()) {
                throw AppException.conflict("AI_TRANSLATION_FACTS_REQUIRED", "当前分支没有可翻译的确认事实");
            }
            Map<String, JsonNode> options = Map.of(
                    "temperature", mapper.getNodeFactory().numberNode(0.1),
                    "max_tokens", mapper.getNodeFactory().numberNode(4000));
            String input = json(Map.of("targetLanguage", branch.languageCode(), "confirmedFacts", facts));
            Response response = gateway.execute(owner.accountId(), new Request(defaultModel,
                    List.of(new Message("system", TRANSLATION_PROMPT), new Message("user", input)), false, options));
            TranslationResult translated = parseTranslation(response, facts);
            long inputTokens = response.usage() == null ? 0 : response.usage().inputTokens();
            long outputTokens = response.usage() == null ? 0 : response.usage().outputTokens();
            if (attempt.cancellationRequested()) {
                quota.release(owner.accountId(), reservation.id());
                attempts.finish(attempt, "CANCELLED", "AI_TASK_CANCELLED",
                        model(response), inputTokens, outputTokens);
                return branch(branch.id(), owner, owner.branchId());
            }
            Map<String, Object> content = new LinkedHashMap<>(sourceContent);
            content.putAll(translated.fields());
            Instant now = clock.now();
            String revisionId = createRevision(new ConversationOwner(owner.accountId(), owner.masterId(), branch.id()),
                    json(content), "LANGUAGE_TRANSLATED", branch.currentRevisionId(), now);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("translationStatus", "AWAITING_CONFIRMATION");
            metadata.put("targetLanguage", branch.languageCode());
            metadata.put("unconfirmedProperNames", translated.unconfirmedProperNames());
            metadata.put("model", model(response));
            metadata.put("inputTokens", inputTokens);
            metadata.put("outputTokens", outputTokens);
            metadata.put("responseHash", sha256(response.text() == null ? "" : response.text()));
            metadata.put("translatedRevisionId", revisionId);
            jdbc.update("UPDATE resume_branches SET status='REVIEWING',review_metadata_json=?,updated_at=? WHERE id=?",
                    json(metadata), now, branch.id());
            quota.settle(owner.accountId(), reservation.id(), 1);
            attempts.finish(attempt, "COMPLETED", null, model(response), inputTokens, outputTokens);
            return branch(branch.id(), owner, owner.branchId());
        } catch (AiGatewayException exception) {
            if (reservation != null) quota.release(owner.accountId(), reservation.id());
            attempts.finish(attempt, "FAILED", "AI_MODEL_FAILED", defaultModel, 0, 0);
            throw AppException.dependency("AI_MODEL_FAILED", "翻译模型调用失败，语言分支没有变化，额度已返还");
        } catch (RuntimeException exception) {
            if (reservation != null) quota.release(owner.accountId(), reservation.id());
            String code = exception instanceof AppException app ? app.reason() : "AI_TRANSLATION_RESPONSE_INVALID";
            attempts.finish(attempt, "FAILED", code, defaultModel, 0, 0);
            if (exception instanceof AppException app) throw app;
            throw AppException.dependency("AI_TRANSLATION_RESPONSE_INVALID", "翻译结果格式无效，语言分支没有变化，额度已返还");
        }
    }

    @Transactional
    public BranchView confirmLanguageBranch(CurrentAccount current, String conversationId, String branchId,
            int expectedVersion) {
        ConversationOwner owner = owner(current, conversationId);
        BranchRow branch = requireBranchForUpdate(owner, branchId);
        if (!"LANGUAGE".equals(branch.branchType()) || !"REVIEWING".equals(branch.status())) {
            throw AppException.conflict("AI_TRANSLATION_STATE_CONFLICT", "该分支没有待确认译文");
        }
        if (branch.versionNo() != expectedVersion) {
            throw AppException.conflict("AI_BRANCH_VERSION_CONFLICT", "分支已更新，请刷新后重试");
        }
        Map<String, Object> review = readMapNullable(branch.reviewMetadataJson());
        if (!"AWAITING_CONFIRMATION".equals(String.valueOf(review.get("translationStatus")))) {
            throw AppException.conflict("AI_TRANSLATION_NOT_READY", "请先生成译文再确认");
        }
        RevisionContent translated = requireRevision(owner, branch.currentRevisionId());
        Instant now = clock.now();
        String revisionId = createRevision(new ConversationOwner(owner.accountId(), owner.masterId(), branch.id()),
                translated.contentJson(), "LANGUAGE_CONFIRMED", branch.currentRevisionId(), now);
        review.put("translationStatus", "CONFIRMED");
        review.put("confirmedRevisionId", revisionId);
        review.put("confirmedAt", now.toString());
        jdbc.update("UPDATE resume_branches SET status='ACTIVE',review_metadata_json=?,updated_at=? WHERE id=?",
                json(review), now, branch.id());
        if (branch.id().equals(owner.branchId())) {
            ResumeMasterEntity master = masters.findById(owner.masterId())
                    .orElseThrow(() -> AppException.user("RESUME_NOT_FOUND", "简历主档不存在"));
            Map<String, Object> content = readMap(translated.contentJson());
            projectLegacy(master, content);
            master.setContentSchemaVersion(SCHEMA);
            master.setContentJson(translated.contentJson());
            master.setVersionNo(master.getVersionNo() + 1);
            master.setUpdatedAt(now);
            masters.save(master);
            syncCards(conversationId, content, now);
        }
        return branch(branch.id(), owner, owner.branchId());
    }

    @Transactional
    public BranchView switchBranch(CurrentAccount current, String conversationId, String branchId) {
        ConversationOwner owner = owner(current, conversationId);
        BranchRow branch = requireBranch(owner, branchId);
        Instant now = clock.now();
        if ("ACTIVE".equals(branch.status())) {
            RevisionContent revision = requireRevision(owner, branch.currentRevisionId());
            ResumeMasterEntity master = masters.findById(owner.masterId())
                    .orElseThrow(() -> AppException.user("RESUME_NOT_FOUND", "简历主档不存在"));
            Map<String, Object> content = readMap(revision.contentJson());
            projectLegacy(master, content);
            master.setContentSchemaVersion(SCHEMA);
            master.setContentJson(revision.contentJson());
            master.setVersionNo(master.getVersionNo() + 1);
            master.setUpdatedAt(now);
            masters.save(master);
            syncCards(conversationId, content, now);
        }
        jdbc.update("UPDATE ai_resume_conversations SET active_branch_id=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                branch.id(), now, conversationId, owner.accountId());
        return branch(branch.id(), owner, branch.id());
    }

    @Transactional(readOnly = true)
    public BranchDiffView branchDiff(CurrentAccount current, String conversationId, String branchId) {
        ConversationOwner owner = owner(current, conversationId);
        BranchRow branch = requireBranch(owner, branchId);
        if (branch.parentBranchId() == null) {
            return new BranchDiffView(branch.id(), null, false, List.of());
        }
        BranchRow parent = requireBranch(owner, branch.parentBranchId());
        RevisionContent parentRevision = requireRevision(owner, parent.currentRevisionId());
        RevisionContent branchRevision = requireRevision(owner, branch.currentRevisionId());
        return new BranchDiffView(branch.id(), parent.id(),
                !Objects.equals(branch.sourceRevisionId(), parent.currentRevisionId()),
                differences(readMap(parentRevision.contentJson()), readMap(branchRevision.contentJson())));
    }

    @Transactional
    public BranchView syncBranch(CurrentAccount current, String conversationId, String branchId, int expectedVersion) {
        ConversationOwner owner = owner(current, conversationId);
        BranchRow branch = requireBranchForUpdate(owner, branchId);
        if (branch.parentBranchId() == null) {
            throw AppException.conflict("AI_BRANCH_SYNC_BASE", "基础分支不需要同步");
        }
        if (branch.versionNo() != expectedVersion) {
            throw AppException.conflict("AI_BRANCH_VERSION_CONFLICT", "分支已更新，请刷新后重试");
        }
        BranchRow parent = requireBranch(owner, branch.parentBranchId());
        RevisionContent source = requireRevision(owner, parent.currentRevisionId());
        Instant now = clock.now();
        String revisionId = createRevision(new ConversationOwner(owner.accountId(), owner.masterId(), branch.id()),
                source.contentJson(), "BRANCH_SYNCED", parent.currentRevisionId(), now);
        boolean languageBranch = "LANGUAGE".equals(branch.branchType());
        String reviewMetadata = languageBranch
                ? json(Map.of("translationStatus", "NOT_STARTED", "targetLanguage", branch.languageCode(),
                        "sourceRevisionId", revisionId))
                : branch.reviewMetadataJson();
        jdbc.update("UPDATE resume_branches SET source_revision_id=?,status=?,review_metadata_json=?,updated_at=? WHERE id=?",
                parent.currentRevisionId(), languageBranch ? "REVIEWING" : "ACTIVE", reviewMetadata, now, branch.id());
        if (branch.id().equals(owner.branchId()) && !languageBranch) {
            ResumeMasterEntity master = masters.findById(owner.masterId())
                    .orElseThrow(() -> AppException.user("RESUME_NOT_FOUND", "简历主档不存在"));
            Map<String, Object> content = readMap(source.contentJson());
            projectLegacy(master, content);
            master.setContentSchemaVersion(SCHEMA);
            master.setContentJson(source.contentJson());
            master.setVersionNo(master.getVersionNo() + 1);
            master.setUpdatedAt(now);
            masters.save(master);
            syncCards(conversationId, content, now);
        }
        return branch(branch.id(), owner, owner.branchId());
    }

    @Transactional(readOnly = true)
    public RevisionDiffView revisionDiff(CurrentAccount current, String conversationId, String fromRevisionId,
            String toRevisionId) {
        ConversationOwner owner = owner(current, conversationId);
        RevisionContent from = requireRevision(owner, fromRevisionId);
        RevisionContent to = requireRevision(owner, toRevisionId);
        return new RevisionDiffView(fromRevisionId, toRevisionId,
                differences(readMap(from.contentJson()), readMap(to.contentJson())));
    }

    @Transactional(readOnly = true)
    public DataExportView dataExport(CurrentAccount current, String conversationId) {
        ConversationOwner owner = owner(current, conversationId);
        BranchRow branch = requireBranch(owner, owner.branchId());
        Map<String, Object> content = readMap(requireRevision(owner, branch.currentRevisionId()).contentJson());
        StringBuilder markdown = new StringBuilder("# 简历数据导出\n\n");
        appendMarkdown(markdown, "基本信息", content.get("basics"));
        appendMarkdown(markdown, "求职意向", content.get("intentions"));
        appendMarkdown(markdown, "个人简介", content.get("summary"));
        appendMarkdown(markdown, "教育经历", content.get("education"));
        appendMarkdown(markdown, "工作与实习", content.get("experiences"));
        appendMarkdown(markdown, "项目经历", content.get("projects"));
        appendMarkdown(markdown, "社团与活动", content.get("organizations"));
        appendMarkdown(markdown, "专业技能", content.get("skills"));
        appendMarkdown(markdown, "证书与资质", content.get("certificates"));
        appendMarkdown(markdown, "荣誉奖项", content.get("honors"));
        appendMarkdown(markdown, "语言能力", content.get("languages"));
        return new DataExportView(SCHEMA, branch.id(), content, markdown.toString());
    }

    @Transactional
    public RevisionView restore(CurrentAccount current, String conversationId, String revisionId) {
        ConversationOwner owner = owner(current, conversationId);
        RevisionContent source = jdbc.query("SELECT branch_id,content_json FROM resume_revisions WHERE id=? AND account_id=? AND master_id=?",
                (rs, n) -> new RevisionContent(rs.getString("branch_id"), rs.getString("content_json")),
                revisionId, owner.accountId(), owner.masterId()).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_REVISION_NOT_FOUND", "简历修订不存在"));
        if (!owner.branchId().equals(source.branchId())) {
            throw AppException.conflict("AI_REVISION_BRANCH_MISMATCH", "只能在当前分支恢复历史修订");
        }
        ResumeMasterEntity master = masters.findById(owner.masterId())
                .orElseThrow(() -> AppException.user("RESUME_NOT_FOUND", "简历主档不存在"));
        Map<String, Object> content = readMap(source.contentJson());
        projectLegacy(master, content);
        Instant now = clock.now();
        master.setContentSchemaVersion(SCHEMA);
        master.setContentJson(source.contentJson());
        master.setVersionNo(master.getVersionNo() + 1);
        master.setUpdatedAt(now);
        masters.save(master);
        String newId = createRevision(owner, source.contentJson(), "VERSION_RESTORED", revisionId, now);
        return new RevisionView(newId, owner.branchId(), nextRevisionNo(owner.branchId()) - 1,
                "VERSION_RESTORED", revisionId, readNode(source.contentJson()), sha256(source.contentJson()), now);
    }

    private ConversationOwner owner(CurrentAccount current, String conversationId) {
        assertSeeker(current);
        return jdbc.query("SELECT account_id,master_id,active_branch_id FROM ai_resume_conversations WHERE id=?",
                (rs, n) -> new ConversationOwner(rs.getString("account_id"), rs.getString("master_id"),
                        rs.getString("active_branch_id")), conversationId).stream().findFirst()
                .filter(value -> current.accountId().equals(value.accountId()))
                .orElseThrow(() -> AppException.forbidden("OBJECT_FORBIDDEN", "不能访问该 AI 简历会话"));
    }

    private BranchView createBranch(ConversationOwner owner, BranchRow parent, String branchType, String title,
            String languageCode, String jobVersionId, boolean reviewRequired,
            java.util.function.Consumer<Map<String, Object>> contentCustomizer) {
        RevisionContent source = requireRevision(owner, parent.currentRevisionId());
        Map<String, Object> content = readMap(source.contentJson());
        contentCustomizer.accept(content);
        String contentJson = json(content);
        Instant now = clock.now();
        String id = Ids.newId();
        jdbc.update("INSERT INTO resume_branches(id,account_id,master_id,parent_branch_id,branch_type,title,language_code,job_version_id,current_revision_id,source_revision_id,status,version_no,created_at,updated_at,archived_at) VALUES(?,?,?,?,?,?,?,?,NULL,?,?,0,?,?,NULL)",
                id, owner.accountId(), owner.masterId(), parent.id(), branchType, title, languageCode, jobVersionId,
                parent.currentRevisionId(), reviewRequired ? "REVIEWING" : "ACTIVE", now, now);
        createRevision(new ConversationOwner(owner.accountId(), owner.masterId(), id), contentJson,
                reviewRequired ? "LANGUAGE_BRANCH_CREATED" : "JOB_BRANCH_CREATED", parent.currentRevisionId(), now);
        return branch(id, owner, owner.branchId());
    }

    private BranchView branch(String id, ConversationOwner owner, String activeBranchId) {
        return jdbc.query("SELECT b.*,p.current_revision_id AS parent_revision_id FROM resume_branches b LEFT JOIN resume_branches p ON p.id=b.parent_branch_id WHERE b.id=? AND b.account_id=? AND b.master_id=?",
                (rs, n) -> branchView(rs, activeBranchId), id, owner.accountId(), owner.masterId()).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_BRANCH_NOT_FOUND", "简历分支不存在"));
    }

    private BranchView branchView(java.sql.ResultSet rs, String activeBranchId) throws java.sql.SQLException {
        String parentRevisionId = rs.getString("parent_revision_id");
        String sourceRevisionId = rs.getString("source_revision_id");
        return new BranchView(rs.getString("id"), rs.getString("parent_branch_id"), rs.getString("branch_type"),
                rs.getString("title"), rs.getString("language_code"), rs.getString("job_version_id"),
                rs.getString("current_revision_id"), sourceRevisionId, rs.getString("status"),
                readNode(rs.getString("review_metadata_json")), rs.getInt("version_no"),
                rs.getString("id").equals(activeBranchId),
                parentRevisionId != null && !Objects.equals(parentRevisionId, sourceRevisionId),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private BranchRow requireBranch(ConversationOwner owner, String branchId) {
        return branchRow(owner, branchId, false);
    }

    private BranchRow requireBranchForUpdate(ConversationOwner owner, String branchId) {
        return branchRow(owner, branchId, true);
    }

    private BranchRow branchRow(ConversationOwner owner, String branchId, boolean lock) {
        String suffix = lock ? " FOR UPDATE" : "";
        return jdbc.query("SELECT * FROM resume_branches WHERE id=? AND account_id=? AND master_id=? AND status<>'ARCHIVED'" + suffix,
                (rs, n) -> new BranchRow(rs.getString("id"), rs.getString("parent_branch_id"),
                        rs.getString("branch_type"), rs.getString("title"), rs.getString("language_code"),
                        rs.getString("job_version_id"), rs.getString("current_revision_id"),
                        rs.getString("source_revision_id"), rs.getString("status"),
                        rs.getString("review_metadata_json"), rs.getInt("version_no")),
                branchId, owner.accountId(), owner.masterId()).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_BRANCH_NOT_FOUND", "简历分支不存在"));
    }

    private RevisionContent requireRevision(ConversationOwner owner, String revisionId) {
        if (revisionId == null) throw AppException.conflict("AI_BRANCH_REVISION_MISSING", "分支没有可用修订");
        return jdbc.query("SELECT branch_id,content_json FROM resume_revisions WHERE id=? AND account_id=? AND master_id=?",
                (rs, n) -> new RevisionContent(rs.getString("branch_id"), rs.getString("content_json")),
                revisionId, owner.accountId(), owner.masterId()).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_REVISION_NOT_FOUND", "简历修订不存在"));
    }

    private void syncCards(String conversationId, Map<String, Object> content, Instant now) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("TARGET_JOB", content.getOrDefault("intentions", Map.of()));
        values.put("CONTACT", content.getOrDefault("basics", Map.of()));
        values.put("EDUCATION", Map.of("text", textOrEmpty(content.get("education"))));
        values.put("EXPERIENCE", Map.of("text", textOrEmpty(content.get("experiences"))));
        values.put("PROJECTS", Map.of("text", textOrEmpty(content.get("projects"))));
        values.put("ORGANIZATIONS", Map.of("text", textOrEmpty(content.get("organizations"))));
        values.put("SKILLS", Map.of("text", textOrEmpty(content.get("skills"))));
        values.put("CERTIFICATES", Map.of("text", textOrEmpty(content.get("certificates"))));
        values.put("HONORS", Map.of("text", textOrEmpty(content.get("honors"))));
        values.put("LANGUAGES", Map.of("text", textOrEmpty(content.get("languages"))));
        values.put("SUMMARY", Map.of("text", textOrEmpty(content.get("summary"))));
        values.forEach((type, payload) -> jdbc.update("UPDATE ai_resume_cards SET payload_json=?,status='CONFIRMED',version_no=version_no+1,updated_at=? WHERE conversation_id=? AND card_type=?",
                json(payload), now, conversationId, type));
    }

    private static List<FieldDiff> differences(Map<String, Object> before, Map<String, Object> after) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        List<FieldDiff> result = new ArrayList<>();
        for (String key : keys) {
            Object left = before.get(key);
            Object right = after.get(key);
            if (!Objects.equals(left, right)) result.add(new FieldDiff(key, left, right));
        }
        return List.copyOf(result);
    }

    private TranslationResult parseTranslation(Response response, Map<String, Object> facts) {
        try {
            String raw = response.text() == null ? "" : response.text().trim();
            if (raw.startsWith("```")) {
                raw = raw.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end < start) throw new IllegalArgumentException();
            JsonNode root = mapper.readTree(raw.substring(start, end + 1));
            JsonNode translated = root.path("translated");
            if (!translated.isObject()) throw new IllegalArgumentException();
            Map<String, Object> fields = new LinkedHashMap<>();
            for (String key : facts.keySet()) {
                JsonNode value = translated.get(key);
                if (value == null || value.isNull()) throw new IllegalArgumentException();
                fields.put(key, mapper.convertValue(value, Object.class));
            }
            if (!numericFacts(json(facts)).equals(numericFacts(json(fields)))) {
                throw AppException.conflict("AI_TRANSLATION_FACT_MISMATCH", "译文改变或遗漏了日期、数字或量化事实");
            }
            List<String> properNames = new ArrayList<>();
            JsonNode names = root.path("unconfirmedProperNames");
            if (!names.isMissingNode() && !names.isArray()) throw new IllegalArgumentException();
            if (names.isArray()) {
                if (names.size() > 100) throw new IllegalArgumentException();
                for (JsonNode name : names) {
                    String value = name.asText("").trim();
                    if (value.isEmpty() || value.length() > 256 || !json(facts).contains(value)) {
                        throw new IllegalArgumentException();
                    }
                    properNames.add(value);
                }
            }
            return new TranslationResult(Map.copyOf(fields), List.copyOf(properNames));
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw AppException.dependency("AI_TRANSLATION_RESPONSE_INVALID", "翻译结果不符合受控结构，未写入语言分支");
        }
    }

    private static List<String> numericFacts(String value) {
        List<String> result = new ArrayList<>();
        Matcher matcher = NUMBER_OR_DATE.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(matcher.group());
        result.sort(String::compareTo);
        return result;
    }

    private String model(Response response) {
        return response.model() == null || response.model().isBlank() ? defaultModel : response.model();
    }

    private JsonNode readNode(String value) {
        try { return mapper.readTree(value); }
        catch (Exception exception) { return mapper.createObjectNode(); }
    }

    private static String cleanTitle(String title, String fallback) {
        String result = title == null || title.isBlank() ? fallback : title.trim();
        if (result == null || result.isBlank() || result.length() > 255) {
            throw AppException.user("AI_BRANCH_TITLE_INVALID", "分支名称必填且最长 255 个字符");
        }
        return result;
    }

    private static String required(String value, String code, String message) {
        if (value == null || value.isBlank()) throw AppException.user(code, message);
        return value.trim();
    }

    private static String textOrEmpty(Object value) { return value == null ? "" : String.valueOf(value); }

    private static boolean hasContent(Object value) {
        if (value == null) return false;
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        if (value instanceof List<?> list) return !list.isEmpty();
        return !String.valueOf(value).isBlank();
    }

    private static void appendMarkdown(StringBuilder value, String heading, Object content) {
        if (content == null || String.valueOf(content).isBlank() || "{}".equals(String.valueOf(content))
                || "[]".equals(String.valueOf(content))) return;
        value.append("## ").append(heading).append("\n\n").append(content).append("\n\n");
    }

    private String createRevision(ConversationOwner owner, String contentJson, String source,
            String sourceObjectId, Instant now) {
        int revisionNo = nextRevisionNo(owner.branchId());
        String id = Ids.newId();
        jdbc.update("INSERT INTO resume_revisions(id,account_id,master_id,branch_id,revision_no,source,source_object_id,content_schema_version,content_json,layout_instance_id,content_hash,created_at) VALUES(?,?,?,?,?,?,?,?,?,NULL,?,?)",
                id, owner.accountId(), owner.masterId(), owner.branchId(), revisionNo, source, sourceObjectId,
                SCHEMA, contentJson, sha256(contentJson), now);
        jdbc.update("UPDATE resume_branches SET current_revision_id=?,version_no=version_no+1,updated_at=? WHERE id=?",
                id, now, owner.branchId());
        return id;
    }

    private int nextRevisionNo(String branchId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM resume_revisions WHERE branch_id=?",
                Integer.class, branchId);
        return (count == null ? 0 : count) + 1;
    }

    private void projectLegacy(ResumeMasterEntity master, Map<String, Object> content) {
        master.setSelfIntro(text(content.get("summary")));
        JsonNode canonical = mapper.valueToTree(content);
        master.setEducationJson(text(ResumeStructuredContent.text(canonical, "education")));
        master.setExperienceJson(text(ResumeStructuredContent.text(canonical, "experience")));
        master.setProjectsJson(text(ResumeStructuredContent.text(canonical, "projects")));
        master.setSkillsJson(text(ResumeStructuredContent.text(canonical, "skills")));
        master.setCertificatesJson(text(ResumeStructuredContent.text(canonical, "certificates")));
    }

    private Map<String, Object> readMap(String value) {
        try { return mapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception exception) { return new LinkedHashMap<>(); }
    }

    private Map<String, Object> readMapNullable(String value) {
        return value == null ? Map.of() : readMap(value);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static String text(Object value) {
        if (value == null) return null;
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
    private static void assertSeeker(CurrentAccount current) {
        if (current == null || !"SEEKER".equals(current.role())) {
            throw AppException.forbidden("AI_RESUME_FORBIDDEN", "仅求职者可以使用 AI 简历工作台");
        }
    }

    private record ConversationOwner(String accountId, String masterId, String branchId) {}
    private record BranchRow(String id, String parentBranchId, String branchType, String title, String languageCode,
            String jobVersionId, String currentRevisionId, String sourceRevisionId, String status,
            String reviewMetadataJson, int versionNo) {}
    private record RevisionContent(String branchId, String contentJson) {}
    private record TranslationResult(Map<String, Object> fields, List<String> unconfirmedProperNames) {}

    public record RevisionView(String id, String branchId, int revisionNo, String source, String sourceObjectId,
            JsonNode content, String contentHash, Instant createdAt) {}
    public record BranchView(String id, String parentBranchId, String branchType, String title, String languageCode,
            String jobVersionId, String currentRevisionId, String sourceRevisionId, String status,
            JsonNode reviewMetadata, int versionNo, boolean active, boolean syncRequired,
            Instant createdAt, Instant updatedAt) {}
    public record FieldDiff(String path, Object before, Object after) {}
    public record BranchDiffView(String branchId, String parentBranchId, boolean syncRequired,
            List<FieldDiff> changes) {}
    public record RevisionDiffView(String fromRevisionId, String toRevisionId, List<FieldDiff> changes) {}
    public record DataExportView(String schemaVersion, String branchId, Map<String, Object> json,
            String markdown) {}
}
