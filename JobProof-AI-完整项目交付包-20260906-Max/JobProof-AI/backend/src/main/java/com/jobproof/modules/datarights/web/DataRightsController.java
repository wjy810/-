package com.jobproof.modules.datarights.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.datarights.application.DataRightsService;
import com.jobproof.modules.datarights.application.DataRightsService.DeletionPreview;
import com.jobproof.modules.datarights.application.DataRightsService.DeletionView;
import com.jobproof.modules.datarights.application.DataRightsService.ExportView;
import com.jobproof.modules.datarights.application.DataRightsService.ShareView;
import com.jobproof.shared.auth.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-rights")
public class DataRightsController {

    private final DataRightsService dataRightsService;

    public DataRightsController(DataRightsService dataRightsService) {
        this.dataRightsService = dataRightsService;
    }

    @GetMapping("/deletions/preview")
    public ApiResponse<DeletionPreview> preview(
            @RequestParam(value = "scope", required = false, defaultValue = "ACCOUNT") String scope,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "targetId", required = false) String targetId) {
        return ApiResponse.ok(dataRightsService.previewDeletion(
                SecurityConfig.currentAccount().accountId(), scope, targetType, targetId));
    }

    @PostMapping("/deletions")
    public ApiResponse<DeletionView> submit(@Valid @RequestBody SubmitDeletionRequest request) {
        CurrentAccount current = SecurityConfig.currentAccount();
        return ApiResponse.ok(dataRightsService.submitDeletion(
                current.accountId(), request.scope(), request.targetType(), request.targetId(), request.confirmationAck()));
    }

    @GetMapping("/deletions/{id}")
    public ApiResponse<DeletionView> get(@PathVariable String id) {
        return ApiResponse.ok(dataRightsService.getDeletion(SecurityConfig.currentAccount().accountId(), id));
    }

    @PostMapping("/exports")
    public ApiResponse<ExportView> export(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest ignored) {
        return ApiResponse.ok(dataRightsService.createExport(SecurityConfig.currentAccount().accountId(), idempotencyKey));
    }

    @GetMapping("/exports/{id}")
    public ApiResponse<ExportView> getExport(@PathVariable String id) {
        return ApiResponse.ok(dataRightsService.getExport(SecurityConfig.currentAccount().accountId(), id));
    }

    @PostMapping("/exports/{id}/cancel")
    public ApiResponse<ExportView> cancel(@PathVariable String id) {
        return ApiResponse.ok(dataRightsService.cancelExport(SecurityConfig.currentAccount().accountId(), id));
    }

    @PostMapping("/exports/{id}/retry")
    public ApiResponse<ExportView> retry(@PathVariable String id) {
        return ApiResponse.ok(dataRightsService.retryExport(SecurityConfig.currentAccount().accountId(), id));
    }

    @GetMapping("/exports/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        byte[] body = dataRightsService.downloadExport(SecurityConfig.currentAccount().accountId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=jobproof-export.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @GetMapping("/shares")
    public ApiResponse<java.util.List<ShareView>> shares() {
        return ApiResponse.ok(dataRightsService.listShares(SecurityConfig.currentAccount().accountId()));
    }

    @PostMapping("/shares")
    public ApiResponse<ShareView> share(@Valid @RequestBody CreateShareRequest request) {
        return ApiResponse.ok(dataRightsService.createShare(
                SecurityConfig.currentAccount().accountId(), request.resourceType(), request.resourceId()));
    }

    @PostMapping("/shares/{id}/revoke")
    public ApiResponse<Void> revoke(@PathVariable String id) {
        dataRightsService.revokeShare(SecurityConfig.currentAccount().accountId(), id);
        return ApiResponse.ok(null);
    }

    public record SubmitDeletionRequest(
            @NotBlank String scope,
            String targetType,
            String targetId,
            boolean confirmationAck) {
    }

    public record CreateShareRequest(@NotBlank String resourceType, @NotBlank String resourceId) {
    }
}
