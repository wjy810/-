package com.jobproof.modules.aibilling.application;

import com.jobproof.modules.aibilling.domain.Subscription;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionStore {
    Optional<Subscription> findByAccountId(UUID accountId);
    void save(Subscription subscription);
}
