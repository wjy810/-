package com.jobproof.modules.storage;

import com.jobproof.modules.datarights.application.DataRightsService;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileAccessService {

    private final PrivateFileJpaRepository files;
    private final ObjectStoragePort storage;
    private final DataRightsService dataRightsService;
    private final ClockPort clock;

    public FileAccessService(
            PrivateFileJpaRepository files,
            ObjectStoragePort storage,
            @Lazy DataRightsService dataRightsService,
            ClockPort clock) {
        this.files = files;
        this.storage = storage;
        this.dataRightsService = dataRightsService;
        this.clock = clock;
    }

    @Transactional
    public FileView upload(CurrentAccount current, String filename, byte[] content) {
        if (current.operator()) {
            throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能写入或查看用户原文");
        }
        FileUploadPolicy.Accepted accepted = FileUploadPolicy.inspect(filename, content);
        return store(current, accepted, content);
    }

    @Transactional
    public FileView uploadResumePhoto(CurrentAccount current, String filename, byte[] content) {
        if (current.operator()) {
            throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能写入或查看用户原文");
        }
        FileUploadPolicy.Accepted accepted = FileUploadPolicy.inspect(filename, content);
        if (!"image/png".equals(accepted.contentType()) && !"image/jpeg".equals(accepted.contentType())) {
            throw AppException.user("RESUME_PHOTO_TYPE_INVALID", "简历照片仅支持 PNG 或 JPEG");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(content));
        } catch (Exception exception) {
            throw AppException.user("RESUME_PHOTO_INVALID", "照片内容无法解析");
        }
        if (image == null || image.getWidth() < 80 || image.getHeight() < 80
                || image.getWidth() > 4096 || image.getHeight() > 4096
                || (long) image.getWidth() * image.getHeight() > 16_000_000L) {
            throw AppException.user("RESUME_PHOTO_DIMENSIONS_INVALID", "照片边长需为 80 至 4096 像素且不超过 1600 万像素");
        }
        return store(current, accepted, content);
    }

    private FileView store(CurrentAccount current, FileUploadPolicy.Accepted accepted, byte[] content) {
        Instant now = clock.now();
        String id = Ids.newId();
        String key = FileUploadPolicy.ownedPrivateKey(current.accountId(), id, accepted.safeFilename());
        storage.put(key, content);
        PrivateFileEntity entity = new PrivateFileEntity();
        entity.setId(id);
        entity.setOwnerId(current.accountId());
        entity.setObjectKey(key);
        entity.setContentType(accepted.contentType());
        entity.setSizeBytes(content.length);
        entity.setCreatedAt(now);
        files.save(entity);
        return new FileView(id, accepted.safeFilename(), entity.getSizeBytes(), now);
    }

    @Transactional(readOnly = true)
    public ObjectStoragePort.SignedUrl downloadUrl(CurrentAccount current, String fileId, String shareToken) {
        PrivateFileEntity file = files.findById(fileId)
                .orElseThrow(() -> AppException.user("FILE_NOT_FOUND", "文件不存在"));
        boolean owner = file.getOwnerId().equals(current.accountId());
        boolean shared = dataRightsService.canReadShared("PRIVATE_FILE", fileId, shareToken);
        if (current.operator() && !owner) {
            throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能查看用户原文或文件");
        }
        if (!owner && !shared) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "无权访问该文件");
        }
        Instant expires = clock.now().plus(Duration.ofMinutes(10));
        return storage.signGet(file.getObjectKey(), expires);
    }

    @Transactional(readOnly = true)
    public byte[] readOwned(CurrentAccount current, String fileId) {
        return downloadOwned(current, fileId).body();
    }

    @Transactional(readOnly = true)
    public OwnedFileDownload downloadOwned(CurrentAccount current, String fileId) {
        PrivateFileEntity file = files.findById(fileId)
                .orElseThrow(() -> AppException.user("FILE_NOT_FOUND", "文件不存在"));
        if (!file.getOwnerId().equals(current.accountId())) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "无权访问该文件");
        }
        if (current.operator()) {
            throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能查看用户原文");
        }
        return new OwnedFileDownload(
                file.getId(),
                filenameOf(file.getObjectKey()),
                file.getContentType(),
                storage.get(file.getObjectKey()));
    }

    private static String filenameOf(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return "file.bin";
        }
        int slash = objectKey.lastIndexOf('/');
        String raw = slash >= 0 ? objectKey.substring(slash + 1) : objectKey;
        return FileUploadPolicy.safeFilename(raw);
    }

    public record FileView(String id, String filename, long sizeBytes, Instant createdAt) {
    }

    public record OwnedFileDownload(String fileId, String filename, String contentType, byte[] body) {
    }
}
