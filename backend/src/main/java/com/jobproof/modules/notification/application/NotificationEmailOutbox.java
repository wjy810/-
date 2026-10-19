package com.jobproof.modules.notification.application;

import com.jobproof.modules.notification.domain.NotificationEmailPort;
import com.jobproof.modules.notification.domain.NotificationEmailPort.NotificationEmail;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Notification emails (docs/phase2/03): written to an outbox in the same step that records the notice,
 * then sent by a sweep. A row is leased before it is sent, so instances never send the same email twice
 * and a crash mid-send is retried after the lease. Failures back off (1 min, 5 min, 30 min, 2 h) and give
 * up after five attempts.
 */
@Service
public class NotificationEmailOutbox {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailOutbox.class);
    static final int MAX_ATTEMPTS = 5;
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final Duration[] BACKOFF = { Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2) };
    private static final int BATCH = 50;

    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    private final NotificationPreferences preferences;
    private final Optional<NotificationEmailPort> port;
    private final String publicBaseUrl;

    public NotificationEmailOutbox(JdbcTemplate jdbc, ClockPort clock, NotificationPreferences preferences,
            Optional<NotificationEmailPort> port, @Value("${jobproof.public-base-url:}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.preferences = preferences;
        this.port = port;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
    }

    /** Queues the email for a new notice; does nothing without a sender or a verified address. Never throws. */
    public void enqueue(String accountId, NotificationType type, String eventId, String title, String body, String actionPath) {
        try {
            if (!preferences.emailAvailable()) return;
            Optional<String> address = preferences.verifiedEmail(accountId);
            if (address.isEmpty()) return;
            Instant now = clock.now();
            jdbc.update("INSERT INTO notification_emails(id,account_id,type,event_id,to_address,subject,body,action_path,status,attempts,next_attempt_at,created_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,'PENDING',0,?,?)",
                    Ids.newId(), accountId, type.name(), eventId, address.get(), subject(title), clip(body, 1024), actionPath, now, now);
        } catch (DuplicateKeyException already) {
            // The same notice was queued before; one email per event.
        } catch (RuntimeException exception) {
            log.warn("notification email not queued type={} eventId={}", type, eventId, exception);
        }
    }

    /** Sends what is due at {@code now}; returns how many were sent. */
    public int sendDue(Instant now) {
        NotificationEmailPort sender = port.filter(NotificationEmailPort::available).orElse(null);
        if (sender == null) return 0;
        List<Row> due = jdbc.query("SELECT id,to_address,subject,body,action_path,attempts FROM notification_emails "
                        + "WHERE status='PENDING' AND next_attempt_at<=? ORDER BY next_attempt_at LIMIT " + BATCH,
                (rs, n) -> new Row(rs.getString("id"), rs.getString("to_address"), rs.getString("subject"), rs.getString("body"),
                        rs.getString("action_path"), rs.getInt("attempts")), now);
        int sent = 0;
        for (Row row : due) {
            int leased = jdbc.update("UPDATE notification_emails SET attempts=attempts+1,next_attempt_at=? WHERE id=? AND status='PENDING' AND attempts=?",
                    now.plus(LEASE), row.id(), row.attempts());
            if (leased == 0) continue;
            try {
                sender.send(render(row));
                jdbc.update("UPDATE notification_emails SET status='SENT',sent_at=?,last_error=NULL WHERE id=?", now, row.id());
                sent++;
            } catch (RuntimeException failure) {
                int attempts = row.attempts() + 1;
                String reason = clip(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(), 200);
                if (attempts >= MAX_ATTEMPTS) {
                    jdbc.update("UPDATE notification_emails SET status='FAILED',last_error=? WHERE id=?", reason, row.id());
                } else {
                    jdbc.update("UPDATE notification_emails SET next_attempt_at=?,last_error=? WHERE id=?",
                            now.plus(BACKOFF[Math.min(attempts - 1, BACKOFF.length - 1)]), reason, row.id());
                }
                log.warn("notification email failed id={} attempt={}", row.id(), attempts);
            }
        }
        return sent;
    }

    NotificationEmail render(Row row) {
        String link = row.actionPath() != null && !publicBaseUrl.isEmpty() ? publicBaseUrl + row.actionPath() : null;
        String settings = publicBaseUrl.isEmpty() ? null : publicBaseUrl + "/account/notifications";
        String title = row.subject().replaceFirst("^【JobProof AI】", "");
        StringBuilder text = new StringBuilder(title).append("\n\n").append(row.body()).append("\n\n");
        if (link != null) text.append("查看详情：").append(link).append("\n\n");
        text.append("不想再收到这类邮件，可以在 JobProof AI 的“设置 › 通知”里关闭。");
        String html = """
                <div style="font-family:-apple-system,'PingFang SC','Microsoft YaHei',Arial,sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#1f2430;line-height:1.7">
                  <p style="margin:0 0 16px;font-size:13px;color:#6b7080">JobProof AI</p>
                  <h2 style="margin:0 0 12px;font-size:20px;line-height:1.4">%s</h2>
                  <p style="margin:0 0 20px;font-size:15px;color:#3a3f4c">%s</p>
                  %s
                  <p style="margin:28px 0 0;font-size:12px;color:#8a8f9c">不想再收到这类邮件，可以在%s里关闭。</p>
                </div>
                """.formatted(escape(title), escape(row.body()),
                link == null ? "" : "<p style=\"margin:0\"><a href=\"" + escape(link)
                        + "\" style=\"display:inline-block;padding:10px 18px;border-radius:8px;background:#4f46e5;color:#ffffff;text-decoration:none;font-weight:600\">查看详情</a></p>",
                settings == null ? "“设置 › 通知”" : "<a href=\"" + escape(settings) + "\" style=\"color:#4f46e5\">设置 › 通知</a>");
        return new NotificationEmail(row.to(), row.subject(), html, text.toString());
    }

    /** Escapes markup only; Chinese punctuation such as "·" stays readable in the source. */
    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value, "UTF-8");
    }

    static String subject(String title) {
        return clip("【JobProof AI】" + (title == null ? "" : title.replaceAll("[\\r\\n]+", " ")), 255);
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() > max ? value.substring(0, max - 1) + "…" : value;
    }

    record Row(String id, String to, String subject, String body, String actionPath, int attempts) {
    }
}
