package com.jobproof.modules.aibilling.application;

import com.jobproof.shared.ai.AiExecutionContext;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiChargeService {
    private final JdbcTemplate jdbc;
    private final ClockPort clock;
    private final CommerceCatalogService catalog;
    private final BalanceWalletService wallets;
    private final AiOperationCatalog operations;

    public AiChargeService(JdbcTemplate jdbc, ClockPort clock, CommerceCatalogService catalog,
            BalanceWalletService wallets, AiOperationCatalog operations) {
        this.jdbc = jdbc; this.clock = clock; this.catalog = catalog; this.wallets = wallets; this.operations = operations;
    }

    @Transactional
    public Quote quote(String accountId, String path, String body, int remaining) {
        var operation = operations.resolve(accountId, path, body);
        if (operation == null || operation.units() == 0) return null;
        String personal = preference(accountId);
        if (personal != null) validatePersonal(accountId, personal);
        String source = personal != null ? "PERSONAL" : remaining >= operation.units() ? "QUOTA" : "WALLET";
        var price = catalog.prices().stream().filter(p -> p.code().equals(operation.code())).findFirst().orElse(null);
        BigDecimal unitPrice = "WALLET".equals(source) && price != null && price.enabled() ? price.amount() : BigDecimal.ZERO;
        String unavailable = "WALLET".equals(source) && (price == null || !price.enabled() || price.amount() == null)
                ? "该功能的余额价格尚未配置，请使用月度额度或个人通道" : null;
        BigDecimal balance = wallets.wallet(accountId).availableBalance();
        BigDecimal total = (unitPrice == null ? BigDecimal.ZERO : unitPrice).multiply(BigDecimal.valueOf(operation.units()));
        if (unavailable == null && "WALLET".equals(source) && balance.compareTo(total) < 0) unavailable = "余额不足，请充值后重试";
        String id = Ids.newId(); Instant expires = clock.now().plusSeconds(300);
        if (unavailable == null) {
            jdbc.update("INSERT INTO ai_charge_quotes(id,account_id,operation_code,units,unit_price,price_version,payment_source,personal_channel_id,request_path,payload_hash,expires_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, accountId, operation.code(), operation.units(), unitPrice, price == null ? 0 : price.version(), source,
                    personal, path, AiOperationCatalog.hash(body), expires, clock.now());
        }
        return new Quote(unavailable == null ? id : null, operation.code(), price == null ? operation.code() : price.name(),
                operation.units(), source, unitPrice, total, balance, remaining, expires, unavailable, personal);
    }

    public String preference(String accountId) {
        return jdbc.query("SELECT personal_channel_id FROM ai_channel_preferences WHERE account_id=?",
                (r, n) -> r.getString(1), accountId).stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    public void acceptHttp(String accountId) {
        var context = AiExecutionContext.current();
        if (context == null || context.quoteId() == null) return;
        Integer replay = jdbc.queryForObject("SELECT COUNT(*) FROM ai_suggestion_results WHERE account_id=? AND request_path=? AND payload_hash=? AND result_json IS NOT NULL", Integer.class, accountId, context.path(), context.payloadHash());
        if (replay != null && replay > 0) return;
        // Expiry prevents new execution, but must not hide an already committed response for this exact request.
        Integer completed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_charge_quotes q
                 WHERE q.id=? AND q.account_id=? AND q.request_path=? AND q.payload_hash=?
                   AND (EXISTS (SELECT 1 FROM async_tasks t WHERE t.ai_quote_id=q.id AND t.account_id=q.account_id
                                AND t.ai_request_path=q.request_path AND t.ai_payload_hash=q.payload_hash AND t.status='SUCCEEDED')
                     OR (q.operation_code='RESUME_CHAT' AND EXISTS (
                         SELECT 1 FROM ai_quota_reservations r
                           JOIN ai_resume_messages u ON u.account_id=r.account_id
                            AND r.request_id=CONCAT('chat:',u.conversation_id,':',u.client_message_id) AND u.role='USER'
                           JOIN ai_resume_messages a ON a.account_id=u.account_id AND a.conversation_id=u.conversation_id
                            AND a.parent_message_id=u.id AND a.role='ASSISTANT' AND a.status IN ('COMPLETED','FAILED','CANCELLED')
                          WHERE r.id=q.reservation_id AND r.account_id=q.account_id AND r.status IN ('SETTLED','RELEASED'))))
                """, Integer.class, context.quoteId(), accountId, context.path(), context.payloadHash());
        if (completed != null && completed > 0) return;
        Integer valid = jdbc.queryForObject("SELECT COUNT(*) FROM ai_charge_quotes WHERE id=? AND account_id=? AND request_path=? AND payload_hash=? AND expires_at>? AND (payment_source<>'WALLET' OR ?=TRUE)",
                Integer.class, context.quoteId(), accountId, context.path(), context.payloadHash(), clock.now(), context.paymentConfirmed());
        if (valid == null || valid == 0) {
            // An expired quote cannot authorize work, but a precisely bound running operation must
            // keep its identity until it reaches a durable terminal result or releases its reservation.
            Integer running = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM ai_charge_quotes q
                     WHERE q.id=? AND q.account_id=? AND q.request_path=? AND q.payload_hash=?
                       AND (EXISTS (SELECT 1 FROM ai_quota_reservations r WHERE r.id=q.reservation_id
                                    AND r.quote_id=q.id AND r.account_id=q.account_id AND r.status='HELD')
                         OR EXISTS (SELECT 1 FROM async_tasks t WHERE t.ai_quote_id=q.id AND t.account_id=q.account_id
                                    AND t.ai_request_path=q.request_path AND t.ai_payload_hash=q.payload_hash
                                    AND t.status IN ('PENDING','RUNNING')))
                    """, Integer.class, context.quoteId(), accountId, context.path(), context.payloadHash());
            if (running != null && running > 0) throw AppException.conflict("AI_REQUEST_RUNNING", "该请求正在处理中，请稍后查看结果");
            throw AppException.conflict("AI_QUOTE_CHANGED", "报价过期、内容变化或尚未确认付款，请重新确认");
        }
    }

    public void validatePersonal(String accountId, String channelId) {
        Integer entitled = jdbc.queryForObject("SELECT COUNT(*) FROM personal_channel_subscription WHERE account_id=? AND status='ACTIVE' AND current_period_end>?", Integer.class, accountId, clock.now());
        Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM ai_channel WHERE id=? AND owner_account_id=? AND scope='PERSONAL' AND status='ACTIVE'", Integer.class, channelId, accountId);
        if (entitled == null || entitled == 0) throw AppException.conflict("PERSONAL_SUBSCRIPTION_REQUIRED", "个人通道订阅已到期，请续费或明确切换平台通道");
        if (owned == null || owned == 0) throw AppException.conflict("PERSONAL_CHANNEL_UNAVAILABLE", "所选个人通道不可用，请检测启用或重新选择");
    }

    /** Called in the reservation transaction after locking the account. */
    public Authorization authorize(String accountId, String operation, int units) {
        var context = AiExecutionContext.current();
        if (context == null || context.quoteId() == null) {
            if (preference(accountId) != null) throw AppException.conflict("AI_QUOTE_REQUIRED", "请重新确认使用个人通道或平台额度");
            return new Authorization(null, "QUOTA", BigDecimal.ZERO, null);
        }
        var rows = jdbc.query("SELECT * FROM ai_charge_quotes WHERE id=? AND account_id=? FOR UPDATE", (r, n) -> new StoredQuote(
                r.getString("id"), r.getString("operation_code"), r.getInt("units"), r.getString("payment_source"),
                r.getBigDecimal("unit_price"), r.getString("personal_channel_id"), r.getString("request_path"),
                r.getString("payload_hash"), r.getTimestamp("expires_at").toInstant(), r.getString("reservation_id")), context.quoteId(), accountId);
        if (rows.isEmpty()) throw AppException.conflict("AI_QUOTE_INVALID", "报价不存在，请重新确认");
        var q = rows.getFirst();
        boolean queued = jdbc.queryForObject("SELECT COUNT(*) FROM async_tasks WHERE ai_quote_id=? AND account_id=? AND status IN ('PENDING','RUNNING')", Integer.class, q.id(), accountId) > 0;
        if (!q.operation().equals(AiOperationCatalog.normalize(operation)) || q.units() != units
                || !q.path().equals(context.path()) || !q.hash().equals(context.payloadHash())
                || (!q.expiresAt().isAfter(clock.now()) && !queued)) {
            throw AppException.conflict("AI_QUOTE_CHANGED", "请求内容或报价已变化，请重新确认");
        }
        if (q.reservationId() != null) throw AppException.conflict("AI_QUOTE_USED", "本次请求已提交，请查看任务结果");
        if (!Objects.equals(preference(accountId), q.personalChannelId())) throw AppException.conflict("AI_CHANNEL_CHANGED", "通道选择已变化，请重新确认使用方式");
        if ("WALLET".equals(q.source()) && !context.paymentConfirmed()) throw AppException.conflict("AI_PAYMENT_CONFIRMATION_REQUIRED", "使用余额前需要确认本次价格");
        if (q.personalChannelId() != null) validatePersonal(accountId, q.personalChannelId());
        return new Authorization(q.id(), q.source(), q.unitPrice(), q.personalChannelId());
    }

    public void bind(Authorization authorization, String reservationId) {
        if (authorization.quoteId() != null) jdbc.update("UPDATE ai_charge_quotes SET reservation_id=?,payment_confirmed=? WHERE id=?", reservationId, "WALLET".equals(authorization.source()), authorization.quoteId());
    }

    public java.util.List<java.util.Map<String, Object>> attempts(com.jobproof.shared.auth.CurrentAccount current, String usageId) {
        var owners = jdbc.queryForList("SELECT account_id FROM ai_usage_record WHERE id=?", String.class, usageId);
        if (owners.isEmpty() || (!owners.getFirst().equals(current.accountId()) && !"ADMIN".equals(current.role())))
            throw AppException.forbidden("OBJECT_FORBIDDEN", "调用记录不存在或无权访问");
        return jdbc.queryForList("SELECT id,channel_id,model_code,status,input_tokens,output_tokens,failure_code,created_at FROM ai_charge_attempts WHERE reservation_id=? ORDER BY created_at,id", usageId);
    }
    private record StoredQuote(String id, String operation, int units, String source, BigDecimal unitPrice,
            String personalChannelId, String path, String hash, Instant expiresAt, String reservationId) {}
    public record Authorization(String quoteId, String source, BigDecimal unitPrice, String personalChannelId) {}
    public record Quote(String id, String operation, String name, int units, String source, BigDecimal unitPrice,
            BigDecimal total, BigDecimal availableBalance, int remainingQuota, Instant expiresAt, String unavailable,
            String personalChannelId) {}
}
