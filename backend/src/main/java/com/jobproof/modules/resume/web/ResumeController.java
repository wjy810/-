package com.jobproof.modules.resume.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.resume.application.ResumeService;
import com.jobproof.modules.resume.application.ResumeAiCandidateService;
import com.jobproof.modules.resume.application.ResumeService.CandidateView;
import com.jobproof.modules.resume.application.ResumeService.CompareView;
import com.jobproof.modules.resume.application.ResumeService.CreateCommand;
import com.jobproof.modules.resume.application.ResumeService.KeyOutcomeWrite;
import com.jobproof.modules.resume.application.ResumeService.MasterSummary;
import com.jobproof.modules.resume.application.ResumeService.MasterView;
import com.jobproof.modules.resume.application.ResumeService.UpdateCommand;
import com.jobproof.modules.resume.application.ResumeService.VersionView;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeAiCandidateService aiCandidates;

    public ResumeController(ResumeService resumeService, ResumeAiCandidateService aiCandidates) {
        this.resumeService = resumeService;
        this.aiCandidates = aiCandidates;
    }

    @PostMapping
    public ApiResponse<MasterView> create(@RequestBody CreateRequest request) {
        return ApiResponse.ok(resumeService.create(
                SecurityConfig.currentAccount(),
                new CreateCommand(request.mode(), request.title(), request.templateCode(), request.importText())));
    }

    @GetMapping
    public ApiResponse<List<MasterSummary>> list() {
        return ApiResponse.ok(resumeService.list(SecurityConfig.currentAccount()));
    }

    @GetMapping("/{id}")
    public ApiResponse<MasterView> get(@PathVariable String id) {
        return ApiResponse.ok(resumeService.get(SecurityConfig.currentAccount(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<MasterView> update(@PathVariable String id, @Valid @RequestBody UpdateRequest request) {
        return ApiResponse.ok(resumeService.update(
                SecurityConfig.currentAccount(),
                id,
                new UpdateCommand(
                        request.title(),
                        request.education(),
                        request.experience(),
                        request.projects(),
                        request.skills(),
                        request.certificates(),
                        request.selfIntro(),
                        request.keyOutcomes()),
                request.expectedVersion()));
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<MasterView> copy(@PathVariable String id) {
        return ApiResponse.ok(resumeService.copy(SecurityConfig.currentAccount(), id));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<MasterView> archive(@PathVariable String id, @RequestBody(required = false) VersionedRequest request) {
        return ApiResponse.ok(resumeService.archive(
                SecurityConfig.currentAccount(), id, request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<MasterView> restore(@PathVariable String id, @RequestBody(required = false) VersionedRequest request) {
        return ApiResponse.ok(resumeService.restore(
                SecurityConfig.currentAccount(), id, request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/{id}/ready")
    public ApiResponse<MasterView> markReady(@PathVariable String id, @RequestBody(required = false) VersionedRequest request) {
        return ApiResponse.ok(resumeService.markReady(
                SecurityConfig.currentAccount(), id, request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/{id}/candidates")
    public ApiResponse<CandidateView> createCandidate(@PathVariable String id, @Valid @RequestBody CandidateRequest request) {
        return ApiResponse.ok(resumeService.createCandidate(
                SecurityConfig.currentAccount(), id, request.fieldKey(), request.proposedValue()));
    }

    @GetMapping("/ai/availability")
    public ApiResponse<ResumeAiCandidateService.Availability> aiAvailability(@RequestParam(required=false) String model) {
        return ApiResponse.ok(aiCandidates.availability(SecurityConfig.currentAccount(),model));
    }

    @PostMapping("/{id}/ai-candidates")
    public ApiResponse<CandidateView> generateAiCandidate(@PathVariable String id,@Valid @RequestBody AiCandidateRequest request) {
        return ApiResponse.ok(aiCandidates.generate(SecurityConfig.currentAccount(),id,
                new ResumeAiCandidateService.GenerateCommand(request.fieldKey(),request.action(),request.model(),request.expectedVersion())));
    }

    @GetMapping("/{id}/candidates")
    public ApiResponse<PageResult<CandidateView>> listCandidates(
            @PathVariable String id,
            @RequestParam(value = "status", required = false) String status,
            @ModelAttribute PageQuery query) {
        return ApiResponse.ok(resumeService.listCandidates(SecurityConfig.currentAccount(), id, status, query));
    }

    @PostMapping("/{id}/candidates/{candidateId}/confirm")
    public ApiResponse<MasterView> confirmCandidate(
            @PathVariable String id,
            @PathVariable String candidateId,
            @RequestBody(required = false) VersionedRequest request) {
        return ApiResponse.ok(resumeService.confirmCandidate(
                SecurityConfig.currentAccount(), id, candidateId, request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/{id}/candidates/{candidateId}/reject")
    public ApiResponse<CandidateView> rejectCandidate(
            @PathVariable String id,
            @PathVariable String candidateId,
            @RequestBody(required = false) VersionedRequest request) {
        return ApiResponse.ok(resumeService.rejectCandidate(
                SecurityConfig.currentAccount(), id, candidateId, request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/{id}/candidates/{candidateId}/correct")
    public ApiResponse<MasterView> correctCandidate(
            @PathVariable String id,
            @PathVariable String candidateId,
            @Valid @RequestBody CorrectRequest request) {
        return ApiResponse.ok(resumeService.correctCandidate(
                SecurityConfig.currentAccount(), id, candidateId, request.value(), request.expectedVersion()));
    }

    @PostMapping("/{id}/outcomes/{outcomeId}/evidence")
    public ApiResponse<MasterView> linkEvidence(
            @PathVariable String id,
            @PathVariable String outcomeId,
            @Valid @RequestBody EvidenceLinkRequest request) {
        return ApiResponse.ok(resumeService.linkEvidence(
                SecurityConfig.currentAccount(), id, outcomeId, request.evidenceId(), request.expectedVersion()));
    }

    @PostMapping("/{id}/outcomes/{outcomeId}/waive")
    public ApiResponse<MasterView> waive(
            @PathVariable String id,
            @PathVariable String outcomeId,
            @RequestBody(required = false) WaiveRequest request) {
        boolean confirmed = request != null && Boolean.TRUE.equals(request.confirmed());
        return ApiResponse.ok(resumeService.waiveOutcome(
                SecurityConfig.currentAccount(),
                id,
                outcomeId,
                confirmed,
                request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/{id}/freeze")
    public ApiResponse<VersionView> freeze(@PathVariable String id, @RequestBody(required = false) VersionedRequest request) {
        return ApiResponse.ok(resumeService.freeze(
                SecurityConfig.currentAccount(), id, request == null ? null : request.expectedVersion()));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<VersionView>> versions(@PathVariable String id) {
        return ApiResponse.ok(resumeService.listVersions(SecurityConfig.currentAccount(), id));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<VersionView> version(@PathVariable String versionId) {
        return ApiResponse.ok(resumeService.getVersion(SecurityConfig.currentAccount(), versionId));
    }

    @PostMapping("/versions/{versionId}/confirm")
    public ApiResponse<VersionView> confirmVersion(
            @PathVariable String versionId,
            @RequestBody(required = false) VersionedRequest request) {
        return ApiResponse.ok(resumeService.confirmCustomizeVersion(
                SecurityConfig.currentAccount(), versionId, request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/versions/{versionId}/archive")
    public ApiResponse<VersionView> archiveVersion(
            @PathVariable String versionId,
            @RequestBody(required = false) VersionedRequest request) {
        return ApiResponse.ok(resumeService.archiveVersion(
                SecurityConfig.currentAccount(), versionId, request == null ? null : request.expectedVersion()));
    }

    @GetMapping("/versions/{versionId}/compare")
    public ApiResponse<CompareView> compare(
            @PathVariable String versionId,
            @RequestParam("with") String otherId) {
        return ApiResponse.ok(resumeService.compare(SecurityConfig.currentAccount(), versionId, otherId));
    }

    @PostMapping("/versions/{versionId}/export-pdf")
    public ApiResponse<TaskView> exportPdf(@PathVariable String versionId) {
        return ApiResponse.ok(resumeService.startPdfExport(SecurityConfig.currentAccount(), versionId));
    }

    @PostMapping("/versions/{versionId}/export-docx")
    public ApiResponse<TaskView> exportDocx(@PathVariable String versionId) {
        return ApiResponse.ok(resumeService.startDocxExport(SecurityConfig.currentAccount(), versionId));
    }

    public record CreateRequest(String mode, String title, String templateCode, String importText) {
    }

    public record UpdateRequest(
            @Size(max = 255) String title,
            String education,
            String experience,
            String projects,
            String skills,
            String certificates,
            String selfIntro,
            List<KeyOutcomeWrite> keyOutcomes,
            Integer expectedVersion) {
    }

    public record CandidateRequest(@NotBlank String fieldKey, @NotNull JsonNode proposedValue) {
    }
    public record AiCandidateRequest(@NotBlank String fieldKey,@NotBlank String action,String model,Integer expectedVersion) {}

    public record CorrectRequest(@NotNull JsonNode value, Integer expectedVersion) {
    }

    public record EvidenceLinkRequest(@NotBlank String evidenceId, Integer expectedVersion) {
    }

    public record WaiveRequest(Boolean confirmed, Integer expectedVersion) {
    }

    public record VersionedRequest(Integer expectedVersion) {
    }
}
