package com.jobproof.modules.identity.infra;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, String> {
    Optional<AccountEntity> findByEmail(String email);

    Optional<AccountEntity> findByPhoneE164(String phoneE164);

    boolean existsByEmail(String email);

    boolean existsByPhoneE164(String phoneE164);
}
