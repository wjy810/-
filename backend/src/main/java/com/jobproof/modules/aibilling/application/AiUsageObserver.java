package com.jobproof.modules.aibilling.application;

import com.jobproof.modules.aigateway.port.AiCallObserver;
import com.jobproof.modules.aigateway.domain.AiGatewayModels.Usage;
import com.jobproof.shared.ai.AiExecutionContext;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiUsageObserver implements AiCallObserver {
    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    public AiUsageObserver(JdbcTemplate jdbc, ClockPort clock) { this.jdbc = jdbc; this.clock = clock; }
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String channelId, String model, Usage usage, String failureCode) {
        var context = AiExecutionContext.current();
        if (context == null || context.reservationId() == null || context.accountId() == null) return;
        jdbc.update("INSERT INTO ai_charge_attempts(id,reservation_id,account_id,channel_id,model_code,status,input_tokens,output_tokens,failure_code,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                Ids.newId(), context.reservationId(), context.accountId(), channelId, model,
                failureCode == null ? "SUCCEEDED" : "FAILED", usage == null ? 0 : usage.inputTokens(),
                usage == null ? 0 : usage.outputTokens(), failureCode, clock.now());
    }
}
