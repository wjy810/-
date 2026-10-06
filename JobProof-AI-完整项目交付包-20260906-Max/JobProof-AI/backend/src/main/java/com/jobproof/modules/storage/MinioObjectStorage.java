package com.jobproof.modules.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "jobproof.storage", name = "type", havingValue = "minio")
public class MinioObjectStorage implements ObjectStoragePort {
    private static final int MAX_SIGNED_URL_SECONDS = 7 * 24 * 60 * 60;
    private static final Set<String> MISSING_CODES = Set.of("NoSuchKey", "NoSuchObject", "XMinioInvalidObjectName");

    private final MinioClient client;
    private final String bucket;
    private final LocalObjectStorage legacyStorage;

    public MinioObjectStorage(
            @Value("${jobproof.storage.minio.endpoint}") String endpoint,
            @Value("${jobproof.storage.minio.access-key}") String accessKey,
            @Value("${jobproof.storage.minio.secret-key}") String secretKey,
            @Value("${jobproof.storage.minio.bucket:jobproof-private}") String bucket,
            @Value("${jobproof.storage.local-dir:.local-data/files}") String legacyDirectory) {
        if (endpoint == null || endpoint.isBlank() || accessKey == null || accessKey.isBlank()
                || secretKey == null || secretKey.isBlank() || bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("MinIO configuration is incomplete");
        }
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        this.legacyStorage = new LocalObjectStorage(legacyDirectory);
        ensureBucket();
    }

    @Override
    public void put(String objectKey, byte[] content) {
        assertObjectKey(objectKey);
        if (content == null) throw new IllegalStateException("Object content is missing");
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(input, content.length, -1)
                    .contentType("application/octet-stream")
                    .build());
        } catch (Exception exception) {
            throw failure("Failed to write object to MinIO", exception);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        assertObjectKey(objectKey);
        try (var response = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            return response.readAllBytes();
        } catch (Exception exception) {
            if (!missing(exception)) throw failure("Failed to read object from MinIO", exception);
            byte[] legacy = legacyStorage.get(objectKey);
            put(objectKey, legacy);
            return legacy;
        }
    }

    @Override
    public void delete(String objectKey) {
        assertObjectKey(objectKey);
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
            legacyStorage.delete(objectKey);
        } catch (Exception exception) {
            throw failure("Failed to delete object from MinIO", exception);
        }
    }

    @Override
    public SignedUrl signGet(String objectKey, Instant expiresAt) {
        assertObjectKey(objectKey);
        migrateLegacyObjectIfNeeded(objectKey);
        long requestedSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        int expirySeconds = (int) Math.max(1, Math.min(MAX_SIGNED_URL_SECONDS, requestedSeconds));
        try {
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expirySeconds)
                    .build());
            return new SignedUrl(url, expiresAt, Duration.ofSeconds(expirySeconds));
        } catch (Exception exception) {
            throw failure("Failed to sign MinIO object URL", exception);
        }
    }

    private void migrateLegacyObjectIfNeeded(String objectKey) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            if (!missing(exception)) throw failure("Failed to inspect MinIO object", exception);
            put(objectKey, legacyStorage.get(objectKey));
        }
    }

    private void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw failure("Failed to initialize MinIO bucket", exception);
        }
    }

    private static boolean missing(Exception exception) {
        return exception instanceof ErrorResponseException error
                && MISSING_CODES.contains(error.errorResponse().code());
    }

    private static IllegalStateException failure(String message, Exception exception) {
        return new IllegalStateException(message, exception);
    }

    private static void assertObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")
                || objectKey.indexOf('\\') >= 0 || objectKey.matches("^[A-Za-z]:.*")) {
            throw new IllegalStateException("Invalid object key");
        }
        for (String part : objectKey.split("/")) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                throw new IllegalStateException("Invalid object key");
            }
        }
    }
}
