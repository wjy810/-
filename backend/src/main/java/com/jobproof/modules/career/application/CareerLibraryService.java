package com.jobproof.modules.career.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.infrastructure.queue.OutboxService;
import com.jobproof.modules.audit.application.AuditService;
import com.jobproof.modules.career.domain.CareerLibraryEventTypes;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileEntity;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.export.AccountExportContributor;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.page.PageQuery;
import com.jobproof.shared.page.PageResult;
import com.jobproof.shared.security.Tokens;
import com.jobproof.shared.time.ClockPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareerLibraryService implements DeletionModuleHandler, AccountExportContributor {
    private static final Set<String> TYPES = Set.of("EDUCATION", "EXPERIENCE", "PROJECT", "ORGANIZATION",
            "SKILL", "CERTIFICATE", "HONOR", "LANGUAGE", "ACHIEVEMENT", "PROFILE_FACT");
    private static final Set<String> MATCH_FACT_KEYS = Set.of("JOB_DIRECTION", "HIGHEST_EDUCATION",
            "GRADUATION_DATE", "MAJOR", "TARGET_CITY", "WORK_MODE", "REALITY_CONSTRAINTS", "SKILLS",
            "YEARS_OF_EXPERIENCE", "INTERESTS", "SALARY_EXPECTATION", "SELF_INTRODUCTION");
    private static final String RECORD_SELECT = """
            SELECT r.*,COALESCE(refs.reference_count,0) AS reference_count
            FROM career_library_records r
            LEFT JOIN (SELECT record_id,COUNT(*) AS reference_count
                       FROM career_library_record_refs WHERE active=1 GROUP BY record_id) refs
              ON refs.record_id=r.id
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OutboxService outbox;
    private final AuditService audit;
    private final ClockPort clock;
    private final PrivateFileJpaRepository privateFiles;
    private final ObjectStoragePort storage;
    private final long fileQuotaBytes;

    public CareerLibraryService(JdbcTemplate jdbc, ObjectMapper mapper, OutboxService outbox,
            AuditService audit, ClockPort clock, PrivateFileJpaRepository privateFiles,
            ObjectStoragePort storage,
            @Value("${jobproof.career-library.quota-bytes:20971520}") long fileQuotaBytes) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.outbox = outbox;
        this.audit = audit;
        this.clock = clock;
        this.privateFiles = privateFiles;
        this.storage = storage;
        this.fileQuotaBytes = fileQuotaBytes;
    }

    @Override public String moduleCode() { return "career-library"; }
    @Override public String moduleKey() { return "careerLibrary"; }

    @Transactional
    public ProfileView profile(CurrentAccount current) {
        assertSeeker(current);
        ensureProfile(current.accountId());
        return profileOf(current.accountId());
    }

    @Transactional
    public ProfileView updateProfile(CurrentAccount current, ProfileWrite write) {
        assertSeeker(current);
        ensureProfile(current.accountId());
        ProfileView existing = profileOf(current.accountId());
        assertVersion(write.expectedVersion(), existing.version());
        JsonNode basics = objectOrEmpty(write.basics());
        JsonNode intentions = objectOrEmpty(write.intentions());
        JsonNode preferences = objectOrEmpty(write.preferences());
        rejectSensitiveKeys(basics);
        rejectSensitiveKeys(intentions);
        rejectSensitiveKeys(preferences);
        Instant now = clock.now();
        int changed = jdbc.update("UPDATE career_library_profiles SET basics_json=?,intentions_json=?,preferences_json=?,summary_text=?,snapshot_version=snapshot_version+1,version_no=version_no+1,updated_at=? WHERE account_id=? AND version_no=?",
                json(basics), json(intentions), json(preferences), blankToNull(write.summary()), now,
                current.accountId(), existing.version());
        requireUpdated(changed);
        emitChanged(current.accountId(), existing.snapshotVersion() + 1, "PROFILE_UPDATED");
        audit.append(current.accountId(), "CAREER_LIBRARY_PROFILE_UPDATED", "CAREER_LIBRARY_PROFILE",
                current.accountId(), "职业主档已由本人更新");
        return profileOf(current.accountId());
    }

    @Transactional
    public ProfileView setAvatar(CurrentAccount current, String fileId) {
        assertSeeker(current);
        ensureProfile(current.accountId());
        if (fileId == null || fileId.isBlank()) {
            throw AppException.user("CAREER_AVATAR_REQUIRED", "头像文件不能为空");
        }
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM career_library_files WHERE id=? AND account_id=? AND content_type LIKE 'image/%'",
                Integer.class, fileId, current.accountId());
        if (owned == null || owned == 0) {
            throw AppException.user("CAREER_AVATAR_NOT_FOUND", "头像文件不存在");
        }
        Instant now = clock.now();
        jdbc.update("UPDATE career_library_profiles SET avatar_file_id=?,snapshot_version=snapshot_version+1,version_no=version_no+1,updated_at=? WHERE account_id=?",
                fileId, now, current.accountId());
        emitChanged(current.accountId(), profileOf(current.accountId()).snapshotVersion(), "AVATAR_UPDATED");
        audit.append(current.accountId(), "CAREER_LIBRARY_AVATAR_UPDATED", "CAREER_LIBRARY_PROFILE",
                current.accountId(), "avatarFileId=" + fileId);
        return profileOf(current.accountId());
    }

    @Transactional
    public ProfileView clearAvatar(CurrentAccount current) {
        assertSeeker(current);
        ensureProfile(current.accountId());
        ProfileView profile = profileOf(current.accountId());
        if (profile.avatarFileId() == null) return profile;
        Instant now = clock.now();
        jdbc.update("UPDATE career_library_profiles SET avatar_file_id=NULL,snapshot_version=snapshot_version+1,version_no=version_no+1,updated_at=? WHERE account_id=?",
                now, current.accountId());
        emitChanged(current.accountId(), profile.snapshotVersion() + 1, "AVATAR_REMOVED");
        audit.append(current.accountId(), "CAREER_LIBRARY_AVATAR_REMOVED", "CAREER_LIBRARY_PROFILE",
                current.accountId(), "头像引用已删除");
        return profileOf(current.accountId());
    }

    @Transactional(readOnly = true)
    public PageResult<RecordView> records(CurrentAccount current, String type, String status, String keyword,
            PageQuery page) {
        assertSeeker(current);
        String requestedType = normalizeOptionalType(type);
        String requestedStatus = normalizeStatus(status);
        String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE account_id=?");
        args.add(current.accountId());
        if (requestedType != null) { where.append(" AND record_type=?"); args.add(requestedType); }
        if (requestedStatus != null) { where.append(" AND status=?"); args.add(requestedStatus); }
        if (!term.isBlank()) {
            where.append(" AND (LOWER(title) LIKE ? OR LOWER(COALESCE(organization,'')) LIKE ? OR LOWER(COALESCE(role_name,'')) LIKE ? OR LOWER(COALESCE(description_text,'')) LIKE ?)");
            String like = "%" + term + "%";
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM career_library_records" + where, Long.class,
                args.toArray());
        args.add(page.size());
        args.add(page.page() * page.size());
        List<RecordView> items = jdbc.query(RECORD_SELECT + where
                        + " ORDER BY status,sort_order,updated_at DESC LIMIT ? OFFSET ?", this::recordView,
                args.toArray());
        return new PageResult<>(items, total == null ? 0 : total, page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public RecordView record(CurrentAccount current, String id) {
        assertSeeker(current);
        return requireRecord(current.accountId(), id);
    }

    @Transactional
    public RecordView createRecord(CurrentAccount current, RecordWrite write) {
        assertSeeker(current);
        String type = normalizeType(write.type());
        String title = requiredTitle(write.title());
        Instant now = clock.now();
        String id = Ids.newId();
        jdbc.update("INSERT INTO career_library_records(id,account_id,record_type,title,organization,role_name,start_date,end_date,location,description_text,core_outcome,url,payload_json,strength,pending_supplement,source_type,source_ref_id,status,confirmed,sort_order,version_no,created_at,updated_at,archived_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,'USER',NULL,'ACTIVE',1,?,0,?,?,NULL)",
                id, current.accountId(), type, title, blankToNull(write.organization()), blankToNull(write.role()),
                blankToNull(write.startDate()), blankToNull(write.endDate()), blankToNull(write.location()),
                blankToNull(write.description()), blankToNull(write.coreOutcome()), blankToNull(write.url()),
                json(write.payload() == null ? mapper.createObjectNode() : write.payload()),
                blankToNull(write.strength()), write.sortOrder() == null ? 0 : write.sortOrder(), now, now);
        bump(current.accountId(), "RECORD_CREATED");
        audit.append(current.accountId(), "CAREER_LIBRARY_RECORD_CREATED", "CAREER_LIBRARY_RECORD", id,
                "type=" + type);
        return requireRecord(current.accountId(), id);
    }

    @Transactional
    public RecordView updateRecord(CurrentAccount current, String id, RecordWrite write) {
        assertSeeker(current);
        RecordView existing = requireRecord(current.accountId(), id);
        assertVersion(write.expectedVersion(), existing.version());
        String type = write.type() == null ? existing.type() : normalizeType(write.type());
        String title = write.title() == null ? existing.title() : requiredTitle(write.title());
        JsonNode payload = write.payload() == null ? existing.payload() : write.payload();
        Instant now = clock.now();
        int changed = jdbc.update("UPDATE career_library_records SET record_type=?,title=?,organization=?,role_name=?,start_date=?,end_date=?,location=?,description_text=?,core_outcome=?,url=?,payload_json=?,strength=?,sort_order=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=? AND version_no=?",
                type, title, choose(write.organization(), existing.organization()), choose(write.role(), existing.role()),
                choose(write.startDate(), existing.startDate()), choose(write.endDate(), existing.endDate()),
                choose(write.location(), existing.location()), choose(write.description(), existing.description()),
                choose(write.coreOutcome(), existing.coreOutcome()), choose(write.url(), existing.url()),
                json(payload), choose(write.strength(), existing.strength()),
                write.sortOrder() == null ? existing.sortOrder() : write.sortOrder(), now, id, current.accountId(), existing.version());
        requireUpdated(changed);
        bump(current.accountId(), "RECORD_UPDATED");
        return requireRecord(current.accountId(), id);
    }

    @Transactional
    public RecordView archiveRecord(CurrentAccount current, String id, Integer expectedVersion) {
        return changeRecordStatus(current, id, expectedVersion, "ARCHIVED");
    }

    @Transactional
    public RecordView restoreRecord(CurrentAccount current, String id, Integer expectedVersion) {
        return changeRecordStatus(current, id, expectedVersion, "ACTIVE");
    }

    @Transactional
    public RecordView copyRecord(CurrentAccount current, String id) {
        assertSeeker(current);
        RecordView source = requireRecord(current.accountId(), id);
        Instant now = clock.now();
        String copyId = Ids.newId();
        jdbc.update("INSERT INTO career_library_records(id,account_id,record_type,title,organization,role_name,start_date,end_date,location,description_text,core_outcome,url,payload_json,strength,pending_supplement,source_type,source_ref_id,status,confirmed,sort_order,version_no,created_at,updated_at,archived_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'USER_COPY',?,'ACTIVE',1,?,0,?,?,NULL)",
                copyId, current.accountId(), source.type(), source.title() + " - 副本", source.organization(),
                source.role(), source.startDate(), source.endDate(), source.location(), source.description(),
                source.coreOutcome(), source.url(), json(source.payload()), source.strength(),
                source.pendingSupplement(), source.id(), source.sortOrder() + 1, now, now);
        bump(current.accountId(), "RECORD_COPIED");
        audit.append(current.accountId(), "CAREER_LIBRARY_RECORD_COPIED", "CAREER_LIBRARY_RECORD", copyId,
                "source=" + source.id());
        return requireRecord(current.accountId(), copyId);
    }

    @Transactional
    public OverviewView overview(CurrentAccount current) {
        assertSeeker(current);
        ensureProfile(current.accountId());
        ProfileView profile = profileOf(current.accountId());
        int totalRecords = count("SELECT COUNT(*) FROM career_library_records WHERE account_id=? AND status='ACTIVE'", current.accountId());
        int inUseRecords = count("SELECT COUNT(DISTINCT r.id) FROM career_library_records r JOIN career_library_record_refs rr ON rr.record_id=r.id AND rr.active=1 WHERE r.account_id=? AND r.status='ACTIVE'", current.accountId());
        int outcomeRecords = count("SELECT COUNT(*) FROM career_library_records WHERE account_id=? AND status='ACTIVE' AND (record_type='ACHIEVEMENT' OR core_outcome IS NOT NULL)", current.accountId());
        int pendingRecords = count("SELECT COUNT(*) FROM career_library_records WHERE account_id=? AND status='ACTIVE' AND pending_supplement=1", current.accountId());
        int skillKeywords = count("SELECT COUNT(*) FROM career_library_records WHERE account_id=? AND status='ACTIVE' AND record_type IN ('SKILL','LANGUAGE','CERTIFICATE')", current.accountId());
        long usedBytes = longValue("SELECT COALESCE(SUM(size_bytes),0) FROM career_library_files WHERE account_id=? AND processing_status<>'INFECTED'", current.accountId());
        int readyFiles = count("SELECT COUNT(*) FROM career_library_files WHERE account_id=? AND status='ACTIVE' AND processing_status='READY'", current.accountId());
        int processingFiles = count("SELECT COUNT(*) FROM career_library_files WHERE account_id=? AND processing_status IN ('SCANNING','PREVIEWING')", current.accountId());
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        jdbc.query("SELECT category,COUNT(*) total FROM career_library_files WHERE account_id=? AND status<>'ARCHIVED' GROUP BY category",
                rs -> { categoryCounts.put(rs.getString("category"), rs.getInt("total")); }, current.accountId());
        int health = Math.min(100, Math.round(profile.completeness() * 0.55f
                + Math.min(30, totalRecords * 6) + Math.min(15, outcomeRecords * 3)));
        return new OverviewView(profile.completeness(), profile.missingItems(), totalRecords, inUseRecords,
                outcomeRecords, pendingRecords, skillKeywords, health, usedBytes, fileQuotaBytes, readyFiles,
                processingFiles, Map.copyOf(categoryCounts), profile.updatedAt());
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(CurrentAccount current, String query) {
        assertSeeker(current);
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (term.isBlank()) return List.of();
        if (term.length() > 80) throw AppException.user("CAREER_SEARCH_TOO_LONG", "搜索关键词不能超过 80 个字符");
        String like = "%" + term + "%";
        List<SearchResult> results = new ArrayList<>();
        List<SearchResult> actions = List.of(
                new SearchResult("ACTION", "profile", "编辑个人信息", "维护职业主档和求职意向", "profile", "profile-name"),
                new SearchResult("ACTION", "record-create", "添加经历记录", "新增教育、工作、项目或成果", "records", "record-create"),
                new SearchResult("ACTION", "file-upload", "上传文件", "上传 DOCX、PDF 或图片资料", "files", "file-upload"),
                new SearchResult("ACTION", "folder-create", "新建文件夹", "整理私有文件资料", "files", "folder-create"));
        actions.stream().filter(item -> (item.title() + item.subtitle()).toLowerCase(Locale.ROOT).contains(term))
                .forEach(results::add);
        results.addAll(jdbc.query("SELECT id,title,organization,role_name,record_type FROM career_library_records WHERE account_id=? AND status='ACTIVE' AND (LOWER(title) LIKE ? OR LOWER(COALESCE(organization,'')) LIKE ? OR LOWER(COALESCE(role_name,'')) LIKE ? OR LOWER(COALESCE(description_text,'')) LIKE ?) ORDER BY updated_at DESC LIMIT 6",
                (rs, row) -> new SearchResult("RECORD", rs.getString("id"), rs.getString("title"),
                        firstNonBlank(rs.getString("organization"), rs.getString("record_type")), "records", rs.getString("id")),
                current.accountId(), like, like, like, like));
        results.addAll(jdbc.query("SELECT id,display_name,original_filename,category FROM career_library_files WHERE account_id=? AND status<>'ARCHIVED' AND (LOWER(display_name) LIKE ? OR LOWER(original_filename) LIKE ?) ORDER BY updated_at DESC LIMIT 6",
                (rs, row) -> new SearchResult("FILE", rs.getString("id"), rs.getString("display_name"),
                        rs.getString("original_filename"), "files", rs.getString("id")), current.accountId(), like, like));
        results.addAll(jdbc.query("SELECT id,name FROM career_library_file_folders WHERE account_id=? AND status='ACTIVE' AND LOWER(name) LIKE ? ORDER BY updated_at DESC LIMIT 6",
                (rs, row) -> new SearchResult("FOLDER", rs.getString("id"), rs.getString("name"),
                        "文件夹", "files", rs.getString("id")), current.accountId(), like));
        return results.stream().limit(16).toList();
    }

    @Transactional
    public List<RecordView> reorder(CurrentAccount current, List<RecordOrder> order) {
        assertSeeker(current);
        if (order == null || order.isEmpty()) throw AppException.user("CAREER_RECORD_ORDER_REQUIRED", "请提交排序内容");
        for (RecordOrder item : order) {
            RecordView existing = requireRecord(current.accountId(), item.id());
            assertVersion(item.expectedVersion(), existing.version());
            int changed = jdbc.update("UPDATE career_library_records SET sort_order=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=? AND version_no=?",
                    item.sortOrder(), clock.now(), item.id(), current.accountId(), existing.version());
            requireUpdated(changed);
        }
        bump(current.accountId(), "RECORDS_REORDERED");
        return order.stream().map(item -> requireRecord(current.accountId(), item.id())).toList();
    }

    @Transactional
    public AiContextRead aiContextRead(String accountId) {
        ensureProfile(accountId);
        ProfileView profile = profileOf(accountId);
        Map<String, JsonNode> facts = new LinkedHashMap<>();
        for (RecordView record : activeConfirmed(accountId)) {
            if ("PROFILE_FACT".equals(record.type()) && MATCH_FACT_KEYS.contains(record.title())) {
                facts.put(record.title(), record.payload());
            }
        }
        putTextFact(facts, "JOB_DIRECTION", profile.intentions(), "targetJob");
        putTextFact(facts, "TARGET_CITY", profile.preferences(), "targetCity");
        putTextFact(facts, "WORK_MODE", profile.preferences(), "workMode");
        if (profile.preferences().has("realityConstraints")) {
            facts.put("REALITY_CONSTRAINTS", profile.preferences().get("realityConstraints"));
        }
        List<JsonNode> skillPayloads = activeConfirmed(accountId).stream()
                .filter(item -> "SKILL".equals(item.type())).map(RecordView::payload).toList();
        if (!skillPayloads.isEmpty()) facts.put("SKILLS", mapper.valueToTree(skillPayloads));
        boolean direction = facts.containsKey("JOB_DIRECTION")
                && !facts.get("JOB_DIRECTION").asText("").isBlank();
        return new AiContextRead(accountId, profile.snapshotVersion(), direction, Map.copyOf(facts));
    }

    @Transactional(readOnly = true)
    public List<AiEvidence> aiEvidence(String accountId) {
        return activeConfirmed(accountId).stream()
                .filter(item -> Set.of("ACHIEVEMENT", "PROJECT", "EXPERIENCE", "CERTIFICATE", "HONOR")
                        .contains(item.type()))
                .map(item -> new AiEvidence(item.id(), item.type(), item.title(), item.description(),
                        item.coreOutcome(), item.strength() == null ? "BASIC" : item.strength(),
                        item.pendingSupplement())).toList();
    }

    @Transactional
    public EvidenceSnapshot evidenceSnapshot(String accountId, String question) {
        List<RecordView> source = activeConfirmed(accountId);
        String term = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<SourceRef> refs = source.stream().filter(item -> relevant(item, term)).limit(20)
                .map(item -> new SourceRef(item.id(), item.type(), item.title(), excerpt(item))).toList();
        ProfileView profile = profileOf(accountId);
        return new EvidenceSnapshot(profile.snapshotVersion(), refs);
    }

    @Transactional(readOnly = true)
    public CertificateEvidenceSnapshot certificateEvidenceSnapshot(String accountId) {
        ProfileView profile = profileOf(accountId);
        List<CertificateSourceRef> sources = activeConfirmed(accountId).stream()
                .filter(item -> "CERTIFICATE".equals(item.type()))
                .limit(50)
                .map(item -> new CertificateSourceRef(item.id(), item.title(), item.organization(),
                        firstNonBlank(item.endDate(), item.startDate()), item.description(), item.coreOutcome()))
                .toList();
        return new CertificateEvidenceSnapshot(profile.snapshotVersion(), sources);
    }

    @Transactional(readOnly = true)
    public HonorEvidenceSnapshot honorEvidenceSnapshot(String accountId) {
        ProfileView profile = profileOf(accountId);
        List<HonorSourceRef> sources = activeConfirmed(accountId).stream()
                .filter(item -> "HONOR".equals(item.type()))
                .limit(50)
                .map(item -> new HonorSourceRef(item.id(), item.title(), item.organization(),
                        firstNonBlank(item.endDate(), item.startDate()), item.description(), item.coreOutcome()))
                .toList();
        return new HonorEvidenceSnapshot(profile.snapshotVersion(), sources);
    }

    @Transactional(readOnly = true)
    public void requireActiveOwned(String accountId, String id) {
        RecordView value = requireRecord(accountId, id);
        if (!"ACTIVE".equals(value.status()) || !value.confirmed()) {
            throw AppException.conflict("CAREER_RECORD_NOT_USABLE", "只能引用已确认且未归档的求职资料");
        }
    }

    @Transactional
    public void registerReference(String accountId, String recordId, String resumeVersionId) {
        requireActiveOwned(accountId, recordId);
        int changed = jdbc.update("UPDATE career_library_record_refs SET active=1 WHERE record_id=? AND resume_version_id=?",
                recordId, resumeVersionId);
        if (changed == 0) jdbc.update("INSERT INTO career_library_record_refs(id,record_id,resume_version_id,active,created_at) VALUES(?,?,?,1,?)",
                Ids.newId(), recordId, resumeVersionId, clock.now());
    }

    @Transactional
    public void releaseReference(String accountId, String recordId, String resumeVersionId) {
        requireRecord(accountId, recordId);
        jdbc.update("UPDATE career_library_record_refs SET active=0 WHERE record_id=? AND resume_version_id=?",
                recordId, resumeVersionId);
    }

    @Override
    @Transactional
    public Result onAccountDeletion(String accountId) {
        try {
            List<String> previewKeys = jdbc.query("SELECT p.object_key FROM career_library_file_previews p JOIN career_library_files f ON f.id=p.file_id WHERE f.account_id=?",
                    (rs, n) -> rs.getString(1), accountId);
            previewKeys.forEach(storage::delete);
            List<String> fileIds = jdbc.query("SELECT private_file_id FROM career_library_files WHERE account_id=?",
                    (rs, n) -> rs.getString(1), accountId);
            for (String fileId : fileIds) privateFiles.findById(fileId).ifPresent(file -> {
                storage.delete(file.getObjectKey());
                privateFiles.delete(file);
            });
            jdbc.update("DELETE FROM career_library_file_previews WHERE file_id IN (SELECT id FROM career_library_files WHERE account_id=?)", accountId);
            jdbc.update("DELETE FROM career_library_files WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM career_library_record_refs WHERE record_id IN (SELECT id FROM career_library_records WHERE account_id=?)", accountId);
            jdbc.update("DELETE FROM career_library_records WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM retired_career_history_records WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM career_library_profiles WHERE account_id=?", accountId);
            return Result.succeeded("求职资料库、私有文件与历史归档已清理");
        } catch (RuntimeException exception) {
            return Result.failed("求职资料库清理失败，删除申请保持可重试");
        }
    }

    @Override
    @Transactional
    public Map<String, Object> contribute(String accountId) {
        ensureProfile(accountId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", profileOf(accountId));
        result.put("records", activeAndArchived(accountId));
        result.put("files", jdbc.query("SELECT id,category,display_name,original_filename,content_type,size_bytes,status,created_at,updated_at FROM career_library_files WHERE account_id=? ORDER BY created_at",
                (rs, n) -> Map.of("id", rs.getString("id"), "category", rs.getString("category"),
                        "displayName", rs.getString("display_name"), "filename", rs.getString("original_filename"),
                        "contentType", rs.getString("content_type"), "sizeBytes", rs.getLong("size_bytes"),
                        "status", rs.getString("status"), "createdAt", rs.getTimestamp("created_at").toInstant().toString(),
                        "updatedAt", rs.getTimestamp("updated_at").toInstant().toString()), accountId));
        result.put("retiredHistory", historyRows(accountId));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historyRows(String accountId) {
        return jdbc.query("SELECT * FROM retired_career_history_records WHERE account_id=? ORDER BY event_time,sequence_no,id",
                (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String key : List.of("id", "record_type", "source_id", "parent_source_id",
                            "secondary_parent_id", "status", "label", "detail_text", "content_text",
                            "payload_json_a", "payload_json_b", "reference_id_a", "reference_id_b",
                            "reference_id_c")) row.put(camel(key), rs.getString(key));
                    row.put("eventTime", rs.getTimestamp("event_time") == null ? null : rs.getTimestamp("event_time").toInstant());
                    return row;
                }, accountId);
    }

    @Transactional(readOnly = true)
    public HistorySummary historySummary(CurrentAccount current) {
        assertSeeker(current);
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbc.query("SELECT record_type,COUNT(*) AS total FROM retired_career_history_records WHERE account_id=? GROUP BY record_type",
                rs -> { counts.put(rs.getString("record_type"), rs.getInt("total")); }, current.accountId());
        Instant archivedAt = jdbc.queryForObject(
                "SELECT MAX(archived_at) FROM retired_career_history_records WHERE account_id=?",
                (rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
                current.accountId());
        return new HistorySummary(counts.values().stream().mapToInt(Integer::intValue).sum(), Map.copyOf(counts), archivedAt);
    }

    public String historyMarkdown(CurrentAccount current) {
        assertSeeker(current);
        StringBuilder out = new StringBuilder("# 历史求职记录归档\n\n");
        for (Map<String, Object> row : historyRows(current.accountId())) {
            out.append("## ").append(Objects.toString(row.get("recordType"), "记录")).append("\n\n")
                    .append("- 时间：").append(Objects.toString(row.get("eventTime"), "未记录")).append("\n")
                    .append("- 状态：").append(Objects.toString(row.get("status"), "未记录")).append("\n")
                    .append("- 标题：").append(Objects.toString(row.get("label"), "未记录")).append("\n\n");
            String content = Objects.toString(row.get("contentText"), "");
            if (!content.isBlank()) out.append(content).append("\n\n");
        }
        return out.toString();
    }

    public byte[] historyJson(CurrentAccount current) {
        assertSeeker(current);
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(historyRows(current.accountId())); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    public String evidenceHash(String accountId) {
        List<Map<String, Object>> snapshot = aiEvidence(accountId).stream()
                .sorted(Comparator.comparing(AiEvidence::id))
                .map(item -> Map.<String, Object>of("id", item.id(), "type", item.type(), "title", item.title(),
                        "body", Objects.toString(item.body(), ""), "coreOutcome", Objects.toString(item.coreOutcome(), ""),
                        "strength", item.strength(), "pendingSupplement", item.pendingSupplement())).toList();
        return Tokens.sha256(json(snapshot));
    }

    private RecordView changeRecordStatus(CurrentAccount current, String id, Integer expectedVersion, String status) {
        assertSeeker(current);
        RecordView value = requireRecord(current.accountId(), id);
        assertVersion(expectedVersion, value.version());
        Instant now = clock.now();
        int changed = jdbc.update("UPDATE career_library_records SET status=?,archived_at=?,version_no=version_no+1,updated_at=? WHERE id=? AND account_id=? AND version_no=?",
                status, "ARCHIVED".equals(status) ? now : null, now, id, current.accountId(), value.version());
        requireUpdated(changed);
        bump(current.accountId(), "RECORD_" + status);
        return requireRecord(current.accountId(), id);
    }

    private void ensureProfile(String accountId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM career_library_profiles WHERE account_id=?", Integer.class, accountId);
        if (count != null && count > 0) return;
        Instant now = clock.now();
        try {
            jdbc.update("INSERT INTO career_library_profiles(account_id,basics_json,intentions_json,preferences_json,summary_text,snapshot_version,version_no,created_at,updated_at) VALUES(?,'{}','{}','{}',NULL,0,0,?,?)",
                    accountId, now, now);
        } catch (DuplicateKeyException ignored) {
            // Another first request created this account's profile after our existence check.
        }
    }

    private ProfileView profileOf(String accountId) {
        return jdbc.query("SELECT * FROM career_library_profiles WHERE account_id=?",
                (rs, n) -> profileView(accountId, read(rs.getString("basics_json")),
                        read(rs.getString("intentions_json")), read(rs.getString("preferences_json")),
                        rs.getString("summary_text"), rs.getString("avatar_file_id"), rs.getInt("snapshot_version"),
                        rs.getInt("version_no"), rs.getTimestamp("updated_at").toInstant()), accountId).stream().findFirst()
                .orElseThrow(() -> AppException.user("CAREER_PROFILE_NOT_FOUND", "求职资料主档不存在"));
    }

    private ProfileView profileView(String accountId, JsonNode basics, JsonNode intentions, JsonNode preferences,
            String summary, String avatarFileId, int snapshotVersion, int version, Instant updatedAt) {
        List<MissingItem> missing = new ArrayList<>();
        missingText(missing, basics, "name", "姓名", "profile-name");
        if (textMissing(basics, "email") && textMissing(basics, "phone")) {
            missing.add(new MissingItem("contact", "邮箱或手机号", "profile-email"));
        }
        missingText(missing, basics, "location", "所在地点", "profile-location");
        if (!basics.path("links").isArray() || basics.path("links").isEmpty()) {
            missing.add(new MissingItem("links", "个人链接", "profile-links"));
        }
        missingText(missing, intentions, "targetJob", "目标岗位", "profile-target-job");
        missingText(missing, intentions, "jobCategory", "岗位分类", "profile-job-category");
        missingText(missing, preferences, "targetCity", "目标城市", "profile-target-city");
        if (summary == null || summary.isBlank()) missing.add(new MissingItem("summary", "职业简介", "profile-summary"));
        int total = 8;
        int completeness = Math.max(0, Math.round((total - missing.size()) * 100f / total));
        return new ProfileView(accountId, basics, intentions, preferences, summary, avatarFileId,
                snapshotVersion, version, updatedAt, completeness, List.copyOf(missing));
    }

    private List<RecordView> activeConfirmed(String accountId) {
        return jdbc.query(RECORD_SELECT + " WHERE account_id=? AND status='ACTIVE' AND confirmed=1 ORDER BY sort_order,updated_at DESC",
                this::recordView, accountId);
    }

    private List<RecordView> activeAndArchived(String accountId) {
        return jdbc.query(RECORD_SELECT + " WHERE account_id=? ORDER BY status,sort_order,updated_at DESC",
                this::recordView, accountId);
    }

    private RecordView requireRecord(String accountId, String id) {
        return jdbc.query(RECORD_SELECT + " WHERE r.id=? AND account_id=?", this::recordView, id, accountId)
                .stream().findFirst().orElseThrow(() -> AppException.user("CAREER_RECORD_NOT_FOUND", "求职资料记录不存在"));
    }

    private RecordView recordView(ResultSet rs, int row) throws SQLException {
        return new RecordView(rs.getString("id"), rs.getString("record_type"), rs.getString("title"),
                rs.getString("organization"), rs.getString("role_name"), rs.getString("start_date"),
                rs.getString("end_date"), rs.getString("location"), rs.getString("description_text"),
                rs.getString("core_outcome"), rs.getString("url"), read(rs.getString("payload_json")),
                rs.getString("strength"), rs.getBoolean("pending_supplement"), rs.getString("source_type"),
                rs.getString("source_ref_id"), rs.getString("status"), rs.getBoolean("confirmed"),
                rs.getInt("sort_order"), rs.getInt("reference_count"), rs.getInt("version_no"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getTimestamp("archived_at") == null ? null : rs.getTimestamp("archived_at").toInstant());
    }

    private void bump(String accountId, String reason) {
        ensureProfile(accountId);
        Instant now = clock.now();
        jdbc.update("UPDATE career_library_profiles SET snapshot_version=snapshot_version+1,version_no=version_no+1,updated_at=? WHERE account_id=?",
                now, accountId);
        int snapshot = jdbc.queryForObject("SELECT snapshot_version FROM career_library_profiles WHERE account_id=?", Integer.class, accountId);
        emitChanged(accountId, snapshot, reason);
    }

    private void emitChanged(String accountId, int snapshot, String reason) {
        outbox.enqueue(CareerLibraryEventTypes.SNAPSHOT_CHANGED,
                Map.of("accountId", accountId, "snapshotVersion", snapshot, "reason", reason));
    }

    private static boolean relevant(RecordView item, String term) {
        if (term == null || term.isBlank()) return true;
        String text = String.join(" ", item.type(), item.title(), Objects.toString(item.organization(), ""),
                Objects.toString(item.role(), ""), Objects.toString(item.description(), ""),
                Objects.toString(item.coreOutcome(), "")).toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(term.split("\\s+|[,，。；;]+"))
                .filter(value -> value.length() > 1).anyMatch(text::contains);
    }

    private static String excerpt(RecordView item) {
        String value = String.join("；", item.title(), Objects.toString(item.description(), ""),
                Objects.toString(item.coreOutcome(), "")).replaceAll("\\s+", " ").trim();
        return value.substring(0, Math.min(240, value.length()));
    }

    private static void putTextFact(Map<String, JsonNode> facts, String factKey, JsonNode source, String key) {
        if (source != null && source.hasNonNull(key) && !source.path(key).asText("").isBlank()) facts.put(factKey, source.get(key));
    }

    private JsonNode objectOrEmpty(JsonNode node) {
        if (node == null || node.isNull()) return mapper.createObjectNode();
        if (!node.isObject()) throw AppException.user("CAREER_PROFILE_INVALID", "个人信息、求职意向和偏好必须是对象");
        return node;
    }

    private static void rejectSensitiveKeys(JsonNode node) {
        Set<String> forbidden = Set.of("age", "gender", "sex", "maritalStatus", "marriage", "ethnicity", "民族", "婚育", "性别", "年龄");
        node.fieldNames().forEachRemaining(key -> {
            if (forbidden.contains(key)) throw AppException.user("SENSITIVE_FIELD_FORBIDDEN", "求职资料库不保存年龄、性别、婚育或民族字段");
        });
    }

    private static String normalizeType(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(value) || "PROFILE_FACT".equals(value)) throw AppException.user("CAREER_RECORD_TYPE_INVALID", "资料记录类型无效");
        return value;
    }

    private static String normalizeOptionalType(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(value)) throw AppException.user("CAREER_RECORD_TYPE_INVALID", "资料记录类型无效");
        return value;
    }

    private static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) return "ACTIVE";
        if ("ALL".equalsIgnoreCase(raw)) return null;
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "ARCHIVED").contains(value)) throw AppException.user("CAREER_RECORD_STATUS_INVALID", "资料状态无效");
        return value;
    }

    private static String requiredTitle(String value) {
        if (value == null || value.isBlank()) throw AppException.user("CAREER_RECORD_TITLE_REQUIRED", "资料标题不能为空");
        return value.trim();
    }

    private static void assertVersion(Integer expected, int actual) {
        if (expected != null && expected != actual) throw AppException.conflict("VERSION_CONFLICT", "资料已在其他位置更新，请刷新后重试");
    }

    private static void requireUpdated(int changed) {
        if (changed != 1) throw AppException.conflict("VERSION_CONFLICT", "资料已在其他位置更新，请刷新后重试");
    }

    private static void assertSeeker(CurrentAccount current) {
        if (current.operator()) throw AppException.forbidden("OPERATOR_NO_ORIGINAL", "运营默认不能查看用户求职资料原文");
    }

    private static String choose(String incoming, String current) { return incoming == null ? current : blankToNull(incoming); }
    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private int count(String sql, String accountId) { Integer value = jdbc.queryForObject(sql, Integer.class, accountId); return value == null ? 0 : value; }
    private long longValue(String sql, String accountId) { Long value = jdbc.queryForObject(sql, Long.class, accountId); return value == null ? 0 : value; }
    private static boolean textMissing(JsonNode node, String key) { return node == null || node.path(key).asText("").isBlank(); }
    private static void missingText(List<MissingItem> missing, JsonNode node, String key, String label, String fieldId) {
        if (textMissing(node, key)) missing.add(new MissingItem(key, label, fieldId));
    }
    private static String camel(String value) {
        StringBuilder out = new StringBuilder(); boolean upper = false;
        for (char c : value.toCharArray()) { if (c == '_') { upper = true; continue; } out.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        return out.toString();
    }
    private JsonNode read(String value) { try { return mapper.readTree(value == null ? "{}" : value); } catch (Exception e) { return mapper.createObjectNode(); } }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }

    public record ProfileWrite(JsonNode basics, JsonNode intentions, JsonNode preferences, String summary, Integer expectedVersion) {}
    public record ProfileView(String accountId, JsonNode basics, JsonNode intentions, JsonNode preferences,
            String summary, String avatarFileId, int snapshotVersion, int version, Instant updatedAt,
            int completeness, List<MissingItem> missingItems) {}
    public record MissingItem(String key, String label, String fieldId) {}
    public record OverviewView(int profileCompleteness, List<MissingItem> missingItems, int totalRecords,
            int inUseRecords, int outcomeRecords, int pendingRecords, int skillKeywords, int healthScore,
            long storageUsedBytes, long storageQuotaBytes, int readyFiles, int processingFiles,
            Map<String, Integer> categoryCounts, Instant updatedAt) {}
    public record SearchResult(String type, String id, String title, String subtitle, String view, String targetId) {}
    public record RecordWrite(String type, String title, String organization, String role, String startDate,
            String endDate, String location, String description, String coreOutcome, String url,
            JsonNode payload, String strength, Integer sortOrder, Integer expectedVersion) {}
    public record RecordOrder(String id, int sortOrder, Integer expectedVersion) {}
    public record RecordView(String id, String type, String title, String organization, String role,
            String startDate, String endDate, String location, String description, String coreOutcome,
            String url, JsonNode payload, String strength, boolean pendingSupplement, String sourceType,
            String sourceRefId, String status, boolean confirmed, int sortOrder, int resumeReferenceCount, int version,
            Instant createdAt, Instant updatedAt, Instant archivedAt) {}
    public record AiContextRead(String accountId, int snapshotVersion, boolean directionConfirmed, Map<String, JsonNode> facts) {}
    public record AiEvidence(String id, String type, String title, String body, String coreOutcome,
            String strength, boolean pendingSupplement) {}
    public record SourceRef(String id, String type, String title, String excerpt) {}
    public record EvidenceSnapshot(int snapshotVersion, List<SourceRef> sources) {}
    public record CertificateSourceRef(String id, String name, String issuer, String date,
            String description, String coreOutcome) {}
    public record CertificateEvidenceSnapshot(int snapshotVersion, List<CertificateSourceRef> sources) {}
    public record HonorSourceRef(String id, String name, String issuer, String date,
            String description, String coreOutcome) {}
    public record HonorEvidenceSnapshot(int snapshotVersion, List<HonorSourceRef> sources) {}
    public record HistorySummary(int total, Map<String, Integer> counts, Instant archivedAt) {}
}
