package com.jobproof.modules.airesume.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.airesume.application.AiResumeLifecycleService;
import com.jobproof.modules.airesume.application.AiResumeLifecycleService.BranchDiffView;
import com.jobproof.modules.airesume.application.AiResumeLifecycleService.BranchView;
import com.jobproof.modules.airesume.application.AiResumeLifecycleService.RevisionDiffView;
import com.jobproof.modules.airesume.application.AiResumeLifecycleService.DataExportView;
import com.jobproof.modules.airesume.application.AiResumeLifecycleService.RevisionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-resume")
public class AiResumeLifecycleController {

    private final AiResumeLifecycleService lifecycle;

    public AiResumeLifecycleController(AiResumeLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @GetMapping("/conversations/{conversationId}/revisions")
    public ApiResponse<List<RevisionView>> revisions(@PathVariable String conversationId) {
        return ApiResponse.ok(lifecycle.revisions(SecurityConfig.currentAccount(), conversationId));
    }

    @GetMapping("/conversations/{conversationId}/branches")
    public ApiResponse<List<BranchView>> branches(@PathVariable String conversationId) {
        return ApiResponse.ok(lifecycle.branches(SecurityConfig.currentAccount(), conversationId));
    }

    @PostMapping("/conversations/{conversationId}/branches/language")
    public ApiResponse<BranchView> createLanguageBranch(@PathVariable String conversationId,
            @Valid @RequestBody LanguageBranchRequest request) {
        return ApiResponse.ok(lifecycle.createLanguageBranch(SecurityConfig.currentAccount(), conversationId,
                request.title(), request.languageCode()));
    }

    @PostMapping("/conversations/{conversationId}/branches/{branchId}/switch")
    public ApiResponse<BranchView> switchBranch(@PathVariable String conversationId, @PathVariable String branchId) {
        return ApiResponse.ok(lifecycle.switchBranch(SecurityConfig.currentAccount(), conversationId, branchId));
    }

    @GetMapping("/conversations/{conversationId}/branches/{branchId}/diff")
    public ApiResponse<BranchDiffView> branchDiff(@PathVariable String conversationId,
            @PathVariable String branchId) {
        return ApiResponse.ok(lifecycle.branchDiff(SecurityConfig.currentAccount(), conversationId, branchId));
    }

    @PostMapping("/conversations/{conversationId}/branches/{branchId}/sync")
    public ApiResponse<BranchView> syncBranch(@PathVariable String conversationId, @PathVariable String branchId,
            @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok(lifecycle.syncBranch(SecurityConfig.currentAccount(), conversationId, branchId,
                request.expectedVersion()));
    }

    @PostMapping("/conversations/{conversationId}/branches/{branchId}/translate")
    public ApiResponse<BranchView> translateBranch(@PathVariable String conversationId,
            @PathVariable String branchId, @Valid @RequestBody TranslateRequest request) {
        return ApiResponse.ok(lifecycle.translateLanguageBranch(SecurityConfig.currentAccount(), conversationId,
                branchId, request.requestId()));
    }

    @PostMapping("/conversations/{conversationId}/branches/{branchId}/translation/confirm")
    public ApiResponse<BranchView> confirmTranslation(@PathVariable String conversationId,
            @PathVariable String branchId, @Valid @RequestBody VersionRequest request) {
        return ApiResponse.ok(lifecycle.confirmLanguageBranch(SecurityConfig.currentAccount(), conversationId,
                branchId, request.expectedVersion()));
    }

    @GetMapping("/conversations/{conversationId}/revisions/diff")
    public ApiResponse<RevisionDiffView> revisionDiff(@PathVariable String conversationId,
            @org.springframework.web.bind.annotation.RequestParam String from,
            @org.springframework.web.bind.annotation.RequestParam String to) {
        return ApiResponse.ok(lifecycle.revisionDiff(SecurityConfig.currentAccount(), conversationId, from, to));
    }

    @GetMapping("/conversations/{conversationId}/data-export")
    public ApiResponse<DataExportView> dataExport(@PathVariable String conversationId) {
        return ApiResponse.ok(lifecycle.dataExport(SecurityConfig.currentAccount(), conversationId));
    }

    @PostMapping("/conversations/{conversationId}/revisions/{revisionId}/restore")
    public ApiResponse<RevisionView> restore(@PathVariable String conversationId, @PathVariable String revisionId) {
        return ApiResponse.ok(lifecycle.restore(SecurityConfig.currentAccount(), conversationId, revisionId));
    }

    public record LanguageBranchRequest(@Size(max = 255) String title, @NotBlank String languageCode) {}
    public record VersionRequest(@Min(0) int expectedVersion) {}
    public record TranslateRequest(@NotBlank @Size(max = 128) String requestId) {}
}
