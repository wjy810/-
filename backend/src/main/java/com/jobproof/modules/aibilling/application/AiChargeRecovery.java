package com.jobproof.modules.aibilling.application;

import com.jobproof.modules.airesume.application.AiQuotaService;
import com.jobproof.shared.time.ClockPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiChargeRecovery {
    private final JdbcTemplate jdbc;
    private final AiQuotaService quotas;
    private final ClockPort clock;
    public AiChargeRecovery(JdbcTemplate jdbc, AiQuotaService quotas, ClockPort clock) {
        this.jdbc = jdbc; this.quotas = quotas; this.clock = clock;
    }
    /** Active recoverable tasks retain their reservation. Durable partial results determine settlement. */
    @Scheduled(fixedDelayString = "${jobproof.ai.billing.recovery-ms:60000}")
    public void recover() {
        var rows = jdbc.query("SELECT r.id,r.account_id FROM ai_quota_reservations r WHERE r.status='HELD' AND (r.expires_at<? OR EXISTS (SELECT 1 FROM async_tasks t WHERE (t.ai_reservation_id=r.id OR t.ai_quote_id=r.quote_id) AND t.status IN ('FAILED','CANCELLED'))) AND NOT EXISTS (SELECT 1 FROM async_tasks t WHERE (t.ai_reservation_id=r.id OR t.ai_quote_id=r.quote_id) AND t.status IN ('PENDING','RUNNING')) ORDER BY r.expires_at LIMIT 100",
                (r, n) -> new String[]{r.getString(1), r.getString(2)}, clock.now());
        for (var row : rows) quotas.release(row[1], row[0]);
    }
}
