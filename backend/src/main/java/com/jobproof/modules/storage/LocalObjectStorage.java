package com.jobproof.modules.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "jobproof.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorage implements ObjectStoragePort {

    private final Path root;

    public LocalObjectStorage(@Value("${jobproof.storage.local-dir:.local-data/files}") String dir) {
        this.root = Path.of(dir);
    }

    @Override
    public void put(String objectKey, byte[] content) {
        try {
            Path path = resolveWithinRoot(objectKey);
            Files.createDirectories(path.getParent());
            Files.write(path, content);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("写入对象存储失败", e);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            return Files.readAllBytes(resolveWithinRoot(objectKey));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取对象存储失败", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolveWithinRoot(objectKey));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("删除对象失败", e);
        }
    }

    @Override
    public SignedUrl signGet(String objectKey, Instant expiresAt) {
        return new SignedUrl("local://" + objectKey, expiresAt, Duration.between(Instant.now(), expiresAt));
    }

    private Path resolveWithinRoot(String objectKey) {
        assertRelativeObjectKey(objectKey);
        Path rootAbs = root.toAbsolutePath().normalize();
        Path resolved = rootAbs.resolve(objectKey).normalize();
        if (!resolved.startsWith(rootAbs) || resolved.equals(rootAbs)) {
            throw new IllegalStateException("非法对象键");
        }
        return resolved;
    }

    private static void assertRelativeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.indexOf('\\') >= 0) {
            throw new IllegalStateException("非法对象键");
        }
        if (objectKey.startsWith("/") || objectKey.startsWith("\\")) {
            throw new IllegalStateException("非法对象键");
        }
        if (objectKey.length() >= 2 && Character.isLetter(objectKey.charAt(0)) && objectKey.charAt(1) == ':') {
            throw new IllegalStateException("非法对象键");
        }
        for (String part : objectKey.split("/")) {
            if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
                throw new IllegalStateException("非法对象键");
            }
        }
    }
}
