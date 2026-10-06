package com.jobproof.modules.resume.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.resume.application.ResumeTemplateService;
import com.jobproof.modules.resume.application.ResumeTemplateService.ApplyCommand;
import com.jobproof.modules.resume.application.ResumeTemplateService.CurrentLayoutView;
import com.jobproof.modules.resume.application.ResumeTemplateService.LayoutView;
import com.jobproof.modules.resume.application.ResumeTemplateService.PreviewCommand;
import com.jobproof.modules.resume.application.ResumeTemplateService.PreviewView;
import com.jobproof.modules.resume.application.ResumeTemplateService.TemplateDetailView;
import com.jobproof.modules.resume.application.ResumeTemplateService.TemplateCatalogQuery;
import com.jobproof.modules.resume.application.ResumeTemplateService.TemplateSummaryView;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.shared.page.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resume-templates")
public class ResumeTemplateController {
    private final ResumeTemplateService service;

    public ResumeTemplateController(ResumeTemplateService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageResult<TemplateSummaryView>> catalog(@ModelAttribute TemplateCatalogQuery query) {
        return ApiResponse.ok(service.catalog(SecurityConfig.currentAccount(), query));
    }

    @GetMapping("/{templateId}")
    public ApiResponse<TemplateDetailView> detail(@PathVariable String templateId) {
        return ApiResponse.ok(service.detail(SecurityConfig.currentAccount(), templateId));
    }

    @PostMapping("/{templateId}/preview")
    public ApiResponse<PreviewView> preview(@PathVariable String templateId, @Valid @RequestBody PreviewRequest request) {
        return ApiResponse.ok(service.preview(SecurityConfig.currentAccount(), templateId,
                new PreviewCommand(request.masterId(), request.variantCode())));
    }

    @PostMapping("/{templateId}/apply")
    public ApiResponse<LayoutView> apply(@PathVariable String templateId, @Valid @RequestBody ApplyRequest request) {
        return ApiResponse.ok(service.apply(SecurityConfig.currentAccount(), templateId,
                new ApplyCommand(request.masterId(), request.variantCode(), request.expectedVersion())));
    }

    @GetMapping("/layouts/{layoutId}")
    public ApiResponse<LayoutView> layout(@PathVariable String layoutId) {
        return ApiResponse.ok(service.layout(SecurityConfig.currentAccount(), layoutId));
    }

    @GetMapping("/layouts/current")
    public ApiResponse<CurrentLayoutView> currentLayout(@RequestParam String masterId) {
        return ApiResponse.ok(service.currentLayout(SecurityConfig.currentAccount(), masterId));
    }

    @PostMapping("/layouts/{layoutId}/export")
    public ApiResponse<TaskView> export(
            @PathVariable String layoutId, @Valid @RequestBody ExportRequest request) {
        return ApiResponse.ok(service.export(SecurityConfig.currentAccount(), layoutId, request.format()));
    }

    public record PreviewRequest(@NotBlank String masterId, @NotBlank String variantCode) {}
    public record ApplyRequest(@NotBlank String masterId, @NotBlank String variantCode, Integer expectedVersion) {}
    public record ExportRequest(@NotBlank String format) {}
}
