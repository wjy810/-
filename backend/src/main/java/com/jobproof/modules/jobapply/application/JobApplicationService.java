package com.jobproof.modules.jobapply.application;

import com.jobproof.modules.jobapply.application.ResumeLinkService.LinkView;
import com.jobproof.modules.jobapply.application.ResumeLinkService.ViewRecord;
import com.jobproof.modules.jobapply.domain.ApplicationStage;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.concurrency.Versions;
import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.export.AccountExportContributor;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投递追踪 (docs/phase2/01): every company applied to, where it stands, what happened, which resume version
 * went out and — through its own resume link — whether anyone opened it.
 */
@Service
public class JobApplicationService implements DeletionModuleHandler, AccountExportContributor {

    static final Set<String> CHANNELS = Set.of("OFFICIAL_SITE", "JOB_BOARD", "REFERRAL", "CAMPUS", "EMAIL",
            "HEADHUNTER", "OTHER");
    static final Set<String> OUTCOMES = Set.of("ACCEPTED", "DECLINED", "REJECTED", "WITHDRAWN", "NO_RESPONSE");
    private static final int MAX_APPLICATIONS = 500;
    private static final String APPLICATION_SELECT = """
            SELECT a.*,m.id resume_master_id,m.title resume_title,j.title match_title,r.total_score match_score
              FROM job_applications a
              LEFT JOIN resume_versions v ON v.id=a.resume_version_id AND v.account_id=a.account_id
              LEFT JOIN resume_masters m ON m.id=v.master_id
              LEFT JOIN job_match_tasks t ON t.id=a.job_match_id AND t.account_id=a.account_id
              LEFT JOIN jobs j ON j.id=t.job_id
              LEFT JOIN match_reports r ON r.id=t.current_report_id
            """;

    private final JdbcTemplate jdbc;
    private final ResumeLinkService links;
    private final ApplicationTimeline timeline;
    private final ClockPort clock;

    public JobApplicationService(JdbcTemplate jdbc, ResumeLinkService links, ApplicationTimeline timeline,
            ClockPort clock) {
        this.jdbc = jdbc;
        this.links = links;
        this.timeline = timeline;
        this.clock = clock;
    }

    @Override public String moduleCode() { return "applications"; }
    @Override public String moduleKey() { return "applications"; }

    @Transactional(readOnly = true)
    public OverviewView overview(CurrentAccount current) {
        assertSeeker(current);
        List<ApplicationView> items = all(current.accountId());
        return new OverviewView(items, stats(items, clock.now()));
    }

    @Transactional(readOnly = true)
    public DetailView detail(CurrentAccount current, String id) {
        assertSeeker(current);
        ApplicationView application = require(current.accountId(), id);
        List<EventView> events = jdbc.query("SELECT * FROM job_application_events WHERE application_id=? AND account_id=? ORDER BY created_at DESC,id",
                (rs, n) -> new EventView(rs.getString("id"), rs.getString("event_type"), rs.getString("from_stage"),
                        rs.getString("to_stage"), rs.getString("detail"), instant(rs, "created_at")),
                id, current.accountId());
        return new DetailView(application, events, links.linksOf(current.accountId(), id),
                links.viewsOf(current.accountId(), id));
    }

    @Transactional
    public ApplicationView create(CurrentAccount current, ApplicationWrite write) {
        assertSeeker(current);
        if (write == null) throw AppException.user("APPLICATION_INVALID", "请填写投递信息");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM job_applications WHERE account_id=?", Integer.class,
                current.accountId());
        if (count != null && count >= MAX_APPLICATIONS) {
            throw AppException.conflict("APPLICATION_LIMIT", "投递记录已达 " + MAX_APPLICATIONS + " 条，请先删除不再需要的记录");
        }
        Fields fields = fields(current.accountId(), write);
        ApplicationStage stage = write.stage() == null ? ApplicationStage.APPLIED : ApplicationStage.parse(write.stage());
        String outcome = outcome(stage, write.outcome());
        LocalDate appliedOn = fields.appliedOn() != null || stage == ApplicationStage.WISHLIST ? fields.appliedOn()
                : LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        Instant now = clock.now();
        String id = Ids.newId();
        jdbc.update("INSERT INTO job_applications(id,account_id,company,position_title,location,channel,job_url,stage,furthest_stage,outcome,salary_text,resume_version_id,job_match_id,applied_on,next_step,next_step_at,notes,version_no,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?)",
                id, current.accountId(), fields.company(), fields.position(), fields.location(), fields.channel(),
                fields.jobUrl(), stage.name(), (stage.open() ? stage : ApplicationStage.APPLIED).name(), outcome,
                fields.salary(), fields.versionId(), fields.matchId(), appliedOn, fields.nextStep(), fields.nextStepAt(),
                fields.notes(), now, now);
        timeline.record(current.accountId(), id, ApplicationTimeline.CREATED, null, stage.name(), null, now);
        if (Boolean.TRUE.equals(write.createLink()) && fields.versionId() != null) {
            links.createFor(current.accountId(), fields.versionId(), fields.company() + " · " + fields.position(), id,
                    Boolean.TRUE.equals(write.hideContact()), null);
        }
        return require(current.accountId(), id);
    }

    @Transactional
    public ApplicationView update(CurrentAccount current, String id, ApplicationWrite write) {
        assertSeeker(current);
        if (write == null) throw AppException.user("APPLICATION_INVALID", "请填写投递信息");
        ApplicationView existing = require(current.accountId(), id);
        Versions.assertExpected(write.expectedVersion(), existing.version());
        Fields fields = fields(current.accountId(), write);
        int changed = jdbc.update("UPDATE job_applications SET company=?,position_title=?,location=?,channel=?,job_url=?,salary_text=?,resume_version_id=?,job_match_id=?,applied_on=?,next_step=?,next_step_at=?,notes=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=? AND version_no=?",
                fields.company(), fields.position(), fields.location(), fields.channel(), fields.jobUrl(), fields.salary(),
                fields.versionId(), fields.matchId(), fields.appliedOn(), fields.nextStep(), fields.nextStepAt(),
                fields.notes(), clock.now(), id, current.accountId(), existing.version());
        if (changed == 0) throw AppException.conflict("VERSION_CONFLICT", "投递记录刚在别处更新过，请刷新后重试");
        return require(current.accountId(), id);
    }

    @Transactional
    public ApplicationView moveStage(CurrentAccount current, String id, StageWrite write) {
        assertSeeker(current);
        if (write == null) throw AppException.user("APPLICATION_STAGE_INVALID", "请选择投递阶段");
        ApplicationView existing = require(current.accountId(), id);
        Versions.assertExpected(write.expectedVersion(), existing.version());
        ApplicationStage next = ApplicationStage.parse(write.stage());
        String outcome = outcome(next, write.outcome());
        ApplicationStage previous = ApplicationStage.parse(existing.stage());
        if (previous == next && java.util.Objects.equals(outcome, existing.outcome())) return existing;
        ApplicationStage furthest = ApplicationStage.furthest(ApplicationStage.parse(existing.furthestStage()), next);
        Instant now = clock.now();
        LocalDate appliedOn = existing.appliedOn() == null && next.open() && next.rank() >= ApplicationStage.APPLIED.rank()
                ? LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")) : existing.appliedOn();
        int changed = jdbc.update("UPDATE job_applications SET stage=?,furthest_stage=?,outcome=?,applied_on=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=? AND version_no=?",
                next.name(), furthest.name(), outcome, appliedOn, now, id, current.accountId(), existing.version());
        if (changed == 0) throw AppException.conflict("VERSION_CONFLICT", "投递记录刚在别处更新过，请刷新后重试");
        timeline.record(current.accountId(), id, ApplicationTimeline.STAGE_CHANGED, previous.name(), next.name(),
                outcome, now);
        return require(current.accountId(), id);
    }

    @Transactional
    public void delete(CurrentAccount current, String id) {
        assertSeeker(current);
        require(current.accountId(), id);
        links.deleteForApplication(current.accountId(), id);
        jdbc.update("DELETE FROM job_application_events WHERE application_id=? AND account_id=?", id, current.accountId());
        jdbc.update("DELETE FROM job_applications WHERE id=? AND account_id=?", id, current.accountId());
    }

    /** Frozen versions across all of the user's resumes, numbered the way the versions page numbers them. */
    @Transactional(readOnly = true)
    public List<VersionOption> shareableVersions(CurrentAccount current) {
        assertSeeker(current);
        Map<String, Integer> ordinals = new HashMap<>();
        List<VersionOption> options = new ArrayList<>();
        jdbc.query("SELECT v.id,v.master_id,v.status,v.source,v.created_at,v.frozen_at,m.title,m.status master_status FROM resume_versions v JOIN resume_masters m ON m.id=v.master_id WHERE v.account_id=? ORDER BY v.master_id,v.created_at,v.id",
                (ResultSet rs) -> {
                    int ordinal = ordinals.merge(rs.getString("master_id"), 1, Integer::sum);
                    if (!"FROZEN".equals(rs.getString("status")) || "ARCHIVED".equals(rs.getString("master_status"))) return;
                    String source = rs.getString("source");
                    Instant frozenAt = instant(rs, "frozen_at");
                    options.add(new VersionOption(rs.getString("id"), rs.getString("master_id"),
                            blank(rs.getString("title"), "我的简历"), ordinal, source,
                            source != null && source.endsWith("_ANONYMOUS"),
                            frozenAt == null ? instant(rs, "created_at") : frozenAt));
                }, current.accountId());
        options.sort((left, right) -> right.frozenAt().compareTo(left.frozenAt()));
        return options;
    }

    @Override
    public Result onAccountDeletion(String accountId) {
        try {
            links.deleteForAccount(accountId);
            jdbc.update("DELETE FROM job_application_events WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM job_applications WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM weekly_digests WHERE account_id=?", accountId);
            return Result.succeeded("投递记录与简历链接已删除，链接已失效");
        } catch (RuntimeException e) {
            return Result.failed("投递记录暂未清除，删除申请会保持可重试");
        }
    }

    @Override
    public Map<String, Object> contribute(String accountId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applications", jdbc.queryForList("SELECT id,company,position_title,location,channel,job_url,stage,furthest_stage,outcome,salary_text,resume_version_id,job_match_id,applied_on,next_step,next_step_at,notes,created_at,updated_at FROM job_applications WHERE account_id=? ORDER BY created_at", accountId));
        result.put("events", jdbc.queryForList("SELECT application_id,event_type,from_stage,to_stage,detail,created_at FROM job_application_events WHERE account_id=? ORDER BY created_at", accountId));
        result.putAll(links.exportFor(accountId));
        return result;
    }

    /** Summary for the dashboard: open applications, unread receipts and the next due step. */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(String accountId) {
        Instant now = clock.now();
        StatsView stats = stats(all(accountId), now);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stats", stats);
        result.put("recentOpens", jdbc.query("SELECT a.id,a.company,a.position_title,l.last_viewed_at,l.view_count FROM resume_share_links l JOIN job_applications a ON a.id=l.application_id WHERE l.account_id=? AND l.last_viewed_at IS NOT NULL ORDER BY l.last_viewed_at DESC LIMIT 3",
                (rs, n) -> Map.of("id", rs.getString("id"), "company", rs.getString("company"),
                        "positionTitle", rs.getString("position_title"), "lastViewedAt", instant(rs, "last_viewed_at"),
                        "viewCount", rs.getInt("view_count")), accountId));
        result.put("nextSteps", jdbc.query("SELECT id,company,next_step,next_step_at FROM job_applications WHERE account_id=? AND stage<>'CLOSED' AND next_step_at IS NOT NULL ORDER BY next_step_at LIMIT 3",
                (rs, n) -> Map.of("id", rs.getString("id"), "company", rs.getString("company"),
                        "nextStep", blank(rs.getString("next_step"), "下一步"), "nextStepAt", instant(rs, "next_step_at")),
                accountId));
        return result;
    }

    // ---- internals --------------------------------------------------------------------------------

    private List<ApplicationView> all(String accountId) {
        Map<String, LinkView> current = links.currentLinks(accountId);
        return jdbc.query(APPLICATION_SELECT + " WHERE a.account_id=? ORDER BY a.updated_at DESC,a.id",
                (rs, n) -> view(rs, current), accountId);
    }

    private ApplicationView require(String accountId, String id) {
        List<ApplicationView> found = jdbc.query(APPLICATION_SELECT + " WHERE a.id=? AND a.account_id=?",
                (rs, n) -> view(rs, Map.of()), id, accountId);
        if (found.isEmpty()) throw AppException.forbidden("OBJECT_FORBIDDEN", "投递记录不存在或无权访问");
        ApplicationView application = found.get(0);
        LinkView link = links.currentLinks(accountId).get(id);
        return link == null ? application : application.withLink(link);
    }

    static StatsView stats(List<ApplicationView> items, Instant now) {
        int applied = 0;
        int linked = 0;
        int opened = 0;
        int interviews = 0;
        int offers = 0;
        int overdue = 0;
        int upcoming = 0;
        int active = 0;
        for (ApplicationView item : items) {
            ApplicationStage furthest = ApplicationStage.parse(item.furthestStage());
            boolean sent = furthest.rank() >= ApplicationStage.APPLIED.rank();
            if (sent) applied++;
            if (ApplicationStage.parse(item.stage()).open()) active++;
            if (sent && item.link() != null) {
                linked++;
                if (item.link().viewCount() > 0) opened++;
            }
            if (furthest.rank() >= ApplicationStage.INTERVIEW.rank()) interviews++;
            if (furthest.rank() >= ApplicationStage.OFFER.rank()) offers++;
            if (item.nextStepAt() != null && !"CLOSED".equals(item.stage())) {
                if (item.nextStepAt().isBefore(now)) overdue++;
                else if (item.nextStepAt().isBefore(now.plus(Duration.ofDays(3)))) upcoming++;
            }
        }
        return new StatsView(items.size(), active, applied, linked, opened, interviews, offers, overdue, upcoming);
    }

    private Fields fields(String accountId, ApplicationWrite write) {
        String company = required(write.company(), "公司名称", 120);
        String position = required(write.positionTitle(), "岗位名称", 120);
        String channel = write.channel() == null || write.channel().isBlank() ? "OTHER"
                : write.channel().trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(channel)) throw AppException.user("APPLICATION_CHANNEL_INVALID", "不支持的投递渠道");
        String jobUrl = optional(write.jobUrl(), 500);
        if (jobUrl != null && !jobUrl.matches("(?i)^https?://\\S+$")) {
            throw AppException.user("APPLICATION_URL_INVALID", "岗位链接需要以 http:// 或 https:// 开头");
        }
        String versionId = optional(write.resumeVersionId(), 36);
        if (versionId != null) {
            Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM resume_versions WHERE id=? AND account_id=?",
                    Integer.class, versionId, accountId);
            if (owned == null || owned == 0) throw AppException.forbidden("OBJECT_FORBIDDEN", "简历版本不存在或无权访问");
        }
        String matchId = optional(write.jobMatchId(), 36);
        if (matchId != null) {
            Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM job_match_tasks WHERE id=? AND account_id=?",
                    Integer.class, matchId, accountId);
            if (owned == null || owned == 0) throw AppException.forbidden("OBJECT_FORBIDDEN", "岗位匹配不存在或无权访问");
        }
        LocalDate appliedOn = null;
        if (write.appliedOn() != null && !write.appliedOn().isBlank()) {
            try {
                appliedOn = LocalDate.parse(write.appliedOn().trim());
            } catch (DateTimeParseException e) {
                throw AppException.user("APPLICATION_DATE_INVALID", "投递日期格式不正确");
            }
        }
        return new Fields(company, position, optional(write.location(), 80), channel, jobUrl,
                optional(write.salaryText(), 60), versionId, matchId, appliedOn, optional(write.nextStep(), 200),
                write.nextStepAt(), optional(write.notes(), 4000));
    }

    private static String outcome(ApplicationStage stage, String outcome) {
        if (stage.open()) return null;
        String value = outcome == null ? "" : outcome.trim().toUpperCase(Locale.ROOT);
        if (!OUTCOMES.contains(value)) throw AppException.user("APPLICATION_OUTCOME_REQUIRED", "结束投递时请选择结果");
        return value;
    }

    private static ApplicationView view(ResultSet rs, Map<String, LinkView> currentLinks) throws SQLException {
        Object score = rs.getObject("match_score");
        java.sql.Date applied = rs.getDate("applied_on");
        return new ApplicationView(rs.getString("id"), rs.getString("company"), rs.getString("position_title"),
                rs.getString("location"), rs.getString("channel"), rs.getString("job_url"), rs.getString("stage"),
                rs.getString("furthest_stage"), rs.getString("outcome"), rs.getString("salary_text"),
                rs.getString("resume_version_id"), rs.getString("resume_master_id"), rs.getString("resume_title"),
                rs.getString("job_match_id"), rs.getString("match_title"),
                score instanceof Number number ? number.intValue() : null,
                applied == null ? null : applied.toLocalDate(), rs.getString("next_step"), instant(rs, "next_step_at"),
                rs.getString("notes"), rs.getInt("version_no"), instant(rs, "created_at"), instant(rs, "updated_at"),
                currentLinks.get(rs.getString("id")));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String required(String value, String label, int max) {
        String clean = optional(value, max);
        if (clean == null) throw AppException.user("APPLICATION_FIELD_REQUIRED", "请填写" + label);
        return clean;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void assertSeeker(CurrentAccount current) {
        if (current.operator()) throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能查看用户求职资料原文");
    }

    private record Fields(String company, String position, String location, String channel, String jobUrl,
            String salary, String versionId, String matchId, LocalDate appliedOn, String nextStep, Instant nextStepAt,
            String notes) {
    }

    public record ApplicationWrite(String company, String positionTitle, String location, String channel, String jobUrl,
            String stage, String outcome, String salaryText, String resumeVersionId, String jobMatchId, String appliedOn,
            String nextStep, Instant nextStepAt, String notes, Boolean createLink, Boolean hideContact,
            Integer expectedVersion) {
    }

    public record StageWrite(String stage, String outcome, Integer expectedVersion) {
    }

    public record ApplicationView(String id, String company, String positionTitle, String location, String channel,
            String jobUrl, String stage, String furthestStage, String outcome, String salaryText,
            String resumeVersionId, String resumeMasterId, String resumeTitle, String jobMatchId, String matchTitle,
            Integer matchScore, LocalDate appliedOn, String nextStep, Instant nextStepAt, String notes, int version,
            Instant createdAt, Instant updatedAt, LinkView link) {
        ApplicationView withLink(LinkView value) {
            return new ApplicationView(id, company, positionTitle, location, channel, jobUrl, stage, furthestStage,
                    outcome, salaryText, resumeVersionId, resumeMasterId, resumeTitle, jobMatchId, matchTitle,
                    matchScore, appliedOn, nextStep, nextStepAt, notes, version, createdAt, updatedAt, value);
        }
    }

    /**
     * Funnel counts: {@code applied} is how many went out, {@code linked} how many of those carry a resume
     * link, {@code opened} how many of those were opened; interviews and offers count the furthest stage.
     */
    public record StatsView(int total, int active, int applied, int linked, int opened, int interviews, int offers,
            int overdue, int upcoming) {
    }

    public record OverviewView(List<ApplicationView> items, StatsView stats) {
    }

    public record EventView(String id, String type, String fromStage, String toStage, String detail, Instant createdAt) {
    }

    public record DetailView(ApplicationView application, List<EventView> events, List<LinkView> links,
            List<ViewRecord> views) {
    }

    public record VersionOption(String versionId, String masterId, String resumeTitle, int ordinal, String source,
            boolean anonymous, Instant frozenAt) {
    }
}
