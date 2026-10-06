package com.jobproof.modules.identity.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetJpaRepository extends JpaRepository<PasswordResetEntity, String> {
    List<PasswordResetEntity> findByAccountIdOrderByCreatedAtDesc(String accountId);

    Optional<PasswordResetEntity> findFirstByAccountIdAndConsumedAtIsNullOrderByCreatedAtDesc(String accountId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetEntity r set r.consumedAt = :now where r.accountId = :accountId and r.consumedAt is null")
    int consumeAllOpenByAccountId(@Param("accountId") String accountId, @Param("now") Instant now);
}
