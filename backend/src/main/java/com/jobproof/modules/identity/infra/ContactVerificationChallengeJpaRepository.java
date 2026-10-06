package com.jobproof.modules.identity.infra;

import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ContactVerificationChallengeJpaRepository
        extends JpaRepository<ContactVerificationChallengeEntity, String> {

    Optional<ContactVerificationChallengeEntity>
            findFirstByChannelAndDestinationHashAndPurposeOrderByCreatedAtDesc(
                    String channel,
                    String destinationHash,
                    String purpose);

    Optional<ContactVerificationChallengeEntity>
            findByVerificationTokenHashAndConsumedAtIsNull(String verificationTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from ContactVerificationChallengeEntity challenge where challenge.id = :id")
    Optional<ContactVerificationChallengeEntity> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from ContactVerificationChallengeEntity challenge "
            + "where challenge.verificationTokenHash = :tokenHash and challenge.consumedAt is null")
    Optional<ContactVerificationChallengeEntity> findByVerificationTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);
}
