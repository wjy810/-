package com.jobproof.modules.notification.application;

import com.jobproof.modules.notification.domain.NotificationStatus;
import com.jobproof.modules.notification.domain.NotificationType;
import com.jobproof.modules.notification.infra.NotificationEntity;
import com.jobproof.modules.notification.infra.NotificationJpaRepository;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import com.jobproof.shared.time.ClockPort;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationJpaRepository notifications;
    private final ClockPort clock;
    private final TransactionTemplate requiresNew;
    private volatile boolean failNextWrite;

    public NotificationService(
            NotificationJpaRepository notifications,
            ClockPort clock,
            PlatformTransactionManager transactionManager) {
        this.notifications = notifications;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void failNextWrite() {
        this.failNextWrite = true;
    }

    /**
     * 同一事件 ID 更新已有提醒，不叠第二条有效提醒。失败不抛给业务调用方。
     */
    public NotificationView upsertActive(
            String accountId,
            NotificationType type,
            String eventId,
            String title,
            String body) {
        try {
            return requiresNew.execute(status -> upsertDelivered(accountId, type, eventId, title, body));
        } catch (RuntimeException ex) {
            log.warn("notification upsert failed, business caller unchanged eventId={} type={}", eventId, type);
            try {
                return requiresNew.execute(status -> persistFailed(accountId, type, eventId, title, body, null));
            } catch (RuntimeException ignored) {
                return syntheticFailed(type, eventId, title, body, null);
            }
        }
    }

    /**
     * 作废旧提醒。找不到则忽略。失败不抛给业务调用方。
     */
    public void expire(String accountId, NotificationType type, String eventId) {
        try {
            requiresNew.execute(status -> {
                expireExisting(accountId, type, eventId);
                return null;
            });
        } catch (RuntimeException ex) {
            log.warn("notification expire failed, business caller unchanged eventId={} type={}", eventId, type);
        }
    }

    /**
     * 渠道失败不得抛给调用方，也不得把外层业务事务标成 rollback-only。
     * 失败时另开事务写入 {@code SEND_FAILED}，对应确认记录：通知失败不回滚业务。
     */
    public NotificationView request(String accountId, NotificationType type, String eventId, String title, String body) {
        return request(accountId, type, eventId, title, body, null);
    }

    public NotificationView request(String accountId, NotificationType type, String eventId, String title, String body,
            String actionPath) {
        try {
            return requiresNew.execute(status -> deliver(accountId, type, eventId, title, body, actionPath));
        } catch (RuntimeException ex) {
            log.warn("notification channel failed, business caller unchanged eventId={} type={}", eventId, type);
            try {
                return requiresNew.execute(status -> persistFailed(accountId, type, eventId, title, body, actionPath));
            } catch (RuntimeException ignored) {
                return syntheticFailed(type, eventId, title, body, actionPath);
            }
        }
    }

    private NotificationView deliver(String accountId, NotificationType type, String eventId, String title, String body,
            String actionPath) {
        if (failNextWrite) {
            failNextWrite = false;
            throw new IllegalStateException("simulated notification channel failure");
        }
        return notifications.findByAccountIdAndEventIdAndType(accountId, eventId, type.name())
                .map(existing -> {
                    if (actionPath != null && !actionPath.equals(existing.getActionPath())) {
                        existing.setActionPath(actionPath);
                        notifications.save(existing);
                    }
                    return NotificationView.from(existing);
                })
                .orElseGet(() -> insertDeduped(accountId, type, eventId, title, body, actionPath));
    }

    private NotificationView persistFailed(String accountId, NotificationType type, String eventId, String title, String body,
            String actionPath) {
        return notifications.findByAccountIdAndEventIdAndType(accountId, eventId, type.name())
                .map(NotificationView::from)
                .orElseGet(() -> {
                    Instant now = clock.now();
                    NotificationEntity entity = new NotificationEntity();
                    entity.setId(Ids.newId());
                    entity.setAccountId(accountId);
                    entity.setType(type.name());
                    entity.setStatus(NotificationStatus.SEND_FAILED.name());
                    entity.setEventId(eventId);
                    entity.setTitle(title);
                    entity.setBody(stripOriginal(body));
                    entity.setActionPath(safeActionPath(actionPath));
                    entity.setCreatedAt(now);
                    try {
                        notifications.saveAndFlush(entity);
                        return NotificationView.from(entity);
                    } catch (DataIntegrityViolationException ignored) {
                        return notifications.findByAccountIdAndEventIdAndType(accountId, eventId, type.name())
                                .map(NotificationView::from)
                                .orElseGet(() -> NotificationView.from(entity));
                    }
                });
    }

    private NotificationView syntheticFailed(NotificationType type, String eventId, String title, String body, String actionPath) {
        return new NotificationView(
                Ids.newId(),
                type.name(),
                NotificationStatus.SEND_FAILED.name(),
                eventId,
                title,
                stripOriginal(body),
                clock.now(),
                null,
                safeActionPath(actionPath));
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationView> list(String accountId, String status, PageQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size());
        Page<NotificationEntity> page = status == null || status.isBlank()
                ? notifications.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                : notifications.findByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status, pageable);
        return new PageResult<>(page.map(NotificationView::from).toList(), page.getTotalElements(), query.page(), query.size());
    }

    @Transactional(readOnly = true)
    public long unreadCount(String accountId) {
        return notifications.countByAccountIdAndStatus(accountId, NotificationStatus.DELIVERED.name());
    }

    @Transactional
    public void markRead(String accountId, String notificationId) {
        NotificationEntity entity = notifications.findById(notificationId)
                .orElseThrow(() -> AppException.user("NOTIFICATION_NOT_FOUND", "通知不存在"));
        if (!entity.getAccountId().equals(accountId)) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "不能操作他人的通知");
        }
        if (NotificationStatus.READ.name().equals(entity.getStatus())) {
            return;
        }
        assertDeliveredForRead(entity);
        entity.setStatus(NotificationStatus.READ.name());
        entity.setReadAt(clock.now());
        notifications.save(entity);
    }

    @Transactional
    public void markReadBatch(String accountId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Instant now = clock.now();
        for (NotificationEntity entity : notifications.findByAccountIdAndIdIn(accountId, ids)) {
            if (!NotificationStatus.DELIVERED.name().equals(entity.getStatus())) {
                continue;
            }
            entity.setStatus(NotificationStatus.READ.name());
            entity.setReadAt(now);
        }
    }

    @Transactional
    public void markReadByEvent(String accountId, NotificationType type, String eventId) {
        notifications.findByAccountIdAndEventIdAndType(accountId, eventId, type.name()).ifPresent(entity -> {
            if (NotificationStatus.DELIVERED.name().equals(entity.getStatus())) {
                entity.setStatus(NotificationStatus.READ.name());
                entity.setReadAt(clock.now());
                notifications.save(entity);
            }
        });
    }

    private static void assertDeliveredForRead(NotificationEntity entity) {
        if (NotificationStatus.DELIVERED.name().equals(entity.getStatus())) {
            return;
        }
        if (NotificationStatus.SEND_FAILED.name().equals(entity.getStatus())) {
            throw AppException.conflict(
                    "NOTIFICATION_SEND_FAILED",
                    "发送失败的通知不能标成已读来假装已送达。通知失败不回滚业务。");
        }
        throw AppException.conflict(
                "NOTIFICATION_NOT_DELIVERED",
                "只有已送达的通知可以标为已读，不能假装已发送。");
    }

    private NotificationView upsertDelivered(String accountId, NotificationType type, String eventId, String title, String body) {
        if (failNextWrite) {
            failNextWrite = false;
            throw new IllegalStateException("simulated notification channel failure");
        }
        return notifications.findByAccountIdAndEventIdAndType(accountId, eventId, type.name())
                .map(existing -> {
                    existing.setTitle(title);
                    existing.setBody(stripOriginal(body));
                    existing.setStatus(NotificationStatus.DELIVERED.name());
                    existing.setReadAt(null);
                    notifications.save(existing);
                    return NotificationView.from(existing);
                })
                .orElseGet(() -> insertDeduped(accountId, type, eventId, title, body, null));
    }

    private void expireExisting(String accountId, NotificationType type, String eventId) {
        notifications.findByAccountIdAndEventIdAndType(accountId, eventId, type.name())
                .ifPresent(existing -> {
                    if (NotificationStatus.EXPIRED.name().equals(existing.getStatus())
                            || NotificationStatus.ARCHIVED.name().equals(existing.getStatus())) {
                        return;
                    }
                    existing.setStatus(NotificationStatus.EXPIRED.name());
                    notifications.save(existing);
                });
    }

    private NotificationView insertDeduped(String accountId, NotificationType type, String eventId, String title, String body,
            String actionPath) {
        Instant now = clock.now();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(Ids.newId());
        entity.setAccountId(accountId);
        entity.setType(type.name());
        entity.setStatus(NotificationStatus.DELIVERED.name());
        entity.setEventId(eventId);
        entity.setTitle(title);
        entity.setBody(stripOriginal(body));
        entity.setActionPath(safeActionPath(actionPath));
        entity.setCreatedAt(now);
        try {
            notifications.saveAndFlush(entity);
            return NotificationView.from(entity);
        } catch (DataIntegrityViolationException ignored) {
            return notifications.findByAccountIdAndEventIdAndType(accountId, eventId, type.name())
                    .map(NotificationView::from)
                    .orElseThrow();
        }
    }

    static String stripOriginal(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 1024 ? body.substring(0, 1024) : body;
    }

    private static String safeActionPath(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (!clean.startsWith("/") || clean.startsWith("//") || clean.length() > 500) return null;
        return clean;
    }

    public record NotificationView(
            String id,
            String type,
            String status,
            String eventId,
            String title,
            String body,
            Instant createdAt,
            Instant readAt,
            String actionPath) {
        static NotificationView from(NotificationEntity entity) {
            return new NotificationView(
                    entity.getId(),
                    entity.getType(),
                    entity.getStatus(),
                    entity.getEventId(),
                    entity.getTitle(),
                    entity.getBody(),
                    entity.getCreatedAt(),
                    entity.getReadAt(),
                    entity.getActionPath());
        }
    }
}
