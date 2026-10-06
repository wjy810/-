package com.jobproof.modules.career.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.career.domain.CareerFilePolicy;
import com.jobproof.modules.resume.domain.ResumeTemplateDocxInspector;
import com.jobproof.modules.resume.domain.ResumeTemplatePreviewRenderer;
import com.jobproof.modules.storage.FileUploadPolicy;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileEntity;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import com.jobproof.modules.task.application.TaskService;
import com.jobproof.modules.task.application.TaskView;
import com.jobproof.modules.task.domain.TaskTypes;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import com.jobproof.shared.time.ClockPort;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CareerFileService {
    private static final Set<String> CATEGORIES = Set.of("RESUME", "CERTIFICATE", "PORTFOLIO",
            "TRANSCRIPT", "WORK_SAMPLE", "PROOF", "OTHER");
    private static final Set<String> FILE_STATUSES = Set.of("ACTIVE", "ARCHIVED", "QUARANTINED");
    private static final Set<String> PROCESSING_STATUSES = Set.of("SCANNING", "PREVIEWING", "READY",
            "SCAN_FAILED", "PREVIEW_FAILED", "INFECTED");
    private static final int PREVIEW_DPI = 120;
    private static final int MAX_PROCESSING_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final PrivateFileJpaRepository privateFiles;
    private final ObjectStoragePort storage;
    private final CareerFileMalwareScanner malwareScanner;
    private final ResumeTemplatePreviewRenderer docxRenderer;
    private final AuditService audit;
    private final ClockPort clock;
    private final TaskService tasks;
    private final ObjectMapper mapper;
    private final long quotaBytes;

    public CareerFileService(JdbcTemplate jdbc, PrivateFileJpaRepository privateFiles, ObjectStoragePort storage,
            CareerFileMalwareScanner malwareScanner, ResumeTemplatePreviewRenderer docxRenderer,
            AuditService audit, ClockPort clock, TaskService tasks, ObjectMapper mapper,
            @Value("${jobproof.career-library.quota-bytes:20971520}") long quotaBytes) {
        this.jdbc = jdbc;
        this.privateFiles = privateFiles;
        this.storage = storage;
        this.malwareScanner = malwareScanner;
        this.docxRenderer = docxRenderer;
        this.audit = audit;
        this.clock = clock;
        this.tasks = tasks;
        this.mapper = mapper;
        this.quotaBytes = Math.max(CareerFilePolicy.MAX_BYTES, quotaBytes);
    }

    @Transactional
    public UploadView upload(CurrentAccount current, String category, String displayName, String folderId,
            String filename, byte[] content) {
        assertSeeker(current);
        String normalizedCategory = category(category);
        CareerFilePolicy.Accepted accepted = CareerFilePolicy.inspect(filename, content);
        String normalizedFolder = folder(current.accountId(), folderId);
        lockAndCheckQuota(current.accountId(), content.length);

        Instant now = clock.now();
        String fileId = Ids.newId();
        String privateId = Ids.newId();
        String safeName = accepted.filename();
        String objectKey = FileUploadPolicy.ownedPrivateKey(current.accountId(), privateId, safeName);
        storage.put(objectKey, content);
        registerRollbackCleanup(objectKey);

        PrivateFileEntity privateFile = new PrivateFileEntity();
        privateFile.setId(privateId);
        privateFile.setOwnerId(current.accountId());
        privateFile.setObjectKey(objectKey);
        privateFile.setContentType(accepted.contentType());
        privateFile.setSizeBytes(content.length);
        privateFile.setCreatedAt(now);
        privateFiles.save(privateFile);

        jdbc.update("INSERT INTO career_library_files(id,account_id,private_file_id,category,display_name,original_filename,content_type,size_bytes,sha256,scan_status,scan_engine,scan_detail,preview_status,preview_page_count,preview_error,status,version_no,created_at,updated_at,archived_at,folder_id,processing_task_id,processing_status,processing_attempts) VALUES(?,?,?,?,?,?,?,?,?,'PENDING',NULL,NULL,'PENDING',0,NULL,'ACTIVE',0,?,?,NULL,?,NULL,'SCANNING',0)",
                fileId, current.accountId(), privateId, normalizedCategory, displayName(displayName, safeName),
                safeName, accepted.contentType(), content.length, ResumeTemplateDocxInspector.sha256(content),
                now, now, normalizedFolder);

        TaskView task = tasks.create(current.accountId(), TaskTypes.CAREER_FILE_PROCESS,
                "CAREER_FILE:" + fileId, json(Map.of("careerFileId", fileId)));
        jdbc.update("UPDATE career_library_files SET processing_task_id=? WHERE id=?", task.id(), fileId);
        audit.append(current.accountId(), "CAREER_LIBRARY_FILE_ACCEPTED", "CAREER_LIBRARY_FILE", fileId,
                "category=" + normalizedCategory + " bytes=" + content.length + " processing=SCANNING");
        return new UploadView(require(current.accountId(), fileId), task);
    }

    public UploadView uploadAvatar(CurrentAccount current, String filename, byte[] content) {
        CareerFilePolicy.Accepted accepted = CareerFilePolicy.inspect(filename, content);
        if (!accepted.contentType().startsWith("image/")) {
            throw AppException.user("CAREER_AVATAR_TYPE_INVALID", "头像仅支持 PNG、JPEG 和 WebP 图片");
        }
        return upload(current, "OTHER", "个人头像", null, filename, content);
    }

    public FileView processTask(String accountId, String fileId) {
        FileView file = require(accountId, fileId);
        if ("READY".equals(file.processingStatus()) && "CLEAN".equals(file.scanStatus())) return file;
        if (file.processingAttempts() >= MAX_PROCESSING_ATTEMPTS) {
            throw new ProcessingException("CAREER_FILE_RETRY_LIMIT", false);
        }
        jdbc.update("UPDATE career_library_files SET processing_status='SCANNING',scan_status='SCANNING',scan_detail=NULL,preview_error=NULL,processing_attempts=processing_attempts+1,updated_at=? WHERE id=? AND account_id=?",
                clock.now(), fileId, accountId);

        PrivateFileEntity privateFile = privateFiles.findById(file.privateFileId())
                .orElseThrow(() -> new ProcessingException("CAREER_FILE_BYTES_MISSING", false));
        if (!accountId.equals(privateFile.getOwnerId())) throw new ProcessingException("CAREER_FILE_OWNER_MISMATCH", false);
        byte[] content = storage.get(privateFile.getObjectKey());

        CareerFileMalwareScanner.ScanResult scan;
        try {
            scan = malwareScanner.scan(content);
        } catch (RuntimeException exception) {
            scan = CareerFileMalwareScanner.ScanResult.error("UNKNOWN", null, "SCANNER_EXCEPTION");
        }
        if (scan.outcome() == CareerFileMalwareScanner.Outcome.INFECTED) {
            deleteOriginal(privateFile);
            deletePreviews(fileId);
            jdbc.update("UPDATE career_library_files SET scan_status='INFECTED',scan_engine=?,scan_detail='MALWARE_DETECTED',preview_status='BLOCKED',preview_page_count=0,processing_status='INFECTED',status='QUARANTINED',updated_at=? WHERE id=?",
                    safeDetail(scan.engine()), clock.now(), fileId);
            audit.append(accountId, "CAREER_LIBRARY_FILE_REJECTED", "CAREER_LIBRARY_FILE", fileId,
                    "reason=MALWARE_DETECTED bytes_deleted=true");
            throw new ProcessingException("CAREER_FILE_MALWARE_DETECTED", false);
        }
        if (scan.outcome() != CareerFileMalwareScanner.Outcome.CLEAN) {
            String state = scan.outcome() == CareerFileMalwareScanner.Outcome.UNAVAILABLE ? "UNAVAILABLE" : "ERROR";
            jdbc.update("UPDATE career_library_files SET scan_status=?,scan_engine=?,scan_detail=?,processing_status='SCAN_FAILED',status='QUARANTINED',updated_at=? WHERE id=?",
                    state, safeDetail(scan.engine()), safeDetail(scan.detailCode()), clock.now(), fileId);
            throw new ProcessingException("CAREER_FILE_SCAN_" + state, true);
        }

        jdbc.update("UPDATE career_library_files SET scan_status='CLEAN',scan_engine=?,scan_detail=?,preview_status='RENDERING',processing_status='PREVIEWING',updated_at=? WHERE id=?",
                safeDetail(scan.engine()), safeDetail(scan.engineVersion()), clock.now(), fileId);
        try {
            deletePreviews(fileId);
            List<byte[]> pages = render(fileId, file.contentType(), content);
            if (pages.isEmpty()) throw new IllegalStateException("PREVIEW_PAGES_MISSING");
            int pageNumber = 1;
            Instant now = clock.now();
            for (byte[] page : pages) storePreview(accountId, fileId, pageNumber++, page, now);
            jdbc.update("UPDATE career_library_files SET preview_status='READY',preview_page_count=?,preview_error=NULL,processing_status='READY',status='ACTIVE',updated_at=? WHERE id=?",
                    pages.size(), clock.now(), fileId);
        } catch (RuntimeException exception) {
            deletePreviews(fileId);
            jdbc.update("UPDATE career_library_files SET preview_status='FAILED',preview_page_count=0,preview_error='PREVIEW_RENDER_FAILED',processing_status='PREVIEW_FAILED',status='QUARANTINED',updated_at=? WHERE id=?",
                    clock.now(), fileId);
            throw new ProcessingException("CAREER_FILE_PREVIEW_FAILED", true);
        }
        audit.append(accountId, "CAREER_LIBRARY_FILE_READY", "CAREER_LIBRARY_FILE", fileId,
                "pages=" + require(accountId, fileId).previewPageCount());
        return require(accountId, fileId);
    }

    @Transactional(readOnly = true)
    public PageResult<FileView> list(CurrentAccount current, String category, String status, String keyword,
            String folderId, String processingStatus, String sort, PageQuery page) {
        assertSeeker(current);
        String requestedCategory = category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)
                ? null : category(category);
        String requestedStatus = optionalStatus(status);
        String requestedProcessing = optionalProcessing(processingStatus);
        String requestedFolder = folderId == null || folderId.isBlank() || "ALL".equalsIgnoreCase(folderId)
                ? null : folder(current.accountId(), folderId);
        StringBuilder where = new StringBuilder(" WHERE account_id=?");
        List<Object> args = new ArrayList<>();
        args.add(current.accountId());
        if (requestedCategory != null) { where.append(" AND category=?"); args.add(requestedCategory); }
        if (requestedStatus != null) { where.append(" AND status=?"); args.add(requestedStatus); }
        if (requestedFolder != null) { where.append(" AND folder_id=?"); args.add(requestedFolder); }
        if (requestedProcessing != null) { where.append(" AND processing_status=?"); args.add(requestedProcessing); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (LOWER(display_name) LIKE ? OR LOWER(original_filename) LIKE ?)");
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(like); args.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM career_library_files" + where, Long.class, args.toArray());
        args.add(page.size()); args.add(page.page() * page.size());
        List<FileView> items = jdbc.query("SELECT * FROM career_library_files" + where + orderBy(sort)
                        + " LIMIT ? OFFSET ?", this::view, args.toArray());
        return new PageResult<>(items, total == null ? 0 : total, page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public FileView get(CurrentAccount current, String id) {
        assertSeeker(current);
        return require(current.accountId(), id);
    }

    @Transactional
    public FileView update(CurrentAccount current, String id, FileWrite write) {
        assertSeeker(current);
        FileView file = require(current.accountId(), id);
        assertVersion(write.expectedVersion(), file.version());
        String nextCategory = write.category() == null ? file.category() : category(write.category());
        String nextName = write.displayName() == null ? file.displayName() : displayName(write.displayName(), file.originalFilename());
        String nextFolder = write.folderId() == null ? file.folderId() : folder(current.accountId(), write.folderId());
        jdbc.update("UPDATE career_library_files SET category=?,display_name=?,folder_id=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                nextCategory, nextName, nextFolder, clock.now(), id, current.accountId());
        return require(current.accountId(), id);
    }

    @Transactional
    public UploadView retry(CurrentAccount current, String id) {
        assertSeeker(current);
        FileView file = require(current.accountId(), id);
        if ("INFECTED".equals(file.processingStatus())) {
            throw AppException.conflict("CAREER_FILE_INFECTED", "感染文件已删除，不能重试");
        }
        if (!Set.of("SCAN_FAILED", "PREVIEW_FAILED").contains(file.processingStatus())) {
            throw AppException.conflict("CAREER_FILE_RETRY_NOT_ALLOWED", "只有扫描或预览失败的文件可以重试");
        }
        // A temporary scanner outage can exhaust the worker retry budget. Once
        // the provider is back, allow an explicit retry to start a fresh
        // budget; infected files remain permanently blocked above.
        boolean scannerFailure = Set.of("UNAVAILABLE", "ERROR").contains(file.scanStatus());
        if (file.processingAttempts() >= MAX_PROCESSING_ATTEMPTS && !scannerFailure) {
            throw AppException.conflict("CAREER_FILE_RETRY_LIMIT", "处理重试次数已达上限");
        }
        TaskView task = tasks.create(current.accountId(), TaskTypes.CAREER_FILE_PROCESS, Ids.newId(),
                json(Map.of("careerFileId", id)));
        jdbc.update("UPDATE career_library_files SET processing_task_id=?,processing_status='SCANNING',processing_attempts=0,scan_status='PENDING',preview_status='PENDING',preview_error=NULL,status='QUARANTINED',updated_at=? WHERE id=? AND account_id=?",
                task.id(), clock.now(), id, current.accountId());
        return new UploadView(require(current.accountId(), id), task);
    }

    @Transactional
    public FileView archive(CurrentAccount current, String id, Integer expectedVersion) {
        return status(current, id, expectedVersion, "ARCHIVED");
    }

    @Transactional
    public FileView restore(CurrentAccount current, String id, Integer expectedVersion) {
        return status(current, id, expectedVersion, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public Binary preview(CurrentAccount current, String id, int pageNumber) {
        assertSeeker(current);
        FileView file = requireReady(current.accountId(), id);
        if (pageNumber < 1 || pageNumber > file.previewPageCount()) {
            throw AppException.user("CAREER_FILE_PREVIEW_NOT_FOUND", "预览页不存在");
        }
        return jdbc.query("SELECT object_key,content_type FROM career_library_file_previews WHERE file_id=? AND page_number=?",
                (rs, n) -> new Binary(file.displayName() + "-" + pageNumber + ".png", rs.getString("content_type"),
                        storage.get(rs.getString("object_key"))), id, pageNumber)
                .stream().findFirst().orElseThrow(() -> AppException.user("CAREER_FILE_PREVIEW_NOT_FOUND", "预览页不存在"));
    }

    @Transactional(readOnly = true)
    public Binary download(CurrentAccount current, String id) {
        assertSeeker(current);
        FileView file = requireReady(current.accountId(), id);
        PrivateFileEntity privateFile = privateFiles.findById(file.privateFileId())
                .orElseThrow(() -> AppException.user("CAREER_FILE_NOT_FOUND", "文件不存在"));
        if (!current.accountId().equals(privateFile.getOwnerId())) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "无权下载该文件");
        }
        return new Binary(file.originalFilename(), file.contentType(), storage.get(privateFile.getObjectKey()));
    }

    /** Internal read for a worker after the same ownership and safety gates as download. */
    @Transactional(readOnly = true)
    public Binary readReadyResume(String accountId, String id) {
        FileView file = requireReady(accountId, id);
        if (!"RESUME".equals(file.category())) {
            throw AppException.user("RESUME_IMPORT_FILE_CATEGORY_INVALID", "只能导入求职资料库中的简历文件");
        }
        if (!Set.of("application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .contains(file.contentType())) {
            throw AppException.user("RESUME_IMPORT_TYPE_UNSUPPORTED", "简历导入仅支持 PDF 或 DOCX");
        }
        PrivateFileEntity privateFile = privateFiles.findById(file.privateFileId())
                .orElseThrow(() -> AppException.user("CAREER_FILE_NOT_FOUND", "文件不存在"));
        if (!accountId.equals(privateFile.getOwnerId())) {
            throw AppException.forbidden("OBJECT_FORBIDDEN", "无权读取该文件");
        }
        return new Binary(file.originalFilename(), file.contentType(), storage.get(privateFile.getObjectKey()));
    }

    @Transactional(readOnly = true)
    public StorageView storage(CurrentAccount current) {
        assertSeeker(current);
        Long used = jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM career_library_files WHERE account_id=? AND processing_status<>'INFECTED'",
                Long.class, current.accountId());
        Map<String, Integer> categories = new LinkedHashMap<>();
        jdbc.query("SELECT category,COUNT(*) total FROM career_library_files WHERE account_id=? AND status<>'ARCHIVED' GROUP BY category",
                rs -> { categories.put(rs.getString("category"), rs.getInt("total")); }, current.accountId());
        return new StorageView(used == null ? 0 : used, quotaBytes, Math.max(0, quotaBytes - (used == null ? 0 : used)),
                Map.copyOf(categories));
    }

    @Transactional(readOnly = true)
    public List<FolderView> folders(CurrentAccount current, String status) {
        assertSeeker(current);
        String normalized = status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "ARCHIVED", "ALL").contains(normalized)) {
            throw AppException.user("CAREER_FOLDER_STATUS_INVALID", "文件夹状态无效");
        }
        String condition = "ALL".equals(normalized) ? "" : " AND f.status='" + normalized + "'";
        return jdbc.query("SELECT f.*,(SELECT COUNT(*) FROM career_library_files c WHERE c.folder_id=f.id AND c.status<>'ARCHIVED') file_count FROM career_library_file_folders f WHERE f.account_id=?"
                + condition + " ORDER BY f.updated_at DESC", this::folderView, current.accountId());
    }

    @Transactional
    public FolderView createFolder(CurrentAccount current, FolderWrite write) {
        assertSeeker(current);
        String name = folderName(write.name());
        Instant now = clock.now();
        String id = Ids.newId();
        try {
            jdbc.update("INSERT INTO career_library_file_folders(id,account_id,name,status,version_no,created_at,updated_at,archived_at) VALUES(?,?,?,'ACTIVE',0,?,?,NULL)",
                    id, current.accountId(), name, now, now);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw AppException.conflict("CAREER_FOLDER_NAME_EXISTS", "同名文件夹已存在");
        }
        return requireFolder(current.accountId(), id);
    }

    @Transactional
    public FolderView updateFolder(CurrentAccount current, String id, FolderWrite write) {
        assertSeeker(current);
        FolderView folder = requireFolder(current.accountId(), id);
        assertVersion(write.expectedVersion(), folder.version());
        jdbc.update("UPDATE career_library_file_folders SET name=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                folderName(write.name()), clock.now(), id, current.accountId());
        return requireFolder(current.accountId(), id);
    }

    @Transactional
    public FolderView archiveFolder(CurrentAccount current, String id, Integer expectedVersion) {
        assertSeeker(current);
        FolderView folder = requireFolder(current.accountId(), id);
        assertVersion(expectedVersion, folder.version());
        Instant now = clock.now();
        jdbc.update("UPDATE career_library_files SET folder_id=NULL,version_no=version_no+1,updated_at=? WHERE account_id=? AND folder_id=?",
                now, current.accountId(), id);
        jdbc.update("UPDATE career_library_file_folders SET status='ARCHIVED',archived_at=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                now, now, id, current.accountId());
        return requireFolder(current.accountId(), id);
    }

    @Transactional
    public FolderView restoreFolder(CurrentAccount current, String id, Integer expectedVersion) {
        assertSeeker(current);
        FolderView folder = requireFolder(current.accountId(), id);
        assertVersion(expectedVersion, folder.version());
        jdbc.update("UPDATE career_library_file_folders SET status='ACTIVE',archived_at=NULL,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                clock.now(), id, current.accountId());
        return requireFolder(current.accountId(), id);
    }

    private FileView status(CurrentAccount current, String id, Integer expectedVersion, String next) {
        assertSeeker(current);
        FileView file = require(current.accountId(), id);
        assertVersion(expectedVersion, file.version());
        if ("ACTIVE".equals(next) && !"READY".equals(file.processingStatus())) {
            throw AppException.conflict("CAREER_FILE_NOT_READY", "文件尚未通过预览与安全门禁");
        }
        Instant now = clock.now();
        jdbc.update("UPDATE career_library_files SET status=?,archived_at=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=?",
                next, "ARCHIVED".equals(next) ? now : null, now, id, current.accountId());
        return require(current.accountId(), id);
    }

    private FileView requireReady(String accountId, String id) {
        FileView file = require(accountId, id);
        if (!"ACTIVE".equals(file.status()) || !"CLEAN".equals(file.scanStatus())
                || !"READY".equals(file.previewStatus()) || !"READY".equals(file.processingStatus())) {
            throw AppException.conflict("CAREER_FILE_NOT_READY", "文件尚未通过全部安全与预览门禁");
        }
        return file;
    }

    private FileView require(String accountId, String id) {
        return jdbc.query("SELECT * FROM career_library_files WHERE id=? AND account_id=?", this::view, id, accountId)
                .stream().findFirst().orElseThrow(() -> AppException.user("CAREER_FILE_NOT_FOUND", "文件资料不存在"));
    }

    private FileView view(ResultSet rs, int row) throws SQLException {
        return new FileView(rs.getString("id"), rs.getString("private_file_id"), rs.getString("category"),
                rs.getString("display_name"), rs.getString("original_filename"), rs.getString("content_type"),
                rs.getLong("size_bytes"), rs.getString("sha256"), rs.getString("scan_status"),
                rs.getString("preview_status"), rs.getInt("preview_page_count"), rs.getString("preview_error"),
                rs.getString("status"), rs.getString("folder_id"), rs.getString("processing_task_id"),
                rs.getString("processing_status"), rs.getInt("processing_attempts"), rs.getInt("version_no"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("archived_at") == null ? null : rs.getTimestamp("archived_at").toInstant());
    }

    private List<byte[]> render(String id, String contentType, byte[] content) {
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)) {
            ResumeTemplatePreviewRenderer.RenderResult result = docxRenderer.render(List.of(
                    new ResumeTemplatePreviewRenderer.RenderRequest(id, ResumeTemplateDocxInspector.sha256(content), content))).get(0);
            if (!result.successful()) throw new IllegalStateException(result.errorCode());
            return result.pages();
        }
        if ("application/pdf".equals(contentType)) return renderPdf(content);
        return List.of(renderImage(content));
    }

    private List<byte[]> renderPdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<byte[]> pages = new ArrayList<>();
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                BufferedImage image = renderer.renderImageWithDPI(index, PREVIEW_DPI, ImageType.RGB);
                pages.add(png(image));
                image.flush();
            }
            return pages;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private byte[] renderImage(byte[] content) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
            if (source == null) throw new IllegalStateException("IMAGE_DECODER_UNAVAILABLE");
            double scale = Math.min(1d, 1600d / Math.max(source.getWidth(), source.getHeight()));
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();
            source.flush();
            byte[] result = png(target);
            target.flush();
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] png(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", out)) throw new IllegalStateException("PNG_ENCODER_UNAVAILABLE");
            return out.toByteArray();
        }
    }

    private void storePreview(String accountId, String fileId, int page, byte[] body, Instant now) {
        String key = "career-library-previews/" + accountId + "/" + fileId + "/page-" + page + ".png";
        storage.put(key, body);
        int width = 0;
        int height = 0;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
            if (image != null) { width = image.getWidth(); height = image.getHeight(); image.flush(); }
        } catch (Exception ignored) { }
        jdbc.update("INSERT INTO career_library_file_previews(id,file_id,page_number,object_key,content_type,size_bytes,width_px,height_px,created_at) VALUES(?,?,?,?,'image/png',?,?,?,?)",
                Ids.newId(), fileId, page, key, body.length, width, height, now);
    }

    private void deletePreviews(String fileId) {
        List<String> keys = jdbc.queryForList("SELECT object_key FROM career_library_file_previews WHERE file_id=?",
                String.class, fileId);
        for (String key : keys) {
            try { storage.delete(key); } catch (RuntimeException ignored) { }
        }
        jdbc.update("DELETE FROM career_library_file_previews WHERE file_id=?", fileId);
    }

    private void deleteOriginal(PrivateFileEntity privateFile) {
        try { storage.delete(privateFile.getObjectKey()); } finally { privateFiles.delete(privateFile); }
    }

    private void lockAndCheckQuota(String accountId, long incomingBytes) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM career_library_profiles WHERE account_id=?",
                Integer.class, accountId);
        if (exists == null || exists == 0) {
            Instant now = clock.now();
            try {
                jdbc.update("INSERT INTO career_library_profiles(account_id,basics_json,intentions_json,preferences_json,summary_text,snapshot_version,version_no,created_at,updated_at,avatar_file_id) VALUES(?,'{}','{}','{}',NULL,0,0,?,?,NULL)",
                        accountId, now, now);
            } catch (DuplicateKeyException ignored) {
                // The row now exists; the lock below still serializes quota checks.
            }
        }
        jdbc.queryForObject("SELECT account_id FROM career_library_profiles WHERE account_id=? FOR UPDATE", String.class, accountId);
        Long used = jdbc.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM career_library_files WHERE account_id=? AND processing_status<>'INFECTED'",
                Long.class, accountId);
        long current = used == null ? 0 : used;
        if (current + incomingBytes > quotaBytes) {
            throw AppException.user("CAREER_FILE_QUOTA_EXCEEDED", "资料库容量不足，请归档不需要的文件或删除失败文件后再试");
        }
    }

    private String folder(String accountId, String raw) {
        if (raw == null || raw.isBlank()) return null;
        FolderView value = requireFolder(accountId, raw.trim());
        if (!"ACTIVE".equals(value.status())) throw AppException.conflict("CAREER_FOLDER_ARCHIVED", "文件夹已归档");
        return value.id();
    }

    private FolderView requireFolder(String accountId, String id) {
        return jdbc.query("SELECT f.*,(SELECT COUNT(*) FROM career_library_files c WHERE c.folder_id=f.id AND c.status<>'ARCHIVED') file_count FROM career_library_file_folders f WHERE f.id=? AND f.account_id=?",
                this::folderView, id, accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CAREER_FOLDER_NOT_FOUND", "文件夹不存在"));
    }

    private FolderView folderView(ResultSet rs, int row) throws SQLException {
        return new FolderView(rs.getString("id"), rs.getString("name"), rs.getString("status"),
                rs.getInt("file_count"), rs.getInt("version_no"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("archived_at") == null ? null : rs.getTimestamp("archived_at").toInstant());
    }

    private static String category(String raw) {
        String value = raw == null ? "OTHER" : raw.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(value)) throw AppException.user("CAREER_FILE_CATEGORY_INVALID", "文件分类无效");
        return value;
    }

    private static String optionalStatus(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!FILE_STATUSES.contains(value)) throw AppException.user("CAREER_FILE_STATUS_INVALID", "文件状态无效");
        return value;
    }

    private static String optionalProcessing(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!PROCESSING_STATUSES.contains(value)) throw AppException.user("CAREER_FILE_PROCESSING_INVALID", "文件处理状态无效");
        return value;
    }

    private static String orderBy(String raw) {
        String value = raw == null ? "RECENT" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "NAME" -> " ORDER BY display_name ASC,updated_at DESC";
            case "SIZE" -> " ORDER BY size_bytes DESC,updated_at DESC";
            case "OLDEST" -> " ORDER BY updated_at ASC";
            case "RECENT" -> " ORDER BY updated_at DESC";
            default -> throw AppException.user("CAREER_FILE_SORT_INVALID", "文件排序方式无效");
        };
    }

    private static String displayName(String raw, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        if (value.length() > 160) throw AppException.user("CAREER_FILE_NAME_TOO_LONG", "显示名称不能超过 160 个字符");
        return value;
    }

    private static String folderName(String raw) {
        if (raw == null || raw.isBlank()) throw AppException.user("CAREER_FOLDER_NAME_REQUIRED", "文件夹名称不能为空");
        String value = raw.trim();
        if (value.length() > 80) throw AppException.user("CAREER_FOLDER_NAME_TOO_LONG", "文件夹名称不能超过 80 个字符");
        return value;
    }

    private static String safeDetail(String value) {
        if (value == null) return null;
        String safe = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return safe.substring(0, Math.min(250, safe.length()));
    }

    private static void assertVersion(Integer expected, int actual) {
        if (expected != null && expected != actual) {
            throw AppException.conflict("VERSION_CONFLICT", "文件资料已更新，请刷新后重试");
        }
    }

    private static void assertSeeker(CurrentAccount current) {
        if (current.operator()) throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能查看用户文件原文");
    }

    private void registerRollbackCleanup(String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) return;
                try { storage.delete(objectKey); } catch (RuntimeException ignored) { }
            }
        });
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public record UploadView(FileView file, TaskView task) { }
    public record FileWrite(String displayName, String category, String folderId, Integer expectedVersion) { }
    public record FolderWrite(String name, Integer expectedVersion) { }
    public record FileView(String id, String privateFileId, String category, String displayName,
            String originalFilename, String contentType, long sizeBytes, String sha256, String scanStatus,
            String previewStatus, int previewPageCount, String previewError, String status, String folderId,
            String processingTaskId, String processingStatus, int processingAttempts, int version,
            Instant createdAt, Instant updatedAt, Instant archivedAt) { }
    public record FolderView(String id, String name, String status, int fileCount, int version,
            Instant createdAt, Instant updatedAt, Instant archivedAt) { }
    public record StorageView(long usedBytes, long quotaBytes, long availableBytes, Map<String, Integer> categoryCounts) { }
    public record Binary(String filename, String contentType, byte[] body) { }

    public static class ProcessingException extends RuntimeException {
        private final boolean retryable;
        public ProcessingException(String reason, boolean retryable) { super(reason); this.retryable = retryable; }
        public boolean retryable() { return retryable; }
    }
}
