package com.jobproof.modules.airesume.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.aibilling.application.AiOperationCatalog;
import com.jobproof.modules.aibilling.application.BalanceWalletService;
import com.jobproof.shared.ai.AiExecutionContext;
import com.jobproof.shared.error.AppException;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Admission survives disconnects; only the short final transaction saves and charges a result. */
@Service
public class AiSuggestionResults {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AiQuotaService quota;
    private final BalanceWalletService wallets;
    private final ClockPort clock;
    private final TransactionTemplate tx;
    private final ThreadLocal<Ticket> active = new ThreadLocal<>();
    public AiSuggestionResults(JdbcTemplate jdbc, ObjectMapper json, AiQuotaService quota,
            BalanceWalletService wallets, ClockPort clock, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.json=json; this.quota=quota; this.wallets=wallets; this.clock=clock;
        this.tx=new TransactionTemplate(manager);
    }
    public <T> T execute(String account, String key, String operation, Object input, Class<T> type, Supplier<T> generation) {
        var context=AiExecutionContext.current();
        String path=context==null||context.path()==null ? operation : context.path();
        String hash=context==null||context.payloadHash()==null ? AiOperationCatalog.hash(encode(input)) : context.payloadHash();
        Ticket ticket=new Ticket(account,key,operation,path,hash,Ids.newId());
        String replay=tx.execute(status -> {
            wallets.lock(account);
            var rows=jdbc.queryForList("SELECT * FROM ai_suggestion_results WHERE account_id=? AND request_key=? FOR UPDATE",account,key);
            if (!rows.isEmpty()) {
                var row=rows.getFirst();
                if (!Objects.equals(operation,row.get("operation_code")) || !Objects.equals(path,row.get("request_path")) || !Objects.equals(hash,row.get("payload_hash")))
                    throw AppException.conflict("AI_REQUEST_CHANGED","相同请求编号的业务内容不一致");
                if (row.get("result_json")!=null) return row.get("result_json").toString();
                var until=(java.sql.Timestamp)row.get("lease_until");
                if (until!=null && until.toInstant().isAfter(clock.now())) throw AppException.conflict("AI_REQUEST_RUNNING","相同请求仍在处理中，请稍后重试");
                jdbc.update("UPDATE ai_suggestion_results SET owner_token=?,lease_until=? WHERE account_id=? AND request_key=?",ticket.owner(),clock.now().plusSeconds(900),account,key);
            } else jdbc.update("INSERT INTO ai_suggestion_results(account_id,request_key,operation_code,request_path,payload_hash,owner_token,lease_until,created_at) VALUES(?,?,?,?,?,?,?,?)",account,key,operation,path,hash,ticket.owner(),clock.now().plusSeconds(900),clock.now());
            return null;
        });
        if (replay!=null) return decode(replay,type);
        Ticket previous=active.get(); active.set(ticket);
        try { return generation.get(); }
        finally {
            active.set(previous);
            tx.executeWithoutResult(status -> jdbc.update("UPDATE ai_suggestion_results SET owner_token=NULL,lease_until=NULL WHERE account_id=? AND request_key=? AND owner_token=?",account,key,ticket.owner()));
        }
    }
    public <T> T complete(String account, String reservation, T response) {
        Ticket ticket=Objects.requireNonNull(active.get(),"Missing suggestion admission");
        return tx.execute(status -> {
            wallets.lock(account);
            var rows=jdbc.queryForList("SELECT owner_token FROM ai_suggestion_results WHERE account_id=? AND request_key=? FOR UPDATE",account,ticket.key());
            if (rows.isEmpty() || !ticket.owner().equals(rows.getFirst().get("owner_token"))) throw AppException.conflict("AI_REQUEST_RUNNING","请求已由其他执行接管");
            var settled=quota.settle(account,reservation,1);
            ObjectNode value=json.valueToTree(response);
            value.put("remainingQuota",settled.remainingUnits());
            String payload=encode(value);
            jdbc.update("UPDATE ai_suggestion_results SET result_json=?,reservation_id=?,completed_at=?,owner_token=NULL,lease_until=NULL WHERE account_id=? AND request_key=?",payload,reservation,clock.now(),account,ticket.key());
            @SuppressWarnings("unchecked") T result=(T)decode(payload,response.getClass());
            return result;
        });
    }
    private String encode(Object value) { try {return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);} }
    private <T> T decode(String value,Class<T> type) {try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException(e);} }
    private record Ticket(String account,String key,String operation,String path,String hash,String owner) {}
}
