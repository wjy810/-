package com.jobproof.modules.notification.application;

import com.jobproof.modules.notification.domain.NotificationEmailPort;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.export.AccountExportContributor;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知偏好 (docs/phase2/03): per kind of notice, whether it shows in the notification center and whether
 * it is emailed. Only the kinds a user can reasonably do without are tunable; security, data rights and
 * task notices always show and are never emailed. Email goes only to a verified address.
 */
@Service
public class NotificationPreferences implements DeletionModuleHandler, AccountExportContributor {

    /** The tunable kinds, in display order, with their defaults. */
    static final Map<NotificationType, Preference> DEFAULTS;
    private static final Preference FIXED = new Preference(true, false);

    static {
        Map<NotificationType, Preference> defaults = new LinkedHashMap<>();
        defaults.put(NotificationType.APPLICATION_ACTIVITY, new Preference(true, true));
        defaults.put(NotificationType.REMINDER, new Preference(true, true));
        defaults.put(NotificationType.MEMBERSHIP, new Preference(true, true));
        defaults.put(NotificationType.WEEKLY_DIGEST, new Preference(true, true));
        defaults.put(NotificationType.PRODUCT_UPDATE, new Preference(true, false));
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    private final Optional<NotificationEmailPort> email;

    public NotificationPreferences(JdbcTemplate jdbc, ClockPort clock, Optional<NotificationEmailPort> email) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.email = email;
    }

    @Override public String moduleCode() { return "notification-settings"; }
    @Override public String moduleKey() { return "notificationSettings"; }

    /** What applies to one notice; untunable kinds get the fixed rule. */
    public Preference of(String accountId, NotificationType type) {
        Preference fallback = DEFAULTS.get(type);
        if (fallback == null) return FIXED;
        return jdbc.query("SELECT in_app,email FROM notification_preferences WHERE account_id=? AND type=?",
                (rs, n) -> new Preference(rs.getBoolean("in_app"), rs.getBoolean("email")), accountId, type.name())
                .stream().findFirst().orElse(fallback);
    }

    public boolean emailAvailable() {
        return email.map(NotificationEmailPort::available).orElse(false);
    }

    /** The address notices may be emailed to: the account's email, once verified. */
    public Optional<String> verifiedEmail(String accountId) {
        return jdbc.queryForList("SELECT email FROM accounts WHERE id=? AND status='ACTIVE' AND email_verified_at IS NOT NULL AND email IS NOT NULL",
                String.class, accountId).stream().filter(value -> !value.isBlank()).findFirst();
    }

    @Transactional(readOnly = true)
    public PreferencesView view(CurrentAccount current) {
        String accountId = current.accountId();
        Map<NotificationType, Preference> saved = new EnumMap<>(NotificationType.class);
        jdbc.query("SELECT type,in_app,email FROM notification_preferences WHERE account_id=?",
                (rs, n) -> Map.entry(rs.getString("type"), new Preference(rs.getBoolean("in_app"), rs.getBoolean("email"))), accountId)
                .forEach(row -> parse(row.getKey()).ifPresent(type -> saved.put(type, row.getValue())));
        List<ItemView> items = new ArrayList<>();
        DEFAULTS.forEach((type, fallback) -> {
            Preference value = saved.getOrDefault(type, fallback);
            items.add(new ItemView(type.name(), value.inApp(), value.email()));
        });
        Optional<String> address = verifiedEmail(accountId);
        return new PreferencesView(items, emailAvailable(), address.map(NotificationPreferences::mask).orElse(null));
    }

    @Transactional
    public PreferencesView update(CurrentAccount current, List<ItemView> items) {
        if (items == null || items.isEmpty()) throw AppException.user("NOTIFICATION_PREFERENCES_EMPTY", "请至少调整一项通知设置");
        Instant now = clock.now();
        for (ItemView item : items) {
            NotificationType type = parse(item == null ? null : item.type())
                    .filter(DEFAULTS::containsKey)
                    .orElseThrow(() -> AppException.user("NOTIFICATION_TYPE_LOCKED", "这类通知不能关闭"));
            int changed = jdbc.update("UPDATE notification_preferences SET in_app=?,email=?,updated_at=? WHERE account_id=? AND type=?",
                    item.inApp(), item.email(), now, current.accountId(), type.name());
            if (changed == 0) {
                try {
                    jdbc.update("INSERT INTO notification_preferences(account_id,type,in_app,email,updated_at) VALUES(?,?,?,?,?)",
                            current.accountId(), type.name(), item.inApp(), item.email(), now);
                } catch (DuplicateKeyException raced) {
                    jdbc.update("UPDATE notification_preferences SET in_app=?,email=?,updated_at=? WHERE account_id=? AND type=?",
                            item.inApp(), item.email(), now, current.accountId(), type.name());
                }
            }
        }
        return view(current);
    }

    @Override
    @Transactional
    public Result onAccountDeletion(String accountId) {
        try {
            jdbc.update("DELETE FROM notification_emails WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM notification_preferences WHERE account_id=?", accountId);
            return Result.succeeded("通知偏好与邮件发送记录已删除，待发的提醒邮件已取消");
        } catch (RuntimeException e) {
            return Result.failed("通知偏好暂未清除，删除申请会保持可重试");
        }
    }

    @Override
    public Map<String, Object> contribute(String accountId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("preferences", jdbc.queryForList("SELECT type,in_app,email,updated_at FROM notification_preferences WHERE account_id=? ORDER BY type", accountId));
        out.put("emails", jdbc.queryForList("SELECT type,to_address,subject,status,created_at,sent_at FROM notification_emails WHERE account_id=? ORDER BY created_at", accountId));
        return out;
    }

    /** "an***@example.com": enough to recognise the address without showing it whole. */
    static String mask(String address) {
        int at = address.indexOf('@');
        if (at <= 0) return "***";
        String name = address.substring(0, at);
        String shown = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return shown + "***" + address.substring(at);
    }

    private static Optional<NotificationType> parse(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(NotificationType.valueOf(value));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    public record Preference(boolean inApp, boolean email) {
    }

    public record ItemView(String type, boolean inApp, boolean email) {
    }

    public record PreferencesView(List<ItemView> items, boolean emailAvailable, String emailAddress) {
    }
}
