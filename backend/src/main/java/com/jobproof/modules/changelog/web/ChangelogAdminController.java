package com.jobproof.modules.changelog.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.changelog.application.ChangelogService;
import com.jobproof.modules.changelog.application.ChangelogService.ReleaseCommand;
import com.jobproof.modules.changelog.application.ChangelogService.ReleaseDetail;
import com.jobproof.modules.changelog.application.ChangelogService.ReleaseSummary;
import com.jobproof.modules.changelog.application.ChangelogService.SectionCommand;
import com.jobproof.shared.page.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/changelog")
public class ChangelogAdminController {
    private final ChangelogService service;

    public ChangelogAdminController(ChangelogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<ReleaseSummary>> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(required = false) String type,
            @RequestParam(required = false) String module, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.adminList(SecurityConfig.currentAccount(), q, status, type, module, page, size));
    }

    @GetMapping("/overview")
    public ApiResponse<ChangelogService.AdminOverview> overview() {
        return ApiResponse.ok(service.adminOverview(SecurityConfig.currentAccount()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReleaseDetail> detail(@PathVariable String id) {
        return ApiResponse.ok(service.adminDetail(SecurityConfig.currentAccount(), id));
    }

    @PostMapping
    public ApiResponse<ReleaseDetail> create(@Valid @RequestBody ReleaseRequest request) {
        return ApiResponse.ok(service.create(SecurityConfig.currentAccount(), request.command()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReleaseDetail> update(@PathVariable String id, @Valid @RequestBody ReleaseRequest request) {
        return ApiResponse.ok(service.updateDraft(SecurityConfig.currentAccount(), id, request.command(), request.expectedVersion()));
    }

    @PostMapping("/{id}/validate")
    public ApiResponse<ChangelogService.ValidationView> validate(@PathVariable String id) {
        return ApiResponse.ok(service.validateRelease(SecurityConfig.currentAccount(), id));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ReleaseDetail> publish(@PathVariable String id, @RequestBody VersionRequest request) {
        return ApiResponse.ok(service.publish(SecurityConfig.currentAccount(), id, request.expectedVersion()));
    }

    @PostMapping("/{id}/publish-history")
    public ApiResponse<ReleaseDetail> publishHistory(@PathVariable String id,
            @Valid @RequestBody HistoricalPublishRequest request) {
        return ApiResponse.ok(service.publishHistorical(SecurityConfig.currentAccount(), id,
                request.expectedVersion(), request.publishedAt()));
    }

    @PostMapping("/{id}/schedule")
    public ApiResponse<ReleaseDetail> schedule(@PathVariable String id, @Valid @RequestBody ScheduleRequest request) {
        return ApiResponse.ok(service.schedule(SecurityConfig.currentAccount(), id, request.expectedVersion(), request.scheduledAt()));
    }

    @PostMapping("/{id}/cancel-schedule")
    public ApiResponse<ReleaseDetail> cancelSchedule(@PathVariable String id, @RequestBody VersionRequest request) {
        return ApiResponse.ok(service.cancelSchedule(SecurityConfig.currentAccount(), id, request.expectedVersion()));
    }

    @PostMapping("/{id}/revise")
    public ApiResponse<ReleaseDetail> revise(@PathVariable String id, @Valid @RequestBody RevisionRequest request) {
        return ApiResponse.ok(service.revise(SecurityConfig.currentAccount(), id, request.release().command(),
                request.reason(), request.release().expectedVersion()));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<ReleaseDetail> archive(@PathVariable String id, @RequestBody VersionRequest request) {
        return ApiResponse.ok(service.archive(SecurityConfig.currentAccount(), id, request.expectedVersion()));
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<ReleaseDetail> copy(@PathVariable String id, @Valid @RequestBody CopyRequest request) {
        return ApiResponse.ok(service.copy(SecurityConfig.currentAccount(), id, request.versionLabel()));
    }

    @PostMapping(value = "/{id}/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ChangelogService.AssetView> asset(@PathVariable String id, @RequestParam("file") MultipartFile file)
            throws IOException {
        return ApiResponse.ok(service.uploadAsset(SecurityConfig.currentAccount(), id, file.getOriginalFilename(), file.getBytes()));
    }

    @GetMapping("/{id}/assets/{assetId}")
    public ResponseEntity<byte[]> asset(@PathVariable String id, @PathVariable String assetId) {
        var asset = service.adminAsset(SecurityConfig.currentAccount(), id, assetId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(asset.filename(), java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .body(asset.content());
    }

    public record SectionRequest(@NotBlank String sectionType, @NotBlank String title, String body, List<String> items,
            String imageAssetId, String imageAlt) {
        SectionCommand command() { return new SectionCommand(sectionType, title, body, items, imageAssetId, imageAlt); }
    }

    public record ReleaseRequest(@NotBlank String versionLabel, @NotBlank String title, @NotBlank String summary,
            @NotBlank String releaseType, @NotBlank String audience, List<String> modules, String ctaLabel,
            String ctaPath, boolean showWhatsNew, boolean sendNotification, List<SectionRequest> sections,
            int expectedVersion) {
        ReleaseCommand command() {
            return new ReleaseCommand(versionLabel, title, summary, releaseType, audience, modules, ctaLabel, ctaPath,
                    showWhatsNew, sendNotification,
                    sections == null ? List.of() : sections.stream().map(SectionRequest::command).toList());
        }
    }

    public record VersionRequest(int expectedVersion) {}
    public record HistoricalPublishRequest(int expectedVersion, @NotNull Instant publishedAt) {}
    public record ScheduleRequest(int expectedVersion, @NotNull Instant scheduledAt) {}
    public record RevisionRequest(@NotBlank String reason, @NotNull ReleaseRequest release) {}
    public record CopyRequest(@NotBlank String versionLabel) {}
}
