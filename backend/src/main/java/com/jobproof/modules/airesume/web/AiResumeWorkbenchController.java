package com.jobproof.modules.airesume.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.airesume.application.AiQuotaService;
import com.jobproof.modules.airesume.application.AiGenerationAttemptService.CancellationView;
import com.jobproof.modules.airesume.application.AiResumeSseService;
import com.jobproof.modules.airesume.application.AiResumeDescriptionSuggestionService;
import com.jobproof.modules.airesume.application.AiResumeDescriptionSuggestionService.DescriptionSuggestionView;
import com.jobproof.modules.airesume.application.AiResumeCertificateSuggestionService;
import com.jobproof.modules.airesume.application.AiResumeCertificateSuggestionService.CertificateSuggestionView;
import com.jobproof.modules.airesume.application.AiResumeHonorSuggestionService;
import com.jobproof.modules.airesume.application.AiResumeHonorSuggestionService.HonorSuggestionView;
import com.jobproof.modules.airesume.application.AiResumeCredentialRecommendationService;
import com.jobproof.modules.airesume.application.AiResumeCredentialRecommendationService.CredentialRecommendationView;
import com.jobproof.modules.airesume.application.AiResumeSkillSuggestionService;
import com.jobproof.modules.airesume.application.AiResumeSkillSuggestionService.SkillSuggestionView;
import com.jobproof.modules.airesume.application.AiResumeSummarySuggestionService;
import com.jobproof.modules.airesume.application.AiResumeSummarySuggestionService.SummarySuggestionView;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService;
import com.jobproof.modules.airesume.application.AiResumeChangeSetService;
import com.jobproof.modules.airesume.application.AiResumeChangeSetService.ChangeSetView;
import com.jobproof.modules.airesume.application.AiResumeChangeSetService.DecisionCommand;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService.ConsentView;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService.ConversationView;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService.CreateConversationCommand;
import com.jobproof.modules.airesume.application.AiResumeMessageView;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService.HistoryDeletionView;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService.TextImportView;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService.WritingPreferenceView;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService.CareerEvidencePreferenceView;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService.DesignPreferenceView;
import com.jobproof.modules.airesume.application.AiResumeWorkbenchService.SmartTemplateView;
import com.jobproof.modules.resume.domain.ResumePdfExportMode;
import com.jobproof.modules.task.application.TaskView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.multipart.MultipartFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai-resume")
public class AiResumeWorkbenchController {

    private final AiResumeWorkbenchService workbench;
    private final AiResumeChangeSetService changeSets;
    private final AiResumeDescriptionSuggestionService descriptionSuggestions;
    private final AiResumeCertificateSuggestionService certificateSuggestions;
    private final AiResumeHonorSuggestionService honorSuggestions;
    private final AiResumeCredentialRecommendationService credentialRecommendations;
    private final AiResumeSkillSuggestionService skillSuggestions;
    private final AiResumeSummarySuggestionService summarySuggestions;
    private final AiResumeSseService events;
    private final AiQuotaService quota;
    private final ObjectMapper mapper;

    public AiResumeWorkbenchController(AiResumeWorkbenchService workbench,
            AiResumeChangeSetService changeSets,
            AiResumeDescriptionSuggestionService descriptionSuggestions,
            AiResumeCertificateSuggestionService certificateSuggestions,
            AiResumeHonorSuggestionService honorSuggestions,
            AiResumeCredentialRecommendationService credentialRecommendations,
            AiResumeSkillSuggestionService skillSuggestions,
            AiResumeSummarySuggestionService summarySuggestions, AiResumeSseService events,
            AiQuotaService quota, ObjectMapper mapper) {
        this.workbench = workbench;
        this.changeSets = changeSets;
        this.descriptionSuggestions = descriptionSuggestions;
        this.certificateSuggestions = certificateSuggestions;
        this.honorSuggestions = honorSuggestions;
        this.credentialRecommendations = credentialRecommendations;
        this.skillSuggestions = skillSuggestions;
        this.summarySuggestions = summarySuggestions;
        this.events = events;
        this.quota = quota;
        this.mapper = mapper;
    }

    @PostMapping("/conversations")
    public ApiResponse<ConversationView> create(@Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.ok(workbench.create(SecurityConfig.currentAccount(),
                new CreateConversationCommand(request.identityType(), request.title())));
    }

    @PostMapping("/conversations/for-resume/{masterId}")
    public ApiResponse<ConversationView> ensure(@PathVariable String masterId) {
        return ApiResponse.ok(workbench.ensureForResume(SecurityConfig.currentAccount(), masterId));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<ConversationView> get(@PathVariable String conversationId) {
        return ApiResponse.ok(workbench.get(SecurityConfig.currentAccount(), conversationId));
    }

    @PutMapping("/conversations/{conversationId}/cards/{cardId}/draft")
    public ApiResponse<ConversationView> draft(@PathVariable String conversationId, @PathVariable String cardId,
            @Valid @RequestBody CardRequest request) {
        return ApiResponse.ok(workbench.saveDraft(SecurityConfig.currentAccount(), conversationId, cardId,
                request.payload(), request.expectedVersion()));
    }

    @PostMapping("/conversations/{conversationId}/cards/{cardId}/submit")
    public ApiResponse<ConversationView> submit(@PathVariable String conversationId, @PathVariable String cardId,
            @Valid @RequestBody CardRequest request) {
        return ApiResponse.ok(workbench.submitCard(SecurityConfig.currentAccount(), conversationId, cardId,
                request.payload(), request.expectedVersion()));
    }

    @PostMapping("/conversations/{conversationId}/cards/{cardId}/skip")
    public ApiResponse<ConversationView> skip(@PathVariable String conversationId, @PathVariable String cardId,
            @Valid @RequestBody SkipCardRequest request) {
        return ApiResponse.ok(workbench.skipCard(SecurityConfig.currentAccount(), conversationId, cardId,
                request.expectedVersion()));
    }

    @PostMapping("/conversations/{conversationId}/cards/{cardId}/records/{recordIndex}/description-suggestion")
    public ApiResponse<DescriptionSuggestionView> descriptionSuggestion(@PathVariable String conversationId,
            @PathVariable String cardId, @PathVariable int recordIndex,
            @Valid @RequestBody DescriptionSuggestionRequest request) {
        return ApiResponse.ok(descriptionSuggestions.generate(SecurityConfig.currentAccount(), conversationId,
                cardId, recordIndex, request.clientRequestId(), request.recordFacts()));
    }

    @PostMapping("/conversations/{conversationId}/cards/{cardId}/summary-suggestions")
    public ApiResponse<SummarySuggestionView> summarySuggestion(@PathVariable String conversationId,
            @PathVariable String cardId, @Valid @RequestBody SummarySuggestionRequest request) {
        return ApiResponse.ok(summarySuggestions.generate(SecurityConfig.currentAccount(), conversationId,
                cardId, request.clientRequestId(), request.currentSummary()));
    }

    @PostMapping("/conversations/{conversationId}/cards/{cardId}/skill-suggestions")
    public ApiResponse<SkillSuggestionView> skillSuggestion(@PathVariable String conversationId,
            @PathVariable String cardId, @Valid @RequestBody SkillSuggestionRequest request) {
        return ApiResponse.ok(skillSuggestions.generate(SecurityConfig.currentAccount(), conversationId, cardId,
                request.clientRequestId(), request.phase(), request.mode(), request.currentSkills(),
                request.selectedNames(), request.confirmedNames()));
    }

    @PostMapping("/conversations/{conversationId}/cards/{cardId}/certificate-suggestions")
    public ApiResponse<CertificateSuggestionView> certificateSuggestion(@PathVariable String conversationId,
            @PathVariable String cardId, @Valid @RequestBody CertificateSuggestionRequest request) {
        return ApiResponse.ok(certificateSuggestions.generate(SecurityConfig.currentAccount(), conversationId,
                cardId, request.clientRequestId(), request.phase(), request.currentCertificates(),
                request.selectedNames(), request.confirmedNames()));
    }

    @PostMapping("/conversations/{conversationId}/cards/{cardId}/honor-suggestions")
    public ApiResponse<HonorSuggestionView> honorSuggestion(@PathVariable String conversationId,
            @PathVariable String cardId, @Valid @RequestBody HonorSuggestionRequest request) {
        return ApiResponse.ok(honorSuggestions.generate(SecurityConfig.currentAccount(), conversationId,
                cardId, request.clientRequestId(), request.phase(), request.currentHonors(),
                request.selectedNames(), request.confirmedNames()));
    }

    @PostMapping("/conversations/{conversationId}/cards/{cardId}/credential-recommendations")
    public ApiResponse<CredentialRecommendationView> credentialRecommendations(
            @PathVariable String conversationId, @PathVariable String cardId,
            @Valid @RequestBody CredentialRecommendationRequest request) {
        return ApiResponse.ok(credentialRecommendations.recommend(SecurityConfig.currentAccount(), conversationId,
                cardId, request.clientRequestId()));
    }

    @PostMapping("/conversations/{conversationId}/capture-confirmed-change")
    public ApiResponse<ConversationView> captureConfirmedChange(@PathVariable String conversationId) {
        return ApiResponse.ok(workbench.captureConfirmedChange(SecurityConfig.currentAccount(), conversationId));
    }

    @PostMapping("/conversations/{conversationId}/template")
    public ApiResponse<ConversationView> template(@PathVariable String conversationId,
            @Valid @RequestBody TemplateRequest request) {
        return ApiResponse.ok(workbench.selectTemplate(SecurityConfig.currentAccount(), conversationId,
                request.selector(), request.expectedLayoutVersion()));
    }

    @GetMapping("/conversations/{conversationId}/smart-templates")
    public ApiResponse<java.util.List<SmartTemplateView>> smartTemplates(@PathVariable String conversationId) {
        return ApiResponse.ok(workbench.smartTemplates(SecurityConfig.currentAccount(), conversationId));
    }

    @PutMapping("/conversations/{conversationId}/design/{templateId}")
    public ApiResponse<DesignPreferenceView> design(@PathVariable String conversationId,
            @PathVariable String templateId, @Valid @RequestBody DesignRequest request) {
        return ApiResponse.ok(workbench.saveDesign(SecurityConfig.currentAccount(), conversationId,
                templateId, request.variantCode(), request.settings(), request.expectedVersion()));
    }

    @PostMapping("/conversations/{conversationId}/export-pdf")
    public ApiResponse<TaskView> exportPdf(@PathVariable String conversationId,
            @RequestBody(required = false) ExportPdfRequest request) {
        ResumePdfExportMode mode = ResumePdfExportMode.parse(request == null ? null : request.exportMode());
        return ApiResponse.ok(workbench.exportPdf(SecurityConfig.currentAccount(), conversationId, mode));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ApiResponse<AiResumeMessageView> message(@PathVariable String conversationId,
            @Valid @RequestBody MessageRequest request) {
        return ApiResponse.ok(workbench.addUserMessage(SecurityConfig.currentAccount(), conversationId,
                request.clientMessageId(), request.text()));
    }

    @PostMapping("/conversations/{conversationId}/messages/respond")
    public ApiResponse<ConversationView> respond(@PathVariable String conversationId,
            @Valid @RequestBody MessageRequest request) {
        return ApiResponse.ok(workbench.respond(SecurityConfig.currentAccount(), conversationId,
                request.clientMessageId(), request.text()));
    }

    @PostMapping("/conversations/{conversationId}/change-sets/{setId}/items/{itemId}/decision")
    public ApiResponse<ChangeSetView> decideChange(@PathVariable String conversationId,
            @PathVariable String setId, @PathVariable String itemId,
            @Valid @RequestBody ChangeDecisionRequest request) {
        return ApiResponse.ok(changeSets.decide(SecurityConfig.currentAccount(), conversationId, setId, itemId,
                new DecisionCommand(request.decision(), request.editedValue(), request.expectedVersion())));
    }

    @PostMapping("/conversations/{conversationId}/change-sets/{setId}/items/{itemId}/undo")
    public ApiResponse<ChangeSetView> undoChange(@PathVariable String conversationId,
            @PathVariable String setId, @PathVariable String itemId,
            @RequestBody(required = false) UndoChangeRequest request) {
        return ApiResponse.ok(changeSets.undo(SecurityConfig.currentAccount(), conversationId, setId, itemId,
                request == null ? null : request.expectedVersion()));
    }

    @PostMapping(value = "/conversations/{conversationId}/messages/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> respondStream(@PathVariable String conversationId,
            @Valid @RequestBody MessageRequest request) {
        var current = SecurityConfig.currentAccount();
        StreamingResponseBody body = output -> workbench.respondStreaming(current, conversationId,
                request.clientMessageId(), request.text(), (eventType, payload) -> {
                    try {
                        String frame = "event:" + eventType + "\n"
                                + "data:" + mapper.writeValueAsString(payload) + "\n\n";
                        output.write(frame.getBytes(StandardCharsets.UTF_8));
                        output.flush();
                    } catch (java.io.IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    @PostMapping("/conversations/{conversationId}/messages/{requestId}/cancel")
    public ApiResponse<CancellationView> cancelResponse(@PathVariable String conversationId,
            @PathVariable String requestId) {
        return ApiResponse.ok(workbench.cancelResponse(SecurityConfig.currentAccount(), conversationId, requestId));
    }

    @GetMapping(value = "/conversations/{conversationId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String conversationId,
            @RequestParam(defaultValue = "0") long afterSequence) {
        return events.subscribe(SecurityConfig.currentAccount().accountId(), conversationId, afterSequence);
    }

    @GetMapping("/consent")
    public ApiResponse<ConsentView> consent() {
        return ApiResponse.ok(workbench.consent(SecurityConfig.currentAccount().accountId()));
    }

    @PostMapping("/consent")
    public ApiResponse<ConsentView> grantConsent() {
        return ApiResponse.ok(workbench.grantConsent(SecurityConfig.currentAccount()));
    }

    @PostMapping("/consent/revoke")
    public ApiResponse<ConsentView> revokeConsent() {
        return ApiResponse.ok(workbench.revokeConsent(SecurityConfig.currentAccount()));
    }

    @GetMapping("/conversations/{conversationId}/preferences/writing-style")
    public ApiResponse<WritingPreferenceView> writingPreference(@PathVariable String conversationId) {
        return ApiResponse.ok(workbench.writingPreference(SecurityConfig.currentAccount(), conversationId));
    }

    @PutMapping("/conversations/{conversationId}/preferences/writing-style")
    public ApiResponse<WritingPreferenceView> writingPreference(@PathVariable String conversationId,
            @Valid @RequestBody WritingPreferenceRequest request) {
        return ApiResponse.ok(workbench.setWritingPreference(SecurityConfig.currentAccount(), conversationId,
                request.styleCode()));
    }

    @GetMapping("/conversations/{conversationId}/preferences/career-library-evidence")
    public ApiResponse<CareerEvidencePreferenceView> careerLibraryEvidence(@PathVariable String conversationId) {
        return ApiResponse.ok(workbench.careerEvidencePreference(SecurityConfig.currentAccount(), conversationId));
    }

    @PutMapping("/conversations/{conversationId}/preferences/career-library-evidence")
    public ApiResponse<CareerEvidencePreferenceView> careerLibraryEvidence(@PathVariable String conversationId,
            @RequestBody CareerEvidencePreferenceRequest request) {
        return ApiResponse.ok(workbench.setCareerEvidencePreference(SecurityConfig.currentAccount(), conversationId,
                request.enabled()));
    }

    @DeleteMapping("/conversations/{conversationId}/history")
    public ApiResponse<HistoryDeletionView> deleteHistory(@PathVariable String conversationId) {
        return ApiResponse.ok(workbench.deleteAiHistory(SecurityConfig.currentAccount(), conversationId));
    }

    @PostMapping("/conversations/{conversationId}/text-import")
    public ApiResponse<TextImportView> importText(@PathVariable String conversationId,
            @Valid @RequestBody TextImportRequest request) {
        return ApiResponse.ok(workbench.importText(SecurityConfig.currentAccount(), conversationId, request.text()));
    }

    @PostMapping(value = "/conversations/{conversationId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ConversationView> uploadPhoto(@PathVariable String conversationId,
            @RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(workbench.uploadPhoto(SecurityConfig.currentAccount(), conversationId,
                file.getOriginalFilename(), file.getBytes()));
    }

    @DeleteMapping("/conversations/{conversationId}/photo")
    public ApiResponse<ConversationView> removePhoto(@PathVariable String conversationId) {
        return ApiResponse.ok(workbench.removePhoto(SecurityConfig.currentAccount(), conversationId));
    }

    @GetMapping("/quota")
    public ApiResponse<AiQuotaService.QuotaView> quota() {
        return ApiResponse.ok(quota.current(SecurityConfig.currentAccount().accountId()));
    }

    public record CreateConversationRequest(@NotBlank String identityType, @Size(max = 255) String title) {}
    public record CardRequest(JsonNode payload, Integer expectedVersion) {}
    public record SkipCardRequest(Integer expectedVersion) {}
    public record DescriptionSuggestionRequest(@NotBlank @Size(max = 128) String clientRequestId,
            @NotNull JsonNode recordFacts) {}
    public record SummarySuggestionRequest(@NotBlank @Size(max = 128) String clientRequestId,
            @Size(max = 1200) String currentSummary) {}
    public record SkillSuggestionRequest(@NotBlank @Size(max = 128) String clientRequestId,
            @NotBlank @Size(max = 16) String phase, @NotBlank @Size(max = 24) String mode,
            JsonNode currentSkills, @Size(max = 12) List<@Size(max = 60) String> selectedNames,
            @Size(max = 12) List<@Size(max = 60) String> confirmedNames) {}
    public record CertificateSuggestionRequest(@NotBlank @Size(max = 128) String clientRequestId,
            @NotBlank @Size(max = 16) String phase, JsonNode currentCertificates,
            @Size(max = 12) List<@Size(max = 120) String> selectedNames,
            @Size(max = 12) List<@Size(max = 120) String> confirmedNames) {}
    public record HonorSuggestionRequest(@NotBlank @Size(max = 128) String clientRequestId,
            @NotBlank @Size(max = 16) String phase, JsonNode currentHonors,
            @Size(max = 12) List<@Size(max = 120) String> selectedNames,
            @Size(max = 12) List<@Size(max = 120) String> confirmedNames) {}
    public record CredentialRecommendationRequest(@NotBlank @Size(max = 128) String clientRequestId) {}
    public record TemplateRequest(String templateId, String templateCode, Integer expectedLayoutVersion) {
        public String selector() {
            String value = templateId == null || templateId.isBlank() ? templateCode : templateId;
            if (value == null || value.isBlank()) {
                throw com.jobproof.shared.error.AppException.user(
                        "AI_RESUME_TEMPLATE_REQUIRED", "请选择一款智能模板");
            }
            return value;
        }
    }
    public record DesignRequest(String variantCode, JsonNode settings, Integer expectedVersion) {}
    public record ExportPdfRequest(String exportMode) {}
    public record MessageRequest(@Size(max = 128) String clientMessageId,
            @NotBlank @Size(max = 8000) String text) {}
    public record ChangeDecisionRequest(@NotBlank @Size(max = 16) String decision,
            @Size(max = 12_000) String editedValue, Integer expectedVersion) {}
    public record UndoChangeRequest(Integer expectedVersion) {}
    public record WritingPreferenceRequest(@NotBlank String styleCode) {}
    public record CareerEvidencePreferenceRequest(boolean enabled) {}
    public record TextImportRequest(@NotBlank @Size(max = 50_000) String text) {}
}
