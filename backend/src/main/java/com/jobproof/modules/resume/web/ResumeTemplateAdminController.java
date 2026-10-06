package com.jobproof.modules.resume.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService;
import com.jobproof.modules.resume.application.ResumeTemplateBatchImportService;
import com.jobproof.modules.resume.application.ResumeTemplateBatchImportService.BatchView;
import com.jobproof.modules.resume.application.ResumeTemplateBatchImportService.ItemView;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.AssetView;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.DraftCommand;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.EvidenceDownload;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.EvidenceView;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.ImportAssetCommand;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.ScannerStatusView;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.ReviewAssetCommand;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.TestCommand;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.TemplateAdminView;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.UploadEvidenceCommand;
import com.jobproof.modules.resume.application.ResumeTemplateAdminService.VersionView;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService.CatalogAdminItemView;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService.CatalogFacetCommand;
import com.jobproof.modules.resume.application.ResumeTemplateCatalogService.CatalogMetadataCommand;
import com.jobproof.modules.resume.application.ResumeTemplatePreviewService;
import com.jobproof.modules.resume.application.ResumeTemplatePreviewService.PreviewStatusView;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/resume-templates")
public class ResumeTemplateAdminController {
    private final ResumeTemplateAdminService service;
    private final ResumeTemplateBatchImportService batchImportService;
    private final ResumeTemplateCatalogService catalogService;
    private final ResumeTemplatePreviewService previewService;

    public ResumeTemplateAdminController(ResumeTemplateAdminService service,
            ResumeTemplateBatchImportService batchImportService, ResumeTemplateCatalogService catalogService,
            ResumeTemplatePreviewService previewService) {
        this.service = service; this.batchImportService = batchImportService; this.catalogService = catalogService;
        this.previewService = previewService;
    }

    @GetMapping("/catalog")
    public ApiResponse<PageResult<CatalogAdminItemView>> catalog(@ModelAttribute PageQuery query) {
        return ApiResponse.ok(catalogService.adminCatalog(SecurityConfig.currentAccount(), query));
    }

    @PutMapping("/catalog/{catalogId}")
    public ApiResponse<CatalogAdminItemView> updateCatalog(@PathVariable String catalogId,
            @Valid @RequestBody CatalogMetadataRequest request) {
        return ApiResponse.ok(catalogService.updateMetadata(SecurityConfig.currentAccount(),catalogId,
                new CatalogMetadataCommand(request.title(),request.summary(),request.assetKind(),request.languageCode(),
                        request.pageCount(),request.photoPolicy(),request.thumbnailUri(),request.facets()),request.expectedVersion()));
    }

    @PostMapping("/catalog/{catalogId}/publish")
    public ApiResponse<CatalogAdminItemView> publishCatalog(@PathVariable String catalogId,@RequestBody VersionedRequest request) {
        return ApiResponse.ok(catalogService.publish(SecurityConfig.currentAccount(),catalogId,request.expectedVersion()));
    }

    @PostMapping("/catalog/{catalogId}/retire")
    public ApiResponse<CatalogAdminItemView> retireCatalog(@PathVariable String catalogId,@RequestBody VersionedRequest request) {
        return ApiResponse.ok(catalogService.retire(SecurityConfig.currentAccount(),catalogId,request.expectedVersion()));
    }

    @GetMapping("/catalog/previews/status")
    public ApiResponse<PreviewStatusView> previewStatus() {
        return ApiResponse.ok(previewService.status(SecurityConfig.currentAccount()));
    }

    @PostMapping("/catalog/previews/retry-failed")
    public ApiResponse<PreviewStatusView> retryFailedPreviews() {
        return ApiResponse.ok(previewService.retryFailed(SecurityConfig.currentAccount()));
    }

    @GetMapping("/import-batches")
    public ApiResponse<PageResult<BatchView>> importBatches(@ModelAttribute PageQuery query) {
        return ApiResponse.ok(batchImportService.batches(SecurityConfig.currentAccount(), query));
    }

    @PostMapping("/import-batches")
    public ApiResponse<BatchView> startImportBatch() {
        return ApiResponse.ok(batchImportService.start(SecurityConfig.currentAccount()));
    }

    @GetMapping("/import-batches/{batchId}/items")
    public ApiResponse<PageResult<ItemView>> importBatchItems(@PathVariable String batchId,
            @ModelAttribute PageQuery query) {
        return ApiResponse.ok(batchImportService.items(SecurityConfig.currentAccount(), batchId, query));
    }

    @PostMapping("/import-batches/{batchId}/pause")
    public ApiResponse<BatchView> pauseImportBatch(@PathVariable String batchId) {
        return ApiResponse.ok(batchImportService.pause(SecurityConfig.currentAccount(), batchId));
    }

    @PostMapping("/import-batches/{batchId}/retry")
    public ApiResponse<BatchView> retryImportBatch(@PathVariable String batchId) {
        return ApiResponse.ok(batchImportService.retry(SecurityConfig.currentAccount(), batchId));
    }

    @GetMapping("/assets")
    public ApiResponse<PageResult<AssetView>> assets(@ModelAttribute PageQuery query) {
        return ApiResponse.ok(service.assets(SecurityConfig.currentAccount(), query));
    }

    @GetMapping("/families")
    public ApiResponse<List<TemplateAdminView>> templates() {
        return ApiResponse.ok(service.templates(SecurityConfig.currentAccount()));
    }

    @GetMapping("/malware-scanner/status")
    public ApiResponse<ScannerStatusView> malwareScannerStatus() {
        return ApiResponse.ok(service.malwareScannerStatus(SecurityConfig.currentAccount()));
    }

    @GetMapping("/evidence-artifacts")
    public ApiResponse<PageResult<EvidenceView>> evidence(@ModelAttribute PageQuery query) {
        return ApiResponse.ok(service.evidence(SecurityConfig.currentAccount(), query));
    }

    @PostMapping(value = "/evidence-artifacts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EvidenceView> uploadEvidence(
            @RequestParam("file") MultipartFile file,
            @RequestParam("evidenceType") String evidenceType,
            @RequestParam("description") String description) throws IOException {
        return ApiResponse.ok(service.uploadEvidence(SecurityConfig.currentAccount(),
                new UploadEvidenceCommand(evidenceType, description, file.getOriginalFilename(), file.getBytes())));
    }

    @GetMapping("/evidence-artifacts/{evidenceId}/download")
    public ResponseEntity<byte[]> downloadEvidence(@PathVariable String evidenceId) {
        EvidenceDownload evidence = service.downloadEvidence(SecurityConfig.currentAccount(), evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(evidence.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(evidence.body());
    }

    @PostMapping(value = "/assets/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AssetView> importAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sourceName", required = false) String sourceName,
            @RequestParam("sourceUri") String sourceUri,
            @RequestParam("licenseStatus") String licenseStatus,
            @RequestParam(value = "licenseEvidenceId", required = false) String licenseEvidenceId) throws IOException {
        return ApiResponse.ok(service.importCandidate(SecurityConfig.currentAccount(),
                new ImportAssetCommand(sourceName, sourceUri, licenseStatus, licenseEvidenceId,
                        file.getOriginalFilename(), file.getBytes())));
    }

    @PostMapping("/assets/{assetId}/review")
    public ApiResponse<AssetView> reviewAsset(@PathVariable String assetId,
            @Valid @RequestBody ReviewAssetRequest request) {
        return ApiResponse.ok(service.reviewAsset(SecurityConfig.currentAccount(), assetId,
                new ReviewAssetCommand(request.decision(), request.licenseStatus(),
                        request.licenseEvidenceId(), request.reason()), request.expectedVersion()));
    }

    @PostMapping("/assets/{assetId}/rescan")
    public ApiResponse<AssetView> rescanAsset(@PathVariable String assetId,
            @RequestBody VersionedRequest request) {
        return ApiResponse.ok(service.rescanAsset(
                SecurityConfig.currentAccount(), assetId, request.expectedVersion()));
    }

    @GetMapping("/{templateId}/versions")
    public ApiResponse<List<VersionView>> versions(@PathVariable String templateId) {
        return ApiResponse.ok(service.versions(SecurityConfig.currentAccount(), templateId));
    }

    @PostMapping("/{templateId}/versions")
    public ApiResponse<VersionView> createDraft(@PathVariable String templateId,
            @Valid @RequestBody DraftRequest request) {
        return ApiResponse.ok(service.createDraft(SecurityConfig.currentAccount(), templateId,
                new DraftCommand(request.rendererProtocol(), request.definitionJson(), request.thumbnailUri(),
                        request.sourceAssetId(), request.independentDesignEvidenceId())));
    }

    @PutMapping("/versions/{versionId}")
    public ApiResponse<VersionView> editDraft(@PathVariable String versionId,
            @Valid @RequestBody DraftRequest request) {
        return ApiResponse.ok(service.editDraft(SecurityConfig.currentAccount(), versionId,
                new DraftCommand(request.rendererProtocol(), request.definitionJson(), request.thumbnailUri(),
                        request.sourceAssetId(), request.independentDesignEvidenceId()),
                request.expectedVersion()));
    }

    @PostMapping("/versions/{versionId}/test")
    public ApiResponse<VersionView> recordTest(@PathVariable String versionId,
            @Valid @RequestBody TestRequest request) {
        return ApiResponse.ok(service.recordTest(SecurityConfig.currentAccount(), versionId,
                new TestCommand(request.gateCode(), request.outcome(), request.evidenceId(),
                        request.environmentJson(), request.summary()), request.expectedVersion()));
    }

    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<VersionView> publish(@PathVariable String versionId, @RequestBody VersionedRequest request) {
        return ApiResponse.ok(service.publish(SecurityConfig.currentAccount(), versionId, request.expectedVersion()));
    }

    @PostMapping("/versions/{versionId}/retire")
    public ApiResponse<VersionView> retire(@PathVariable String versionId, @RequestBody VersionedRequest request) {
        return ApiResponse.ok(service.retire(SecurityConfig.currentAccount(), versionId, request.expectedVersion()));
    }

    public record DraftRequest(@NotBlank String rendererProtocol, @NotBlank String definitionJson,
            String thumbnailUri, String sourceAssetId, String independentDesignEvidenceId,
            Integer expectedVersion) {}
    public record ReviewAssetRequest(@NotBlank String decision, String licenseStatus,
            String licenseEvidenceId, String reason, Integer expectedVersion) {}
    public record TestRequest(@NotBlank String gateCode, @NotBlank String outcome,
            @NotBlank String evidenceId, String environmentJson, @NotBlank String summary,
            Integer expectedVersion) {}
    public record VersionedRequest(Integer expectedVersion) {}
    public record CatalogMetadataRequest(@NotBlank String title,String summary,@NotBlank String assetKind,
            @NotBlank String languageCode,String pageCount,@NotBlank String photoPolicy,String thumbnailUri,
            List<CatalogFacetCommand> facets,Integer expectedVersion) {}
}
