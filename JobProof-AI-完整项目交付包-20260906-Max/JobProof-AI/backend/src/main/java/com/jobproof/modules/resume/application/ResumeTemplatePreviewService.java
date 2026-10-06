package com.jobproof.modules.resume.application;

import com.jobproof.modules.resume.domain.ResumeTemplateDocxInspector;
import com.jobproof.modules.resume.domain.ResumeTemplatePreviewRenderer;
import com.jobproof.modules.resume.domain.ResumeTemplatePreviewRenderer.RenderRequest;
import com.jobproof.modules.resume.domain.ResumeTemplatePreviewRenderer.RenderResult;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateAssetJpaRepository;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryEntity;
import com.jobproof.modules.resume.infra.ResumeTemplateCatalogEntryJpaRepository;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.time.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ResumeTemplatePreviewService {
    private static final Logger log = LoggerFactory.getLogger(ResumeTemplatePreviewService.class);
    private static final String ENTRY_TYPE = "DOCX_ASSET";
    private static final Set<String> STATUSES = Set.of("PENDING", "PROCESSING", "READY", "FAILED");
    private final ResumeTemplateCatalogEntryJpaRepository entries;
    private final ResumeTemplateAssetJpaRepository assets;
    private final ObjectStoragePort storage;
    private final ResumeTemplatePreviewRenderer renderer;
    private final ResumeTemplateCatalogService catalogService;
    private final ClockPort clock;

    public ResumeTemplatePreviewService(ResumeTemplateCatalogEntryJpaRepository entries,
            ResumeTemplateAssetJpaRepository assets, ObjectStoragePort storage,
            ResumeTemplatePreviewRenderer renderer, ResumeTemplateCatalogService catalogService, ClockPort clock) {
        this.entries = entries;
        this.assets = assets;
        this.storage = storage;
        this.renderer = renderer;
        this.catalogService = catalogService;
        this.clock = clock;
    }

    @Transactional
    public List<RenderRequest> claimNext(int batchSize) {
        Instant now = clock.now();
        recoverStaleClaims(now.minus(Duration.ofMinutes(20)));
        List<ResumeTemplateCatalogEntryEntity> candidates = entries
                .findByEntryTypeAndPreviewStatusOrderByPreviewUpdatedAtAsc(
                        ENTRY_TYPE, "PENDING", PageRequest.of(0, Math.max(1, Math.min(batchSize, 32))));
        List<RenderRequest> requests = new ArrayList<>();
        for (ResumeTemplateCatalogEntryEntity entry : candidates) {
            ResumeTemplateAssetEntity asset = assets.findById(entry.getReferenceId()).orElse(null);
            if (asset == null || asset.getStorageKey() == null || asset.getFileHash() == null
                    || !"APPROVED".equals(asset.getStatus()) || !"PASSED".equals(asset.getScanStatus())) {
                fail(entry, "PREVIEW_ASSET_GATE_FAILED", now);
                continue;
            }
            try {
                byte[] docx = storage.get(asset.getStorageKey());
                if (!asset.getFileHash().equals(ResumeTemplateDocxInspector.sha256(docx))) {
                    fail(entry, "PREVIEW_SOURCE_INTEGRITY_FAILED", now);
                    continue;
                }
                entry.setPreviewStatus("PROCESSING");
                entry.setPreviewError(null);
                entry.setPreviewUpdatedAt(now);
                entries.save(entry);
                requests.add(new RenderRequest(entry.getId(), asset.getFileHash(), docx));
            } catch (RuntimeException exception) {
                log.warn("Unable to load DOCX for preview catalogId={}", entry.getId(), exception);
                fail(entry, "PREVIEW_SOURCE_READ_FAILED", now);
            }
        }
        return requests;
    }

    @Transactional
    public void complete(RenderRequest request, RenderResult result) {
        ResumeTemplateCatalogEntryEntity entry = entries.findById(request.catalogEntryId()).orElse(null);
        if (entry == null || !"PROCESSING".equals(entry.getPreviewStatus())) return;
        Instant now = clock.now();
        if (result == null || !result.successful()) {
            fail(entry, result == null ? "PREVIEW_RESULT_MISSING" : sanitizeCode(result.errorCode()), now);
            catalogService.invalidatePublicSnapshot();
            return;
        }
        try {
            int pageNumber = 1;
            for (byte[] page : result.pages()) {
                assertPng(page);
                String key = previewKey(entry.getId(), request.fileHash(), pageNumber++);
                storage.put(key, page);
                registerRollbackCleanup(key);
            }
            entry.setPreviewStatus("READY");
            entry.setPreviewPageCount(result.pages().size());
            entry.setPageCount(Integer.toString(result.pages().size()));
            entry.setPreviewError(null);
            entry.setPreviewRendererVersion(renderer.version());
            entry.setPreviewUpdatedAt(now);
            entry.setThumbnailUri("/api/v1/template-catalog/" + entry.getId() + "/thumbnail");
            entry.setUpdatedAt(now);
            entries.save(entry);
            catalogService.invalidatePublicSnapshot();
        } catch (RuntimeException exception) {
            log.warn("Unable to store template preview catalogId={}", entry.getId(), exception);
            fail(entry, "PREVIEW_STORAGE_FAILED", now);
            catalogService.invalidatePublicSnapshot();
        }
    }

    @Transactional
    public PreviewStatusView status(CurrentAccount current) {
        assertAdmin(current);
        return statusView();
    }

    @Transactional
    public PreviewStatusView retryFailed(CurrentAccount current) {
        assertAdmin(current);
        Instant now = clock.now();
        List<ResumeTemplateCatalogEntryEntity> failed = entries
                .findByEntryTypeAndPreviewStatusOrderByPreviewUpdatedAtAsc(ENTRY_TYPE, "FAILED", PageRequest.of(0, Integer.MAX_VALUE));
        for (ResumeTemplateCatalogEntryEntity entry : failed) {
            entry.setPreviewStatus("PENDING");
            entry.setPreviewError(null);
            entry.setPreviewUpdatedAt(now);
            entries.save(entry);
        }
        catalogService.invalidatePublicSnapshot();
        return statusView();
    }

    private PreviewStatusView statusView() {
        Map<String, Long> counts = STATUSES.stream().collect(java.util.stream.Collectors.toMap(
                status -> status, status -> entries.countByEntryTypeAndPreviewStatus(ENTRY_TYPE, status)));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new PreviewStatusView(total, counts.get("PENDING"), counts.get("PROCESSING"),
                counts.get("READY"), counts.get("FAILED"), renderer.version());
    }

    private void recoverStaleClaims(Instant before) {
        for (ResumeTemplateCatalogEntryEntity entry : entries
                .findByEntryTypeAndPreviewStatusAndPreviewUpdatedAtBefore(ENTRY_TYPE, "PROCESSING", before)) {
            entry.setPreviewStatus("PENDING");
            entry.setPreviewError("STALE_PREVIEW_CLAIM_RECOVERED");
            entry.setPreviewUpdatedAt(clock.now());
            entries.save(entry);
        }
    }

    private void fail(ResumeTemplateCatalogEntryEntity entry, String code, Instant now) {
        entry.setPreviewStatus("FAILED");
        entry.setPreviewPageCount(0);
        entry.setPreviewError(sanitizeCode(code));
        entry.setPreviewRendererVersion(renderer.version());
        entry.setPreviewUpdatedAt(now);
        entry.setThumbnailUri(null);
        entry.setUpdatedAt(now);
        entries.save(entry);
    }

    private void registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) return;
                try { storage.delete(objectKey); }
                catch (RuntimeException exception) { log.warn("Preview rollback cleanup failed objectKey={}", objectKey, exception); }
            }
        });
    }

    private static void assertPng(byte[] body) {
        if (body == null || body.length < 8 || body[0] != (byte) 0x89 || body[1] != 0x50
                || body[2] != 0x4e || body[3] != 0x47 || body[4] != 0x0d || body[5] != 0x0a
                || body[6] != 0x1a || body[7] != 0x0a) {
            throw new IllegalArgumentException("Preview page is not a PNG");
        }
    }

    private static String sanitizeCode(String value) {
        if (value == null || value.isBlank()) return "PREVIEW_RENDER_FAILED";
        String safe = value.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        return safe.substring(0, Math.min(128, safe.length()));
    }

    private static void assertAdmin(CurrentAccount current) {
        if (current == null || !"ADMIN".equals(current.role()))
            throw AppException.forbidden("ADMIN_ONLY", "仅管理员可以管理模板预览");
    }

    public static String previewKey(String catalogEntryId, String hash, int pageNumber) {
        return "template-previews/" + catalogEntryId + "/" + hash + "/page-" + pageNumber + ".png";
    }

    public record PreviewStatusView(long total, long pending, long processing, long ready, long failed,
            String rendererVersion) {}
}
