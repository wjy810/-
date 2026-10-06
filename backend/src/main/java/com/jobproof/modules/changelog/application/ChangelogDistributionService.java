package com.jobproof.modules.changelog.application;

import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.notification.application.NotificationService;
import com.jobproof.modules.notification.domain.NotificationStatus;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangelogDistributionService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ClockPort clock;

    public ChangelogDistributionService(JdbcTemplate jdbc, NotificationService notifications, AuditService audit,
            ClockPort clock) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void enqueue(String releaseId) {
        Boolean enabled = jdbc.query("SELECT send_notification FROM changelog_releases WHERE id=? AND status='PUBLISHED'",
                rs -> rs.next() ? rs.getBoolean(1) : null, releaseId);
        if (!Boolean.TRUE.equals(enabled)) return;
        Instant now = clock.now();
        try {
            jdbc.update("INSERT INTO changelog_distribution_jobs(id,release_id,status,cursor_account_id,delivered_count,failed_count,attempts,created_at,updated_at,completed_at) VALUES(?,?,'PENDING',NULL,0,0,0,?,?,NULL)",
                    Ids.newId(), releaseId, now, now);
        } catch (DataIntegrityViolationException ignored) {
            // A published release has exactly one resumable distribution job.
        }
    }

    @Transactional
    public boolean processOneBatch() {
        Job job = jdbc.query("SELECT id,release_id,cursor_account_id,delivered_count,failed_count FROM changelog_distribution_jobs WHERE status IN ('PENDING','RUNNING') ORDER BY created_at LIMIT 1 FOR UPDATE",
                rs -> rs.next() ? new Job(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5)) : null);
        if (job == null) return false;
        Instant now = clock.now();
        jdbc.update("UPDATE changelog_distribution_jobs SET status='RUNNING',attempts=attempts+1,updated_at=? WHERE id=?", now, job.id());
        Release release = jdbc.query("SELECT version_label,title,summary,slug FROM changelog_releases WHERE id=? AND status='PUBLISHED'",
                rs -> rs.next() ? new Release(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)) : null,
                job.releaseId());
        if (release == null) {
            jdbc.update("UPDATE changelog_distribution_jobs SET status='CANCELLED',updated_at=?,completed_at=? WHERE id=?", now, now, job.id());
            return true;
        }
        String cursor = job.cursorAccountId() == null ? "" : job.cursorAccountId();
        List<String> accounts = jdbc.query("SELECT id FROM accounts WHERE status='ACTIVE' AND id>? ORDER BY id LIMIT 100",
                (rs, n) -> rs.getString(1), cursor);
        if (accounts.isEmpty()) {
            jdbc.update("UPDATE changelog_distribution_jobs SET status='SUCCEEDED',updated_at=?,completed_at=? WHERE id=?", now, now, job.id());
            audit.append(null, "CHANGELOG_DISTRIBUTED", "CHANGELOG_RELEASE", job.releaseId(),
                    "delivered=" + job.deliveredCount() + ", failed=" + job.failedCount());
            return true;
        }
        long delivered = 0;
        long failed = 0;
        for (String accountId : accounts) {
            var notification = notifications.request(accountId, NotificationType.PRODUCT_UPDATE, job.releaseId(),
                    release.versionLabel() + " " + release.title(), release.summary(), "/updates/" + release.slug());
            if (NotificationStatus.SEND_FAILED.name().equals(notification.status())) failed++; else delivered++;
        }
        jdbc.update("UPDATE changelog_distribution_jobs SET cursor_account_id=?,delivered_count=delivered_count+?,failed_count=failed_count+?,updated_at=? WHERE id=?",
                accounts.get(accounts.size() - 1), delivered, failed, clock.now(), job.id());
        return true;
    }

    private record Job(String id, String releaseId, String cursorAccountId, long deliveredCount, long failedCount) {}
    private record Release(String versionLabel, String title, String summary, String slug) {}
}
