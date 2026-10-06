package com.jobproof.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, String> {
    /** Undelivered events whose next attempt (or expired processing lease) is due. */
    @Query("""
            select e.id from OutboxEventEntity e
            where e.publishedAt is null and e.status in :statuses
              and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
            order by e.createdAt asc
            """)
    List<String> findDueIds(@Param("statuses") Collection<String> statuses, @Param("now") Instant now, Pageable pageable);

    /**
     * Claims one event by conditional update, so concurrent relays (several instances) never deliver
     * the same event twice. Returns 1 when this caller won the claim.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEventEntity e set e.status = 'PROCESSING', e.nextAttemptAt = :leaseUntil
            where e.id = :id and e.publishedAt is null and e.status in :statuses
              and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
            """)
    int claim(@Param("id") String id, @Param("statuses") Collection<String> statuses,
            @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    long countByStatus(String status);
}
