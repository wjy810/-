package com.jobproof.modules.notification.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, String> {
    Optional<NotificationEntity> findByAccountIdAndEventIdAndType(String accountId, String eventId, String type);

    Page<NotificationEntity> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);

    Page<NotificationEntity> findByAccountIdAndStatusOrderByCreatedAtDesc(
            String accountId, String status, Pageable pageable);

    List<NotificationEntity> findByAccountIdAndIdIn(String accountId, List<String> ids);

    long countByAccountIdAndStatus(String accountId, String status);
}
