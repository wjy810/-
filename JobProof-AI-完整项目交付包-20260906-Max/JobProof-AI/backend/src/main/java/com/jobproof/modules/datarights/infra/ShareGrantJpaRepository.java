package com.jobproof.modules.datarights.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareGrantJpaRepository extends JpaRepository<ShareGrantEntity, String> {
    List<ShareGrantEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<ShareGrantEntity> findByTokenHash(String tokenHash);

    List<ShareGrantEntity> findByResourceTypeAndResourceId(String resourceType, String resourceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ShareGrantEntity s set s.revokedAt = :now where s.ownerId = :ownerId and s.revokedAt is null")
    int revokeAllByOwner(@Param("ownerId") String ownerId, @Param("now") Instant now);
}
