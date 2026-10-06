package com.jobproof.modules.storage;

import java.time.Duration;
import java.time.Instant;

public interface ObjectStoragePort {

    void put(String objectKey, byte[] content);

    byte[] get(String objectKey);

    void delete(String objectKey);

    SignedUrl signGet(String objectKey, Instant expiresAt);

    record SignedUrl(String url, Instant expiresAt, Duration ttl) {
    }
}
