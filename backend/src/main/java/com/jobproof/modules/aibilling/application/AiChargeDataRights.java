package com.jobproof.modules.aibilling.application;

import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.export.AccountExportContributor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiChargeDataRights implements DeletionModuleHandler, AccountExportContributor {
    private final JdbcTemplate jdbc;
    private final BalanceWalletService wallets;
    public AiChargeDataRights(JdbcTemplate jdbc, BalanceWalletService wallets) { this.jdbc = jdbc; this.wallets = wallets; }
    public String moduleCode() { return "ai-charges"; }
    public String moduleKey() { return "ai-charges"; }
    @Transactional public Result onAccountDeletion(String accountId) {
        wallets.lock(accountId);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ai_quota_reservations WHERE account_id=? AND status='HELD'", Integer.class, accountId) > 0) return Result.restricted("AI 任务仍在执行，请结束任务后重试");
        jdbc.update("DELETE FROM ai_charge_successes WHERE reservation_id IN (SELECT id FROM ai_quota_reservations WHERE account_id=?)", accountId);
        for (String table : new String[]{"ai_charge_attempts", "ai_charge_quotes", "ai_channel_preferences", "ai_channel_commands", "ai_suggestion_results"}) jdbc.update("DELETE FROM " + table + " WHERE account_id=?", accountId);
        return Result.succeeded("AI 计费上下文已删除");
    }
    public Map<String, Object> contribute(String accountId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("quotes", jdbc.queryForList("SELECT id,operation_code,units,unit_price,price_version,payment_source,reservation_id,created_at FROM ai_charge_quotes WHERE account_id=?", accountId));
        result.put("channelCommands", jdbc.queryForList("SELECT operation,idempotency_key,result_id,created_at FROM ai_channel_commands WHERE account_id=?", accountId));
        result.put("suggestions", jdbc.queryForList("SELECT request_key,operation_code,result_json,created_at,completed_at FROM ai_suggestion_results WHERE account_id=?", accountId));
        result.put("attempts", jdbc.queryForList("SELECT * FROM ai_charge_attempts WHERE account_id=?", accountId));
        return result;
    }
}
