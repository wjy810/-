package com.jobproof.modules.changelog.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.infrastructure.queue.OutboxService;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.event.EventTypes;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.page.PageResult;
import com.jobproof.shared.time.ClockPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangelogService {

    private static final Pattern VERSION = Pattern.compile("^v?\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?$");
    private static final Set<String> RELEASE_TYPES = Set.of(
            "FEATURE", "IMPROVEMENT", "FIX", "SECURITY", "DEPRECATED", "PLANNED");
    private static final Set<String> SECTION_TYPES = Set.of(
            "HIGHLIGHTS", "FEATURES", "IMPROVEMENTS", "FIXES", "IMPORTANT", "COMPATIBILITY");
    private static final Set<String> AUDIENCES = Set.of("PUBLIC", "AUTHENTICATED");
    private static final Set<String> MODULES = Set.of(
            "AI_RESUME", "CAREER_LIBRARY", "JOB_MATCHING", "MOCK_INTERVIEW", "CAREER_PLANNING",
            "TEMPLATE_CENTER", "ACCOUNT", "NOTIFICATIONS", "PLATFORM");
    private static final Set<String> STATUSES = Set.of("DRAFT", "SCHEDULED", "PUBLISHED", "ARCHIVED");
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ClockPort clock;
    private final AuditService audit;
    private final OutboxService outbox;
    private final NotificationService notifications;
    private final ObjectStoragePort storage;

    public ChangelogService(JdbcTemplate jdbc, ObjectMapper mapper, ClockPort clock, AuditService audit,
            OutboxService outbox, NotificationService notifications, ObjectStoragePort storage) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.audit = audit;
        this.outbox = outbox;
        this.notifications = notifications;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public PageResult<ReleaseSummary> publicList(CurrentAccount current, String keyword, String type, String module, String versionFrom,
            String versionTo, Instant publishedFrom, Instant publishedTo, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<ReleaseSummary> filtered = publicSummaries(current).stream()
                .filter(row -> contains(row, keyword))
                .filter(row -> blank(type) || row.releaseType().equals(normalize(type)))
                .filter(row -> blank(module) || row.modules().contains(normalize(module)))
                .filter(row -> blank(versionFrom) || compareVersion(row.versionLabel(), versionFrom) >= 0)
                .filter(row -> blank(versionTo) || compareVersion(row.versionLabel(), versionTo) <= 0)
                .filter(row -> publishedFrom == null || (row.publishedAt() != null && !row.publishedAt().isBefore(publishedFrom)))
                .filter(row -> publishedTo == null || (row.publishedAt() != null && !row.publishedAt().isAfter(publishedTo)))
                .toList();
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        return new PageResult<>(filtered.subList(from, to), filtered.size(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public FacetsView facets(CurrentAccount current) {
        List<ReleaseSummary> releases = publicSummaries(current);
        return new FacetsView(count(releases.stream().map(ReleaseSummary::releaseType).toList()),
                count(releases.stream().flatMap(row -> row.modules().stream()).toList()),
                releases.stream().map(ReleaseSummary::versionLabel).toList());
    }

    @Transactional(readOnly = true)
    public ReleaseDetail latest(CurrentAccount current) {
        return publicSummaries(current).stream().findFirst().map(row -> detail(row.id(), true, current)).orElse(null);
    }

    @Transactional(readOnly = true)
    public ReleaseDetail publicDetail(CurrentAccount current, String versionOrSlug) {
        String id = jdbc.query("SELECT id FROM changelog_releases WHERE (version_label=? OR slug=?) AND status IN ('PUBLISHED','ARCHIVED')",
                rs -> rs.next() ? rs.getString(1) : null, versionOrSlug, versionOrSlug);
        if (id == null) throw AppException.user("CHANGELOG_NOT_FOUND", "更新版本不存在");
        return detail(id, true, current);
    }

    @Transactional
    public ReleaseDetail whatsNew(CurrentAccount current) {
        if (current == null) return null;
        Instant now = clock.now();
        List<String> ids = jdbc.query("""
                SELECT r.id FROM changelog_releases r
                LEFT JOIN changelog_user_receipts u ON u.release_id=r.id AND u.account_id=?
                WHERE r.status='PUBLISHED' AND r.show_whats_new=TRUE
                  AND r.audience IN ('PUBLIC','AUTHENTICATED')
                  AND u.acknowledged_at IS NULL
                  AND (u.remind_after IS NULL OR u.remind_after<=?)
                ORDER BY r.published_at DESC
                """, (rs, n) -> rs.getString(1), current.accountId(), now);
        if (ids.isEmpty()) return null;
        String releaseId = ids.get(0);
        ensureReceipt(releaseId, current.accountId(), now);
        return detail(releaseId, true, current);
    }

    @Transactional
    public void markRead(CurrentAccount current, String releaseId) {
        assertReleaseVisible(releaseId);
        Instant now = clock.now();
        ensureReceipt(releaseId, current.accountId(), now);
        jdbc.update("UPDATE changelog_user_receipts SET read_at=COALESCE(read_at,?),updated_at=? WHERE release_id=? AND account_id=?",
                now, now, releaseId, current.accountId());
        notifications.markReadByEvent(current.accountId(), NotificationType.PRODUCT_UPDATE, releaseId);
    }

    @Transactional
    public void acknowledge(CurrentAccount current, String releaseId) {
        markRead(current, releaseId);
        Instant now = clock.now();
        jdbc.update("UPDATE changelog_user_receipts SET acknowledged_at=COALESCE(acknowledged_at,?),remind_after=NULL,updated_at=? WHERE release_id=? AND account_id=?",
                now, now, releaseId, current.accountId());
    }

    @Transactional
    public void remindLater(CurrentAccount current, String releaseId) {
        assertReleaseVisible(releaseId);
        Instant now = clock.now();
        ensureReceipt(releaseId, current.accountId(), now);
        jdbc.update("UPDATE changelog_user_receipts SET remind_after=?,updated_at=? WHERE release_id=? AND account_id=? AND acknowledged_at IS NULL",
                now.plus(7, ChronoUnit.DAYS), now, releaseId, current.accountId());
    }

    @Transactional(readOnly = true)
    public PageResult<ReleaseSummary> adminList(CurrentAccount current, String keyword, String status, String type,
            String module, int page, int size) {
        assertAdmin(current);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        List<ReleaseSummary> filtered = allSummaries(false).stream()
                .filter(row -> contains(row, keyword))
                .filter(row -> blank(status) || row.status().equals(normalize(status)))
                .filter(row -> blank(type) || row.releaseType().equals(normalize(type)))
                .filter(row -> blank(module) || row.modules().contains(normalize(module)))
                .toList();
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        return new PageResult<>(filtered.subList(from, to), filtered.size(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public AdminOverview adminOverview(CurrentAccount current) {
        assertAdmin(current);
        Map<String, Long> statuses = count(allSummaries(false).stream().map(ReleaseSummary::status).toList());
        Long reads = jdbc.queryForObject("SELECT COUNT(*) FROM changelog_user_receipts WHERE read_at IS NOT NULL", Long.class);
        return new AdminOverview(statuses.getOrDefault("PUBLISHED", 0L), statuses.getOrDefault("DRAFT", 0L),
                statuses.getOrDefault("SCHEDULED", 0L), reads == null ? 0 : reads);
    }

    @Transactional(readOnly = true)
    public ReleaseDetail adminDetail(CurrentAccount current, String id) {
        assertAdmin(current);
        return detail(id, false, current);
    }

    @Transactional
    public ReleaseDetail create(CurrentAccount current, ReleaseCommand command) {
        assertAdmin(current);
        Validated valid = validate(command, false);
        if (valid.sections().stream().anyMatch(section -> section.imageAssetId() != null)) {
            throw AppException.user("CHANGELOG_ASSET_RELEASE_REQUIRED", "请先创建草稿，再为内容区块上传图片");
        }
        Instant now = clock.now();
        String id = Ids.newId();
        String slug = slug(valid.versionLabel());
        try {
            jdbc.update("""
                    INSERT INTO changelog_releases(id,version_label,slug,title,summary,release_type,status,audience,
                    modules_json,cta_label,cta_path,show_whats_new,send_notification,scheduled_at,published_at,
                    archived_at,current_revision,version_no,created_by,updated_by,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?,NULL,NULL,NULL,1,0,?,?,?,?)
                    """, id, valid.versionLabel(), slug, valid.title(), valid.summary(), valid.releaseType(),
                    valid.audience(), json(valid.modules()), valid.ctaLabel(), valid.ctaPath(), valid.showWhatsNew(),
                    valid.sendNotification(), current.accountId(), current.accountId(), now, now);
            insertRevision(id, 1, valid, null, current.accountId(), now);
        } catch (DataIntegrityViolationException ex) {
            throw AppException.conflict("CHANGELOG_VERSION_EXISTS", "版本号已存在");
        }
        audit.append(current.accountId(), "CHANGELOG_CREATED", "CHANGELOG_RELEASE", id, "version=" + valid.versionLabel());
        return detail(id, false, current);
    }

    @Transactional
    public ReleaseDetail updateDraft(CurrentAccount current, String id, ReleaseCommand command, int expectedVersion) {
        assertAdmin(current);
        ReleaseSummary existing = summary(id);
        if (!"DRAFT".equals(existing.status())) throw AppException.conflict("CHANGELOG_NOT_DRAFT", "只有草稿可以编辑");
        Validated valid = validate(command, false);
        assertSectionAssetsOwned(id, valid.sections());
        Instant now = clock.now();
        int changed = jdbc.update("""
                UPDATE changelog_releases SET version_label=?,slug=?,title=?,summary=?,release_type=?,audience=?,
                modules_json=?,cta_label=?,cta_path=?,show_whats_new=?,send_notification=?,updated_by=?,updated_at=?,version_no=version_no+1
                WHERE id=? AND status='DRAFT' AND version_no=?
                """, valid.versionLabel(), slug(valid.versionLabel()), valid.title(), valid.summary(), valid.releaseType(),
                valid.audience(), json(valid.modules()), valid.ctaLabel(), valid.ctaPath(), valid.showWhatsNew(),
                valid.sendNotification(), current.accountId(), now, id, expectedVersion);
        if (changed != 1) throw AppException.conflict("VERSION_CONFLICT", "草稿已被其他操作修改");
        replaceDraftRevision(id, existing.currentRevision(), valid, current.accountId(), now);
        return detail(id, false, current);
    }

    @Transactional
    public ValidationView validateRelease(CurrentAccount current, String id) {
        assertAdmin(current);
        return validateReleaseInternal(id);
    }

    @Transactional
    public ReleaseDetail schedule(CurrentAccount current, String id, int expectedVersion, Instant scheduledAt) {
        assertAdmin(current);
        assertPublishable(current, id);
        if (scheduledAt == null || !scheduledAt.isAfter(clock.now()))
            throw AppException.user("CHANGELOG_SCHEDULE_INVALID", "定时发布时间必须晚于当前时间");
        int changed = jdbc.update("UPDATE changelog_releases SET status='SCHEDULED',scheduled_at=?,updated_by=?,updated_at=?,version_no=version_no+1 WHERE id=? AND status='DRAFT' AND version_no=?",
                scheduledAt, current.accountId(), clock.now(), id, expectedVersion);
        if (changed != 1) throw AppException.conflict("VERSION_CONFLICT", "版本状态已发生变化");
        audit.append(current.accountId(), "CHANGELOG_SCHEDULED", "CHANGELOG_RELEASE", id, "scheduledAt=" + scheduledAt);
        return detail(id, false, current);
    }

    @Transactional
    public ReleaseDetail cancelSchedule(CurrentAccount current, String id, int expectedVersion) {
        assertAdmin(current);
        int changed = jdbc.update("UPDATE changelog_releases SET status='DRAFT',scheduled_at=NULL,updated_by=?,updated_at=?,version_no=version_no+1 WHERE id=? AND status='SCHEDULED' AND version_no=?",
                current.accountId(), clock.now(), id, expectedVersion);
        if (changed != 1) throw AppException.conflict("VERSION_CONFLICT", "版本状态已发生变化");
        audit.append(current.accountId(), "CHANGELOG_SCHEDULE_CANCELLED", "CHANGELOG_RELEASE", id, "schedule cancelled");
        return detail(id, false, current);
    }

    @Transactional
    public ReleaseDetail publish(CurrentAccount current, String id, int expectedVersion) {
        assertAdmin(current);
        assertPublishable(current, id);
        publishInternal(id, current.accountId(), expectedVersion, Set.of("DRAFT", "SCHEDULED"), clock.now(),
                "CHANGELOG_PUBLISHED");
        return detail(id, false, current);
    }

    @Transactional
    public ReleaseDetail publishHistorical(CurrentAccount current, String id, int expectedVersion, Instant publishedAt) {
        assertAdmin(current);
        ReleaseSummary existing = summary(id);
        Instant now = clock.now();
        if (publishedAt == null || publishedAt.isAfter(now))
            throw AppException.user("CHANGELOG_HISTORY_DATE_INVALID", "历史发布日期必须是当前时间或过去时间");
        if (existing.showWhatsNew() || existing.sendNotification())
            throw AppException.user("CHANGELOG_HISTORY_MUST_BE_SILENT", "历史补录不能发送通知或显示版本亮点弹窗");
        assertPublishable(current, id);
        publishInternal(id, current.accountId(), expectedVersion, Set.of("DRAFT"), publishedAt,
                "CHANGELOG_HISTORY_PUBLISHED");
        return detail(id, false, current);
    }

    @Transactional
    public ReleaseDetail revise(CurrentAccount current, String id, ReleaseCommand command, String reason, int expectedVersion) {
        assertAdmin(current);
        ReleaseSummary existing = summary(id);
        if (!"PUBLISHED".equals(existing.status())) throw AppException.conflict("CHANGELOG_NOT_PUBLISHED", "只有已发布版本可以创建修订");
        if (clean(reason, 500).length() < 4) throw AppException.user("CHANGELOG_REASON_REQUIRED", "修订原因至少 4 个字");
        Validated valid = validate(command, true);
        assertSectionAssetsOwned(id, valid.sections());
        int revision = existing.currentRevision() + 1;
        Instant now = clock.now();
        int changed = jdbc.update("""
                UPDATE changelog_releases SET title=?,summary=?,release_type=?,audience=?,modules_json=?,cta_label=?,cta_path=?,
                show_whats_new=?,send_notification=?,current_revision=?,updated_by=?,updated_at=?,version_no=version_no+1
                WHERE id=? AND status='PUBLISHED' AND version_no=?
                """, valid.title(), valid.summary(), valid.releaseType(), valid.audience(), json(valid.modules()),
                valid.ctaLabel(), valid.ctaPath(), valid.showWhatsNew(), valid.sendNotification(), revision,
                current.accountId(), now, id, expectedVersion);
        if (changed != 1) throw AppException.conflict("VERSION_CONFLICT", "版本已被其他操作修改");
        insertRevision(id, revision, valid, clean(reason, 500), current.accountId(), now);
        audit.append(current.accountId(), "CHANGELOG_REVISED", "CHANGELOG_RELEASE", id, "revision=" + revision + ", reason=" + clean(reason, 200));
        return detail(id, false, current);
    }

    @Transactional
    public ReleaseDetail archive(CurrentAccount current, String id, int expectedVersion) {
        assertAdmin(current);
        Instant now = clock.now();
        int changed = jdbc.update("UPDATE changelog_releases SET status='ARCHIVED',archived_at=?,show_whats_new=FALSE,updated_by=?,updated_at=?,version_no=version_no+1 WHERE id=? AND status='PUBLISHED' AND version_no=?",
                now, current.accountId(), now, id, expectedVersion);
        if (changed != 1) throw AppException.conflict("VERSION_CONFLICT", "版本状态已发生变化");
        audit.append(current.accountId(), "CHANGELOG_ARCHIVED", "CHANGELOG_RELEASE", id, "archived");
        return detail(id, false, current);
    }

    @Transactional
    public ReleaseDetail copy(CurrentAccount current, String id, String versionLabel) {
        assertAdmin(current);
        ReleaseDetail source = detail(id, false, current);
        ReleaseCommand command = new ReleaseCommand(versionLabel, source.release().title() + " 副本",
                source.release().summary(), source.release().releaseType(), source.release().audience(),
                source.release().modules(), source.release().ctaLabel(), source.release().ctaPath(), false, false,
                source.sections().stream().map(s -> new SectionCommand(s.sectionType(), s.title(), s.body(), s.items(),
                        null, null)).toList());
        return create(current, command);
    }

    @Transactional
    public AssetView uploadAsset(CurrentAccount current, String releaseId, String filename, byte[] content) {
        assertAdmin(current);
        summary(releaseId);
        if (content == null || content.length == 0 || content.length > 5 * 1024 * 1024)
            throw AppException.user("CHANGELOG_ASSET_SIZE_INVALID", "图片不能为空且不能超过 5 MiB");
        String contentType = imageType(content);
        String id = Ids.newId();
        String ext = switch (contentType) { case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".jpg"; };
        String objectKey = "changelog/" + releaseId + "/" + id + ext;
        storage.put(objectKey, content);
        Instant now = clock.now();
        String safeFilename = clean(filename, 255);
        if (safeFilename.isBlank()) safeFilename = "update-image" + ext;
        jdbc.update("INSERT INTO changelog_assets(id,release_id,object_key,filename,content_type,size_bytes,sha256,status,created_by,created_at) VALUES(?,?,?,?,?,?,?,'READY',?,?)",
                id, releaseId, objectKey, safeFilename, contentType, content.length, sha256(content), current.accountId(), now);
        audit.append(current.accountId(), "CHANGELOG_ASSET_UPLOADED", "CHANGELOG_ASSET", id, "releaseId=" + releaseId);
        return new AssetView(id, releaseId, safeFilename, contentType, content.length,
                "/api/v1/updates/assets/" + id, "READY");
    }

    @Transactional(readOnly = true)
    public AssetDownload publicAsset(CurrentAccount current, String id) {
        return jdbc.query("""
                SELECT a.object_key,a.content_type,a.filename,r.audience FROM changelog_assets a
                JOIN changelog_releases r ON r.id=a.release_id
                WHERE a.id=? AND a.status='READY' AND r.status IN ('PUBLISHED','ARCHIVED')
                """, rs -> {
            if (!rs.next()) throw AppException.user("CHANGELOG_ASSET_NOT_FOUND", "更新图片不存在");
            if ("AUTHENTICATED".equals(rs.getString(4)) && current == null)
                throw AppException.user("CHANGELOG_ASSET_NOT_FOUND", "更新图片不存在");
            return new AssetDownload(rs.getString(3), rs.getString(2), storage.get(rs.getString(1)));
        }, id);
    }

    @Transactional(readOnly = true)
    public AssetDownload adminAsset(CurrentAccount current, String releaseId, String id) {
        assertAdmin(current);
        return jdbc.query("""
                SELECT object_key,content_type,filename FROM changelog_assets
                WHERE id=? AND release_id=? AND status='READY'
                """, rs -> {
            if (!rs.next()) throw AppException.user("CHANGELOG_ASSET_NOT_FOUND", "更新图片不存在");
            return new AssetDownload(rs.getString(3), rs.getString(2), storage.get(rs.getString(1)));
        }, id, releaseId);
    }

    @Transactional
    public int publishDue() {
        Instant now = clock.now();
        List<ReleaseSummary> due = allSummaries(false).stream()
                .filter(r -> "SCHEDULED".equals(r.status()) && r.scheduledAt() != null && !r.scheduledAt().isAfter(now))
                .toList();
        int published = 0;
        for (ReleaseSummary release : due) {
            try {
                assertPublishable(null, release.id());
                publishInternal(release.id(), release.updatedBy(), release.versionNo(), Set.of("SCHEDULED"), now,
                        "CHANGELOG_PUBLISHED");
                published++;
            } catch (AppException ignored) {
                // Another worker or a validation change won the race.
            }
        }
        return published;
    }

    private void publishInternal(String id, String actorId, int expectedVersion, Set<String> expectedStatuses,
            Instant publishedAt, String auditAction) {
        ReleaseSummary existing = summary(id);
        if (!expectedStatuses.contains(existing.status())) throw AppException.conflict("CHANGELOG_STATE_INVALID", "当前状态不能发布");
        Instant now = clock.now();
        int changed = jdbc.update("UPDATE changelog_releases SET status='PUBLISHED',scheduled_at=NULL,published_at=COALESCE(published_at,?),updated_by=?,updated_at=?,version_no=version_no+1 WHERE id=? AND status=? AND version_no=?",
                publishedAt, actorId, now, id, existing.status(), expectedVersion);
        if (changed != 1) throw AppException.conflict("VERSION_CONFLICT", "版本状态已发生变化");
        outbox.enqueue(EventTypes.CHANGELOG_PUBLISHED, Map.of("releaseId", id));
        audit.append(actorId, auditAction, "CHANGELOG_RELEASE", id,
                "version=" + existing.versionLabel() + ", publishedAt=" + publishedAt);
    }

    private void assertPublishable(CurrentAccount current, String id) {
        if (current != null) assertAdmin(current);
        ValidationView validation = validateReleaseInternal(id);
        if (!validation.valid()) throw AppException.user("CHANGELOG_VALIDATION_FAILED", String.join("；", validation.issues()));
    }

    private ValidationView validateReleaseInternal(String id) {
        ReleaseDetail detail = detail(id, false, null);
        List<String> issues = new ArrayList<>();
        if (detail.sections().isEmpty()) issues.add("至少需要一个更新内容区块");
        if (detail.release().summary().length() < 10) issues.add("摘要至少 10 个字");
        if (detail.release().modules().isEmpty()) issues.add("至少选择一个产品模块");
        if (detail.release().showWhatsNew() && detail.sections().stream().noneMatch(s -> "HIGHLIGHTS".equals(s.sectionType())))
            issues.add("版本亮点弹窗需要 HIGHLIGHTS 区块");
        try {
            assertSectionAssetsOwned(id, detail.sections().stream()
                    .map(section -> new SectionCommand(section.sectionType(), section.title(), section.body(),
                            section.items(), section.imageAssetId(), section.imageAlt()))
                    .toList());
        } catch (AppException exception) {
            issues.add(exception.getMessage());
        }
        return new ValidationView(issues.isEmpty(), issues);
    }

    private ReleaseDetail detail(String id, boolean publicOnly, CurrentAccount current) {
        ReleaseSummary release = summary(id);
        if (publicOnly && !Set.of("PUBLISHED", "ARCHIVED").contains(release.status()))
            throw AppException.user("CHANGELOG_NOT_FOUND", "更新版本不存在");
        if (publicOnly && "AUTHENTICATED".equals(release.audience()) && current == null)
            throw AppException.user("CHANGELOG_NOT_FOUND", "更新版本不存在");
        String revisionId = jdbc.query("SELECT id FROM changelog_revisions WHERE release_id=? AND revision_no=?",
                rs -> rs.next() ? rs.getString(1) : null, id, release.currentRevision());
        List<SectionView> sections = revisionId == null ? List.of() : jdbc.query(
                "SELECT id,section_type,title,body_text,items_json,sort_order,image_asset_id,image_alt FROM changelog_sections WHERE revision_id=? ORDER BY sort_order",
                (rs, n) -> new SectionView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        strings(rs.getString(5)), rs.getInt(6), rs.getString(7), rs.getString(8)), revisionId);
        List<ReleaseSummary> publicRows = publicSummaries(current);
        int position = -1;
        for (int i = 0; i < publicRows.size(); i++) if (publicRows.get(i).id().equals(id)) position = i;
        VersionLink previous = position >= 0 && position + 1 < publicRows.size() ? link(publicRows.get(position + 1)) : null;
        VersionLink next = position > 0 ? link(publicRows.get(position - 1)) : null;
        return new ReleaseDetail(release, sections, previous, next);
    }

    private ReleaseSummary summary(String id) {
        List<ReleaseSummary> rows = allSummaries(false).stream().filter(row -> row.id().equals(id)).toList();
        if (rows.isEmpty()) throw AppException.user("CHANGELOG_NOT_FOUND", "更新版本不存在");
        return rows.get(0);
    }

    private List<ReleaseSummary> allSummaries(boolean publicOnly) {
        String sql = """
                SELECT r.id,r.version_label,r.slug,r.title,r.summary,r.release_type,r.status,r.audience,r.modules_json,
                r.cta_label,r.cta_path,r.show_whats_new,r.send_notification,r.scheduled_at,r.published_at,r.archived_at,
                r.current_revision,r.version_no,r.created_at,r.updated_at,r.updated_by,
                (SELECT COUNT(*) FROM changelog_user_receipts u WHERE u.release_id=r.id AND u.read_at IS NOT NULL) read_count,
                (SELECT MAX(d.status) FROM changelog_distribution_jobs d WHERE d.release_id=r.id) distribution_status
                FROM changelog_releases r
                """ + (publicOnly ? " WHERE r.status='PUBLISHED' " : "")
                + " ORDER BY CASE WHEN r.published_at IS NULL THEN 1 ELSE 0 END,r.published_at DESC,r.updated_at DESC";
        return jdbc.query(sql, (rs, n) -> new ReleaseSummary(
                rs.getString("id"), rs.getString("version_label"), rs.getString("slug"), rs.getString("title"),
                rs.getString("summary"), rs.getString("release_type"), rs.getString("status"), rs.getString("audience"),
                strings(rs.getString("modules_json")), rs.getString("cta_label"), rs.getString("cta_path"),
                rs.getBoolean("show_whats_new"), rs.getBoolean("send_notification"),
                instant(rs.getTimestamp("scheduled_at")), instant(rs.getTimestamp("published_at")),
                instant(rs.getTimestamp("archived_at")), rs.getInt("current_revision"), rs.getInt("version_no"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")), rs.getString("updated_by"),
                rs.getLong("read_count"), rs.getString("distribution_status")));
    }

    private List<ReleaseSummary> publicSummaries(CurrentAccount current) {
        return allSummaries(true).stream()
                .filter(release -> "PUBLIC".equals(release.audience()) || current != null)
                .toList();
    }

    private void replaceDraftRevision(String releaseId, int revisionNo, Validated valid, String actorId, Instant now) {
        String revisionId = jdbc.query("SELECT id FROM changelog_revisions WHERE release_id=? AND revision_no=?",
                rs -> rs.next() ? rs.getString(1) : null, releaseId, revisionNo);
        if (revisionId == null) {
            insertRevision(releaseId, revisionNo, valid, null, actorId, now);
            return;
        }
        jdbc.update("DELETE FROM changelog_sections WHERE revision_id=?", revisionId);
        jdbc.update("UPDATE changelog_revisions SET title=?,summary=?,release_type=?,modules_json=?,cta_label=?,cta_path=?,content_hash=?,created_by=?,created_at=? WHERE id=?",
                valid.title(), valid.summary(), valid.releaseType(), json(valid.modules()), valid.ctaLabel(), valid.ctaPath(),
                contentHash(valid), actorId, now, revisionId);
        insertSections(revisionId, valid.sections());
    }

    private void insertRevision(String releaseId, int revisionNo, Validated valid, String reason, String actorId, Instant now) {
        String revisionId = Ids.newId();
        jdbc.update("INSERT INTO changelog_revisions(id,release_id,revision_no,title,summary,release_type,modules_json,cta_label,cta_path,correction_reason,content_hash,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                revisionId, releaseId, revisionNo, valid.title(), valid.summary(), valid.releaseType(), json(valid.modules()),
                valid.ctaLabel(), valid.ctaPath(), reason, contentHash(valid), actorId, now);
        insertSections(revisionId, valid.sections());
    }

    private void insertSections(String revisionId, List<SectionCommand> sections) {
        for (int i = 0; i < sections.size(); i++) {
            SectionCommand section = sections.get(i);
            jdbc.update("INSERT INTO changelog_sections(id,revision_id,section_type,title,body_text,items_json,sort_order,image_asset_id,image_alt) VALUES(?,?,?,?,?,?,?,?,?)",
                    Ids.newId(), revisionId, section.sectionType(), section.title(), section.body(), json(section.items()), i,
                    section.imageAssetId(), section.imageAlt());
        }
    }

    private Validated validate(ReleaseCommand command, boolean keepVersion) {
        if (command == null) throw AppException.user("CHANGELOG_INVALID", "版本内容不能为空");
        String version = normalizeVersion(command.versionLabel());
        String title = clean(command.title(), 255);
        String summary = clean(command.summary(), 500);
        String type = normalize(command.releaseType());
        String audience = normalize(command.audience());
        if (!VERSION.matcher(version).matches()) throw AppException.user("CHANGELOG_VERSION_INVALID", "版本号格式应为 v1.2.3");
        if (title.length() < 2) throw AppException.user("CHANGELOG_TITLE_INVALID", "标题至少 2 个字");
        if (summary.length() < 2) throw AppException.user("CHANGELOG_SUMMARY_INVALID", "摘要至少 2 个字");
        if (!RELEASE_TYPES.contains(type)) throw AppException.user("CHANGELOG_TYPE_INVALID", "更新类型不受支持");
        if (!AUDIENCES.contains(audience)) throw AppException.user("CHANGELOG_AUDIENCE_INVALID", "受众不受支持");
        List<String> modules = command.modules() == null ? List.of() : command.modules().stream()
                .map(ChangelogService::normalize).filter(MODULES::contains).distinct().toList();
        List<SectionCommand> sections = new ArrayList<>();
        if (command.sections() != null) {
            for (SectionCommand raw : command.sections()) {
                String sectionType = normalize(raw.sectionType());
                if (!SECTION_TYPES.contains(sectionType)) throw AppException.user("CHANGELOG_SECTION_INVALID", "更新区块类型不受支持");
                String sectionTitle = clean(raw.title(), 120);
                String body = clean(raw.body(), 4000);
                List<String> items = raw.items() == null ? List.of() : raw.items().stream()
                        .map(item -> clean(item, 500)).filter(item -> !item.isBlank()).limit(20).toList();
                String imageAssetId = cleanNullable(raw.imageAssetId(), 36);
                String imageAlt = cleanNullable(raw.imageAlt(), 255);
                if (imageAssetId != null && imageAssetId.length() != 36)
                    throw AppException.user("CHANGELOG_ASSET_INVALID", "区块图片引用无效");
                if (imageAssetId == null && imageAlt != null)
                    throw AppException.user("CHANGELOG_ASSET_INVALID", "图片替代文本必须关联图片");
                if (imageAssetId != null && imageAlt == null)
                    throw AppException.user("CHANGELOG_ASSET_ALT_REQUIRED", "更新图片必须填写替代文本");
                if (sectionTitle.isBlank() || (body.isBlank() && items.isEmpty()))
                    throw AppException.user("CHANGELOG_SECTION_EMPTY", "区块标题和内容不能为空");
                sections.add(new SectionCommand(sectionType, sectionTitle, body, items, imageAssetId, imageAlt));
            }
        }
        String ctaLabel = cleanNullable(command.ctaLabel(), 64);
        String ctaPath = cleanNullable(command.ctaPath(), 500);
        if (ctaPath != null && !safeLink(ctaPath)) throw AppException.user("CHANGELOG_CTA_INVALID", "体验链接必须是站内地址或 HTTPS 地址");
        if ((ctaLabel == null) != (ctaPath == null)) throw AppException.user("CHANGELOG_CTA_INVALID", "体验按钮文字和地址必须同时填写");
        return new Validated(version, title, summary, type, audience, modules, ctaLabel, ctaPath,
                command.showWhatsNew(), command.sendNotification(), List.copyOf(sections));
    }

    private void assertSectionAssetsOwned(String releaseId, List<SectionCommand> sections) {
        List<String> assetIds = sections.stream().map(SectionCommand::imageAssetId).filter(Objects::nonNull).distinct().toList();
        if (assetIds.isEmpty()) return;
        for (String assetId : assetIds) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM changelog_assets WHERE id=? AND release_id=? AND status='READY'",
                    Integer.class, assetId, releaseId);
            if (count == null || count != 1)
                throw AppException.user("CHANGELOG_ASSET_INVALID", "更新图片不存在、尚未就绪或不属于当前版本");
        }
    }

    private void assertReleaseVisible(String releaseId) {
        ReleaseSummary release = summary(releaseId);
        if (!Set.of("PUBLISHED", "ARCHIVED").contains(release.status()))
            throw AppException.user("CHANGELOG_NOT_FOUND", "更新版本不存在");
    }

    private void ensureReceipt(String releaseId, String accountId, Instant now) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM changelog_user_receipts WHERE release_id=? AND account_id=?",
                Integer.class, releaseId, accountId);
        if (count != null && count > 0) return;
        try {
            jdbc.update("INSERT INTO changelog_user_receipts(release_id,account_id,first_seen_at,read_at,acknowledged_at,remind_after,updated_at) VALUES(?,?,?,NULL,NULL,NULL,?)",
                    releaseId, accountId, now, now);
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent first view is idempotent.
        }
    }

    private static boolean contains(ReleaseSummary row, String keyword) {
        if (blank(keyword)) return true;
        String q = keyword.trim().toLowerCase(Locale.ROOT);
        return (row.versionLabel() + " " + row.title() + " " + row.summary() + " " + String.join(" ", row.modules()))
                .toLowerCase(Locale.ROOT).contains(q);
    }

    private static Map<String, Long> count(List<String> values) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String value : values) result.merge(value, 1L, Long::sum);
        return result;
    }

    private static int compareVersion(String left, String right) {
        int[] a = versionParts(left); int[] b = versionParts(right);
        for (int i = 0; i < 3; i++) { int compared = Integer.compare(a[i], b[i]); if (compared != 0) return compared; }
        return 0;
    }

    private static int[] versionParts(String value) {
        String core = normalizeVersion(value).substring(1).split("-", 2)[0];
        String[] parts = core.split("\\.");
        try { return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])}; }
        catch (RuntimeException ex) { return new int[] {0, 0, 0}; }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private List<String> strings(String value) {
        try { return value == null || value.isBlank() ? List.of() : mapper.readValue(value, STRING_LIST); }
        catch (Exception ex) { return List.of(); }
    }

    private String contentHash(Validated valid) { return sha256(json(valid).getBytes(StandardCharsets.UTF_8)); }

    private static String sha256(byte[] content) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static String imageType(byte[] bytes) {
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47)
            return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff)
            return "image/jpeg";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        throw AppException.user("CHANGELOG_ASSET_TYPE_INVALID", "仅支持 PNG、JPEG 和 WebP 图片");
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private static String cleanNullable(String value, int max) { String clean = clean(value, max); return clean.isBlank() ? null : clean; }
    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static String normalizeVersion(String value) { String clean = clean(value, 32); return clean.startsWith("v") ? clean : "v" + clean; }
    private static String slug(String version) { return normalizeVersion(version).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]", "-"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean safeLink(String value) { return value.startsWith("/") && !value.startsWith("//") || value.matches("^https://[^\\s]+$"); }
    private static Instant instant(java.sql.Timestamp value) { return value == null ? null : value.toInstant(); }
    private static VersionLink link(ReleaseSummary row) { return new VersionLink(row.versionLabel(), row.title(), "/updates/" + row.slug()); }
    private static void assertAdmin(CurrentAccount current) {
        if (current == null || !"ADMIN".equals(current.role())) throw AppException.forbidden("ADMIN_ONLY", "仅管理员可以管理更新日志");
    }

    public record SectionCommand(String sectionType, String title, String body, List<String> items,
            String imageAssetId, String imageAlt) {}
    public record ReleaseCommand(String versionLabel, String title, String summary, String releaseType, String audience,
            List<String> modules, String ctaLabel, String ctaPath, boolean showWhatsNew, boolean sendNotification,
            List<SectionCommand> sections) {}
    private record Validated(String versionLabel, String title, String summary, String releaseType, String audience,
            List<String> modules, String ctaLabel, String ctaPath, boolean showWhatsNew, boolean sendNotification,
            List<SectionCommand> sections) {}
    public record SectionView(String id, String sectionType, String title, String body, List<String> items, int sortOrder,
            String imageAssetId, String imageAlt) {}
    public record ReleaseSummary(String id, String versionLabel, String slug, String title, String summary,
            String releaseType, String status, String audience, List<String> modules, String ctaLabel, String ctaPath,
            boolean showWhatsNew, boolean sendNotification, Instant scheduledAt, Instant publishedAt, Instant archivedAt,
            int currentRevision, int versionNo, Instant createdAt, Instant updatedAt, String updatedBy,
            long readCount, String distributionStatus) {}
    public record ReleaseDetail(ReleaseSummary release, List<SectionView> sections, VersionLink previousVersion, VersionLink nextVersion) {}
    public record VersionLink(String versionLabel, String title, String path) {}
    public record FacetsView(Map<String, Long> types, Map<String, Long> modules, List<String> versions) {}
    public record ValidationView(boolean valid, List<String> issues) {}
    public record AdminOverview(long published, long drafts, long scheduled, long reads) {}
    public record AssetView(String id, String releaseId, String filename, String contentType, long sizeBytes, String url, String status) {}
    public record AssetDownload(String filename, String contentType, byte[] content) {}
}
