package com.jobproof.modules.changelog.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.changelog.application.ChangelogService;
import com.jobproof.modules.changelog.application.ChangelogService.AssetDownload;
import com.jobproof.modules.changelog.application.ChangelogService.AssetView;
import com.jobproof.modules.changelog.application.ChangelogService.ReleaseCommand;
import com.jobproof.modules.changelog.application.ChangelogService.ReleaseDetail;
import com.jobproof.modules.changelog.application.ChangelogService.ReleaseSummary;
import com.jobproof.modules.changelog.application.ChangelogService.SectionCommand;
import com.jobproof.shared.page.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/updates")
public class ChangelogController {
    private final ChangelogService service;

    public ChangelogController(ChangelogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<ReleaseSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String versionFrom,
            @RequestParam(required = false) String versionTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant publishedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant publishedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.publicList(SecurityConfig.currentAccountOrNull(), q, type, module, versionFrom,
                versionTo, publishedFrom, publishedTo, page, size));
    }

    @GetMapping("/facets")
    public ApiResponse<ChangelogService.FacetsView> facets() {
        return ApiResponse.ok(service.facets(SecurityConfig.currentAccountOrNull()));
    }

    @GetMapping("/latest")
    public ApiResponse<ReleaseDetail> latest() {
        return ApiResponse.ok(service.latest(SecurityConfig.currentAccountOrNull()));
    }

    @GetMapping("/whats-new")
    public ApiResponse<ReleaseDetail> whatsNew() {
        return ApiResponse.ok(service.whatsNew(SecurityConfig.currentAccountOrNull()));
    }

    @GetMapping("/assets/{id}")
    public ResponseEntity<byte[]> asset(@PathVariable String id) {
        AssetDownload asset = service.publicAsset(SecurityConfig.currentAccountOrNull(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.contentType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(asset.filename(), StandardCharsets.UTF_8).build().toString())
                .body(asset.content());
    }

    @GetMapping("/{version}")
    public ApiResponse<ReleaseDetail> detail(@PathVariable String version) {
        return ApiResponse.ok(service.publicDetail(SecurityConfig.currentAccountOrNull(), version));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id) {
        service.markRead(SecurityConfig.currentAccount(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/acknowledge")
    public ApiResponse<Void> acknowledge(@PathVariable String id) {
        service.acknowledge(SecurityConfig.currentAccount(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/remind-later")
    public ApiResponse<Void> remindLater(@PathVariable String id) {
        service.remindLater(SecurityConfig.currentAccount(), id);
        return ApiResponse.ok(null);
    }
}
