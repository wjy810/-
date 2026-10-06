package com.jobproof.modules.identity.domain;

import java.time.Instant;

public record SessionRecord(String id, String accountId, String tokenHash, Instant expiresAt, Instant revokedAt) {

    public boolean active(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public SessionRecord revoke(Instant now) {
        return new SessionRecord(id, accountId, tokenHash, expiresAt, now);
    }
}
