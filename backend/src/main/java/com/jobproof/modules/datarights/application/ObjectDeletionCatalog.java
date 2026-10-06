package com.jobproof.modules.datarights.application;

import com.jobproof.modules.datarights.application.ObjectDeletionPlan.ImpactItem;
import com.jobproof.modules.datarights.domain.DeletionStateMachine.Receipt;
import com.jobproof.modules.datarights.domain.DeletionStateMachine.ReceiptStatus;
import com.jobproof.modules.datarights.domain.DeletionTargetType;
import com.jobproof.modules.storage.ObjectStoragePort;
import com.jobproof.modules.storage.PrivateFileEntity;
import com.jobproof.modules.storage.PrivateFileJpaRepository;
import com.jobproof.shared.error.AppException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObjectDeletionCatalog {
    private static final Set<String> OPEN_TASK_STATUSES = Set.of("PENDING", "RUNNING");

    private final JdbcTemplate jdbc;
    private final PrivateFileJpaRepository privateFiles;
    private final ObjectStoragePort storage;

    public ObjectDeletionCatalog(JdbcTemplate jdbc, PrivateFileJpaRepository privateFiles, ObjectStoragePort storage) {
        this.jdbc = jdbc;
        this.privateFiles = privateFiles;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public ObjectDeletionPlan analyze(String accountId, String rawType, String targetId) {
        DeletionTargetType type = DeletionTargetType.parse(rawType);
        String id = targetId == null ? "" : targetId.trim();
        if (id.isBlank()) throw AppException.user("TARGET_REQUIRED", "对象级删除必须指定 ID");
        return switch (type) {
            case CAREER_RECORD, CAREER_FILE, CAREER_PROFILE -> analyzeCareer(accountId, type, id);
            case RESUME_MASTER -> analyzeResumeMaster(accountId, id);
            case RESUME_VERSION -> analyzeResumeVersion(accountId, id);
        };
    }

    @Transactional
    public List<Receipt> execute(String accountId, String rawType, String targetId) {
        ObjectDeletionPlan plan = analyze(accountId, rawType, targetId);
        if (!plan.canProceed()) {
            throw AppException.conflict(plan.blockers().get(0), blockerMessage(plan.blockers().get(0)));
        }
        switch (plan.targetType()) {
            case CAREER_RECORD -> {
                jdbc.update("DELETE FROM career_library_record_refs WHERE record_id=?", plan.targetId());
                jdbc.update("DELETE FROM career_library_records WHERE id=? AND account_id=?", plan.targetId(), accountId);
            }
            case CAREER_FILE -> deleteCareerFile(accountId, plan.targetId());
            case CAREER_PROFILE -> {
                jdbc.update("DELETE FROM career_library_records WHERE account_id=?", accountId);
                jdbc.update("DELETE FROM career_library_profiles WHERE account_id=?", accountId);
            }
            case RESUME_MASTER -> deleteResumeMaster(accountId, plan.targetId());
            case RESUME_VERSION -> deleteResumeVersion(accountId, plan.targetId());
        }
        return List.of(new Receipt(plan.targetType().moduleCode(), ReceiptStatus.SUCCEEDED,
                plan.targetType().label() + "及关联数据已永久删除"));
    }

    private ObjectDeletionPlan analyzeCareer(String accountId, DeletionTargetType type, String id) {
        boolean found = switch (type) {
            case CAREER_RECORD -> exists("SELECT COUNT(*) FROM career_library_records WHERE id=? AND account_id=?", id, accountId);
            case CAREER_FILE -> exists("SELECT COUNT(*) FROM career_library_files WHERE id=? AND account_id=?", id, accountId);
            case CAREER_PROFILE -> accountId.equals(id)
                    && exists("SELECT COUNT(*) FROM career_library_profiles WHERE account_id=?", accountId);
            default -> false;
        };
        requireFound(found);
        List<String> blockers = type == DeletionTargetType.CAREER_RECORD
                && exists("SELECT COUNT(*) FROM career_library_record_refs WHERE record_id=? AND active=1", id)
                ? List.of("CAREER_RECORD_REFERENCED") : List.of();
        return new ObjectDeletionPlan(type, id, type.label() + "将被永久删除，无法恢复",
                List.of(new ImpactItem(type.name(), id, "", "TARGET", type.label())), blockers, blockers.isEmpty());
    }

    private ObjectDeletionPlan analyzeResumeMaster(String accountId, String masterId) {
        requireFound(exists("SELECT COUNT(*) FROM resume_masters WHERE id=? AND account_id=?", masterId, accountId));
        ResumeScope scope = masterScope(accountId, masterId);
        List<String> blockers = resumeBlockers(scope);
        List<ImpactItem> impacts = new ArrayList<>();
        impacts.add(new ImpactItem("RESUME_MASTER", masterId, "", "TARGET", "简历主档"));
        addImpact(impacts, "RESUME_VERSION", scope.versionIds().size(), "CHILD", "冻结/定制版本");
        addImpact(impacts, "AI_RESUME_CONVERSATION", scope.conversationIds().size(), "CHILD", "AI 对话及候选记录");
        addImpact(impacts, "RESUME_EXPORT", scope.fileIds().size(), "GENERATED", "PDF/DOCX 导出文件");
        addImpact(impacts, "MATCH_REPORT", scope.reportIds().size(), "REFERENCE", "岗位匹配报告");
        addImpact(impacts, "CAREER_RECORD_REFERENCE", scope.careerReferenceCount(), "REFERENCE", "求职资料引用");
        addImpact(impacts, "RETIRED_HISTORY", scope.historyIds().size(), "REFERENCE", "关联历史归档记录");
        String summary = "将永久删除该简历主档、" + scope.versionIds().size() + " 个版本、"
                + scope.conversationIds().size() + " 个 AI 对话、" + scope.fileIds().size()
                + " 个导出文件和 " + scope.reportIds().size() + " 份匹配报告。操作不可恢复。";
        return new ObjectDeletionPlan(DeletionTargetType.RESUME_MASTER, masterId, summary,
                impacts, blockers, blockers.isEmpty());
    }

    private ObjectDeletionPlan analyzeResumeVersion(String accountId, String versionId) {
        requireFound(exists("SELECT COUNT(*) FROM resume_versions WHERE id=? AND account_id=?", versionId, accountId));
        ResumeScope scope = versionScope(accountId, versionId);
        List<String> blockers = resumeBlockers(scope);
        List<ImpactItem> impacts = new ArrayList<>();
        impacts.add(new ImpactItem("RESUME_VERSION", versionId, "", "TARGET", "简历冻结版本"));
        addImpact(impacts, "RESUME_EXPORT", scope.fileIds().size(), "GENERATED", "PDF/DOCX 导出文件");
        addImpact(impacts, "CAREER_RECORD_REFERENCE", scope.careerReferenceCount(), "REFERENCE", "求职资料引用");
        addImpact(impacts, "RETIRED_HISTORY", scope.historyIds().size(), "REFERENCE", "关联历史归档记录");
        String summary = "将永久删除该冻结版本、" + scope.fileIds().size() + " 个导出文件和 "
                + scope.careerReferenceCount() + " 条求职资料引用。简历主档及其他版本不受影响。";
        return new ObjectDeletionPlan(DeletionTargetType.RESUME_VERSION, versionId, summary,
                impacts, blockers, blockers.isEmpty());
    }

    private List<String> resumeBlockers(ResumeScope scope) {
        return scope.hasOpenTask() ? List.of("RESUME_TASK_IN_PROGRESS") : List.of();
    }

    private ResumeScope masterScope(String accountId, String masterId) {
        List<String> versionIds = strings("SELECT id FROM resume_versions WHERE account_id=? AND master_id=?", accountId, masterId);
        List<String> branchIds = strings("SELECT id FROM resume_branches WHERE account_id=? AND master_id=?", accountId, masterId);
        List<String> revisionIds = strings("SELECT id FROM resume_revisions WHERE account_id=? AND master_id=?", accountId, masterId);
        List<String> conversationIds = strings("SELECT id FROM ai_resume_conversations WHERE account_id=? AND master_id=?", accountId, masterId);
        List<String> layoutIds = strings("SELECT id FROM resume_layout_instances WHERE account_id=? AND master_id=?", accountId, masterId);
        List<String> reportIds = strings("SELECT id FROM match_reports WHERE account_id=? AND resume_master_id=?", accountId, masterId);
        List<String> importIds = strings("SELECT id FROM resume_import_sessions WHERE account_id=? AND result_master_id=?", accountId, masterId);
        List<String> artifactIds = queryRelatedArtifactIds(accountId, versionIds, layoutIds);
        List<String> fileIds = queryArtifactFileIds(accountId, artifactIds);
        List<String> historyIds = historyIds(accountId, versionIds);
        int careerRefs = countByIds("career_library_record_refs", "resume_version_id", versionIds);
        Set<String> references = linkedSet(List.of(masterId), versionIds, branchIds, revisionIds, conversationIds,
                layoutIds, reportIds, importIds, artifactIds);
        TaskSelection taskSelection = relatedTasks(accountId, references);
        return new ResumeScope(versionIds, branchIds, revisionIds, conversationIds, layoutIds, reportIds,
                importIds, artifactIds, fileIds, historyIds, taskSelection.taskIds(), careerRefs,
                taskSelection.hasOpenTask());
    }

    private ResumeScope versionScope(String accountId, String versionId) {
        List<String> layoutIds = strings("SELECT id FROM resume_layout_instances WHERE account_id=? AND content_version_id=?", accountId, versionId);
        List<String> directLayout = strings("SELECT layout_instance_id FROM resume_versions WHERE id=? AND account_id=? AND layout_instance_id IS NOT NULL", versionId, accountId);
        layoutIds = distinct(layoutIds, directLayout);
        List<String> artifactIds = queryRelatedArtifactIds(accountId, List.of(versionId), layoutIds);
        List<String> fileIds = queryArtifactFileIds(accountId, artifactIds);
        List<String> historyIds = historyIds(accountId, List.of(versionId));
        int careerRefs = countByIds("career_library_record_refs", "resume_version_id", List.of(versionId));
        Set<String> references = linkedSet(List.of(versionId), layoutIds, artifactIds);
        TaskSelection taskSelection = relatedTasks(accountId, references);
        return new ResumeScope(List.of(versionId), List.of(), List.of(), List.of(), layoutIds, List.of(),
                List.of(), artifactIds, fileIds, historyIds, taskSelection.taskIds(), careerRefs,
                taskSelection.hasOpenTask());
    }

    private void deleteResumeMaster(String accountId, String masterId) {
        ResumeScope scope = masterScope(accountId, masterId);
        deleteMatchReports(scope.reportIds());
        deleteHistory(scope.historyIds());
        deleteByIds("career_library_record_refs", "resume_version_id", scope.versionIds());
        deleteByIds("resume_render_artifacts", "id", scope.artifactIds());
        jdbc.update("DELETE FROM resume_layout_preferences WHERE account_id=? AND master_id=?", accountId, masterId);
        jdbc.update("DELETE FROM resume_layout_instances WHERE account_id=? AND master_id=?", accountId, masterId);
        deleteAiResume(scope.conversationIds());
        deleteByIds("resume_import_sessions", "id", scope.importIds());
        jdbc.update("DELETE FROM resume_revisions WHERE account_id=? AND master_id=?", accountId, masterId);
        jdbc.update("DELETE FROM resume_branches WHERE account_id=? AND master_id=?", accountId, masterId);
        jdbc.update("DELETE FROM resume_candidates WHERE account_id=? AND master_id=?", accountId, masterId);
        jdbc.update("DELETE FROM resume_versions WHERE account_id=? AND master_id=?", accountId, masterId);
        deleteByIds("async_tasks", "id", scope.taskIds());
        jdbc.update("DELETE FROM resume_masters WHERE id=? AND account_id=?", masterId, accountId);
        deletePrivateFiles(accountId, scope.fileIds());
    }

    private void deleteResumeVersion(String accountId, String versionId) {
        ResumeScope scope = versionScope(accountId, versionId);
        deleteHistory(scope.historyIds());
        jdbc.update("DELETE FROM career_library_record_refs WHERE resume_version_id=?", versionId);
        deleteByIds("resume_render_artifacts", "id", scope.artifactIds());
        jdbc.update("DELETE FROM resume_versions WHERE id=? AND account_id=?", versionId, accountId);
        for (String layoutId : scope.layoutIds()) {
            boolean stillUsed = exists("SELECT COUNT(*) FROM resume_versions WHERE layout_instance_id=?", layoutId)
                    || exists("SELECT COUNT(*) FROM resume_revisions WHERE layout_instance_id=?", layoutId);
            if (stillUsed) {
                jdbc.update("UPDATE resume_layout_instances SET content_version_id=NULL WHERE id=? AND content_version_id=?", layoutId, versionId);
            } else {
                jdbc.update("DELETE FROM resume_layout_instances WHERE id=? AND account_id=?", layoutId, accountId);
            }
        }
        deleteByIds("async_tasks", "id", scope.taskIds());
        deletePrivateFiles(accountId, scope.fileIds());
    }

    private void deleteAiResume(List<String> conversationIds) {
        if (conversationIds.isEmpty()) return;
        List<String> changeSetIds = selectByIds("SELECT id FROM ai_resume_change_sets WHERE conversation_id IN (%s)", conversationIds);
        deleteByIds("ai_resume_change_items", "change_set_id", changeSetIds);
        deleteByIds("ai_resume_generation_attempts", "conversation_id", conversationIds);
        deleteByIds("ai_resume_preferences", "conversation_id", conversationIds);
        deleteByIds("ai_resume_stream_events", "conversation_id", conversationIds);
        deleteByIds("ai_resume_change_sets", "conversation_id", conversationIds);
        deleteByIds("ai_resume_cards", "conversation_id", conversationIds);
        deleteByIds("ai_resume_messages", "conversation_id", conversationIds);
        deleteByIds("ai_resume_conversations", "id", conversationIds);
    }

    private void deleteMatchReports(List<String> reportIds) {
        if (reportIds.isEmpty()) return;
        deleteByIds("match_advice_items", "report_id", reportIds);
        deleteByIds("match_ai_advice_runs", "report_id", reportIds);
        deleteByIds("match_requirement_items", "report_id", reportIds);
        deleteByIds("match_reports", "id", reportIds);
    }

    private List<String> historyIds(String accountId, List<String> versionIds) {
        if (versionIds.isEmpty()) return List.of();
        String owned = "account_id='" + safeId(accountId) + "'";
        List<String> rowIds = selectByIds("SELECT id FROM retired_career_history_records WHERE " + owned
                + " AND reference_id_a IN (%s)", versionIds);
        Set<String> sourceIds = new LinkedHashSet<>(selectByIds(
                "SELECT source_id FROM retired_career_history_records WHERE " + owned + " AND reference_id_a IN (%s)", versionIds));
        for (int depth = 0; depth < 4 && !sourceIds.isEmpty(); depth++) {
            List<String> childRows = selectByIds("SELECT id FROM retired_career_history_records WHERE " + owned
                    + " AND (parent_source_id IN (%s) OR secondary_parent_id IN (%s))", sourceIds, sourceIds);
            List<String> children = selectByIds("SELECT source_id FROM retired_career_history_records WHERE " + owned
                    + " AND (parent_source_id IN (%s) OR secondary_parent_id IN (%s))", sourceIds, sourceIds);
            int before = sourceIds.size();
            rowIds = distinct(rowIds, childRows);
            sourceIds.addAll(children);
            if (sourceIds.size() == before) break;
        }
        return distinct(rowIds);
    }

    private void deleteHistory(List<String> historyIds) {
        deleteByIds("retired_career_history_records", "id", historyIds);
    }

    private TaskSelection relatedTasks(String accountId, Set<String> references) {
        if (references.isEmpty()) return new TaskSelection(List.of(), false);
        List<TaskRow> tasks = jdbc.query("SELECT id,status,payload_json FROM async_tasks WHERE account_id=?",
                (rs, row) -> new TaskRow(rs.getString("id"), rs.getString("status"), rs.getString("payload_json")), accountId);
        List<String> ids = new ArrayList<>();
        boolean open = false;
        for (TaskRow task : tasks) {
            String payload = task.payload() == null ? "" : task.payload();
            boolean related = references.contains(task.id()) || references.stream().anyMatch(payload::contains);
            if (!related) continue;
            ids.add(task.id());
            if (OPEN_TASK_STATUSES.contains(task.status())) open = true;
        }
        return new TaskSelection(distinct(ids), open);
    }

    private List<String> queryRelatedArtifactIds(String accountId, List<String> versionIds, List<String> layoutIds) {
        List<String> ids = new ArrayList<>();
        String owned = "account_id='" + safeId(accountId) + "'";
        if (!versionIds.isEmpty()) {
            ids.addAll(selectByIds("SELECT id FROM resume_render_artifacts WHERE " + owned
                    + " AND content_version_id IN (%s)", versionIds));
        }
        if (!layoutIds.isEmpty()) {
            ids.addAll(selectByIds("SELECT id FROM resume_render_artifacts WHERE " + owned
                    + " AND layout_instance_id IN (%s)", layoutIds));
        }
        return distinct(ids);
    }

    private List<String> queryArtifactFileIds(String accountId, List<String> artifactIds) {
        if (artifactIds.isEmpty()) return List.of();
        return distinct(selectByIds("SELECT file_id FROM resume_render_artifacts WHERE account_id='" + safeId(accountId)
                + "' AND file_id IS NOT NULL AND id IN (%s)", artifactIds));
    }

    private void deletePrivateFiles(String accountId, List<String> fileIds) {
        for (String fileId : distinct(fileIds)) {
            if (privateFileStillReferenced(fileId)) continue;
            privateFiles.findById(fileId)
                    .filter(file -> accountId.equals(file.getOwnerId()))
                    .ifPresent(this::deletePrivate);
        }
    }

    private boolean privateFileStillReferenced(String fileId) {
        return exists("SELECT COUNT(*) FROM resume_render_artifacts WHERE file_id=?", fileId)
                || exists("SELECT COUNT(*) FROM career_library_files WHERE private_file_id=?", fileId)
                || exists("SELECT COUNT(*) FROM career_library_profiles WHERE avatar_file_id=?", fileId)
                || exists("SELECT COUNT(*) FROM mock_interview_audio_chunks WHERE private_file_id=?", fileId);
    }

    private void deleteCareerFile(String accountId, String id) {
        List<String> keys = jdbc.query("SELECT p.object_key FROM career_library_file_previews p JOIN career_library_files f ON f.id=p.file_id WHERE f.id=? AND f.account_id=?",
                (rs, n) -> rs.getString(1), id, accountId);
        keys.forEach(storage::delete);
        List<String> privateIds = jdbc.query("SELECT private_file_id FROM career_library_files WHERE id=? AND account_id=?",
                (rs, n) -> rs.getString(1), id, accountId);
        jdbc.update("DELETE FROM career_library_file_previews WHERE file_id=?", id);
        jdbc.update("DELETE FROM career_library_files WHERE id=? AND account_id=?", id, accountId);
        for (String privateId : privateIds) privateFiles.findById(privateId).ifPresent(this::deletePrivate);
    }

    private void deletePrivate(PrivateFileEntity value) {
        storage.delete(value.getObjectKey());
        privateFiles.delete(value);
    }

    private void addImpact(List<ImpactItem> impacts, String kind, int count, String relation, String label) {
        if (count > 0) impacts.add(new ImpactItem(kind, String.valueOf(count), "", relation, label + " · " + count + " 项"));
    }

    private int countByIds(String table, String column, List<String> ids) {
        if (ids.isEmpty()) return 0;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " IN (" + placeholders(ids.size()) + ")",
                Integer.class, ids.toArray());
        return count == null ? 0 : count;
    }

    private void deleteByIds(String table, String column, List<String> ids) {
        if (ids.isEmpty()) return;
        jdbc.update("DELETE FROM " + table + " WHERE " + column + " IN (" + placeholders(ids.size()) + ")", ids.toArray());
    }

    private List<String> selectByIds(String sqlTemplate, Collection<String> first) {
        return selectByIds(sqlTemplate, first, List.of());
    }

    private List<String> selectByIds(String sqlTemplate, Collection<String> first, Collection<String> second) {
        if (first.isEmpty()) return List.of();
        String sql = sqlTemplate.replaceFirst("%s", placeholders(first.size()));
        List<Object> args = new ArrayList<>(first);
        if (sql.contains("%s")) {
            if (second.isEmpty()) return List.of();
            sql = sql.replaceFirst("%s", placeholders(second.size()));
            args.addAll(second);
        }
        return jdbc.query(sql, (rs, row) -> rs.getString(1), args.toArray()).stream()
                .filter(value -> value != null && !value.isBlank()).toList();
    }

    private List<String> strings(String sql, Object... args) {
        return jdbc.query(sql, (rs, row) -> rs.getString(1), args).stream()
                .filter(value -> value != null && !value.isBlank()).toList();
    }

    @SafeVarargs
    private static Set<String> linkedSet(Collection<String>... values) {
        Set<String> result = new LinkedHashSet<>();
        for (Collection<String> rows : values) result.addAll(rows);
        result.removeIf(value -> value == null || value.isBlank());
        return result;
    }

    @SafeVarargs
    private static List<String> distinct(Collection<String>... values) {
        return List.copyOf(linkedSet(values));
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static String safeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9-]+")) throw AppException.user("TARGET_INVALID", "对象 ID 格式不正确");
        return value;
    }

    private static void requireFound(boolean found) {
        if (!found) throw AppException.user("TARGET_NOT_FOUND", "对象不存在或不属于当前账号");
    }

    private static String blockerMessage(String blocker) {
        return switch (blocker) {
            case "RESUME_TASK_IN_PROGRESS" -> "该简历仍有定制、匹配或导出任务运行中，请等待任务结束或先取消任务";
            case "CAREER_RECORD_REFERENCED" -> "这条资料仍被简历版本使用，请先在简历中移除后再删除";
            default -> "这项内容仍被其他内容使用，请先解除引用后再删除";
        };
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }

    private record TaskRow(String id, String status, String payload) {}
    private record TaskSelection(List<String> taskIds, boolean hasOpenTask) {}
    private record ResumeScope(
            List<String> versionIds,
            List<String> branchIds,
            List<String> revisionIds,
            List<String> conversationIds,
            List<String> layoutIds,
            List<String> reportIds,
            List<String> importIds,
            List<String> artifactIds,
            List<String> fileIds,
            List<String> historyIds,
            List<String> taskIds,
            int careerReferenceCount,
            boolean hasOpenTask) {}
}
