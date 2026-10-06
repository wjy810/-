package com.jobproof.modules.identity.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionJpaRepository extends JpaRepository<SessionEntity, String> {
    Optional<SessionEntity> findByTokenHash(String tokenHash);

    List<SessionEntity> findByAccountId(String accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SessionEntity s set s.revokedAt = :now where s.accountId = :accountId and s.revokedAt is null")
    int revokeAllByAccountId(@Param("accountId") String accountId, @Param("now") Instant now);
}
