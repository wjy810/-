package com.jobproof.modules.aibilling.application;

import com.jobproof.shared.deletion.DeletionModuleHandler;
import com.jobproof.shared.export.AccountExportContributor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CommerceDataRights implements DeletionModuleHandler, AccountExportContributor {
    private final JdbcTemplate jdbc;
    private final BalanceWalletService wallets;

    public CommerceDataRights(JdbcTemplate jdbc, BalanceWalletService wallets) {
        this.jdbc = jdbc;
        this.wallets = wallets;
    }

    public String moduleCode() {
        return "commerce";
    }

    public String moduleKey() {
        return "commerce";
    }

    @Transactional
    public Result onAccountDeletion(String accountId) {
        wallets.lock(accountId);
        Integer held =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wallet_hold WHERE account_id=? AND status='HELD'",
                        Integer.class,
                        accountId);
        if (held != null && held > 0) return Result.restricted("仍有冻结金额，请等待业务结算后重试删除");
        for (String table :
                List.of(
                        "commerce_orders",
                        "ai_billing_record",
                        "wallet_hold",
                        "wallet_ledger",
                        "personal_channel_subscription",
                        "wallet_account",
                        "ai_usage_record"))
            jdbc.update("DELETE FROM " + table + " WHERE account_id=?", accountId);
        return Result.succeeded("钱包、交易和订阅记录已删除");
    }

    public Map<String, Object> contribute(String accountId) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String table :
                List.of(
                        "wallet_account",
                        "wallet_ledger",
                        "wallet_hold",
                        "commerce_orders",
                        "personal_channel_subscription",
                        "ai_billing_record",
                        "ai_usage_record"))
            result.put(
                    table,
                    jdbc.queryForList("SELECT * FROM " + table + " WHERE account_id=?", accountId));
        return result;
    }
}
