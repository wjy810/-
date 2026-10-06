package com.jobproof.modules.audit.application;

import com.jobproof.modules.audit.infra.AuditEventEntity;
import com.jobproof.modules.audit.infra.AuditEventJpaRepository;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventJpaRepository repository;
    private final ClockPort clock;

    public AuditService(AuditEventJpaRepository repository, ClockPort clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void append(String actorId, String action, String objectType, String objectId, String summary) {
        AuditEventEntity event = new AuditEventEntity();
        event.setId(Ids.newId());
        event.setActorId(actorId);
        event.setAction(action);
        event.setObjectType(objectType);
        event.setObjectId(objectId);
        event.setSummary(sanitize(summary));
        event.setCreatedAt(clock.now());
        repository.save(event);
    }

    /**
     * 运营默认不能看用户原文：审计只留必要痕迹，剥离疑似正文。
     */
    static String sanitize(String summary) {
        if (summary == null) {
            return "";
        }
        String trimmed = summary.length() > 512 ? summary.substring(0, 512) : summary;
        return trimmed.replaceAll("(?i)password=[^\\s,]+", "password=***")
                .replaceAll("\\b\\d{6}\\b", "******");
    }
}
