package com.jobproof.modules.airesume.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.airesume.application.AiQuotaService.QuotaView;
import com.jobproof.modules.airesume.application.AiGenerationAttemptService.CancellationView;
import com.jobproof.modules.airesume.application.AiResumeGenerationService.GeneratedReply;
import com.jobproof.modules.airesume.application.AiResumeGenerationService.GenerationResult;
import com.jobproof.modules.airesume.application.AiResumeGenerationService.PreparedRequest;
import com.jobproof.modules.airesume.application.AiResumeSseService.Event;
import com.jobproof.modules.airesume.application.AiResumeChangeSetService.ChangeSetView;
import com.jobproof.modules.airesume.application.AiResumeChangeSetService.GeneratedChange;
import com.jobproof.modules.aigateway.application.AiGatewayException;
import com.jobproof.modules.aigateway.application.AiGatewayService;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Message;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Request;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Response;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.career.application.CareerLibraryService;
import com.jobproof.modules.career.application.CareerLibraryService.EvidenceSnapshot;
import com.jobproof.modules.resume.application.ResumeAiCandidateService;
import com.jobproof.modules.resume.application.ResumeService;
import com.jobproof.modules.resume.application.ResumeService.CreateCommand;
import com.jobproof.modules.resume.application.ResumeTemplateService;
import com.jobproof.modules.resume.application.ResumeTemplateService.ApplyCommand;
import com.jobproof.modules.resume.application.ResumeTemplateService.LayoutView;
import com.jobproof.modules.resume.application.ResumeTemplateService.TemplateDetailView;
import com.jobproof.modules.resume.application.BuiltInTemplateCatalog;
import com.jobproof.modules.resume.domain.ResumeDesignSettings;
import com.jobproof.modules.resume.domain.ResumeDesignV2;
import com.jobproof.modules.resume.domain.ResumeTemplateManifest;
import com.jobproof.modules.resume.domain.ResumeLayoutDefinition;
import com.jobproof.modules.resume.domain.ResumeLayoutProtocol;
import com.jobproof.modules.resume.domain.ResumePdfExportMode;
import com.jobproof.modules.resume.infra.ResumeMasterEntity;
import com.jobproof.modules.resume.infra.ResumeMasterJpaRepository;
import com.jobproof.modules.storage.FileAccessService;
import com.jobproof.modules.storage.FileAccessService.FileView;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiResumeWorkbenchService {

    private static final String SCHEMA = "resume-content-v3";
    private static final String CONSENT_TYPE = "AI_RESUME_WORKBENCH";
    private static final String POLICY_VERSION = "2026-08-v1";
    private static final Set<String> IDENTITIES = Set.of("STUDENT", "GRADUATE", "PROFESSIONAL");
    private static final Set<String> SENSITIVE_KEYS = Set.of("age", "gender", "maritalstatus", "ethnicity",
            "年龄", "性别", "婚育", "民族");
    private static final Map<String, String> WRITING_STYLES = Map.of(
            "SYSTEM_RECOMMENDED", "系统推荐",
            "PROFESSIONAL_CONCISE", "专业简洁",
            "RESULTS_ORIENTED", "成果导向",
            "TECHNICAL_RIGOR", "技术严谨",
            "STEADY_FORMAL", "稳健正式");
    private static final List<String> CARD_TYPES = List.of("TARGET_JOB", "EDUCATION", "EXPERIENCE",
            "PROJECTS", "ORGANIZATIONS", "SKILLS", "CERTIFICATES", "HONORS", "LANGUAGES", "CONTACT", "SUMMARY");
    private static final Set<String> STRUCTURED_LIST_CARDS = Set.of("EDUCATION", "EXPERIENCE", "PROJECTS",
            "ORGANIZATIONS", "SKILLS", "CERTIFICATES", "HONORS", "LANGUAGES");
    private static final int CREDENTIAL_DESCRIPTION_MIN_CHARACTERS = 60;
    private static final String CHAT_SYSTEM_PROMPT = """
            你是 JobProof AI 简历顾问。只回答简历、岗位、求职资料和求职准备问题。
            只能依据用户已确认的简历事实和当前消息回答；事实不足时必须追问。
            不得编造公司、学校、岗位、日期、技能、证书、数字或成果，不得推断敏感属性。
            你的回复只是建议，不能声称已经修改正式简历；正式内容必须由用户在卡片或候选中确认。
            使用简洁中文，不输出 Markdown 表格，不复述联系方式。
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final ResumeService resumes;
    private final ResumeTemplateService resumeTemplates;
    private final ResumeMasterJpaRepository masters;
    private final ResumeAiCandidateService aiCandidates;
    private final AiQuotaService quota;
    private final AiResumeSseService sse;
    private final AiGatewayService gateway;
    private final AuditService audit;
    private final FileAccessService files;
    private final AiResumeGenerationService generation;
    private final AiResumeConversationEventService conversationEvents;
    private final CareerLibraryService careerLibrary;
    private final AiResumeChangeSetService changeSets;
    private final BuiltInTemplateCatalog builtInTemplates;
    private final boolean enabled;
    private final String defaultModel;

    public AiResumeWorkbenchService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock,
            ResumeService resumes, ResumeTemplateService resumeTemplates,
            ResumeMasterJpaRepository masters, ResumeAiCandidateService aiCandidates,
            AiQuotaService quota, AiResumeSseService sse, AiGatewayService gateway, AuditService audit,
            FileAccessService files, AiResumeGenerationService generation,
            AiResumeConversationEventService conversationEvents, CareerLibraryService careerLibrary,
            AiResumeChangeSetService changeSets, BuiltInTemplateCatalog builtInTemplates,
            @Value("${jobproof.ai.workbench.enabled:false}") boolean enabled,
            @Value("${jobproof.ai.resume-model:qwen-plus}") String defaultModel) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.resumes = resumes;
        this.resumeTemplates = resumeTemplates;
        this.masters = masters;
        this.aiCandidates = aiCandidates;
        this.quota = quota;
        this.sse = sse;
        this.gateway = gateway;
        this.audit = audit;
        this.files = files;
        this.generation = generation;
        this.conversationEvents = conversationEvents;
        this.careerLibrary = careerLibrary;
        this.changeSets = changeSets;
        this.builtInTemplates = builtInTemplates;
        this.enabled = enabled;
        this.defaultModel = defaultModel;
    }

    @Transactional
    public ConversationView create(CurrentAccount current, CreateConversationCommand command) {
        assertSeeker(current);
        String identity = normalizeIdentity(command.identityType());
        ResumeService.MasterView master = resumes.create(current,
                new CreateCommand("BLANK", blankTo(command.title(), defaultTitle(identity)), null, null));
        return ensure(current, master.id(), identity);
    }

    @Transactional
    public ConversationView selectTemplate(CurrentAccount current, String conversationId, String templateCode) {
        return selectTemplate(current, conversationId, templateCode, null);
    }

    @Transactional
    public ConversationView selectTemplate(CurrentAccount current, String conversationId, String templateSelector,
            Integer expectedLayoutVersion) {
        assertSeeker(current);
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        String templateId = smartTemplateId(templateSelector);
        TemplateDetailView template = resumeTemplates.detail(current, templateId);
        LayoutView currentLayout = currentLayout(current, conversation.masterId());
        if (currentLayout != null && templateId.equals(currentLayout.templateId())
                && !"FROZEN".equals(currentLayout.status())) {
            return view(current, conversation);
        }
        DesignPreferenceView preference = ensureDesignPreference(current, conversation, template, currentLayout);
        LayoutView layout = resumeTemplates.apply(current, templateId,
                new ApplyCommand(conversation.masterId(), preference.variantCode(),
                        expectedLayoutVersion == null
                                ? currentLayout == null ? null : currentLayout.version()
                                : expectedLayoutVersion));
        layout = resumeTemplates.configureDesign(current, layout.id(), conversation.activeBranchId(),
                preference.settings(), false);
        Instant now = clock.now();
        jdbc.update("UPDATE ai_resume_conversations SET version_no=version_no+1,updated_at=? WHERE id=?", now,
                conversation.id());
        Event event = appendEvent(current.accountId(), conversation.id(), "layout.confirmed",
                Map.of("layoutId", layout.id(), "templateId", templateId,
                        "designVersion", preference.versionNo()), now);
        sse.publish(event);
        return view(current, requireConversation(current.accountId(), conversation.id()));
    }

    @Transactional(readOnly = true)
    public List<SmartTemplateView> smartTemplates(CurrentAccount current, String conversationId) {
        assertSeeker(current);
        ConversationRow conversation = requireConversation(current.accountId(), conversationId);
        List<SmartTemplateView> result = new ArrayList<>();
        for (String templateId : builtInTemplates.ids()) {
            TemplateDetailView template;
            try {
                template = resumeTemplates.detail(current, templateId);
            } catch (AppException unavailable) {
                continue; // retired by an operator
            }
            result.add(smartTemplateView(current, conversation, template));
        }
        return List.copyOf(result);
    }

    @Transactional
    public TaskView exportPdf(CurrentAccount current, String conversationId) {
        return exportPdf(current, conversationId, ResumePdfExportMode.STANDARD);
    }

    @Transactional
    public TaskView exportPdf(CurrentAccount current, String conversationId, ResumePdfExportMode exportMode) {
        assertSeeker(current);
        ResumePdfExportMode mode = exportMode == null ? ResumePdfExportMode.STANDARD : exportMode;
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        LayoutView editableLayout = ensureEditableLayout(current, conversation);
        if ("OVERFLOW".equals(editableLayout.status())) {
            JsonNode first = editableLayout.overflow().path("items").path(0);
            String detail = first.isMissingNode() || first.isNull()
                    ? "当前模板存在内容溢出，请精简内容或调整设计后再导出"
                    : "第 " + first.path("page").asInt(1) + " 页的 "
                            + first.path("slotKey").asText("内容区域") + " 溢出，请调整后再导出";
            throw AppException.conflict("RESUME_TEMPLATE_OVERFLOW", detail);
        }

        ResumeService.MasterView master = resumes.get(current, conversation.masterId());
        ResumeService.VersionView frozen = resumes.freezeConfirmedForWorkbench(
                current, conversation.masterId(), master.version(), mode);

        // The exported version keeps the frozen layout. A fresh editable instance
        // lets the user continue changing content, design, and template afterward.
        ensureEditableLayout(current, conversation);
        TaskView task = resumes.startPdfExport(current, frozen.id(), mode);
        Instant now = clock.now();
        Event event = appendEvent(current.accountId(), conversation.id(), "pdf.export.started",
                Map.of("taskId", task.id(), "resumeVersionId", frozen.id(),
                        "templateId", editableLayout.templateId(), "exportMode", mode.name()), now);
        sse.publish(event);
        audit.append(current.accountId(), "AI_RESUME_PDF_EXPORT_STARTED", "AI_RESUME_CONVERSATION",
                conversation.id(), "mode=" + mode.name() + " taskId=" + task.id()
                        + " resumeVersionId=" + frozen.id());
        return task;
    }

    /** The resume a conversation edits, for read-only flows such as the export preview. */
    @Transactional(readOnly = true)
    public String masterId(CurrentAccount current, String conversationId) {
        assertSeeker(current);
        return requireConversation(current.accountId(), conversationId).masterId();
    }

    @Transactional
    public DesignPreferenceView saveDesign(CurrentAccount current, String conversationId, String templateId,
            String requestedVariant, JsonNode settings, Integer expectedVersion) {
        assertSeeker(current);
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        String normalizedTemplateId = smartTemplateId(templateId);
        TemplateDetailView template = resumeTemplates.detail(current, normalizedTemplateId);
        DesignPreferenceView existing = findDesignPreference(current, conversation, template);
        int actualVersion = existing == null ? 0 : existing.versionNo();
        assertVersion(expectedVersion, actualVersion, "RESUME_DESIGN_VERSION_CONFLICT");
        String variant = requestedVariant == null || requestedVariant.isBlank()
                ? existing == null ? defaultVariant(template) : existing.variantCode()
                : requestedVariant.trim().toUpperCase(Locale.ROOT);
        if (isHtmlTemplate(template)) variant = defaultVariant(template);
        if (!template.variants().contains(variant)) {
            throw AppException.user("RESUME_DESIGN_PRESET_INVALID", "设计预设不属于当前模板");
        }
        Object validated;
        try {
            validated = isHtmlTemplate(template)
                    ? ResumeDesignV2.validate(manifest(template), settings)
                    : ResumeDesignSettings.fromJson(settings, layoutDefinition(template), variant,
                            template.template().photoPolicy(), normalizedTemplateId);
        } catch (IllegalArgumentException exception) {
            throw AppException.user("RESUME_DESIGN_INVALID", exception.getMessage());
        }
        Instant now = clock.now();
        if (existing == null) {
            jdbc.update("INSERT INTO resume_layout_preferences(id,account_id,master_id,branch_id,template_id,variant_code,design_schema_version,settings_json,version_no,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,0,?,?)",
                    Ids.newId(), current.accountId(), conversation.masterId(), conversation.activeBranchId(),
                    normalizedTemplateId, variant, designSchema(template),
                    json(validated), now, now);
        } else {
            jdbc.update("UPDATE resume_layout_preferences SET variant_code=?,design_schema_version=?,settings_json=?,version_no=version_no+1,updated_at=? WHERE id=?",
                    variant, designSchema(template), json(validated), now, existing.id());
        }
        LayoutView currentLayout = currentLayout(current, conversation.masterId());
        if (currentLayout != null && normalizedTemplateId.equals(currentLayout.templateId())) {
            if ("FROZEN".equals(currentLayout.status()) || !variant.equals(currentLayout.variantCode())) {
                currentLayout = resumeTemplates.apply(current, normalizedTemplateId,
                        new ApplyCommand(conversation.masterId(), variant,
                                "FROZEN".equals(currentLayout.status()) ? null : currentLayout.version()));
            }
            resumeTemplates.configureDesign(current, currentLayout.id(), conversation.activeBranchId(),
                    mapper.valueToTree(validated), true);
        }
        Event event = appendEvent(current.accountId(), conversation.id(), "design.saved",
                Map.of("templateId", normalizedTemplateId, "variantCode", variant,
                        "versionNo", existing == null ? 0 : actualVersion + 1), now);
        sse.publish(event);
        return designPreference(current, conversation, template);
    }

    @Transactional
    public ConversationView ensureForResume(CurrentAccount current, String masterId) {
        assertSeeker(current);
        ResumeService.MasterView master = resumes.get(current, masterId);
        return ensure(current, master.id(), null);
    }

    @Transactional
    public ConversationView attachExternalChanges(CurrentAccount current, String conversationId,
            AiResumeChangeSetService.GeneratedChange generated, String sourceType, String sourceId) {
        assertSeeker(current);
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM ai_resume_change_sets WHERE account_id=? AND conversation_id=? AND source_type=? AND source_id=?",
                Integer.class, current.accountId(), conversationId, sourceType, sourceId);
        if (existing != null && existing > 0) return view(current, conversation);
        AiResumeMessageView assistant = generation.addAssistantMessage(current.accountId(), conversationId,
                generated.assistantText(), generated.model(), generated.inputTokens(), generated.outputTokens());
        changeSets.persistExternal(current.accountId(), conversationId, assistant.id(), generated, sourceType, sourceId);
        jdbc.update("UPDATE ai_resume_conversations SET version_no=version_no+1,updated_at=? WHERE id=?",
                clock.now(), conversationId);
        audit.append(current.accountId(), "AI_RESUME_EXTERNAL_CHANGES_ATTACHED", "AI_RESUME_CONVERSATION",
                conversationId, "source=" + sourceType + ":" + sourceId + " items=" + generated.items().size());
        return view(current, requireConversation(current.accountId(), conversationId));
    }

    @Transactional(readOnly = true)
    public ConversationView get(CurrentAccount current, String conversationId) {
        assertSeeker(current);
        ConversationRow conversation = requireConversation(current.accountId(), conversationId);
        return view(current, conversation);
    }

    @Transactional
    public ConversationView saveDraft(CurrentAccount current, String conversationId, String cardId,
            JsonNode payload, Integer expectedVersion) {
        assertSeeker(current);
        ConversationRow conversation = requireConversation(current.accountId(), conversationId);
        CardRow card = requireCard(current.accountId(), conversation.id(), cardId);
        assertVersion(expectedVersion, card.versionNo(), "AI_CARD_VERSION_CONFLICT");
        validatePayload(card.cardType(), payload, false);
        Instant now = clock.now();
        jdbc.update("UPDATE ai_resume_cards SET status='EDITING',payload_json=?,validation_json=NULL,version_no=version_no+1,updated_at=? WHERE id=?",
                json(payload), now, cardId);
        Event event = appendEvent(current.accountId(), conversation.id(), "card.draft",
                Map.of("cardId", cardId, "cardType", card.cardType(), "status", "editing"), now);
        sse.publish(event);
        return view(current, requireConversation(current.accountId(), conversation.id()));
    }

    @Transactional
    public ConversationView submitCard(CurrentAccount current, String conversationId, String cardId,
            JsonNode payload, Integer expectedVersion) {
        assertSeeker(current);
        ConversationRow conversation = requireConversation(current.accountId(), conversationId);
        CardRow card = requireCard(current.accountId(), conversation.id(), cardId);
        assertVersion(expectedVersion, card.versionNo(), "AI_CARD_VERSION_CONFLICT");
        validatePayload(card.cardType(), payload, true);
        ResumeMasterEntity master = requireMaster(current.accountId(), conversation.masterId());
        Map<String, Object> content = content(master);
        apply(card.cardType(), payload, master, content);
        Instant now = clock.now();
        master.setContentSchemaVersion(SCHEMA);
        master.setContentJson(json(content));
        master.setVersionNo(master.getVersionNo() + 1);
        master.setUpdatedAt(now);
        masters.save(master);

        String revisionId = createRevision(current.accountId(), master, conversation.activeBranchId(),
                "USER_CONFIRMED", card.id(), now);
        jdbc.update("UPDATE ai_resume_cards SET status='CONFIRMED',payload_json=?,validation_json='{}',version_no=version_no+1,updated_at=? WHERE id=?",
                json(payload), now, card.id());
        String stage = readiness(content) ? "READY_FOR_PREVIEW" : "COLLECTING_FACTS";
        jdbc.update("UPDATE ai_resume_conversations SET onboarding_stage=?,summary_json=?,version_no=version_no+1,updated_at=? WHERE id=?",
                stage, json(traceableSummary(content, stage, revisionId, conversation.activeBranchId())), now,
                conversation.id());
        Event event = appendEvent(current.accountId(), conversation.id(), "card.confirmed",
                Map.of("cardId", card.id(), "cardType", card.cardType(), "revisionId", revisionId,
                        "onboardingStage", stage), now);
        sse.publish(event);
        return view(current, requireConversation(current.accountId(), conversation.id()));
    }

    @Transactional
    public ConversationView skipCard(CurrentAccount current, String conversationId, String cardId,
            Integer expectedVersion) {
        assertSeeker(current);
        ConversationRow conversation = requireConversation(current.accountId(), conversationId);
        CardRow card = requireCard(current.accountId(), conversation.id(), cardId);
        assertVersion(expectedVersion, card.versionNo(), "AI_CARD_VERSION_CONFLICT");
        if (!"LANGUAGES".equals(card.cardType())) {
            throw AppException.user("AI_CARD_SKIP_UNSUPPORTED", "当前只支持跳过语言能力步骤");
        }
        if ("CONFIRMED".equals(card.status())) {
            throw AppException.conflict("AI_CARD_ALREADY_CONFIRMED", "已确认的语言能力不能直接跳过，请在编辑视图中修改");
        }
        Instant now = clock.now();
        jdbc.update("UPDATE ai_resume_cards SET status='SKIPPED',payload_json=?,validation_json='{}',version_no=version_no+1,updated_at=? WHERE id=?",
                json(initialPayload(card.cardType())), now, card.id());
        Event event = appendEvent(current.accountId(), conversation.id(), "card.skipped",
                Map.of("cardId", card.id(), "cardType", card.cardType()), now);
        sse.publish(event);
        return view(current, requireConversation(current.accountId(), conversation.id()));
    }

    @Transactional
    public ConversationView captureConfirmedChange(CurrentAccount current, String conversationId) {
        assertSeeker(current);
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        ResumeMasterEntity master = requireMaster(current.accountId(), conversation.masterId());
        Map<String, Object> content = content(master);
        content.put("summary", nullToEmpty(master.getSelfIntro()));
        content.put("education", legacyItems(master.getEducationJson()));
        content.put("experiences", legacyItems(master.getExperienceJson()));
        content.put("projects", legacyItems(master.getProjectsJson()));
        content.put("skills", legacyItems(master.getSkillsJson()));
        content.put("certificates", legacyItems(master.getCertificatesJson()));
        String projected = json(content);
        String currentHash = jdbc.query("SELECT content_hash FROM resume_revisions WHERE branch_id=? ORDER BY revision_no DESC LIMIT 1",
                (rs, n) -> rs.getString("content_hash"), conversation.activeBranchId()).stream()
                .findFirst().orElse("");
        if (!sha256(projected).equals(currentHash)) {
            Instant now = clock.now();
            master.setContentSchemaVersion(SCHEMA);
            master.setContentJson(projected);
            master.setUpdatedAt(now);
            masters.save(master);
            String revisionId = createRevision(current.accountId(), master, conversation.activeBranchId(),
                    "CANDIDATE_CONFIRMED", null, now);
            jdbc.update("UPDATE ai_resume_conversations SET summary_json=?,version_no=version_no+1,updated_at=? WHERE id=?",
                    json(traceableSummary(content, conversation.onboardingStage(), revisionId,
                            conversation.activeBranchId())), now, conversation.id());
            Event event = appendEvent(current.accountId(), conversation.id(), "revision.created",
                    Map.of("revisionId", revisionId, "source", "CANDIDATE_CONFIRMED"), now);
            sse.publish(event);
        }
        return view(current, requireConversation(current.accountId(), conversationId));
    }

    @Transactional
    public AiResumeMessageView addUserMessage(CurrentAccount current, String conversationId, String clientMessageId,
            String text) {
        assertSeeker(current);
        return generation.prepare(current.accountId(), conversationId, clientMessageId, text).user();
    }

    public ConversationView respond(CurrentAccount current, String conversationId, String clientMessageId,
            String text) {
        assertSeeker(current);
        ConversationRow conversation = requireConversation(current.accountId(), conversationId);
        if (!"GRANTED".equals(consent(current.accountId()).status())) {
            throw AppException.conflict("AI_CONSENT_REQUIRED", "请先阅读并同意 AI 简历授权说明");
        }
        ResumeAiCandidateService.Availability availability = aiCandidates.availability(current, defaultModel);
        if (!enabled || !availability.available()) {
            throw AppException.conflict(availability.reason() == null ? "AI_CHANNEL_UNAVAILABLE" : availability.reason(),
                    "没有可用 AI 通道，当前不会生成伪 AI 回复");
        }
        PreparedRequest prepared = generation.prepare(current.accountId(), conversationId, clientMessageId, text);
        GenerationResult<ChangeSetView> result = generation.execute(prepared, "AI_RESPONSE_INVALID", cancellation -> {
            ResumeMasterEntity master = requireMaster(current.accountId(), conversation.masterId());
            if (changeSets.isChangeRequest(text)) {
                GeneratedChange generated = changeSets.generate(current.accountId(), conversationId, text,
                        chatPrompt(conversation, master, prepared.user().id()));
                return new GeneratedReply<>(changeAnswer(generated), generated.model(), generated.inputTokens(),
                        generated.outputTokens(), assistant -> changeSets.persist(current.accountId(), conversationId,
                                assistant.id(), generated));
            }
            Map<String, JsonNode> options = Map.of(
                    "temperature", mapper.getNodeFactory().numberNode(0.2),
                    "max_tokens", mapper.getNodeFactory().numberNode(1200));
            Response response = gateway.execute(current.accountId(), new Request(defaultModel,
                    List.of(new Message("system", CHAT_SYSTEM_PROMPT),
                            new Message("user", chatPrompt(conversation, master, prepared.user().id()))), false, options));
            String answer = response.text() == null ? "" : response.text().trim();
            if (answer.isEmpty() || answer.length() > 12_000) {
                throw new AiGatewayException("AI returned empty or oversized text", false, false);
            }
            long inputTokens = response.usage() == null ? 0 : response.usage().inputTokens();
            long outputTokens = response.usage() == null ? 0 : response.usage().outputTokens();
            return new GeneratedReply<>(answer, blankTo(response.model(), defaultModel), inputTokens, outputTokens, null);
        });
        if (result.failure() != null) {
            RuntimeException exception = result.failure();
            if (exception instanceof AppException app) throw app;
            if (exception instanceof AiGatewayException) {
                throw AppException.dependency("AI_MODEL_FAILED", "AI 模型调用失败，正式简历没有变化，额度已返还");
            }
            throw AppException.dependency("AI_RESPONSE_INVALID", "AI 回复无法处理，正式简历没有变化，额度已返还");
        }
        return view(current, requireConversation(current.accountId(), conversationId));
    }

    public void respondStreaming(CurrentAccount current, String conversationId, String clientMessageId,
            String text, StreamEventSink sink) {
        String requestId = clientMessageId == null || clientMessageId.isBlank()
                ? Ids.newId() : clientMessageId.trim();
        StreamPreparation prepared;
        try {
            prepared = prepareStreaming(current, conversationId, requestId, text);
        } catch (RuntimeException exception) {
            String errorCode = exception instanceof AppException app ? app.reason() : "AI_REQUEST_REJECTED";
            sink.emit("request.failed", Map.of("requestId", requestId, "errorCode", errorCode,
                    "message", safeStreamError(exception, "消息无法提交，请稍后重试")));
            return;
        }
        if (prepared == null) {
            sink.emit("request.failed", Map.of("requestId", requestId, "errorCode", "AI_REQUEST_REJECTED",
                    "message", "消息无法提交，请稍后重试"));
            return;
        }
        PreparedRequest admitted = prepared.generation();
        sink.emit("user.accepted", Map.of("requestId", requestId, "message", admitted.user()));
        GenerationResult<ChangeSetView> result = generation.execute(admitted, "AI_MODEL_FAILED", cancellation -> {
            sink.emit("assistant.started", Map.of("requestId", requestId));
            if (changeSets.isChangeRequest(admitted.user().content())) {
                sink.emit("assistant.progress", Map.of("requestId", requestId,
                        "stage", "LOCATING_FIELDS", "message", "正在定位需要修改的简历字段"));
                GeneratedChange generated = changeSets.generate(current.accountId(), conversationId,
                        admitted.user().content(), prepared.request().messages().get(1).content());
                sink.emit("assistant.progress", Map.of("requestId", requestId,
                        "stage", "VALIDATING_FACTS", "message", "正在核对事实来源和内容质量"));
                if (!cancellation.requested()) {
                    sink.emit("assistant.progress", Map.of("requestId", requestId,
                            "stage", "BUILDING_CHANGES", "message", "正在生成逐条待确认修改"));
                }
                return new GeneratedReply<>(changeAnswer(generated), generated.model(), generated.inputTokens(),
                        generated.outputTokens(), message -> changeSets.persist(current.accountId(), conversationId,
                                message.id(), generated));
            }
            Response response = gateway.executeStreaming(current.accountId(), prepared.request(), delta -> {
                if (!cancellation.requested()) {
                    sink.emit("assistant.delta", Map.of("requestId", requestId, "delta", delta));
                }
            });
            String answer = response.text() == null ? "" : response.text().trim();
            if (answer.isEmpty() || answer.length() > 12_000) {
                throw new AiGatewayException("AI returned empty or oversized text", false, true);
            }
            long inputTokens = response.usage() == null ? 0 : response.usage().inputTokens();
            long outputTokens = response.usage() == null ? 0 : response.usage().outputTokens();
            String model = blankTo(response.model(), defaultModel);
            return new GeneratedReply<>(answer, model, inputTokens, outputTokens, null);
        });
        if (result.failure() != null) {
            RuntimeException exception = result.failure();
            String errorCode = exception instanceof AppException app ? app.reason() : "AI_MODEL_FAILED";
            if (result.rejected()) {
                sink.emit("request.failed", Map.of("requestId", requestId, "errorCode", errorCode,
                        "message", safeStreamError(exception, "AI 请求无法开始，请稍后重试")));
                return;
            }
            sink.emit("assistant.failed", Map.of("requestId", requestId, "message", result.message(),
                    "errorCode", errorCode,
                    "detail", safeStreamError(exception, "AI 模型调用失败，额度已返还")));
            return;
        }
        String eventType = "CANCELLED".equals(result.message().status())
                && !result.replayed() ? "assistant.cancelled" : "assistant.completed";
        sink.emit(eventType, Map.of("requestId", requestId, "message", result.message()));
        if (result.payload() != null) {
            sink.emit("change-set.created", Map.of("requestId", requestId, "changeSet", result.payload()));
        }
    }

    private StreamPreparation prepareStreaming(CurrentAccount current, String conversationId, String requestId,
            String text) {
        assertSeeker(current);
        ConversationRow conversation = requireConversation(current.accountId(), conversationId);
        if (!"GRANTED".equals(consent(current.accountId()).status())) {
            throw AppException.conflict("AI_CONSENT_REQUIRED", "请先阅读并同意 AI 简历授权说明");
        }
        ResumeAiCandidateService.Availability availability = aiCandidates.availability(current, defaultModel);
        if (!enabled || !availability.available()) {
            throw AppException.conflict(availability.reason() == null ? "AI_CHANNEL_UNAVAILABLE" : availability.reason(),
                    "没有可用 AI 通道，当前不会生成伪 AI 回复");
        }
        PreparedRequest admitted = generation.prepare(current.accountId(), conversationId, requestId, text);
        if (admitted.existingAssistant() != null) return new StreamPreparation(admitted, null);
        ResumeMasterEntity master = requireMaster(current.accountId(), conversation.masterId());
        Map<String, JsonNode> options = Map.of(
                "temperature", mapper.getNodeFactory().numberNode(0.2),
                "max_tokens", mapper.getNodeFactory().numberNode(1200),
                "stream_options", mapper.createObjectNode().put("include_usage", true));
        Request request = new Request(defaultModel,
                List.of(new Message("system", CHAT_SYSTEM_PROMPT),
                        new Message("user", chatPrompt(conversation, master, admitted.user().id()))), true, options);
        return new StreamPreparation(admitted, request);
    }

    private static String safeStreamError(RuntimeException exception, String fallback) {
        if (exception instanceof AppException app && app.getMessage() != null && !app.getMessage().isBlank()) {
            return app.getMessage();
        }
        return fallback;
    }

    private static String changeAnswer(GeneratedChange generated) {
        String answer = generated.assistantText() == null ? "" : generated.assistantText().trim();
        if (!generated.clarificationQuestions().isEmpty()) {
            String questions = generated.clarificationQuestions().stream()
                    .map(value -> "• " + value.trim())
                    .collect(java.util.stream.Collectors.joining("\n"));
            answer = answer.isBlank() ? questions : answer + "\n" + questions;
        }
        return answer.isBlank() ? "已生成逐条修改，请确认后写入简历。" : answer;
    }

    @Transactional(readOnly = true)
    public CancellationView cancelResponse(CurrentAccount current, String conversationId, String requestId) {
        assertSeeker(current);
        requireConversation(current.accountId(), conversationId);
        return generation.cancel(current.accountId(), conversationId, requestId);
    }

    @Transactional
    public ConsentView grantConsent(CurrentAccount current) {
        assertSeeker(current);
        Instant now = clock.now();
        int updated = jdbc.update("UPDATE ai_user_consents SET policy_version=?,status='GRANTED',granted_at=?,revoked_at=NULL,updated_at=? WHERE account_id=? AND consent_type=?",
                POLICY_VERSION, now, now, current.accountId(), CONSENT_TYPE);
        if (updated == 0) {
            jdbc.update("INSERT INTO ai_user_consents(id,account_id,consent_type,policy_version,status,granted_at,revoked_at,updated_at) VALUES(?,?,?,?,'GRANTED',?,NULL,?)",
                    Ids.newId(), current.accountId(), CONSENT_TYPE, POLICY_VERSION, now, now);
        }
        return consent(current.accountId());
    }

    @Transactional
    public ConsentView revokeConsent(CurrentAccount current) {
        assertSeeker(current);
        Instant now = clock.now();
        jdbc.update("UPDATE ai_user_consents SET status='REVOKED',revoked_at=?,updated_at=? WHERE account_id=? AND consent_type=?",
                now, now, current.accountId(), CONSENT_TYPE);
        return consent(current.accountId());
    }

    @Transactional(readOnly = true)
    public WritingPreferenceView writingPreference(CurrentAccount current, String conversationId) {
        assertSeeker(current);
        ConversationRow conversation = requireConversation(current.accountId(), conversationId);
        return writingPreference(conversation);
    }

    @Transactional
    public WritingPreferenceView setWritingPreference(CurrentAccount current, String conversationId, String styleCode) {
        assertSeeker(current);
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        String code = styleCode == null ? "" : styleCode.trim().toUpperCase(Locale.ROOT);
        String label = WRITING_STYLES.get(code);
        if (label == null) {
            throw AppException.user("AI_WRITING_STYLE_INVALID", "写作风格不在受控选项中");
        }
        Instant now = clock.now();
        int updated = jdbc.update("UPDATE ai_resume_preferences SET preference_value=?,source='USER',updated_at=? WHERE account_id=? AND conversation_id=? AND preference_key='WRITING_STYLE'",
                code, now, current.accountId(), conversationId);
        if (updated == 0) {
            jdbc.update("INSERT INTO ai_resume_preferences(id,account_id,conversation_id,preference_key,preference_value,source,created_at,updated_at) VALUES(?,?,?,'WRITING_STYLE',?,'USER',?,?)",
                    Ids.newId(), current.accountId(), conversationId, code, now, now);
        }
        jdbc.update("UPDATE ai_resume_conversations SET version_no=version_no+1,updated_at=? WHERE id=?", now,
                conversation.id());
        audit.append(current.accountId(), "AI_RESUME_WRITING_STYLE_UPDATED", "AI_RESUME_CONVERSATION",
                conversationId, "style=" + code);
        return new WritingPreferenceView(code, label, "USER", now);
    }

    @Transactional(readOnly = true)
    public CareerEvidencePreferenceView careerEvidencePreference(CurrentAccount current, String conversationId) {
        assertSeeker(current);
        ConversationRow conversation = requireConversation(current.accountId(), conversationId);
        return careerEvidencePreference(conversation);
    }

    @Transactional
    public CareerEvidencePreferenceView setCareerEvidencePreference(CurrentAccount current, String conversationId,
            boolean enabled) {
        assertSeeker(current);
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        int snapshot = careerLibrary.aiContextRead(current.accountId()).snapshotVersion();
        Instant now = clock.now();
        String value = enabled ? "true" : "false";
        int updated = jdbc.update("UPDATE ai_resume_preferences SET preference_value=?,source='USER',updated_at=? WHERE account_id=? AND conversation_id=? AND preference_key='CAREER_LIBRARY_EVIDENCE'",
                value, now, current.accountId(), conversationId);
        if (updated == 0) {
            jdbc.update("INSERT INTO ai_resume_preferences(id,account_id,conversation_id,preference_key,preference_value,source,created_at,updated_at) VALUES(?,?,?,'CAREER_LIBRARY_EVIDENCE',?,'USER',?,?)",
                    Ids.newId(), current.accountId(), conversationId, value, now, now);
        }
        jdbc.update("UPDATE ai_resume_conversations SET version_no=version_no+1,updated_at=? WHERE id=?", now, conversationId);
        Event event = appendEvent(current.accountId(), conversationId, "career-library-evidence.preference",
                Map.of("enabled", enabled, "snapshotVersion", snapshot), now);
        sse.publish(event);
        audit.append(current.accountId(), "AI_CAREER_LIBRARY_EVIDENCE_CHANGED", "AI_RESUME_CONVERSATION",
                conversationId, "enabled=" + enabled + " snapshotVersion=" + snapshot);
        return new CareerEvidencePreferenceView(enabled, snapshot, "USER", now);
    }

    @Transactional
    public HistoryDeletionView deleteAiHistory(CurrentAccount current, String conversationId) {
        assertSeeker(current);
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        Instant now = clock.now();
        int messageBodies = jdbc.update("UPDATE ai_resume_messages SET content_text=NULL,response_hash=NULL WHERE account_id=? AND conversation_id=? AND content_text IS NOT NULL",
                current.accountId(), conversationId);
        int pendingCandidates = jdbc.update("DELETE FROM resume_candidates WHERE account_id=? AND master_id=? AND status='PENDING'",
                current.accountId(), conversation.masterId());
        List<String> pendingChangeSets = jdbc.query("SELECT DISTINCT s.id FROM ai_resume_change_sets s JOIN ai_resume_change_items i ON i.change_set_id=s.id WHERE s.account_id=? AND s.conversation_id=? AND i.status='PENDING'",
                (rs, n) -> rs.getString(1), current.accountId(), conversationId);
        int pendingChanges = 0;
        for (String setId : pendingChangeSets) {
            pendingChanges += jdbc.update("UPDATE ai_resume_change_items SET before_value_json='\"\"',proposed_value_json='\"\"',corrected_value_json=NULL,reason_text='正文已由用户删除',source_facts_json='[]',quality_json='{}',status='REJECTED',version_no=version_no+1,decided_at=?,updated_at=? WHERE change_set_id=? AND status='PENDING'",
                    now, now, setId);
            jdbc.update("UPDATE ai_resume_change_sets SET status='REJECTED',fact_manifest_json='{}',summary_text='正文已由用户删除',version_no=version_no+1,updated_at=? WHERE id=?",
                    now, setId);
        }
        jdbc.update("UPDATE ai_resume_conversations SET summary_json=NULL,version_no=version_no+1,updated_at=? WHERE id=?",
                now, conversationId);
        if (pendingCandidates > 0) {
            jdbc.update("UPDATE resume_masters SET status=CASE WHEN status='PENDING_CONFIRMATION' THEN 'DRAFT' ELSE status END,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                    now, conversation.masterId(), current.accountId());
        }
        int pendingRecords = pendingCandidates + pendingChanges;
        audit.append(current.accountId(), "AI_RESUME_HISTORY_REDACTED", "AI_RESUME_CONVERSATION",
                conversationId, "messageBodies=" + messageBodies + " pendingRecords=" + pendingRecords);
        Event event = appendEvent(current.accountId(), conversationId, "history.redacted",
                Map.of("messageBodiesDeleted", messageBodies, "pendingCandidatesDeleted", pendingRecords), now);
        sse.publish(event);
        return new HistoryDeletionView(messageBodies, pendingRecords, "AI_RESUME_HISTORY_REDACTED", now);
    }

    @Transactional
    public TextImportView importText(CurrentAccount current, String conversationId, String text) {
        assertSeeker(current);
        requireConversation(current.accountId(), conversationId);
        throw AppException.user("AI_TEXT_IMPORT_MOVED_TO_CHAT",
                "请直接在 AI 对话框粘贴简历文本并说明希望修改的模块，系统会生成逐条待确认修改");
    }

    @Transactional
    public ConversationView uploadPhoto(CurrentAccount current, String conversationId, String filename,
            byte[] contentBytes) {
        assertSeeker(current);
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        FileView uploaded = files.uploadResumePhoto(current, filename, contentBytes);
        ResumeMasterEntity master = requireMaster(current.accountId(), conversation.masterId());
        Map<String, Object> content = content(master);
        content.put("photoFileId", uploaded.id());
        Instant now = clock.now();
        master.setContentSchemaVersion(SCHEMA);
        master.setContentJson(json(content));
        master.setVersionNo(master.getVersionNo() + 1);
        master.setUpdatedAt(now);
        masters.save(master);
        String revisionId = createRevision(current.accountId(), master, conversation.activeBranchId(),
                "PHOTO_CONFIRMED", uploaded.id(), now);
        jdbc.update("UPDATE ai_resume_conversations SET version_no=version_no+1,updated_at=? WHERE id=?",
                now, conversationId);
        Event event = appendEvent(current.accountId(), conversationId, "photo.confirmed",
                Map.of("fileId", uploaded.id(), "revisionId", revisionId), now);
        sse.publish(event);
        return view(current, requireConversation(current.accountId(), conversationId));
    }

    @Transactional
    public ConversationView removePhoto(CurrentAccount current, String conversationId) {
        assertSeeker(current);
        ConversationRow conversation = requireConversationForUpdate(current.accountId(), conversationId);
        ResumeMasterEntity master = requireMaster(current.accountId(), conversation.masterId());
        Map<String, Object> content = content(master);
        String previousFileId = String.valueOf(content.getOrDefault("photoFileId", ""));
        if (previousFileId.isBlank() || "null".equals(previousFileId)) {
            return view(current, conversation);
        }
        content.put("photoFileId", null);
        Instant now = clock.now();
        master.setContentSchemaVersion(SCHEMA);
        master.setContentJson(json(content));
        master.setVersionNo(master.getVersionNo() + 1);
        master.setUpdatedAt(now);
        masters.save(master);
        String revisionId = createRevision(current.accountId(), master, conversation.activeBranchId(),
                "PHOTO_REMOVED", previousFileId, now);
        jdbc.update("UPDATE ai_resume_conversations SET version_no=version_no+1,updated_at=? WHERE id=?",
                now, conversationId);
        Event event = appendEvent(current.accountId(), conversationId, "photo.removed",
                Map.of("revisionId", revisionId), now);
        sse.publish(event);
        return view(current, requireConversation(current.accountId(), conversationId));
    }

    private String chatPrompt(ConversationRow conversation, ResumeMasterEntity master, String currentMessageId) {
        Map<String, Object> confirmed = content(master);
        Map<String, Object> facts = new LinkedHashMap<>();
        for (String key : List.of("intentions", "summary", "education", "experiences", "projects",
                "organizations", "skills", "certificates", "honors", "languages", "evidence")) {
            facts.put(key, confirmed.get(key));
        }
        Map<String, Object> branch = jdbc.query("SELECT id,branch_type,title,language_code,job_version_id,current_revision_id FROM resume_branches WHERE id=? AND account_id=? AND master_id=?",
                (rs, n) -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", rs.getString("id"));
                    value.put("type", rs.getString("branch_type"));
                    value.put("title", rs.getString("title"));
                    value.put("language", rs.getString("language_code"));
                    value.put("jobVersionId", rs.getString("job_version_id"));
                    value.put("revisionId", rs.getString("current_revision_id"));
                    return value;
                }, conversation.activeBranchId(), conversation.accountId(), conversation.masterId()).stream()
                .findFirst().orElse(Map.of());
        List<Map<String, Object>> recentMessages = jdbc.query("SELECT id,sequence_no,role,content_text FROM ai_resume_messages WHERE account_id=? AND conversation_id=? AND status='COMPLETED' AND content_text IS NOT NULL ORDER BY sequence_no DESC LIMIT 20",
                (rs, n) -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", rs.getString("id"));
                    value.put("sequence", rs.getLong("sequence_no"));
                    value.put("role", rs.getString("role"));
                    value.put("content", rs.getString("content_text"));
                    return value;
                }, conversation.accountId(), conversation.id());
        java.util.Collections.reverse(recentMessages);
        String summaryJson = jdbc.queryForObject("SELECT summary_json FROM ai_resume_conversations WHERE id=?",
                String.class, conversation.id());
        JsonNode summary = readNode(summaryJson);
        WritingPreferenceView preference = writingPreference(conversation);
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("confirmedResumeFacts", facts);
        prompt.put("activeBranch", branch);
        prompt.put("writingStyle", Map.of("code", preference.code(), "label", preference.label()));
        prompt.put("recentMessages", recentMessages);
        prompt.put("traceableSummary", summary);
        CareerEvidencePreferenceView careerPreference = careerEvidencePreference(conversation);
        if (careerPreference.enabled()) {
            String question = jdbc.query("SELECT content_text FROM ai_resume_messages WHERE id=? AND account_id=? AND conversation_id=?",
                    (rs, n) -> rs.getString(1), currentMessageId, conversation.accountId(), conversation.id())
                    .stream().findFirst().orElse("");
            EvidenceSnapshot evidence = careerLibrary.evidenceSnapshot(conversation.accountId(), question);
            prompt.put("careerLibraryEvidence", Map.of(
                    "snapshotVersion", evidence.snapshotVersion(),
                    "confirmedStructuredSources", evidence.sources(),
                    "notice", "Only confirmed structured facts are included. Original files and previews are excluded."));
        }
        prompt.put("currentMessageId", currentMessageId);
        return json(prompt);
    }

    private WritingPreferenceView writingPreference(ConversationRow conversation) {
        return jdbc.query("SELECT preference_value,source,updated_at FROM ai_resume_preferences WHERE account_id=? AND conversation_id=? AND preference_key='WRITING_STYLE'",
                (rs, n) -> {
                    String code = rs.getString("preference_value");
                    return new WritingPreferenceView(code, WRITING_STYLES.getOrDefault(code, code),
                            rs.getString("source"), rs.getTimestamp("updated_at").toInstant());
                }, conversation.accountId(), conversation.id()).stream().findFirst()
                .orElse(new WritingPreferenceView("SYSTEM_RECOMMENDED",
                        WRITING_STYLES.get("SYSTEM_RECOMMENDED"), "SYSTEM", null));
    }

    private CareerEvidencePreferenceView careerEvidencePreference(ConversationRow conversation) {
        int snapshot = careerLibrary.aiContextRead(conversation.accountId()).snapshotVersion();
        return jdbc.query("SELECT preference_value,source,updated_at FROM ai_resume_preferences WHERE account_id=? AND conversation_id=? AND preference_key='CAREER_LIBRARY_EVIDENCE'",
                (rs, n) -> new CareerEvidencePreferenceView(Boolean.parseBoolean(rs.getString("preference_value")),
                        snapshot, rs.getString("source"), rs.getTimestamp("updated_at").toInstant()),
                conversation.accountId(), conversation.id()).stream().findFirst()
                .orElse(new CareerEvidencePreferenceView(false, snapshot, "SYSTEM", null));
    }

    public ConsentView consent(String accountId) {
        return jdbc.query("SELECT policy_version,status,granted_at,revoked_at FROM ai_user_consents WHERE account_id=? AND consent_type=?",
                (rs, n) -> new ConsentView(rs.getString("status"), rs.getString("policy_version"),
                        instant(rs.getTimestamp("granted_at")), instant(rs.getTimestamp("revoked_at"))),
                accountId, CONSENT_TYPE).stream().findFirst()
                .orElse(new ConsentView("REQUIRED", POLICY_VERSION, null, null));
    }

    private ConversationView ensure(CurrentAccount current, String masterId, String identityType) {
        ConversationRow found = jdbc.query("SELECT * FROM ai_resume_conversations WHERE master_id=?",
                (rs, n) -> conversation(rs), masterId).stream().findFirst().orElse(null);
        if (found != null) {
            if (!current.accountId().equals(found.accountId())) {
                throw AppException.forbidden("OBJECT_FORBIDDEN", "不能访问他人的 AI 简历会话");
            }
            return view(current, found);
        }
        ResumeMasterEntity master = requireMaster(current.accountId(), masterId);
        Instant now = clock.now();
        LayoutView layout = ensureDefaultLayout(current, masterId, identityType);
        Map<String, Object> content = content(master);
        master.setContentSchemaVersion(SCHEMA);
        master.setContentJson(json(content));
        masters.save(master);
        String branchId = Ids.newId();
        jdbc.update("INSERT INTO resume_branches(id,account_id,master_id,parent_branch_id,branch_type,title,language_code,job_version_id,current_revision_id,source_revision_id,status,version_no,created_at,updated_at,archived_at) VALUES(?,?,?,NULL,'BASE',?,'zh-CN',NULL,NULL,NULL,'ACTIVE',0,?,?,NULL)",
                branchId, current.accountId(), masterId, master.getTitle(), now, now);
        String revisionId = createRevision(current.accountId(), master, branchId, "WORKBENCH_CREATED", null,
                layout.id(), now);
        String conversationId = Ids.newId();
        jdbc.update("INSERT INTO ai_resume_conversations(id,account_id,master_id,active_branch_id,status,onboarding_stage,identity_type,last_sequence,summary_json,version_no,created_at,updated_at,archived_at) VALUES(?,?,?,?,'ACTIVE','COLLECTING_FACTS',?,0,?,0,?,?,NULL)",
                conversationId, current.accountId(), masterId, branchId, identityType,
                json(traceableSummary(content, "COLLECTING_FACTS", revisionId, branchId)), now, now);
        ConversationRow createdConversation = requireConversation(current.accountId(), conversationId);
        TemplateDetailView initialTemplate = resumeTemplates.detail(current, layout.templateId());
        DesignPreferenceView initialDesign = ensureDesignPreference(current, createdConversation, initialTemplate, layout);
        jdbc.update("UPDATE resume_layout_instances SET branch_id=?,design_schema_version=?,design_json=?,updated_at=? WHERE id=?",
                branchId, designSchema(initialTemplate), json(initialDesign.settings()), now, layout.id());
        insertInitialCards(current.accountId(), conversationId, identityType, now);
        Event event = appendEvent(current.accountId(), conversationId, "conversation.created",
                Map.of("masterId", masterId, "branchId", branchId, "revisionId", revisionId), now);
        sse.publish(event);
        return view(current, requireConversation(current.accountId(), conversationId));
    }

    private ConversationView view(CurrentAccount current, ConversationRow conversation) {
        ResumeService.MasterView master = resumes.get(current, conversation.masterId());
        PhotoView photo = photo(requireMaster(current.accountId(), conversation.masterId()));
        List<CardView> cards = jdbc.query("SELECT * FROM ai_resume_cards WHERE conversation_id=? ORDER BY created_at,id",
                (rs, n) -> cardView(rs), conversation.id());
        List<AiResumeMessageView> ordered = generation.recentMessages(current.accountId(), conversation.id());
        QuotaView quotaView = quota.current(current.accountId());
        ResumeAiCandidateService.Availability availability;
        try {
            availability = aiCandidates.availability(current, null);
        } catch (RuntimeException exception) {
            availability = new ResumeAiCandidateService.Availability(false, "", "AI_CHANNEL_UNAVAILABLE");
        }
        LayoutView layout = currentLayout(current, conversation.masterId());
        SmartTemplateView activeTemplate = layout == null ? null
                : smartTemplateView(current, conversation, resumeTemplates.detail(current, layout.templateId()));
        DesignPreferenceView activeDesign = activeTemplate == null ? null : activeTemplate.design();
        CareerEvidencePreferenceView careerEvidence = careerEvidencePreference(conversation);
        JsonNode canonicalContent = mapper.valueToTree(content(requireMaster(current.accountId(), conversation.masterId())));
        List<ChangeSetView> inlineChanges = changeSets.list(current.accountId(), conversation.id());
        return new ConversationView(conversation.id(), conversation.masterId(), conversation.activeBranchId(),
                conversation.status(), conversation.onboardingStage(), conversation.identityType(),
                conversation.lastSequence(), conversation.versionNo(), master, canonicalContent,
                layout, activeTemplate, activeDesign, photo, cards, List.copyOf(ordered), inlineChanges,
                quotaView, consent(current.accountId()), careerEvidence,
                enabled && availability.available(), availability.reason(),
                conversation.createdAt(), conversation.updatedAt());
    }

    private PhotoView photo(ResumeMasterEntity master) {
        Object value = content(master).get("photoFileId");
        if (value == null || String.valueOf(value).isBlank() || "null".equals(String.valueOf(value))) return null;
        String fileId = String.valueOf(value);
        return new PhotoView(fileId, "/api/v1/files/" + fileId + "/content");
    }

    private void insertInitialCards(String accountId, String conversationId, String identity, Instant now) {
        insertCard(accountId, conversationId, "IDENTITY", "CONFIRMED",
                Map.of("identityType", identity == null ? "PROFESSIONAL" : identity), now);
        for (String type : CARD_TYPES) {
            insertCard(accountId, conversationId, type, "IDLE", initialPayload(type), now.plusMillis(CARD_TYPES.indexOf(type) + 1));
        }
    }

    private void insertCard(String accountId, String conversationId, String type, String status,
            Map<String, Object> payload, Instant now) {
        jdbc.update("INSERT INTO ai_resume_cards(id,account_id,conversation_id,message_id,card_type,schema_version,status,payload_json,validation_json,version_no,created_at,updated_at) VALUES(?,?,?,NULL,?,'card-v1',?,?,NULL,0,?,?)",
                Ids.newId(), accountId, conversationId, type, status, json(payload), now, now);
    }

    private static Map<String, Object> initialPayload(String type) {
        return switch (type) {
            case "TARGET_JOB" -> Map.of("targetJob", "", "taxonomyNodeId", "");
            case "CONTACT" -> Map.of("name", "", "email", "", "phone", "", "location", "", "links", List.of());
            case "EDUCATION", "EXPERIENCE", "PROJECTS", "ORGANIZATIONS", "SKILLS", "CERTIFICATES",
                    "HONORS", "LANGUAGES" -> Map.of("items", List.of());
            default -> Map.of("text", "");
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> traceableSummary(Map<String, Object> content, String stage,
            String revisionId, String branchId) {
        List<String> modules = new ArrayList<>();
        for (String key : List.of("intentions", "summary", "education", "experiences", "projects",
                "organizations", "skills", "certificates", "honors", "languages", "evidence")) {
            Object value = content.get(key);
            boolean present = value instanceof Map<?, ?> map ? !map.isEmpty()
                    : value instanceof List<?> list ? !list.isEmpty()
                    : value != null && !String.valueOf(value).isBlank();
            if (present) modules.add(key);
        }
        Map<String, Object> intentions = content.get("intentions") instanceof Map<?, ?> value
                ? (Map<String, Object>) value : Map.of();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", "ai-resume-summary-v1");
        summary.put("branchId", branchId);
        summary.put("latestRevisionId", revisionId);
        summary.put("onboardingStage", stage);
        summary.put("targetJob", String.valueOf(intentions.getOrDefault("targetJob", "")));
        summary.put("confirmedModules", modules);
        return summary;
    }

    private Map<String, Object> content(ResumeMasterEntity master) {
        if (master.getContentJson() != null && !master.getContentJson().isBlank()) {
            try {
                Map<String, Object> parsed = mapper.readValue(
                        master.getContentJson(), new TypeReference<LinkedHashMap<String, Object>>() {});
                return normalizeContent(parsed);
            } catch (Exception ignored) {
                // Fall through to a deterministic legacy projection.
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", SCHEMA);
        result.put("basics", new LinkedHashMap<>());
        result.put("intentions", new LinkedHashMap<>());
        result.put("summary", nullToEmpty(master.getSelfIntro()));
        result.put("education", legacyItems(master.getEducationJson()));
        result.put("experiences", legacyItems(master.getExperienceJson()));
        result.put("projects", legacyItems(master.getProjectsJson()));
        result.put("organizations", List.of());
        result.put("skills", legacyItems(master.getSkillsJson()));
        result.put("certificates", legacyItems(master.getCertificatesJson()));
        result.put("honors", List.of());
        result.put("languages", List.of());
        result.put("evidence", readList(master.getKeyOutcomesJson()));
        result.put("photoFileId", null);
        return result;
    }

    private static Map<String, Object> normalizeContent(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source == null ? Map.of() : source);
        result.put("schemaVersion", SCHEMA);
        if (!(result.get("basics") instanceof Map<?, ?>)) result.put("basics", new LinkedHashMap<>());
        if (!(result.get("intentions") instanceof Map<?, ?>)) result.put("intentions", new LinkedHashMap<>());
        if (result.get("summary") == null) result.put("summary", "");
        for (String key : List.of("education", "experiences", "projects", "organizations", "skills",
                "certificates", "honors", "languages", "evidence")) {
            Object value = result.get(key);
            if (!(value instanceof List<?>)) result.put(key, legacyItems(value));
        }
        if (!result.containsKey("photoFileId")) result.put("photoFileId", null);
        return result;
    }

    private static List<Object> legacyItems(Object value) {
        if (value instanceof List<?> list) return new ArrayList<>(list);
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text) || "[]".equals(text)) return List.of();
        return List.of(Map.of("description", text));
    }

    @SuppressWarnings("unchecked")
    private void apply(String cardType, JsonNode payload, ResumeMasterEntity master, Map<String, Object> content) {
        String text = payload.path("text").asText("").trim();
        String projected = STRUCTURED_LIST_CARDS.contains(cardType) ? structuredText(cardType, payload) : text;
        Object canonical = STRUCTURED_LIST_CARDS.contains(cardType)
                ? payload.path("items").isArray()
                        ? mapper.convertValue(payload.path("items"), List.class)
                        : legacyItems(projected)
                : projected;
        switch (cardType) {
            case "TARGET_JOB" -> content.put("intentions", mapper.convertValue(payload, Map.class));
            case "CONTACT" -> content.put("basics", mapper.convertValue(payload, Map.class));
            case "EDUCATION" -> { content.put("education", canonical); master.setEducationJson(blankToNull(projected)); }
            case "EXPERIENCE" -> { content.put("experiences", canonical); master.setExperienceJson(blankToNull(projected)); }
            case "PROJECTS" -> { content.put("projects", canonical); master.setProjectsJson(blankToNull(projected)); }
            case "ORGANIZATIONS" -> content.put("organizations", canonical);
            case "SKILLS" -> { content.put("skills", canonical); master.setSkillsJson(blankToNull(projected)); }
            case "CERTIFICATES" -> { content.put("certificates", canonical); master.setCertificatesJson(blankToNull(projected)); }
            case "HONORS" -> content.put("honors", canonical);
            case "LANGUAGES" -> content.put("languages", canonical);
            case "SUMMARY" -> { content.put("summary", text); master.setSelfIntro(blankToNull(text)); }
            default -> throw AppException.user("AI_CARD_TYPE_INVALID", "该卡片不能提交到简历主档");
        }
    }

    private static String structuredText(String cardType, JsonNode payload) {
        JsonNode items = payload.path("items");
        if (!items.isArray()) return payload.path("text").asText("").trim();
        List<String> blocks = new ArrayList<>();
        for (JsonNode item : items) {
            String heading = switch (cardType) {
                case "EDUCATION" -> joinNonBlank(" · ", item.path("school").asText(),
                        item.path("major").asText(), item.path("degree").asText());
                case "EXPERIENCE" -> joinNonBlank(" · ", item.path("company").asText(),
                        item.path("role").asText());
                case "PROJECTS" -> joinNonBlank(" · ", item.path("name").asText(),
                        item.path("role").asText());
                case "ORGANIZATIONS" -> joinNonBlank(" · ", item.path("name").asText(),
                        item.path("role").asText());
                case "SKILLS" -> joinNonBlank(" · ", item.path("category").asText(),
                        item.path("name").asText());
                case "CERTIFICATES" -> joinNonBlank(" · ", item.path("name").asText(),
                        item.path("issuer").asText(), item.path("date").asText());
                case "HONORS" -> joinNonBlank(" · ", item.path("name").asText(),
                        item.path("issuer").asText(), item.path("date").asText());
                case "LANGUAGES" -> joinNonBlank(" · ", item.path("language").asText(),
                        item.path("level").asText(), item.path("score").asText());
                default -> "";
            };
            String period = joinNonBlank(" - ", item.path("startDate").asText(),
                    item.path("current").asBoolean(false) ? "至今" : item.path("endDate").asText());
            String meta = joinNonBlank(" · ", period, item.path("location").asText());
            String description = item.path("description").asText("").trim();
            if ("SKILLS".equals(cardType) && item.path("items").isArray()) {
                List<String> values = new ArrayList<>();
                item.path("items").forEach(value -> {
                    if (!value.asText("").isBlank()) values.add(value.asText().trim());
                });
                description = String.join("、", values);
            }
            String block = joinNonBlank("\n", heading, meta, description);
            if (!block.isBlank()) blocks.add(block);
        }
        return String.join("\n\n", blocks);
    }

    private static String joinNonBlank(String separator, String... values) {
        return java.util.Arrays.stream(values).map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.joining(separator));
    }

    private String createRevision(String accountId, ResumeMasterEntity master, String branchId, String source,
            String sourceObjectId, Instant now) {
        return createRevision(accountId, master, branchId, source, sourceObjectId, null, now);
    }

    private String createRevision(String accountId, ResumeMasterEntity master, String branchId, String source,
            String sourceObjectId, String layoutInstanceId, Instant now) {
        int revisionNo = jdbc.queryForObject("SELECT COUNT(*) FROM resume_revisions WHERE branch_id=?",
                Integer.class, branchId) + 1;
        String contentJson = master.getContentJson() == null ? json(content(master)) : master.getContentJson();
        String id = Ids.newId();
        jdbc.update("INSERT INTO resume_revisions(id,account_id,master_id,branch_id,revision_no,source,source_object_id,content_schema_version,content_json,layout_instance_id,content_hash,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                id, accountId, master.getId(), branchId, revisionNo, source, sourceObjectId, SCHEMA, contentJson,
                layoutInstanceId, sha256(contentJson), now);
        jdbc.update("UPDATE resume_branches SET current_revision_id=?,version_no=version_no+1,updated_at=? WHERE id=?",
                id, now, branchId);
        return id;
    }

    private LayoutView ensureDefaultLayout(CurrentAccount current, String masterId, String identityType) {
        LayoutView currentLayout = currentLayout(current, masterId);
        if (currentLayout != null && !"FROZEN".equals(currentLayout.status())) return currentLayout;
        if (currentLayout != null) {
            return resumeTemplates.apply(current, currentLayout.templateId(),
                    new ApplyCommand(masterId, currentLayout.variantCode(), null));
        }
        String templateId = builtInTemplates.defaultTemplateId(identityType);
        TemplateDetailView template = resumeTemplates.detail(current, templateId);
        return resumeTemplates.apply(current, templateId,
                new ApplyCommand(masterId, defaultVariant(template), null));
    }

    private LayoutView ensureEditableLayout(CurrentAccount current, ConversationRow conversation) {
        LayoutView currentLayout = currentLayout(current, conversation.masterId());
        if (currentLayout == null) {
            currentLayout = ensureDefaultLayout(current, conversation.masterId(), conversation.identityType());
        }
        if (!"FROZEN".equals(currentLayout.status())) return currentLayout;

        TemplateDetailView template = resumeTemplates.detail(current, currentLayout.templateId());
        DesignPreferenceView preference = ensureDesignPreference(current, conversation, template, currentLayout);
        LayoutView editable = resumeTemplates.apply(current, currentLayout.templateId(),
                new ApplyCommand(conversation.masterId(), preference.variantCode(), null));
        return resumeTemplates.configureDesign(current, editable.id(), conversation.activeBranchId(),
                preference.settings(), false);
    }

    private LayoutView currentLayout(CurrentAccount current, String masterId) {
        ResumeTemplateService.CurrentLayoutView currentLayout = resumeTemplates.currentLayout(current, masterId);
        return currentLayout.selected() ? currentLayout.layout() : null;
    }

    /** A built-in template id; ids of the retired v3 drafts and legacy codes map to their replacement. */
    private String smartTemplateId(String selector) {
        return builtInTemplates.resolve(selector).orElseThrow(() -> AppException.user(
                "AI_RESUME_TEMPLATE_UNSUPPORTED", "AI 工作台不支持该模板，请从模板列表中选择"));
    }

    private static boolean isHtmlTemplate(TemplateDetailView template) {
        return ResumeLayoutProtocol.isRenderV4(template.rendererProtocol());
    }

    private static String designSchema(TemplateDetailView template) {
        return isHtmlTemplate(template) ? ResumeDesignV2.SCHEMA : ResumeDesignSettings.SCHEMA;
    }

    private ResumeTemplateManifest manifest(TemplateDetailView template) {
        try {
            return ResumeTemplateManifest.parse(mapper, template.layoutDefinitionJson());
        } catch (IllegalArgumentException exception) {
            throw AppException.conflict("RESUME_TEMPLATE_DEFINITION_INVALID", "模板定义无法读取");
        }
    }

    private static String defaultVariant(TemplateDetailView template) {
        if (template.variants() == null || template.variants().isEmpty()) {
            throw AppException.conflict("RESUME_TEMPLATE_VARIANT_MISSING", "智能模板缺少受控设计预设");
        }
        return template.variants().get(0);
    }

    private ResumeLayoutDefinition layoutDefinition(TemplateDetailView template) {
        try {
            return ResumeLayoutProtocol.validate(template.rendererProtocol(),
                    mapper.readValue(template.layoutDefinitionJson(), ResumeLayoutDefinition.class));
        } catch (Exception exception) {
            throw AppException.conflict("RESUME_TEMPLATE_DEFINITION_INVALID", "智能模板定义无法读取");
        }
    }

    private SmartTemplateView smartTemplateView(CurrentAccount current, ConversationRow conversation,
            TemplateDetailView template) {
        DesignPreferenceView design = designPreference(current, conversation, template);
        return new SmartTemplateView(template.template().id(), template.template().displayName(),
                template.template().familyName(), template.template().languageCode(),
                template.template().recommendedPages(), template.template().photoPolicy(),
                template.variants(), template.rendererProtocol(), template.layoutDefinitionJson(),
                template.thumbnailUri(), template.docxAvailable(), template.docxUnavailableReason(),
                designPresets(template), design);
    }

    private List<DesignPresetView> designPresets(TemplateDetailView template) {
        // Built-in templates expose palettes, header variants and fonts in their manifest instead of presets.
        if (isHtmlTemplate(template)) return List.of();
        ResumeLayoutDefinition definition = layoutDefinition(template);
        return template.variants().stream().map(variant -> new DesignPresetView(
                variant,
                presetLabel(variant),
                mapper.valueToTree(ResumeDesignSettings.defaults(definition, variant,
                        template.template().photoPolicy(), template.template().id())))).toList();
    }

    private static String presetLabel(String variant) {
        return switch (variant) {
            case "MONO" -> "经典黑白";
            case "BLUE" -> "清晰蓝调";
            case "GRAY" -> "稳重灰阶";
            case "NO_PHOTO" -> "清爽无照片";
            case "PHOTO" -> "照片分栏";
            case "NO_PHOTO_01" -> "精简咨询";
            case "NO_PHOTO_02" -> "经典咨询";
            case "FINANCE_MINIMAL" -> "金融极简";
            case "BANKING_FORMAL" -> "银行正式";
            case "MARKETING" -> "市场增长";
            case "ECOMMERCE" -> "电商运营";
            case "TEACHER" -> "教师实践";
            case "ACADEMIC" -> "学术研究";
            case "CLASSIC" -> "Classic";
            case "MODERN" -> "Modern";
            case "STANDARD" -> "标准表格";
            case "COMPACT" -> "紧凑表格";
            case "QA" -> "质量测试";
            case "DATA" -> "数据分析";
            default -> variant;
        };
    }

    private DesignPreferenceView designPreference(CurrentAccount current, ConversationRow conversation,
            TemplateDetailView template) {
        DesignPreferenceView existing = findDesignPreference(current, conversation, template);
        if (existing != null && isHtmlTemplate(template)) {
            // Stored settings stay usable when a template revision drops a palette or header variant.
            return new DesignPreferenceView(existing.id(), existing.templateId(), existing.variantCode(),
                    mapper.valueToTree(ResumeDesignV2.coerce(manifest(template), existing.settings())),
                    existing.versionNo(), existing.updatedAt());
        }
        if (existing != null) return existing;
        String variant = defaultVariant(template);
        Object settings = isHtmlTemplate(template)
                ? ResumeDesignV2.defaults(manifest(template))
                : ResumeDesignSettings.defaults(layoutDefinition(template), variant,
                        template.template().photoPolicy(), template.template().id());
        return new DesignPreferenceView(null, template.template().id(), variant,
                mapper.valueToTree(settings), 0, null);
    }

    /**
     * The saved design for this template, created on first use. A built-in template used for the first
     * time inherits the general settings of the layout the user is switching from (DSN-06).
     */
    private DesignPreferenceView ensureDesignPreference(CurrentAccount current, ConversationRow conversation,
            TemplateDetailView template, LayoutView carryFrom) {
        DesignPreferenceView value = designPreference(current, conversation, template);
        if (value.id() != null) return value;
        if (isHtmlTemplate(template) && carryFrom != null && carryFrom.design() != null
                && !template.template().id().equals(carryFrom.templateId())) {
            ResumeTemplateManifest previous = ResumeLayoutProtocol.isRenderV4(carryFrom.rendererProtocol())
                    ? ResumeTemplateManifest.parse(mapper, carryFrom.layoutDefinitionJson()) : null;
            value = new DesignPreferenceView(null, value.templateId(), value.variantCode(),
                    mapper.valueToTree(ResumeDesignV2.carryOver(manifest(template), carryFrom.design(), previous)),
                    0, null);
        }
        Instant now = clock.now();
        String id = Ids.newId();
        jdbc.update("INSERT INTO resume_layout_preferences(id,account_id,master_id,branch_id,template_id,variant_code,design_schema_version,settings_json,version_no,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,0,?,?)",
                id, current.accountId(), conversation.masterId(), conversation.activeBranchId(),
                template.template().id(), value.variantCode(), designSchema(template),
                json(value.settings()), now, now);
        return new DesignPreferenceView(id, value.templateId(), value.variantCode(), value.settings(), 0, now);
    }

    private DesignPreferenceView findDesignPreference(CurrentAccount current, ConversationRow conversation,
            TemplateDetailView template) {
        return jdbc.query("SELECT id,variant_code,settings_json,version_no,updated_at FROM resume_layout_preferences WHERE account_id=? AND branch_id=? AND template_id=?",
                (rs, n) -> new DesignPreferenceView(rs.getString("id"), template.template().id(),
                        rs.getString("variant_code"), readNode(rs.getString("settings_json")),
                        rs.getInt("version_no"), rs.getTimestamp("updated_at").toInstant()),
                current.accountId(), conversation.activeBranchId(), template.template().id())
                .stream().findFirst().orElse(null);
    }

    private Event appendEvent(String accountId, String conversationId, String type, Map<String, Object> payload,
            Instant now) {
        return conversationEvents.append(accountId, conversationId, type, payload, now);
    }

    private void validatePayload(String cardType, JsonNode payload, boolean submitting) {
        if (payload == null || !payload.isObject()) {
            throw AppException.user("AI_CARD_PAYLOAD_INVALID", "卡片内容格式无效");
        }
        rejectSensitiveFields(payload);
        if (json(payload).length() > 50_000) {
            throw AppException.user("AI_CARD_PAYLOAD_TOO_LARGE", "单张卡片内容过长");
        }
        if (!submitting) return;
        if ("TARGET_JOB".equals(cardType) && payload.path("targetJob").asText("").trim().isEmpty()) {
            throw AppException.user("AI_TARGET_JOB_REQUIRED", "请先选择或填写目标岗位");
        }
        if ("TARGET_JOB".equals(cardType) && payload.has("taxonomyNodeId")) {
            validateTaxonomySelection(payload);
        }
        if (("EDUCATION".equals(cardType) || "EXPERIENCE".equals(cardType))
                && structuredText(cardType, payload).isEmpty()) {
            throw AppException.user("AI_CORE_FACT_REQUIRED", "教育或经历卡片不能以空内容提交");
        }
        if ("LANGUAGES".equals(cardType) && structuredText(cardType, payload).isEmpty()) {
            throw AppException.user("AI_LANGUAGE_REQUIRED", "请至少选择一种语言，或使用暂时跳过");
        }
        if ("CONTACT".equals(cardType)) {
            validateContact(payload);
        }
        if ("CERTIFICATES".equals(cardType) || "HONORS".equals(cardType)) {
            validateCredentialDescriptions(cardType, payload);
        }
    }

    private static void validateContact(JsonNode payload) {
        String name = payload.path("name").asText("").trim();
        String email = payload.path("email").asText("").trim();
        String phone = payload.path("phone").asText("").trim();
        if (name.isEmpty()) {
            throw AppException.user("AI_CONTACT_NAME_REQUIRED", "请填写姓名");
        }
        if (email.isEmpty() && phone.isEmpty()) {
            throw AppException.user("AI_CONTACT_METHOD_REQUIRED", "邮箱和手机号请至少填写一项");
        }
        if (!email.isEmpty() && (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw AppException.user("AI_CONTACT_EMAIL_INVALID", "请填写有效的邮箱地址");
        }
        if (!phone.isEmpty()) {
            String digits = phone.replaceAll("\\D", "");
            if (phone.length() > 30 || !phone.matches("^\\+?[0-9\\s().-]+$")
                    || digits.length() < 7 || digits.length() > 15) {
                throw AppException.user("AI_CONTACT_PHONE_INVALID", "请填写有效的手机号，可包含国家或地区代码");
            }
        }
    }

    private static void validateCredentialDescriptions(String cardType, JsonNode payload) {
        JsonNode items = payload.path("items");
        if (!items.isArray()) return;
        String label = "CERTIFICATES".equals(cardType) ? "证书与资质" : "荣誉奖项";
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            boolean populated = java.util.stream.Stream.of("name", "issuer", "date", "description")
                    .map(key -> item.path(key).asText("").trim()).anyMatch(value -> !value.isEmpty());
            if (!populated) continue;
            if (item.path("name").asText("").trim().isEmpty()) {
                throw AppException.user("AI_CREDENTIAL_NAME_REQUIRED",
                        label + "第 " + (index + 1) + " 项必须填写名称");
            }
            if (item.path("issuer").asText("").trim().isEmpty()) {
                throw AppException.user("AI_CREDENTIAL_ISSUER_REQUIRED",
                        label + "第 " + (index + 1) + " 项必须填写"
                                + ("CERTIFICATES".equals(cardType) ? "颁发机构" : "授予机构"));
            }
            int characters = meaningfulCharacters(item.path("description").asText(""));
            if (characters < CREDENTIAL_DESCRIPTION_MIN_CHARACTERS) {
                throw AppException.user("AI_CREDENTIAL_DESCRIPTION_TOO_SHORT",
                        label + "第 " + (index + 1) + " 项补充说明至少需要 "
                                + CREDENTIAL_DESCRIPTION_MIN_CHARACTERS + " 个有效字符，当前为 " + characters + " 个");
            }
        }
    }

    private static int meaningfulCharacters(String value) {
        if (value == null || value.isBlank()) return 0;
        return (int) value.codePoints().filter(Character::isLetterOrDigit).count();
    }

    private void validateTaxonomySelection(JsonNode payload) {
        String nodeId = payload.path("taxonomyNodeId").asText("").trim();
        if (nodeId.isEmpty()) {
            throw AppException.user("AI_TARGET_JOB_TAXONOMY_REQUIRED", "请从岗位库选择一个标准岗位");
        }
        List<Map<String, Object>> matches = jdbc.queryForList("""
                SELECT n.display_name,n.parent_id AS group_id,g.parent_id AS category_id,n.code,n.catalog_occupation_code
                FROM job_taxonomy_nodes n
                JOIN job_taxonomy_nodes g ON g.id=n.parent_id AND g.node_level='GROUP' AND g.status='PUBLISHED'
                JOIN job_taxonomy_nodes c ON c.id=g.parent_id AND c.node_level='CATEGORY' AND c.status='PUBLISHED'
                WHERE n.id=? AND n.node_level='JOB' AND n.status='PUBLISHED'
                """, nodeId);
        if (matches.isEmpty()) {
            throw AppException.user("AI_TARGET_JOB_TAXONOMY_INVALID", "所选岗位已失效，请重新选择");
        }
        Map<String, Object> selected = matches.get(0);
        String displayName = String.valueOf(selected.get("DISPLAY_NAME"));
        if (!displayName.equals(payload.path("targetJob").asText("").trim())) {
            throw AppException.user("AI_TARGET_JOB_TAXONOMY_MISMATCH", "岗位名称与岗位库记录不一致，请重新选择");
        }
        validateTaxonomyPath(payload, "taxonomyGroupId", selected.get("GROUP_ID"));
        validateTaxonomyPath(payload, "taxonomyCategoryId", selected.get("CATEGORY_ID"));
    }

    private static void validateTaxonomyPath(JsonNode payload, String key, Object expected) {
        if (payload.has(key) && !payload.path(key).asText("").isBlank()
                && !String.valueOf(expected).equals(payload.path(key).asText())) {
            throw AppException.user("AI_TARGET_JOB_TAXONOMY_MISMATCH", "岗位层级与岗位库记录不一致，请重新选择");
        }
    }

    private static void rejectSensitiveFields(JsonNode value) {
        if (value == null || value.isValueNode()) return;
        if (value.isArray()) {
            value.forEach(AiResumeWorkbenchService::rejectSensitiveFields);
            return;
        }
        value.fields().forEachRemaining(field -> {
            String key = field.getKey().replace("_", "").toLowerCase(Locale.ROOT);
            if (SENSITIVE_KEYS.contains(key)) {
                throw AppException.user("AI_SENSITIVE_ATTRIBUTE_FORBIDDEN", "简历不保存年龄、性别、婚育或民族信息");
            }
            rejectSensitiveFields(field.getValue());
        });
    }

    @SuppressWarnings("unchecked")
    private static boolean readiness(Map<String, Object> content) {
        Map<String, Object> intentions = content.get("intentions") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        boolean target = !String.valueOf(intentions.getOrDefault("targetJob", "")).isBlank();
        boolean facts = hasContent(content.get("education")) || hasContent(content.get("experiences"));
        return target && facts;
    }

    private static boolean hasContent(Object value) {
        if (value instanceof List<?> list) return !list.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return value != null && !String.valueOf(value).isBlank();
    }

    private ConversationRow requireConversation(String accountId, String id) {
        ConversationRow value = jdbc.query("SELECT * FROM ai_resume_conversations WHERE id=?",
                (rs, n) -> conversation(rs), id).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_CONVERSATION_NOT_FOUND", "AI 简历会话不存在"));
        if (!accountId.equals(value.accountId())) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能访问他人的 AI 简历会话");
        }
        return value;
    }

    private ConversationRow requireConversationForUpdate(String accountId, String id) {
        ConversationRow value = jdbc.query("SELECT * FROM ai_resume_conversations WHERE id=? FOR UPDATE",
                (rs, n) -> conversation(rs), id).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_CONVERSATION_NOT_FOUND", "AI 简历会话不存在"));
        if (!accountId.equals(value.accountId())) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能访问他人的 AI 简历会话");
        }
        return value;
    }

    private CardRow requireCard(String accountId, String conversationId, String id) {
        return jdbc.query("SELECT * FROM ai_resume_cards WHERE id=? AND account_id=? AND conversation_id=?",
                (rs, n) -> new CardRow(rs.getString("id"), rs.getString("card_type"),
                        rs.getString("status"), rs.getString("payload_json"), rs.getInt("version_no")),
                id, accountId, conversationId).stream().findFirst()
                .orElseThrow(() -> AppException.user("AI_CARD_NOT_FOUND", "AI 简历卡片不存在"));
    }

    private ResumeMasterEntity requireMaster(String accountId, String id) {
        ResumeMasterEntity master = masters.findById(id)
                .orElseThrow(() -> AppException.user("RESUME_NOT_FOUND", "简历主档不存在"));
        if (!accountId.equals(master.getAccountId())) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能访问他人的简历");
        }
        return master;
    }

    private ConversationRow conversation(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ConversationRow(rs.getString("id"), rs.getString("account_id"), rs.getString("master_id"),
                rs.getString("active_branch_id"), rs.getString("status"), rs.getString("onboarding_stage"),
                rs.getString("identity_type"), rs.getLong("last_sequence"), rs.getInt("version_no"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private CardView cardView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CardView(rs.getString("id"), rs.getString("card_type"), rs.getString("schema_version"),
                rs.getString("status"), readNode(rs.getString("payload_json")), readNode(rs.getString("validation_json")),
                rs.getInt("version_no"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static String normalizeIdentity(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!IDENTITIES.contains(result)) {
            throw AppException.user("AI_IDENTITY_INVALID", "身份仅支持学生、应届生或职场人士");
        }
        return result;
    }

    private static String defaultTitle(String identity) {
        return switch (identity) {
            case "STUDENT" -> "学生求职简历";
            case "GRADUATE" -> "应届生求职简历";
            default -> "职业简历";
        };
    }

    private static void assertVersion(Integer expected, int actual, String reason) {
        if (expected != null && expected != actual) {
            throw AppException.conflict(reason, "内容已在其他页面更新，请刷新后重试");
        }
    }

    private static void assertSeeker(CurrentAccount current) {
        if (current == null || !"SEEKER".equals(current.role())) {
            throw AppException.forbidden("AI_RESUME_FORBIDDEN", "仅求职者可以使用 AI 简历工作台");
        }
    }

    private JsonNode readNode(String value) {
        if (value == null) return null;
        try { return mapper.readTree(value); } catch (Exception exception) { return null; }
    }

    private Object readList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return mapper.readValue(value, Object.class); } catch (Exception exception) { return List.of(); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
    private static String blankTo(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    private record ConversationRow(String id, String accountId, String masterId, String activeBranchId,
            String status, String onboardingStage, String identityType, long lastSequence, int versionNo,
            Instant createdAt, Instant updatedAt) {}
    private record CardRow(String id, String cardType, String status, String payloadJson, int versionNo) {}
    private record StreamPreparation(PreparedRequest generation, Request request) {}

    @FunctionalInterface
    public interface StreamEventSink {
        void emit(String eventType, Object payload);
    }

    public record CreateConversationCommand(String identityType, String title) {}
    public record ConsentView(String status, String policyVersion, Instant grantedAt, Instant revokedAt) {}
    public record WritingPreferenceView(String code, String label, String source, Instant updatedAt) {}
    public record CareerEvidencePreferenceView(boolean enabled, int snapshotVersion, String source, Instant updatedAt) {}
    public record HistoryDeletionView(int messageBodiesDeleted, int pendingCandidatesDeleted,
            String auditMarker, Instant deletedAt) {}
    public record TextImportView(String parserVersion, boolean aiCalled, int ignoredSensitiveLines,
            int ambiguousLines, List<ResumeService.CandidateView> candidates) {}
    public record PhotoView(String fileId, String contentUrl) {}
    public record DesignPreferenceView(String id, String templateId, String variantCode, JsonNode settings,
            int versionNo, Instant updatedAt) {}
    public record DesignPresetView(String variantCode, String displayName, JsonNode settings) {}
    public record SmartTemplateView(String templateId, String displayName, String familyName,
            String languageCode, String recommendedPages, String photoPolicy, List<String> variants,
            String rendererProtocol, String layoutDefinitionJson, String thumbnailUri,
            boolean docxAvailable, String docxUnavailableReason, List<DesignPresetView> presets,
            DesignPreferenceView design) {}
    public record CardView(String id, String cardType, String schemaVersion, String status, JsonNode payload,
            JsonNode validation, int versionNo, Instant createdAt, Instant updatedAt) {}
    public record ConversationView(String id, String masterId, String activeBranchId, String status,
            String onboardingStage, String identityType, long lastSequence, int versionNo,
            ResumeService.MasterView resume, JsonNode content, LayoutView layout, SmartTemplateView activeTemplate,
            DesignPreferenceView activeDesign, PhotoView photo, List<CardView> cards,
            List<AiResumeMessageView> messages, List<ChangeSetView> changeSets,
            QuotaView quota, ConsentView consent, CareerEvidencePreferenceView careerLibraryEvidence,
            boolean aiAvailable, String aiUnavailableReason,
            Instant createdAt, Instant updatedAt) {}
}
