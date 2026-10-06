package com.jobproof.modules.storage.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.storage.FileAccessService;
import com.jobproof.modules.storage.FileAccessService.FileView;
import com.jobproof.modules.storage.FileAccessService.OwnedFileDownload;
import com.jobproof.modules.storage.ObjectStoragePort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileAccessService fileAccessService;

    public FileController(FileAccessService fileAccessService) {
        this.fileAccessService = fileAccessService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileView> upload(@RequestParam("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(fileAccessService.upload(
                SecurityConfig.currentAccount(),
                file.getOriginalFilename(),
                file.getBytes()));
    }

    @GetMapping("/{id}/download-url")
    public ApiResponse<ObjectStoragePort.SignedUrl> downloadUrl(
            @PathVariable String id,
            @RequestParam(value = "shareToken", required = false) String shareToken) {
        return ApiResponse.ok(fileAccessService.downloadUrl(SecurityConfig.currentAccount(), id, shareToken));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        OwnedFileDownload file = fileAccessService.downloadOwned(SecurityConfig.currentAccount(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(mediaTypeOf(file.contentType()))
                .body(file.body());
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable String id) {
        OwnedFileDownload file = fileAccessService.downloadOwned(SecurityConfig.currentAccount(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(mediaTypeOf(file.contentType()))
                .body(file.body());
    }

    private static MediaType mediaTypeOf(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
