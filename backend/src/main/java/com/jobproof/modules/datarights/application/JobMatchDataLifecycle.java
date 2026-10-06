package com.jobproof.modules.datarights.application;

import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.export.AccountExportContributor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Account export and deletion coverage for historical and current job-match protocols. */
@Component
public class JobMatchDataLifecycle implements DeletionModuleHandler, AccountExportContributor {
    private final JdbcTemplate jdbc;

    public JobMatchDataLifecycle(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String moduleCode() { return "matching"; }

    @Override
    public String moduleKey() { return "matching"; }

    @Override
    @Transactional
    public Result onAccountDeletion(String accountId) {
        try {
            jdbc.update("DELETE FROM job_match_exports WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM job_match_stream_events WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM job_match_improvement_tasks WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM job_match_clarifications WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM job_match_claims WHERE match_id IN (SELECT id FROM job_match_tasks WHERE account_id=?)", accountId);
            jdbc.update("DELETE FROM job_match_report_versions WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM job_match_evidence_items WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM job_match_authorizations WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM job_requirements WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM match_advice_items WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM match_ai_advice_runs WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM match_requirement_items WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM match_reports WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM job_match_tasks WHERE account_id=?", accountId);
            return Result.succeeded("岗位匹配任务、授权快照、报告、结论、建议和导出记录已清理");
        } catch (RuntimeException exception) {
            return Result.failed("岗位匹配数据清理失败，删除申请保持可重试");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> contribute(String accountId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", "job-match-v2");
        result.put("tasks", rows("SELECT * FROM job_match_tasks WHERE account_id=? ORDER BY created_at", accountId));
        result.put("jobRequirements", rows("SELECT * FROM job_requirements WHERE account_id=? ORDER BY match_id,sequence_no", accountId));
        result.put("authorizations", rows("SELECT * FROM job_match_authorizations WHERE account_id=? ORDER BY created_at", accountId));
        result.put("evidenceSnapshots", rows("SELECT * FROM job_match_evidence_items WHERE account_id=? ORDER BY created_at", accountId));
        result.put("reportVersions", rows("SELECT * FROM job_match_report_versions WHERE account_id=? ORDER BY created_at", accountId));
        result.put("claims", rows("SELECT c.* FROM job_match_claims c JOIN job_match_tasks t ON t.id=c.match_id WHERE t.account_id=? ORDER BY c.created_at", accountId));
        result.put("clarifications", rows("SELECT * FROM job_match_clarifications WHERE account_id=? ORDER BY created_at", accountId));
        result.put("learningTasks", rows("SELECT * FROM job_match_improvement_tasks WHERE account_id=? ORDER BY created_at", accountId));
        result.put("streamEvents", rows("SELECT * FROM job_match_stream_events WHERE account_id=? ORDER BY match_id,sequence_no", accountId));
        result.put("exports", rows("SELECT * FROM job_match_exports WHERE account_id=? ORDER BY created_at", accountId));
        result.put("reports", rows("SELECT * FROM match_reports WHERE account_id=? ORDER BY created_at", accountId));
        result.put("requirements", rows("SELECT * FROM match_requirement_items WHERE account_id=? ORDER BY report_id,sequence_no", accountId));
        result.put("adviceRuns", rows("SELECT * FROM match_ai_advice_runs WHERE account_id=? ORDER BY created_at", accountId));
        result.put("adviceItems", rows("SELECT * FROM match_advice_items WHERE account_id=? ORDER BY report_id,sequence_no", accountId));
        return result;
    }

    private java.util.List<Map<String, Object>> rows(String sql, String accountId) {
        return jdbc.queryForList(sql, accountId);
    }
}
