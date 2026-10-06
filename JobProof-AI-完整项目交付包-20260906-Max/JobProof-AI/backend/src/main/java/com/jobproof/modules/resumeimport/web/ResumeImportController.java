package com.jobproof.modules.resumeimport.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.resumeimport.application.ResumeImportService;
import com.jobproof.modules.resumeimport.application.ResumeImportService.ConfirmCommand;
import com.jobproof.modules.resumeimport.application.ResumeImportService.CreateCommand;
import com.jobproof.modules.resumeimport.application.ResumeImportService.ImportStartView;
import com.jobproof.modules.resumeimport.application.ResumeImportService.ImportView;
import com.jobproof.modules.resumeimport.application.ResumeImportService.UpdateCommand;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resume-imports")
public class ResumeImportController {
    private final ResumeImportService imports;
    public ResumeImportController(ResumeImportService imports) { this.imports = imports; }

    @PostMapping
    public ApiResponse<ImportStartView> create(@RequestBody CreateCommand command) {
        return ApiResponse.ok(imports.create(SecurityConfig.currentAccount(), command));
    }
    @GetMapping("/{id}")
    public ApiResponse<ImportView> get(@PathVariable String id) {
        return ApiResponse.ok(imports.get(SecurityConfig.currentAccount(), id));
    }
    @PutMapping("/{id}")
    public ApiResponse<ImportView> update(@PathVariable String id, @RequestBody UpdateCommand command) {
        return ApiResponse.ok(imports.update(SecurityConfig.currentAccount(), id, command));
    }
    @PostMapping("/{id}/confirm")
    public ApiResponse<ImportView> confirm(@PathVariable String id, @RequestBody(required = false) ConfirmCommand command) {
        return ApiResponse.ok(imports.confirm(SecurityConfig.currentAccount(), id, command));
    }
}
