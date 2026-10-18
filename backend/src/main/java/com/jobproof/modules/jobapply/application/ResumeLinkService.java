package com.jobproof.modules.jobapply.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.jobapply.domain.ViewerAgent;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.modules.resume.application.ResumeService;
import com.jobproof.modules.resume.application.ResumeService.SharedRenderView;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.security.Tokens;
import com.jobproof.shared.time.ClockPort;
import java.net.URI;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resume links with view receipts (docs/phase2/01). Each link shows one frozen resume version to whoever
 * has it, and tells its owner when it was opened, on what kind of device, in which app and for how long.
 * The viewer's address is only ever hashed together with the link, so receipts tell visitors apart
 * without keeping anything that identifies them.
 */
@Service
public class ResumeLinkService {

    /** Active links one account may keep; plans (docs/phase2/01 §5) can raise it. */
    static final int MAX_ACTIVE_LINKS = 20;
    private static final Set<Integer> EXPIRY_DAYS = Set.of(7, 30, 90);
    /** A reload within this window continues the same visit instead of counting a new one. */
    private static final Duration SAME_VISIT = Duration.ofMinutes(30);
    private static final int VIEWS_PER_HOUR = 120;
    private static final int MAX_READ_SECONDS = 30 * 60;
    /** "A new reader opened it" is announced for the first few new visitors only. */
    private static final int VISITOR_NOTICES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LINK_SELECT = """
            SELECT l.*,m.title resume_title FROM resume_share_links l
              LEFT JOIN resume_versions v ON v.id=l.resume_version_id
              LEFT JOIN resume_masters m ON m.id=v.master_id
            """;

    private final JdbcTemplate jdbc;
    private final ResumeService resumes;
    private final NotificationService notifications;
    private final ApplicationTimeline timeline;
    private final AuditService audit;
    private final ClockPort clock;

    public ResumeLinkService(JdbcTemplate jdbc, ResumeService resumes, NotificationService notifications,
            ApplicationTimeline timeline, AuditService audit, ClockPort clock) {
        this.jdbc = jdbc;
        this.resumes = resumes;
        this.notifications = notifications;
        this.timeline = timeline;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- owner side -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LinkView> list(CurrentAccount current) {
        assertSeeker(current);
        Instant now = clock.now();
        return jdbc.query(LINK_SELECT + " WHERE l.account_id=? ORDER BY l.created_at DESC",
                (rs, n) -> view(row(rs), now), current.accountId());
    }

    @Transactional
    public LinkView create(CurrentAccount current, LinkCommand command) {
        assertSeeker(current);
        if (command == null) throw AppException.user("RESUME_LINK_INVALID", "请填写链接信息");
        String applicationId = blankToNull(command.applicationId());
        String label = blankToNull(command.label());
        if (applicationId != null) {
            List<String> names = jdbc.query("SELECT company,position_title FROM job_applications WHERE id=? AND account_id=?",
                    (rs, n) -> rs.getString("company") + " · " + rs.getString("position_title"),
                    applicationId, current.accountId());
            if (names.isEmpty()) throw AppException.forbidden("OBJECT_FORBIDDEN", "投递记录不存在或无权访问");
            if (label == null) label = names.get(0);
        }
        return createFor(current.accountId(), command.resumeVersionId(), label, applicationId,
                Boolean.TRUE.equals(command.hideContact()), command.expiresInDays());
    }

    /** One application keeps one active link: a new one replaces the old, whose receipts stay readable. */
    LinkView createFor(String accountId, String versionId, String label, String applicationId,
            boolean hideContact, Integer expiresInDays) {
        String resumeTitle = shareableVersion(accountId, versionId);
        if (expiresInDays != null && !EXPIRY_DAYS.contains(expiresInDays)) {
            throw AppException.user("RESUME_LINK_EXPIRY_INVALID", "有效期只能是 7、30、90 天或长期有效");
        }
        Instant now = clock.now();
        if (applicationId != null) {
            for (String previous : jdbc.queryForList("SELECT id FROM resume_share_links WHERE application_id=? AND account_id=? AND revoked_at IS NULL",
                    String.class, applicationId, accountId)) {
                revokeRow(accountId, previous, applicationId, now, "生成了新的专属链接，旧链接已停用");
            }
        }
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM resume_share_links WHERE account_id=? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>?)",
                Integer.class, accountId, now);
        if (active != null && active >= MAX_ACTIVE_LINKS) {
            throw AppException.conflict("RESUME_LINK_LIMIT", "有效链接已达 " + MAX_ACTIVE_LINKS + " 个，请先撤回不再使用的链接");
        }
        String id = Ids.newId();
        String finalLabel = clip(label == null ? resumeTitle : label, 120);
        jdbc.update("INSERT INTO resume_share_links(id,account_id,token,resume_version_id,application_id,label,hide_contact,expires_at,revoked_at,view_count,visitor_count,read_seconds,first_viewed_at,last_viewed_at,notified_visitors,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,NULL,0,0,0,NULL,NULL,0,?,?)",
                id, accountId, newToken(), versionId, applicationId, finalLabel, hideContact,
                expiresInDays == null ? null : now.plus(Duration.ofDays(expiresInDays)), now, now);
        timeline.record(accountId, applicationId, ApplicationTimeline.LINK_CREATED, null, null,
                hideContact ? "生成专属简历链接（隐藏联系方式）" : "生成专属简历链接", now);
        audit.append(accountId, "RESUME_LINK_CREATED", "RESUME_LINK", id, "创建简历分享链接");
        return requireLink(accountId, id, now);
    }

    @Transactional
    public LinkView revoke(CurrentAccount current, String linkId) {
        assertSeeker(current);
        Instant now = clock.now();
        LinkView link = requireLink(current.accountId(), linkId, now);
        if (link.revokedAt() == null) revokeRow(current.accountId(), linkId, link.applicationId(), now, "撤回了专属简历链接");
        return requireLink(current.accountId(), linkId, now);
    }

    @Transactional(readOnly = true)
    public List<ViewRecord> views(CurrentAccount current, String linkId) {
        assertSeeker(current);
        requireLink(current.accountId(), linkId, clock.now());
        return jdbc.query("SELECT * FROM resume_share_views WHERE link_id=? ORDER BY started_at DESC LIMIT 100",
                (rs, n) -> viewRecord(rs), linkId);
    }

    /** Links of one application, newest first, and the latest openings across them. */
    List<LinkView> linksOf(String accountId, String applicationId) {
        Instant now = clock.now();
        return jdbc.query(LINK_SELECT + " WHERE l.account_id=? AND l.application_id=? ORDER BY l.created_at DESC",
                (rs, n) -> view(row(rs), now), accountId, applicationId);
    }

    List<ViewRecord> viewsOf(String accountId, String applicationId) {
        return jdbc.query("SELECT v.* FROM resume_share_views v JOIN resume_share_links l ON l.id=v.link_id WHERE l.account_id=? AND l.application_id=? ORDER BY v.started_at DESC LIMIT 50",
                (rs, n) -> viewRecord(rs), accountId, applicationId);
    }

    /** The link an application card shows: the active one, else the most recent. */
    Map<String, LinkView> currentLinks(String accountId) {
        Instant now = clock.now();
        Map<String, LinkView> byApplication = new LinkedHashMap<>();
        jdbc.query(LINK_SELECT + " WHERE l.account_id=? AND l.application_id IS NOT NULL ORDER BY l.created_at DESC",
                (ResultSet rs) -> {
                    LinkView link = view(row(rs), now);
                    LinkView seen = byApplication.get(link.applicationId());
                    if (seen == null || (!"ACTIVE".equals(seen.status()) && "ACTIVE".equals(link.status()))) {
                        byApplication.put(link.applicationId(), link);
                    }
                }, accountId);
        return byApplication;
    }

    void deleteForApplication(String accountId, String applicationId) {
        jdbc.update("DELETE FROM resume_share_views WHERE link_id IN (SELECT id FROM resume_share_links WHERE account_id=? AND application_id=?)",
                accountId, applicationId);
        jdbc.update("DELETE FROM resume_share_links WHERE account_id=? AND application_id=?", accountId, applicationId);
    }

    void deleteForAccount(String accountId) {
        jdbc.update("DELETE FROM resume_share_views WHERE link_id IN (SELECT id FROM resume_share_links WHERE account_id=?)", accountId);
        jdbc.update("DELETE FROM resume_share_links WHERE account_id=?", accountId);
    }

    Map<String, Object> exportFor(String accountId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("links", jdbc.queryForList("SELECT id,token,resume_version_id,application_id,label,hide_contact,expires_at,revoked_at,view_count,visitor_count,read_seconds,first_viewed_at,last_viewed_at,created_at FROM resume_share_links WHERE account_id=? ORDER BY created_at", accountId));
        result.put("views", jdbc.queryForList("SELECT v.link_id,v.device,v.client,v.referrer_host,v.started_at,v.read_seconds FROM resume_share_views v JOIN resume_share_links l ON l.id=v.link_id WHERE l.account_id=? ORDER BY v.started_at", accountId));
        return result;
    }

    // ---- public side ------------------------------------------------------------------------------

    /** The page behind a link. Its owner sees it too, flagged so the page can say the visit is not counted. */
    @Transactional(readOnly = true)
    public SharedResumeView open(String token, CurrentAccount viewer) {
        LinkRow link = activeLink(token, clock.now());
        SharedRenderView render = resumes.sharedRender(link.accountId(), link.versionId(), link.hideContact())
                .orElseThrow(() -> AppException.user("RESUME_LINK_CONTENT_GONE", "分享的简历版本已不可用，请联系求职者获取新的链接"));
        return new SharedResumeView(render.templateId(), render.design(), render.content(), render.photo(),
                render.pageLimit(), link.createdAt(), link.expiresAt(), isOwner(viewer, link), link.hideContact());
    }

    @Transactional
    public ViewStart recordView(String token, CurrentAccount viewer, String address, String userAgent, String referrer) {
        Instant now = clock.now();
        LinkRow link = activeLink(token, now);
        if (isOwner(viewer, link)) return ViewStart.skipped("OWNER");
        ViewerAgent agent = ViewerAgent.parse(userAgent);
        if (agent.bot()) return ViewStart.skipped("AUTOMATED");
        String visitor = Tokens.sha256(link.id() + "|" + nullToEmpty(address) + "|" + nullToEmpty(userAgent));
        String key = randomKey();
        List<Object[]> recent = jdbc.query("SELECT id,read_seconds FROM resume_share_views WHERE link_id=? AND visitor_hash=? AND last_seen_at>? ORDER BY last_seen_at DESC",
                (rs, n) -> new Object[] { rs.getString("id"), rs.getInt("read_seconds") },
                link.id(), visitor, now.minus(SAME_VISIT));
        if (!recent.isEmpty()) {
            String viewId = (String) recent.get(0)[0];
            jdbc.update("UPDATE resume_share_views SET view_key=?,last_seen_at=? WHERE id=?", Tokens.sha256(key), now, viewId);
            return ViewStart.recorded(viewId, key, (Integer) recent.get(0)[1]);
        }
        Integer lastHour = jdbc.queryForObject("SELECT COUNT(*) FROM resume_share_views WHERE link_id=? AND started_at>?",
                Integer.class, link.id(), now.minus(Duration.ofHours(1)));
        if (lastHour != null && lastHour >= VIEWS_PER_HOUR) return ViewStart.skipped("LIMITED");
        Integer earlier = jdbc.queryForObject("SELECT COUNT(*) FROM resume_share_views WHERE link_id=? AND visitor_hash=?",
                Integer.class, link.id(), visitor);
        boolean newVisitor = earlier == null || earlier == 0;
        String viewId = Ids.newId();
        jdbc.update("INSERT INTO resume_share_views(id,link_id,view_key,visitor_hash,device,client,referrer_host,started_at,last_seen_at,read_seconds) VALUES(?,?,?,?,?,?,?,?,?,0)",
                viewId, link.id(), Tokens.sha256(key), visitor, agent.device(), agent.client(), referrerHost(referrer), now, now);
        jdbc.update("UPDATE resume_share_links SET view_count=view_count+1,visitor_count=visitor_count+?,last_viewed_at=?,updated_at=? WHERE id=?",
                newVisitor ? 1 : 0, now, now, link.id());
        boolean first = jdbc.update("UPDATE resume_share_links SET first_viewed_at=? WHERE id=? AND first_viewed_at IS NULL", now, link.id()) == 1;
        if (first) {
            announce(link, agent, now, true, 0);
        } else if (newVisitor && jdbc.update("UPDATE resume_share_links SET notified_visitors=notified_visitors+1 WHERE id=? AND notified_visitors<?",
                link.id(), VISITOR_NOTICES) == 1) {
            Integer notice = jdbc.queryForObject("SELECT notified_visitors FROM resume_share_links WHERE id=?", Integer.class, link.id());
            announce(link, agent, now, false, notice == null ? 1 : notice);
        }
        return ViewStart.recorded(viewId, key, 0);
    }

    /** How long the page has been visible, as the viewer's page counts it; only ever grows, capped. */
    @Transactional
    public void heartbeat(String token, String viewId, String viewKey, int seconds) {
        if (token == null || viewId == null || viewKey == null || viewKey.isBlank()) return;
        int capped = Math.max(0, Math.min(MAX_READ_SECONDS, seconds));
        List<Object[]> rows = jdbc.query("SELECT v.read_seconds,l.id FROM resume_share_views v JOIN resume_share_links l ON l.id=v.link_id WHERE v.id=? AND l.token=? AND v.view_key=?",
                (rs, n) -> new Object[] { rs.getInt(1), rs.getString(2) }, viewId, token, Tokens.sha256(viewKey));
        if (rows.isEmpty()) return;
        int before = (Integer) rows.get(0)[0];
        if (capped <= before) return;
        Instant now = clock.now();
        jdbc.update("UPDATE resume_share_views SET read_seconds=?,last_seen_at=? WHERE id=?", capped, now, viewId);
        jdbc.update("UPDATE resume_share_links SET read_seconds=read_seconds+? WHERE id=?", capped - before, rows.get(0)[1]);
    }

    // ---- internals --------------------------------------------------------------------------------

    private void announce(LinkRow link, ViewerAgent agent, Instant now, boolean first, int notice) {
        String where = deviceLabel(agent.device()) + clientPhrase(agent.client());
        String company = link.applicationId() == null ? null
                : jdbc.query("SELECT company FROM job_applications WHERE id=?", (rs, n) -> rs.getString(1), link.applicationId())
                        .stream().findFirst().orElse(null);
        String subject = company != null ? company : "「" + link.label() + "」";
        String title = first ? subject + "打开了你的简历" : subject + "的简历有了新的读者";
        String body = first
                ? "对方" + where + "打开了你分享的简历。可以在投递追踪里看到阅读时长。"
                : "又有一位读者" + where + "打开了这份简历，可能是转给了面试官或团队负责人。";
        String action = link.applicationId() != null ? "/applications?focus=" + link.applicationId() : "/applications?tab=links";
        timeline.record(link.accountId(), link.applicationId(),
                first ? ApplicationTimeline.FIRST_VIEWED : ApplicationTimeline.NEW_VISITOR, null, null,
                (first ? "简历首次被打开：" : "新的读者：") + deviceLabel(agent.device()) + clientPhrase(agent.client()), now);
        notifications.request(link.accountId(), NotificationType.APPLICATION_ACTIVITY,
                "resume-link:" + link.id() + (first ? ":first" : ":visitor:" + notice), title, body, action);
    }

    static String deviceLabel(String device) {
        return switch (device) {
            case "MOBILE" -> "用手机";
            case "TABLET" -> "用平板";
            case "DESKTOP" -> "用电脑";
            default -> "";
        };
    }

    static String clientPhrase(String client) {
        return switch (client) {
            case "WECHAT" -> "在微信里";
            case "WECOM" -> "在企业微信里";
            case "DINGTALK" -> "在钉钉里";
            case "FEISHU" -> "在飞书里";
            case "QQ" -> "在 QQ 里";
            default -> "";
        };
    }

    private void revokeRow(String accountId, String linkId, String applicationId, Instant now, String detail) {
        jdbc.update("UPDATE resume_share_links SET revoked_at=?,updated_at=? WHERE id=? AND account_id=? AND revoked_at IS NULL",
                now, now, linkId, accountId);
        timeline.record(accountId, applicationId, ApplicationTimeline.LINK_REVOKED, null, null, detail, now);
        audit.append(accountId, "RESUME_LINK_REVOKED", "RESUME_LINK", linkId, "撤回简历分享链接");
    }

    /** Title of the resume a frozen, owned version belongs to; anything else cannot be shared. */
    private String shareableVersion(String accountId, String versionId) {
        if (versionId == null || versionId.isBlank()) throw AppException.user("RESUME_VERSION_REQUIRED", "请选择要分享的简历版本");
        List<String[]> rows = jdbc.query("SELECT v.status,m.title FROM resume_versions v JOIN resume_masters m ON m.id=v.master_id WHERE v.id=? AND v.account_id=?",
                (rs, n) -> new String[] { rs.getString(1), rs.getString(2) }, versionId, accountId);
        if (rows.isEmpty()) throw AppException.forbidden("OBJECT_FORBIDDEN", "简历版本不存在或无权访问");
        if (!"FROZEN".equals(rows.get(0)[0])) throw AppException.conflict("RESUME_VERSION_NOT_SHAREABLE", "只能分享已冻结的简历版本");
        return rows.get(0)[1] == null || rows.get(0)[1].isBlank() ? "我的简历" : rows.get(0)[1];
    }

    private LinkRow activeLink(String token, Instant now) {
        if (token == null || token.length() > 32 || !token.matches("[A-Za-z0-9_-]+")) {
            throw AppException.user("RESUME_LINK_NOT_FOUND", "简历链接不存在，请检查链接是否完整");
        }
        LinkRow link = jdbc.query(LINK_SELECT + " WHERE l.token=?", (rs, n) -> row(rs), token).stream().findFirst()
                .orElseThrow(() -> AppException.user("RESUME_LINK_NOT_FOUND", "简历链接不存在，请检查链接是否完整"));
        if (link.revokedAt() != null) throw AppException.user("RESUME_LINK_REVOKED", "求职者已撤回这个简历链接");
        if (link.expiresAt() != null && !link.expiresAt().isAfter(now)) {
            throw AppException.user("RESUME_LINK_EXPIRED", "这个简历链接已过期");
        }
        return link;
    }

    private LinkView requireLink(String accountId, String linkId, Instant now) {
        return jdbc.query(LINK_SELECT + " WHERE l.id=? AND l.account_id=?", (rs, n) -> view(row(rs), now), linkId, accountId)
                .stream().findFirst()
                .orElseThrow(() -> AppException.forbidden("OBJECT_FORBIDDEN", "简历链接不存在或无权访问"));
    }

    private static boolean isOwner(CurrentAccount viewer, LinkRow link) {
        return viewer != null && link.accountId().equals(viewer.accountId());
    }

    private static LinkRow row(ResultSet rs) throws SQLException {
        return new LinkRow(rs.getString("id"), rs.getString("account_id"), rs.getString("token"),
                rs.getString("resume_version_id"), rs.getString("application_id"), rs.getString("label"),
                rs.getBoolean("hide_contact"), instant(rs, "expires_at"), instant(rs, "revoked_at"),
                rs.getInt("view_count"), rs.getInt("visitor_count"), rs.getInt("read_seconds"),
                instant(rs, "first_viewed_at"), instant(rs, "last_viewed_at"), instant(rs, "created_at"),
                rs.getString("resume_title"));
    }

    private static LinkView view(LinkRow row, Instant now) {
        String status = row.revokedAt() != null ? "REVOKED"
                : row.expiresAt() != null && !row.expiresAt().isAfter(now) ? "EXPIRED" : "ACTIVE";
        return new LinkView(row.id(), row.token(), "/r/" + row.token(), row.label(), row.versionId(),
                row.resumeTitle(), row.applicationId(), row.hideContact(), row.expiresAt(), row.revokedAt(), status,
                row.viewCount(), row.visitorCount(), row.readSeconds(), row.firstViewedAt(), row.lastViewedAt(),
                row.createdAt());
    }

    private static ViewRecord viewRecord(ResultSet rs) throws SQLException {
        return new ViewRecord(rs.getString("id"), rs.getString("link_id"), rs.getString("device"),
                rs.getString("client"), rs.getString("referrer_host"), instant(rs, "started_at"),
                rs.getInt("read_seconds"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) return null;
        try {
            String host = URI.create(referrer.trim()).getHost();
            if (host == null || host.isBlank()) return null;
            String lower = host.toLowerCase(Locale.ROOT);
            return lower.length() > 120 ? lower.substring(0, 120) : lower;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String randomKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void assertSeeker(CurrentAccount current) {
        if (current.operator()) throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能查看用户求职资料原文");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record LinkRow(String id, String accountId, String token, String versionId, String applicationId,
            String label, boolean hideContact, Instant expiresAt, Instant revokedAt, int viewCount, int visitorCount,
            int readSeconds, Instant firstViewedAt, Instant lastViewedAt, Instant createdAt, String resumeTitle) {
    }

    public record LinkCommand(String resumeVersionId, String label, String applicationId, Boolean hideContact,
            Integer expiresInDays) {
    }

    public record LinkView(String id, String token, String path, String label, String resumeVersionId,
            String resumeTitle, String applicationId, boolean hideContact, Instant expiresAt, Instant revokedAt,
            String status, int viewCount, int visitorCount, int readSeconds, Instant firstViewedAt,
            Instant lastViewedAt, Instant createdAt) {
    }

    public record ViewRecord(String id, String linkId, String device, String client, String referrerHost,
            Instant startedAt, int readSeconds) {
    }

    public record SharedResumeView(String templateId, JsonNode design, JsonNode content, String photo, int pageLimit,
            Instant sharedAt, Instant expiresAt, boolean ownerPreview, boolean hideContact) {
    }

    /** {@code recorded=false} with a reason when the visit is not counted (the owner, an automated agent). */
    public record ViewStart(boolean recorded, String reason, String viewId, String viewKey, int readSeconds) {
        static ViewStart skipped(String reason) {
            return new ViewStart(false, reason, null, null, 0);
        }

        static ViewStart recorded(String viewId, String viewKey, int readSeconds) {
            return new ViewStart(true, null, viewId, viewKey, readSeconds);
        }
    }
}
