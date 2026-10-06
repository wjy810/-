package com.jobproof.modules.datarights.application;

import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.export.AccountExportContributor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Keeps data-rights guarantees for JD records created before the job-data feature was retired. */
@Component
public class RetiredJobDataLifecycle implements DeletionModuleHandler, AccountExportContributor {
    private final JdbcTemplate jdbc;

    public RetiredJobDataLifecycle(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String moduleCode() {
        return "job";
    }

    @Override
    public String moduleKey() {
        return "job";
    }

    @Override
    @Transactional
    public Result onAccountDeletion(String accountId) {
        try {
            jdbc.update("DELETE FROM job_versions WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM jobs WHERE account_id=?", accountId);
            jdbc.update("DELETE FROM jd_snapshots WHERE account_id=?", accountId);
            return Result.succeeded("已退役岗位资料的历史原文与版本已清理");
        } catch (RuntimeException exception) {
            return Result.failed("已退役岗位资料的历史数据清理失败，删除申请保持可重试");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> contribute(String accountId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("retired", true);
        result.put("items", jdbc.queryForList(
                "SELECT id,title,company_name,created_at,updated_at FROM jobs WHERE account_id=? ORDER BY updated_at",
                accountId));
        result.put("versions", jdbc.queryForList(
                "SELECT * FROM job_versions WHERE account_id=? ORDER BY created_at", accountId));
        return result;
    }
}
