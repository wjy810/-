package com.jobproof.modules.jobapply.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.jobapply.domain.ViewerAgent;
import com.jobproof.modules.membership.application.MembershipService;
import com.jobproof.modules.membership.application.MembershipService.Entitlements;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resume links with view receipts (docs/phase2/01). Each link shows one frozen resume version to whoever
 * has it, and tells its owner when it was opened, on what kind of device, in which app and for how long.
 * A reader is told apart by a random first-party cookie the share page gets (hashed together with the link
 * and the browser), never by address, so receipts keep nothing that identifies a person. The owner's own
 * openings are left out: from a logged-in session, from a browser registered when they opened 投递追踪,
 * or from a reader they marked as themselves.
 */
@Service
public class ResumeLinkService {

    private static final Set<Integer> EXPIRY_DAYS = Set.of(7, 30, 60, 90);
    /** Links made together with an application last this long unless the owner picks otherwise. */
    static final int DEFAULT_EXPIRY_DAYS = 60;
    /** Once an application ends, its link stays readable this long, then stops counting towards the limit. */
    static final Duration AFTER_CLOSE = Duration.ofDays(14);
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
    private final MembershipService memberships;
    private final ClockPort clock;

    public ResumeLinkService(JdbcTemplate jdbc, ResumeService resumes, NotificationService notifications,
            ApplicationTimeline timeline, AuditService audit, MembershipService memberships, ClockPort clock) {
        this.jdbc = jdbc;
        this.resumes = resumes;
        this.notifications = notifications;
        this.timeline = timeline;
        this.audit = audit;
        this.memberships = memberships;
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
            throw AppException.user("RESUME_LINK_EXPIRY_INVALID", "有效期只能是 7、30、60、90 天或长期有效");
        }
        Instant now = clock.now();
        if (applicationId != null) {
            for (String previous : jdbc.queryForList("SELECT id FROM resume_share_links WHERE application_id=? AND account_id=? AND revoked_at IS NULL",
                    String.class, applicationId, accountId)) {
                revokeRow(accountId, previous, applicationId, now, "生成了新的专属链接，旧链接已停用");
            }
        }
        assertWithinLimit(accountId, now);
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

    /**
     * Changes how long a link lasts and whether it shows contact details, keeping its address and receipts.
     * An expired link can be given a new expiry, which makes it active again if the plan allows another.
     */
    @Transactional
    public LinkView update(CurrentAccount current, String linkId, LinkPatch patch) {
        assertSeeker(current);
        if (patch == null) throw AppException.user("RESUME_LINK_INVALID", "请填写要修改的内容");
        Instant now = clock.now();
        LinkView link = requireLink(current.accountId(), linkId, now);
        if (link.revokedAt() != null) throw AppException.conflict("RESUME_LINK_REVOKED", "已撤回的链接不能修改，请生成新的链接");
        List<String> changes = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(patch.neverExpires()) || patch.expiresInDays() != null) {
            if (patch.expiresInDays() != null && !EXPIRY_DAYS.contains(patch.expiresInDays())) {
                throw AppException.user("RESUME_LINK_EXPIRY_INVALID", "有效期只能是 7、30、60、90 天或长期有效");
            }
            if ("EXPIRED".equals(link.status())) assertWithinLimit(current.accountId(), now);
            Instant expiresAt = Boolean.TRUE.equals(patch.neverExpires()) ? null : now.plus(Duration.ofDays(patch.expiresInDays()));
            jdbc.update("UPDATE resume_share_links SET expires_at=?,updated_at=? WHERE id=? AND account_id=?",
                    expiresAt == null ? null : Timestamp.from(expiresAt), now, linkId, current.accountId());
            changes.add(expiresAt == null ? "改为长期有效" : "有效期改为 " + patch.expiresInDays() + " 天");
        }
        if (patch.hideContact() != null && patch.hideContact() != link.hideContact()) {
            jdbc.update("UPDATE resume_share_links SET hide_contact=?,updated_at=? WHERE id=? AND account_id=?",
                    patch.hideContact(), now, linkId, current.accountId());
            changes.add(patch.hideContact() ? "隐藏联系方式" : "显示联系方式");
        }
        if (!changes.isEmpty()) {
            timeline.record(current.accountId(), link.applicationId(), ApplicationTimeline.LINK_UPDATED, null, null,
                    "专属链接" + String.join("，", changes), now);
            audit.append(current.accountId(), "RESUME_LINK_UPDATED", "RESUME_LINK", linkId, String.join("，", changes));
        }
        return requireLink(current.accountId(), linkId, now);
    }

    /**
     * 这是我自己打开的：drops that reader's visits from the receipts, recounts the link from what is left, and
     * leaves the same reader out from now on.
     */
    @Transactional
    public LinkView markOwnView(CurrentAccount current, String linkId, String viewId) {
        assertSeeker(current);
        Instant now = clock.now();
        LinkView link = requireLink(current.accountId(), linkId, now);
        String visitor = jdbc.query("SELECT visitor_hash FROM resume_share_views WHERE id=? AND link_id=?",
                (rs, n) -> rs.getString(1), viewId, linkId).stream().findFirst()
                .orElseThrow(() -> AppException.user("RESUME_LINK_VIEW_NOT_FOUND", "这次打开记录不存在"));
        try {
            jdbc.update("INSERT INTO resume_share_owner_visitors(link_id,visitor_hash,created_at) VALUES(?,?,?)", linkId, visitor, now);
        } catch (DuplicateKeyException alreadyMarked) {
            // Marked before; its visits are removed again below.
        }
        int removed = jdbc.update("DELETE FROM resume_share_views WHERE link_id=? AND visitor_hash=?", linkId, visitor);
        recount(linkId, now);
        timeline.record(current.accountId(), link.applicationId(), ApplicationTimeline.OWN_VIEW_MARKED, null, null,
                "标记了 " + removed + " 次本人打开，回执已更正", now);
        return requireLink(current.accountId(), linkId, now);
    }

    /** An application has ended: its active links stop in {@link #AFTER_CLOSE} unless they end sooner anyway. */
    void expireAfterClose(String accountId, String applicationId, Instant now) {
        Instant until = now.plus(AFTER_CLOSE);
        int changed = jdbc.update("UPDATE resume_share_links SET expires_at=?,updated_at=? WHERE account_id=? AND application_id=? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>?)",
                Timestamp.from(until), now, accountId, applicationId, Timestamp.from(until));
        if (changed > 0) {
            timeline.record(accountId, applicationId, ApplicationTimeline.LINK_UPDATED, null, null,
                    "投递已结束，专属链接将在 14 天后失效，不再占用有效链接名额", now);
        }
    }

    /** The owner opened 投递追踪 in this browser: their own openings of their links here are not receipts. */
    @Transactional
    public void registerOwnerDevice(String accountId, String deviceToken) {
        if (deviceToken == null || deviceToken.length() < 20 || deviceToken.length() > 64) return;
        Instant now = clock.now();
        String hash = Tokens.sha256(deviceToken);
        // The browser may have been someone else's before: the device belongs to whoever uses it now.
        if (jdbc.update("UPDATE resume_owner_devices SET account_id=?,last_seen_at=? WHERE device_hash=?", accountId, now, hash) > 0) return;
        try {
            jdbc.update("INSERT INTO resume_owner_devices(device_hash,account_id,created_at,last_seen_at) VALUES(?,?,?,?)", hash, accountId, now, now);
        } catch (DuplicateKeyException raced) {
            jdbc.update("UPDATE resume_owner_devices SET account_id=?,last_seen_at=? WHERE device_hash=?", accountId, now, hash);
        }
    }

    /** Per application, across every link it has had: what a card and the funnel show. */
    Map<String, Receipts> receipts(String accountId) {
        Map<String, Receipts> result = new LinkedHashMap<>();
        jdbc.query("SELECT application_id,COUNT(*) link_count,SUM(view_count) views,SUM(visitor_count) visitors,SUM(read_seconds) seconds,MIN(first_viewed_at) first_viewed,MAX(last_viewed_at) last_viewed FROM resume_share_links WHERE account_id=? AND application_id IS NOT NULL GROUP BY application_id",
                (ResultSet rs) -> {
                    result.put(rs.getString("application_id"), new Receipts(rs.getInt("link_count"), rs.getInt("views"),
                            rs.getInt("visitors"), rs.getInt("seconds"), instant(rs, "first_viewed"), instant(rs, "last_viewed")));
                }, accountId);
        return result;
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
        jdbc.update("DELETE FROM resume_share_owner_visitors WHERE link_id IN (SELECT id FROM resume_share_links WHERE account_id=? AND application_id=?)",
                accountId, applicationId);
        jdbc.update("DELETE FROM resume_share_links WHERE account_id=? AND application_id=?", accountId, applicationId);
    }

    void deleteForAccount(String accountId) {
        jdbc.update("DELETE FROM resume_share_views WHERE link_id IN (SELECT id FROM resume_share_links WHERE account_id=?)", accountId);
        jdbc.update("DELETE FROM resume_share_owner_visitors WHERE link_id IN (SELECT id FROM resume_share_links WHERE account_id=?)", accountId);
        jdbc.update("DELETE FROM resume_owner_devices WHERE account_id=?", accountId);
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

    /**
     * {@code readerId}: the share page's own random cookie; {@code ownerDevice}: the cookie a browser got when its
     * user opened 投递追踪. Neither is stored as is.
     */
    @Transactional
    public ViewStart recordView(String token, CurrentAccount viewer, String readerId, String ownerDevice,
            String userAgent, String referrer) {
        Instant now = clock.now();
        LinkRow link = activeLink(token, now);
        if (isOwner(viewer, link) || ownerDevice(ownerDevice, link)) return ViewStart.skipped("OWNER");
        ViewerAgent agent = ViewerAgent.parse(userAgent);
        if (agent.bot()) return ViewStart.skipped("AUTOMATED");
        String visitor = Tokens.sha256(link.id() + "|" + nullToEmpty(readerId) + "|" + nullToEmpty(userAgent));
        Integer markedOwn = jdbc.queryForObject("SELECT COUNT(*) FROM resume_share_owner_visitors WHERE link_id=? AND visitor_hash=?",
                Integer.class, link.id(), visitor);
        if (markedOwn != null && markedOwn > 0) return ViewStart.skipped("OWNER");
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

    private void assertWithinLimit(String accountId, Instant now) {
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM resume_share_links WHERE account_id=? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>?)",
                Integer.class, accountId, now);
        Entitlements plan = memberships.entitlements(accountId);
        if (active != null && active >= plan.activeLinks()) {
            throw AppException.conflict("RESUME_LINK_LIMIT", "FREE".equals(plan.plan())
                    ? "免费版最多同时保留 " + plan.activeLinks() + " 条有效链接（投递结束 14 天后链接自动失效）。撤回不再使用的链接，或升级专业版"
                    : "有效链接已达 " + plan.activeLinks() + " 条，请先撤回不再使用的链接");
        }
    }

    private void recount(String linkId, Instant now) {
        jdbc.update("UPDATE resume_share_links SET view_count=(SELECT COUNT(*) FROM resume_share_views WHERE link_id=?),"
                        + "visitor_count=(SELECT COUNT(DISTINCT visitor_hash) FROM resume_share_views WHERE link_id=?),"
                        + "read_seconds=(SELECT COALESCE(SUM(read_seconds),0) FROM resume_share_views WHERE link_id=?),"
                        + "first_viewed_at=(SELECT MIN(started_at) FROM resume_share_views WHERE link_id=?),"
                        + "last_viewed_at=(SELECT MAX(started_at) FROM resume_share_views WHERE link_id=?),updated_at=? WHERE id=?",
                linkId, linkId, linkId, linkId, linkId, now, linkId);
    }

    private boolean ownerDevice(String deviceToken, LinkRow link) {
        if (deviceToken == null || deviceToken.isBlank() || deviceToken.length() > 64) return false;
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM resume_owner_devices WHERE device_hash=? AND account_id=?",
                Integer.class, Tokens.sha256(deviceToken), link.accountId());
        return found != null && found > 0;
    }

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

    /** {@code neverExpires=true} for 长期有效; otherwise {@code expiresInDays} from now. Absent fields stay as they are. */
    public record LinkPatch(Integer expiresInDays, Boolean neverExpires, Boolean hideContact) {
    }

    /** All links an application has had, added up: replacing a link no longer makes its receipts disappear. */
    public record Receipts(int linkCount, int views, int visitors, int readSeconds, Instant firstViewedAt,
            Instant lastViewedAt) {
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
