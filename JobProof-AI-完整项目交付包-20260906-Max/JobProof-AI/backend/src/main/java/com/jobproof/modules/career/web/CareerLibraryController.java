package com.jobproof.modules.career.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.career.application.CareerFileService;
import com.jobproof.modules.career.application.CareerFileService.Binary;
import com.jobproof.modules.career.application.CareerFileService.FileView;
import com.jobproof.modules.career.application.CareerFileService.FileWrite;
import com.jobproof.modules.career.application.CareerFileService.FolderView;
import com.jobproof.modules.career.application.CareerFileService.FolderWrite;
import com.jobproof.modules.career.application.CareerFileService.StorageView;
import com.jobproof.modules.career.application.CareerFileService.UploadView;
import com.jobproof.modules.career.application.CareerRecordAiCandidateService;
import com.jobproof.modules.career.application.CareerRecordAiCandidateService.AvailabilityView;
import com.jobproof.modules.career.application.CareerRecordAiCandidateService.CandidateView;
import com.jobproof.modules.career.application.CareerRecordAiCandidateService.Decision;
import com.jobproof.modules.career.application.CareerRecordAiCandidateService.GenerateCommand;
import com.jobproof.modules.career.application.CareerLibraryService;
import com.jobproof.modules.career.application.CareerLibraryService.HistorySummary;
import com.jobproof.modules.career.application.CareerLibraryService.OverviewView;
import com.jobproof.modules.career.application.CareerLibraryService.ProfileView;
import com.jobproof.modules.career.application.CareerLibraryService.ProfileWrite;
import com.jobproof.modules.career.application.CareerLibraryService.RecordOrder;
import com.jobproof.modules.career.application.CareerLibraryService.RecordView;
import com.jobproof.modules.career.application.CareerLibraryService.RecordWrite;
import com.jobproof.modules.career.application.CareerLibraryService.SearchResult;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/career-library")
public class CareerLibraryController {
    private final CareerLibraryService library;
    private final CareerFileService files;
    private final CareerRecordAiCandidateService recordAi;

    public CareerLibraryController(CareerLibraryService library, CareerFileService files,
            CareerRecordAiCandidateService recordAi) {
        this.library = library;
        this.files = files;
        this.recordAi = recordAi;
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileView> profile() { return ApiResponse.ok(library.profile(SecurityConfig.currentAccount())); }

    @PutMapping("/profile")
    public ApiResponse<ProfileView> updateProfile(@RequestBody ProfileWrite write) {
        return ApiResponse.ok(library.updateProfile(SecurityConfig.currentAccount(), write));
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarUploadView>> uploadAvatar(@RequestParam("file") MultipartFile file)
            throws Exception {
        var current = SecurityConfig.currentAccount();
        UploadView upload = files.uploadAvatar(current, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.accepted().body(ApiResponse.ok(new AvatarUploadView(
                library.profile(current), upload.file(), upload.task())));
    }

    @PutMapping("/profile/avatar/{fileId}")
    public ApiResponse<ProfileView> commitAvatar(@PathVariable String fileId) {
        var current = SecurityConfig.currentAccount();
        FileView candidate = files.get(current, fileId);
        if (!candidate.contentType().startsWith("image/") || !"READY".equals(candidate.processingStatus())
                || !"CLEAN".equals(candidate.scanStatus())) {
            throw com.jobproof.shared.error.AppException.conflict("CAREER_AVATAR_NOT_READY",
                    "头像尚未通过安全扫描和缩略图门禁");
        }
        ProfileView previous = library.profile(current);
        ProfileView profile = library.setAvatar(current, fileId);
        if (previous.avatarFileId() != null && !previous.avatarFileId().equals(fileId)) {
            files.archive(current, previous.avatarFileId(), null);
        }
        return ApiResponse.ok(profile);
    }

    @DeleteMapping("/profile/avatar")
    public ApiResponse<ProfileView> deleteAvatar() {
        var current = SecurityConfig.currentAccount();
        ProfileView previous = library.profile(current);
        ProfileView profile = library.clearAvatar(current);
        if (previous.avatarFileId() != null) files.archive(current, previous.avatarFileId(), null);
        return ApiResponse.ok(profile);
    }

    @GetMapping("/profile/avatar/content")
    public ResponseEntity<byte[]> avatarContent() {
        var current = SecurityConfig.currentAccount();
        ProfileView profile = library.profile(current);
        if (profile.avatarFileId() == null) {
            throw com.jobproof.shared.error.AppException.user("CAREER_AVATAR_NOT_FOUND", "尚未上传头像");
        }
        Binary value = files.preview(current, profile.avatarFileId(), 1);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.IMAGE_PNG).contentLength(value.body().length).body(value.body());
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewView> overview() {
        return ApiResponse.ok(library.overview(SecurityConfig.currentAccount()));
    }

    @GetMapping("/search")
    public ApiResponse<List<SearchResult>> search(@RequestParam("q") String query) {
        return ApiResponse.ok(library.search(SecurityConfig.currentAccount(), query));
    }

    @GetMapping("/records")
    public ApiResponse<PageResult<RecordView>> records(@RequestParam(required = false) String type,
            @RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
            @ModelAttribute PageQuery page) {
        return ApiResponse.ok(library.records(SecurityConfig.currentAccount(), type, status, keyword, page));
    }

    @PostMapping("/records")
    public ApiResponse<RecordView> createRecord(@RequestBody RecordWrite write) {
        return ApiResponse.ok(library.createRecord(SecurityConfig.currentAccount(), write));
    }

    @GetMapping("/records/{id}")
    public ApiResponse<RecordView> record(@PathVariable String id) {
        return ApiResponse.ok(library.record(SecurityConfig.currentAccount(), id));
    }

    @PutMapping("/records/{id}")
    public ApiResponse<RecordView> updateRecord(@PathVariable String id, @RequestBody RecordWrite write) {
        return ApiResponse.ok(library.updateRecord(SecurityConfig.currentAccount(), id, write));
    }

    @PutMapping("/records/order")
    public ApiResponse<List<RecordView>> reorder(@RequestBody List<RecordOrder> order) {
        return ApiResponse.ok(library.reorder(SecurityConfig.currentAccount(), order));
    }

    @PostMapping("/records/{id}/archive")
    public ApiResponse<RecordView> archiveRecord(@PathVariable String id, @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(library.archiveRecord(SecurityConfig.currentAccount(), id, request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/records/{id}/restore")
    public ApiResponse<RecordView> restoreRecord(@PathVariable String id, @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(library.restoreRecord(SecurityConfig.currentAccount(), id, request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/records/{id}/copy")
    public ApiResponse<RecordView> copyRecord(@PathVariable String id) {
        return ApiResponse.ok(library.copyRecord(SecurityConfig.currentAccount(), id));
    }

    @GetMapping("/records/ai-availability")
    public ApiResponse<AvailabilityView> recordAiAvailability() {
        return ApiResponse.ok(recordAi.availability(SecurityConfig.currentAccount()));
    }

    @GetMapping("/records/{id}/ai-candidates")
    public ApiResponse<List<CandidateView>> recordAiCandidates(@PathVariable String id,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(recordAi.list(SecurityConfig.currentAccount(), id, status));
    }

    @PostMapping("/records/{id}/ai-candidates")
    public ApiResponse<CandidateView> generateRecordAiCandidate(@PathVariable String id,
            @RequestBody(required = false) GenerateCommand command) {
        return ApiResponse.ok(recordAi.generate(SecurityConfig.currentAccount(), id, command));
    }

    @PostMapping("/records/ai-candidates/{candidateId}/accept")
    public ApiResponse<CandidateView> acceptRecordAiCandidate(@PathVariable String candidateId,
            @RequestBody(required = false) Decision command) {
        return ApiResponse.ok(recordAi.accept(SecurityConfig.currentAccount(), candidateId, command));
    }

    @PostMapping("/records/ai-candidates/{candidateId}/reject")
    public ApiResponse<CandidateView> rejectRecordAiCandidate(@PathVariable String candidateId,
            @RequestBody(required = false) Decision command) {
        return ApiResponse.ok(recordAi.reject(SecurityConfig.currentAccount(), candidateId, command));
    }

    @GetMapping("/files")
    public ApiResponse<PageResult<FileView>> fileList(@RequestParam(required = false) String category,
            @RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String folderId,
            @RequestParam(required = false) String processingStatus,
            @RequestParam(required = false) String sort,
            @ModelAttribute PageQuery page) {
        return ApiResponse.ok(files.list(SecurityConfig.currentAccount(), category, status, keyword,
                folderId, processingStatus, sort, page));
    }

    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadView>> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "OTHER") String category,
            @RequestParam(required = false) String displayName,
            @RequestParam(required = false) String folderId) throws Exception {
        UploadView value = files.upload(SecurityConfig.currentAccount(), category, displayName, folderId,
                file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.accepted().body(ApiResponse.ok(value));
    }

    @GetMapping("/files/{id}")
    public ApiResponse<FileView> file(@PathVariable String id) { return ApiResponse.ok(files.get(SecurityConfig.currentAccount(), id)); }

    @PutMapping("/files/{id}")
    public ApiResponse<FileView> updateFile(@PathVariable String id, @RequestBody FileWrite write) {
        return ApiResponse.ok(files.update(SecurityConfig.currentAccount(), id, write));
    }

    @PostMapping("/files/{id}/retry")
    public ResponseEntity<ApiResponse<UploadView>> retryFile(@PathVariable String id) {
        return ResponseEntity.accepted().body(ApiResponse.ok(files.retry(SecurityConfig.currentAccount(), id)));
    }

    @PostMapping("/files/{id}/archive")
    public ApiResponse<FileView> archiveFile(@PathVariable String id, @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(files.archive(SecurityConfig.currentAccount(), id, request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/files/{id}/restore")
    public ApiResponse<FileView> restoreFile(@PathVariable String id, @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(files.restore(SecurityConfig.currentAccount(), id, request == null ? null : request.expectedVersion()));
    }

    @GetMapping("/storage")
    public ApiResponse<StorageView> storage() {
        return ApiResponse.ok(files.storage(SecurityConfig.currentAccount()));
    }

    @GetMapping("/folders")
    public ApiResponse<List<FolderView>> folders(@RequestParam(required = false) String status) {
        return ApiResponse.ok(files.folders(SecurityConfig.currentAccount(), status));
    }

    @PostMapping("/folders")
    public ApiResponse<FolderView> createFolder(@RequestBody FolderWrite write) {
        return ApiResponse.ok(files.createFolder(SecurityConfig.currentAccount(), write));
    }

    @PutMapping("/folders/{id}")
    public ApiResponse<FolderView> updateFolder(@PathVariable String id, @RequestBody FolderWrite write) {
        return ApiResponse.ok(files.updateFolder(SecurityConfig.currentAccount(), id, write));
    }

    @PostMapping("/folders/{id}/archive")
    public ApiResponse<FolderView> archiveFolder(@PathVariable String id, @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(files.archiveFolder(SecurityConfig.currentAccount(), id,
                request == null ? null : request.expectedVersion()));
    }

    @PostMapping("/folders/{id}/restore")
    public ApiResponse<FolderView> restoreFolder(@PathVariable String id, @RequestBody(required = false) VersionRequest request) {
        return ApiResponse.ok(files.restoreFolder(SecurityConfig.currentAccount(), id,
                request == null ? null : request.expectedVersion()));
    }

    @GetMapping("/files/{id}/preview-pages/{pageNumber}")
    public ResponseEntity<byte[]> preview(@PathVariable String id, @PathVariable int pageNumber) {
        Binary value = files.preview(SecurityConfig.currentAccount(), id, pageNumber);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.IMAGE_PNG).contentLength(value.body().length).body(value.body());
    }

    @GetMapping("/files/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        Binary value = files.download(SecurityConfig.currentAccount(), id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeHeader(value.filename()) + "\"")
                .contentType(mediaType(value.contentType())).contentLength(value.body().length).body(value.body());
    }

    @GetMapping("/history")
    public ApiResponse<HistorySummary> history() { return ApiResponse.ok(library.historySummary(SecurityConfig.currentAccount())); }

    @GetMapping("/history/export.json")
    public ResponseEntity<byte[]> historyJson() {
        byte[] body = library.historyJson(SecurityConfig.currentAccount());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=career-history.json")
                .contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @GetMapping("/history/export.md")
    public ResponseEntity<byte[]> historyMarkdown() {
        byte[] body = library.historyMarkdown(SecurityConfig.currentAccount()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=career-history.md")
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8")).body(body);
    }

    private static MediaType mediaType(String value) {
        try { return MediaType.parseMediaType(value); } catch (Exception exception) { return MediaType.APPLICATION_OCTET_STREAM; }
    }
    private static String safeHeader(String value) { return value == null ? "file" : value.replaceAll("[\\r\\n\"]", "_"); }
    public record AvatarUploadView(ProfileView profile, FileView file, com.jobproof.modules.task.application.TaskView task) { }
    public record VersionRequest(Integer expectedVersion) {}
}
