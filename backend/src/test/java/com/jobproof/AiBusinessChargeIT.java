package com.jobproof;

import static org.assertj.core.api.Assertions.*;
import com.jobproof.modules.aibilling.application.*;
import com.jobproof.modules.airesume.application.AiQuotaService;
import com.jobproof.shared.ai.AiExecutionContext;
import com.jobproof.shared.id.Ids;
import com.jobproof.shared.time.ClockPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = {JobProofApiApplication.class, AiBusinessChargeIT.TimeConfig.class},
        properties = {"jobproof.worker.in-process=false", "jobproof.worker.poll-ms=600000"})
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class AiBusinessChargeIT {
    static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.parse("2026-10-31T15:59:00Z"));
    static final String PATH = "/api/v1/ai-resume/conversations/test/cards/test/summary-suggestions";
    static final String BODY = "{\"requestId\":\"business-one\"}";
    @TestConfiguration static class TimeConfig {
        @Bean @Primary ClockPort testChargeClock() { return NOW::get; }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired AiQuotaService quotas;
    @Autowired AiChargeService charges;
    @Autowired BalanceWalletService wallets;
    @Autowired AiChargeRecovery recovery;
    @Autowired PlatformTransactionManager tx;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.jobproof.modules.task.application.TaskService tasks;

    @Autowired com.jobproof.modules.airesume.application.AiSuggestionResults suggestionResults;
    @Autowired AiChargeDataRights dataRights;
    @Autowired com.jobproof.modules.airesume.application.AiResumeGenerationService generation;
    @Autowired com.jobproof.modules.airesume.application.AiResumeWorkbenchService workbench;
    record SavedSuggestion(String text, int remainingQuota) {}

    @Test void accountDataRightsIncludeAndEraseSuggestionsAndChannelCommandsOnlyForTheirOwner() {
        String owner=account(),other=account();
        for(String id:java.util.List.of(owner,other)) {
            suggestionResults.execute(id,"export-result","RESUME_SUMMARY",BODY,SavedSuggestion.class,()-> {
                var r=quotas.reserve(id,"export-result","RESUME_SUMMARY",1);
                return suggestionResults.complete(id,r.id(),new SavedSuggestion("owned output",0));
            });
            jdbc.update("INSERT INTO ai_channel_commands(account_id,operation,idempotency_key,request_hash,result_id,created_at) VALUES(?,'CREATE','command','hash','channel',?)",id,NOW.get());
        }
        assertThat((java.util.List<?>)dataRights.contribute(owner).get("suggestions")).hasSize(1);
        assertThat((java.util.List<?>)dataRights.contribute(owner).get("channelCommands")).hasSize(1);
        dataRights.onAccountDeletion(owner);
        assertThat((java.util.List<?>)dataRights.contribute(owner).get("suggestions")).isEmpty();
        assertThat((java.util.List<?>)dataRights.contribute(owner).get("channelCommands")).isEmpty();
        assertThat((java.util.List<?>)dataRights.contribute(other).get("suggestions")).hasSize(1);
        assertThat((java.util.List<?>)dataRights.contribute(other).get("channelCommands")).hasSize(1);
    }

    @Test void suggestionResultSurvivesExpiredQuoteAndRejectsChangedPayload() {
        String id=account(); exhaust(id); var quote=quote(id);
        SavedSuggestion original;
        try(var scope=use(id,quote,true)) {
            original=suggestionResults.execute(id,"summary:test:business-one","RESUME_SUMMARY",BODY,SavedSuggestion.class,()-> {
                var r=quotas.reserve(id,"summary:test:business-one","RESUME_SUMMARY",1);
                return suggestionResults.complete(id,r.id(),new SavedSuggestion("durable output",0));
            });
        }
        NOW.set(NOW.get().plusSeconds(600));
        try(var scope=use(id,quote,true)) {
            charges.acceptHttp(id);
            var replay=suggestionResults.execute(id,"summary:test:business-one","RESUME_SUMMARY",BODY,SavedSuggestion.class,()-> {throw new AssertionError("provider replayed");});
            assertThat(replay).isEqualTo(original);
        }
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("98");
        try(var scope=AiExecutionContext.use(new AiExecutionContext.State(quote.id(),PATH,AiOperationCatalog.hash("changed"),true,id,null,null))) {
            assertThatThrownBy(()->suggestionResults.execute(id,"summary:test:business-one","RESUME_SUMMARY","changed",SavedSuggestion.class,()->null)).hasMessageContaining("内容不一致");
        }
    }

    @Test void completedChatReplaysAfterQuoteExpiryWithoutAnotherGenerationOrCharge() {
        NOW.set(Instant.parse("2026-10-21T05:00:00Z"));
        String id=account(), conversation=Ids.newId();
        jdbc.update("INSERT INTO ai_resume_conversations(id,account_id,master_id,active_branch_id,status,onboarding_stage,last_sequence,version_no,created_at,updated_at) VALUES(?,?,?,?,'ACTIVE','TARGET_JOB',0,0,?,?)",
                conversation,id,Ids.newId(),Ids.newId(),NOW.get(),NOW.get());
        String path="/api/v1/ai-resume/conversations/"+conversation+"/messages/stream";
        String body="{\"clientMessageId\":\"chat-one\",\"text\":\"same question\"}";
        var quote=charges.quote(id,path,body,quotas.current(id).remainingUnits());
        var context=new AiExecutionContext.State(quote.id(),path,AiOperationCatalog.hash(body),false,id,null,null);
        String response;
        int before=quotas.current(id).remainingUnits();
        try(var scope=AiExecutionContext.use(context)) {
            var prepared=generation.prepare(id,conversation,"chat-one","same question");
            response=generation.execute(prepared,"TEST",cancel->new com.jobproof.modules.airesume.application.AiResumeGenerationService.GeneratedReply<String>("committed answer","synthetic",0,0,null)).message().id();
        }
        NOW.set(NOW.get().plusSeconds(600));
        try(var scope=AiExecutionContext.use(context)) {
            charges.acceptHttp(id);
            var replay=generation.execute(generation.prepare(id,conversation,"chat-one","same question"),"TEST",cancel->{throw new AssertionError("provider called twice");});
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.message().id()).isEqualTo(response);
            var events = new java.util.ArrayList<String>();
            workbench.respondStreaming(new com.jobproof.shared.auth.CurrentAccount(id,"","SEEKER",""),conversation,"chat-one","same question",(type,payload)->events.add(type));
            assertThat(events).containsExactly("user.accepted","assistant.completed");
        }
        assertThat(quotas.current(id).remainingUnits()).isEqualTo(before-1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_quota_reservations WHERE account_id=?",Integer.class,id)).isEqualTo(1);
        try(var scope=AiExecutionContext.use(new AiExecutionContext.State(quote.id(),path,AiOperationCatalog.hash("changed"),false,id,null,null))) {
            assertThatThrownBy(()->charges.acceptHttp(id)).hasMessageContaining("报价过期");
        }
        String other=account();
        try(var scope=AiExecutionContext.use(context)) {
            assertThatThrownBy(()->charges.acceptHttp(other)).hasMessageContaining("报价过期");
        }
    }

    @Test void onlyPersistedSuccessfulTaskAllowsExpiredExactRequestReplay() {
        String id=account(); var quote=quote(id); String taskId;
        try(var scope=use(id,quote,false)) {taskId=tasks.create(id,"REPLAY_TEST","stable-request","{}").id();}
        NOW.set(NOW.get().plusSeconds(600));
        try(var scope=use(id,quote,false)) {
            assertThatThrownBy(()->charges.acceptHttp(id)).isInstanceOfSatisfying(com.jobproof.shared.error.AppException.class,
                    error->assertThat(error.reason()).isEqualTo("AI_REQUEST_RUNNING"));
            assertThat(tasks.markSucceeded(taskId,"saved-result","{\"result\":\"durable\"}")).isTrue();
            charges.acceptHttp(id);
            assertThat(tasks.create(id,"REPLAY_TEST","stable-request","{}").id()).isEqualTo(taskId);
        }
        try(var scope=AiExecutionContext.use(new AiExecutionContext.State(quote.id(),PATH+"/changed",AiOperationCatalog.hash(BODY),false,id,null,null))) {
            assertThatThrownBy(()->charges.acceptHttp(id)).hasMessageContaining("报价过期");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM async_tasks WHERE account_id=?",Integer.class,id)).isEqualTo(1);
    }

    @Test void expiredPreciselyBoundInFlightRequestsRetainIdentityWithoutAuthorizingExecution() {
        NOW.set(Instant.parse("2026-10-21T05:00:00Z"));
        String id=account(), other=account(); var heldQuote=quote(id); String reservation;
        try(var scope=use(id,heldQuote,false)) {reservation=quotas.reserve(id,"long-running","RESUME_SUMMARY",1).id();}
        NOW.set(NOW.get().plusSeconds(600));
        try(var scope=use(id,heldQuote,false)) {
            assertThatThrownBy(()->charges.acceptHttp(id)).isInstanceOfSatisfying(com.jobproof.shared.error.AppException.class,
                    error->assertThat(error.reason()).isEqualTo("AI_REQUEST_RUNNING"));
            assertThatThrownBy(()->charges.acceptHttp(other)).hasMessageContaining("报价过期");
        }
        for(var invalid:java.util.List.of(
                new AiExecutionContext.State(heldQuote.id(),PATH+"/wrong",AiOperationCatalog.hash(BODY),false,id,null,null),
                new AiExecutionContext.State(heldQuote.id(),PATH,AiOperationCatalog.hash("changed"),false,id,null,null),
                new AiExecutionContext.State(Ids.newId(),PATH,AiOperationCatalog.hash(BODY),false,id,null,null))) {
            try(var scope=AiExecutionContext.use(invalid)) {assertThatThrownBy(()->charges.acceptHttp(id)).hasMessageContaining("报价过期");}
        }
        quotas.release(id,reservation);
        try(var scope=use(id,heldQuote,false)) {assertThatThrownBy(()->charges.acceptHttp(id)).hasMessageContaining("报价过期");}
        var queuedQuote=quote(id); String task;
        try(var scope=use(id,queuedQuote,false)) {task=tasks.create(id,"LONG_RUNNING_TEST","queued-one","{}").id();}
        NOW.set(NOW.get().plusSeconds(600));
        try(var scope=use(id,queuedQuote,false)) {
            assertThatThrownBy(()->charges.acceptHttp(id)).isInstanceOfSatisfying(com.jobproof.shared.error.AppException.class,
                    error->assertThat(error.reason()).isEqualTo("AI_REQUEST_RUNNING"));
            tasks.markFailed(task,"SYNTHETIC_FAILURE","no persisted result");
            assertThatThrownBy(()->charges.acceptHttp(id)).hasMessageContaining("报价过期");
        }
        var neverAdmitted=quote(id); NOW.set(NOW.get().plusSeconds(600));
        try(var scope=use(id,neverAdmitted,false)) {assertThatThrownBy(()->charges.acceptHttp(id)).hasMessageContaining("报价过期");}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_quota_reservations WHERE account_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(quotas.current(id).heldUnits()).isZero();
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("100");
    }

    @Test void concurrentForegroundRequestsAdmitOneReservationOnly() throws Exception {
        String id=account();
        var start=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<String> attempt=()->{
                start.await();
                try {return quotas.reserveExclusive(id,"foreground-one","MOCK_INTERVIEW_QUESTIONS",1).id();}
                catch(com.jobproof.shared.error.AppException error) {return error.reason();}
            };
            var one=pool.submit(attempt); var two=pool.submit(attempt); start.countDown();
            var results=java.util.List.of(one.get(5,TimeUnit.SECONDS),two.get(5,TimeUnit.SECONDS));
            assertThat(results.stream().filter("AI_REQUEST_RUNNING"::equals)).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_quota_reservations WHERE account_id=?",Integer.class,id)).isEqualTo(1);
            String reservation=results.stream().filter(value->!value.equals("AI_REQUEST_RUNNING")).findFirst().orElseThrow();
            quotas.release(id,reservation);
            assertThatThrownBy(()->quotas.reserveExclusive(id,"foreground-one","MOCK_INTERVIEW_QUESTIONS",1)).hasMessageContaining("已处理");
        } finally {pool.shutdownNow();}
    }

    @Test void concurrentSuggestionAdmissionNeverInvokesSecondGeneration() throws Exception {
        String id=account(); var entered=new CountDownLatch(1); var finish=new CountDownLatch(1);
        var pool=Executors.newSingleThreadExecutor();
        try {
            var first=pool.submit(()->suggestionResults.execute(id,"concurrent","RESUME_SUMMARY",BODY,SavedSuggestion.class,()-> {
                entered.countDown();
                try { if(!finish.await(10,TimeUnit.SECONDS)) throw new AssertionError("timeout"); } catch(InterruptedException e) {throw new RuntimeException(e);}
                var r=quotas.reserve(id,"concurrent","RESUME_SUMMARY",1);
                return suggestionResults.complete(id,r.id(),new SavedSuggestion("one",0));
            }));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->suggestionResults.execute(id,"concurrent","RESUME_SUMMARY",BODY,SavedSuggestion.class,()-> {throw new AssertionError("duplicate provider");})).hasMessageContaining("处理中");
            finish.countDown(); assertThat(first.get(10,TimeUnit.SECONDS).text()).isEqualTo("one");
        } finally {finish.countDown();pool.shutdownNow();}
    }

    @Test void suggestionCompletionRollsBackPayloadAndChargeTogetherAndSurvivesLaterTelemetryFailure() {
        String id=account(); exhaust(id); var q=quote(id); AtomicReference<String> reservation=new AtomicReference<>();
        try(var scope=use(id,q,true)) {
            assertThatThrownBy(()->suggestionResults.execute(id,"rollback-result","RESUME_SUMMARY",BODY,SavedSuggestion.class,()-> {
                var r=quotas.reserve(id,"rollback-result","RESUME_SUMMARY",1); reservation.set(r.id());
                return new TransactionTemplate(tx).execute(status->{
                    suggestionResults.complete(id,r.id(),new SavedSuggestion("not committed",0));
                    throw new IllegalStateException("persistence failure");
                });
            })).hasMessage("persistence failure");
        }
        assertThat(jdbc.queryForObject("SELECT result_json FROM ai_suggestion_results WHERE account_id=? AND request_key='rollback-result'",String.class,id)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_billing_record WHERE account_id=?",Integer.class,id)).isZero();
        quotas.release(id,reservation.get());
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("100");
        var next=quote(id);
        try(var scope=use(id,next,true)) {
            assertThatThrownBy(()->suggestionResults.execute(id,"telemetry-result","RESUME_SUMMARY",BODY,SavedSuggestion.class,()-> {
                var r=quotas.reserve(id,"telemetry-result","RESUME_SUMMARY",1);
                suggestionResults.complete(id,r.id(),new SavedSuggestion("committed",0));
                throw new IllegalStateException("telemetry failure");
            })).hasMessage("telemetry failure");
            assertThat(suggestionResults.execute(id,"telemetry-result","RESUME_SUMMARY",BODY,SavedSuggestion.class,()->{throw new AssertionError("provider replayed");}).text()).isEqualTo("committed");
        }
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("98");
    }

    @Test void freeNameExtractionDoesNotRequirePersonalSubscriptionOrConfiguredPrice() {
        String id=account(); exhaust(id);
        for(String type:java.util.List.of("certificate","honor")) {
            String path="/api/v1/ai-resume/conversations/test/cards/test/"+type+"-suggestions";
            assertThat(charges.quote(id,path,"{\"phase\":\" names \"}",0)).isNull();
            assertThat(charges.quote(id,path,"{}",0)).isNull();
        }
    }

    @BeforeEach void reset() {
        NOW.set(Instant.parse("2026-10-31T15:59:00Z"));
        AiExecutionContext.clear();
        jdbc.update("UPDATE commerce_prices SET amount=2,enabled=TRUE,version=1 WHERE code='RESUME_SUMMARY'");
    }
    @AfterEach void cleanup() { AiExecutionContext.clear(); }
    String account() {
        String id = Ids.newId();
        jdbc.update("INSERT INTO accounts(id,email,password_hash,status,role,created_at,updated_at,password_changed_at) VALUES(?,?,'test','ACTIVE','SEEKER',?,?,?)", id, id + "@example.test", NOW.get(), NOW.get(), NOW.get());
        wallets.credit(id, "seed", new BigDecimal("100"));
        return id;
    }
    void exhaust(String id) { quotas.current(id); jdbc.update("UPDATE ai_quota_accounts SET used_units=granted_units WHERE account_id=?", id); }
    AiChargeService.Quote quote(String id) { return charges.quote(id, PATH, BODY, quotas.current(id).remainingUnits()); }
    AiExecutionContext.Scope use(String id, AiChargeService.Quote quote, boolean paid) {
        return AiExecutionContext.use(new AiExecutionContext.State(quote.id(), PATH, AiOperationCatalog.hash(BODY), paid, id, null, quote.personalChannelId()));
    }
    @Test void allowanceAlwaysWinsAndNoBalanceChanges() {
        String id=account(); var quote=quote(id); assertThat(quote.source()).isEqualTo("QUOTA");
        int before=quotas.current(id).remainingUnits();
        try(var scope=use(id,quote,false)) { var r=quotas.reserve(id,"one","RESUME_SUMMARY",1); quotas.settle(id,r.id(),1); }
        assertThat(quotas.current(id).remainingUnits()).isEqualTo(before-1);
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("100");
    }
    @Test void exhaustedOldClientAndUnconfirmedQuoteCannotCharge() {
        String id=account(); exhaust(id);
        assertThatThrownBy(()->quotas.reserve(id,"legacy","RESUME_SUMMARY",1)).hasMessageContaining("AI 次数不足");
        var quote=quote(id);
        try(var scope=use(id,quote,false)) { assertThatThrownBy(()->quotas.reserve(id,"new","RESUME_SUMMARY",1)).hasMessageContaining("确认"); }
        assertThat(wallets.wallet(id).heldBalance()).isZero();
    }
    @Test void confirmedSnapshotFreezesAndConcurrentSettlementOnlyChargesOnce() throws Exception {
        String id=account(); exhaust(id); var quote=quote(id); String reservation;
        jdbc.update("UPDATE commerce_prices SET amount=9,version=2 WHERE code='RESUME_SUMMARY'");
        try(var scope=use(id,quote,true)) { reservation=quotas.reserve(id,"paid","RESUME_SUMMARY",1).id(); }
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("98");
        assertThat(wallets.wallet(id).heldBalance()).isEqualByComparingTo("2");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->quotas.settle(id,reservation,1)); var b=pool.submit(()->quotas.settle(id,reservation,1));
            a.get(10,TimeUnit.SECONDS); b.get(10,TimeUnit.SECONDS);
        }
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("98");
        assertThat(wallets.wallet(id).heldBalance()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_billing_record WHERE account_id=?",Integer.class,id)).isEqualTo(1);
        try(var scope=use(id,quote,true)) { assertThatThrownBy(()->quotas.reserve(id,"paid","RESUME_SUMMARY",1)).hasMessageContaining("已处理"); }
    }
    @Test void failedTaskReleasesAndSuccessCannotBeRefundedByLaterCancel() {
        String id=account(); exhaust(id); var quote=quote(id);
        try(var scope=use(id,quote,true)) { var r=quotas.reserve(id,"failed","RESUME_SUMMARY",1); quotas.release(id,r.id()); quotas.release(id,r.id()); }
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("100");
        var second=quote(id);
        try(var scope=use(id,second,true)) { var r=quotas.reserve(id,"saved","RESUME_SUMMARY",1); quotas.settle(id,r.id(),1); quotas.release(id,r.id()); }
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("98");
    }
    @Test void tamperingExpiredQuoteAndReusingDifferentRequestAreRejected() {
        String id=account(); exhaust(id); var quote=quote(id);
        try(var scope=AiExecutionContext.use(new AiExecutionContext.State(quote.id(),PATH,AiOperationCatalog.hash("changed"),true,id,null,null))) {
            assertThatThrownBy(()->quotas.reserve(id,"changed","RESUME_SUMMARY",1)).hasMessageContaining("变化");
        }
        try(var scope=use(id,quote,true)) {
            var r=quotas.reserve(id,"original","RESUME_SUMMARY",1);
            assertThatThrownBy(()->quotas.reserve(id,"other","RESUME_SUMMARY",1)).hasMessageContaining("已提交");
            quotas.release(id,r.id());
        }
        var expired=quote(id); NOW.set(NOW.get().plusSeconds(301));
        try(var scope=use(id,expired,true)) { assertThatThrownBy(()->quotas.reserve(id,"expired","RESUME_SUMMARY",1)).hasMessageContaining("变化"); }
    }
    @Test void crossMonthSettlementUsesOriginalAllowance() {
        String id=account(); var old=quotas.current(id); var r=quotas.reserve(id,"cross-month","RESUME_SUMMARY",1);
        NOW.set(NOW.get().plusSeconds(120)); var next=quotas.current(id); quotas.settle(id,r.id(),1);
        assertThat(quotas.current(id).heldUnits()).isZero(); assertThat(quotas.current(id).usedUnits()).isZero();
        assertThat(jdbc.queryForObject("SELECT used_units FROM ai_quota_accounts WHERE id=?",Integer.class,old.id())).isEqualTo(1);
        assertThat(old.id()).isNotEqualTo(next.id());
    }
    @Test void durableBatchUnitSettlesAfterCrashAndRollbackLeavesNoUnit() {
        String id=account(); exhaust(id); var quote=quote(id);
        jdbc.update("UPDATE ai_charge_quotes SET units=3 WHERE id=?",quote.id()); String r;
        try(var scope=use(id,quote,true)) { r=quotas.reserve(id,"batch","RESUME_SUMMARY",3).id(); }
        new TransactionTemplate(tx).executeWithoutResult(status->quotas.successfulUnit(id,r,"answer-1"));
        assertThatThrownBy(()->new TransactionTemplate(tx).executeWithoutResult(status->{quotas.successfulUnit(id,r,"answer-2");throw new IllegalStateException("rollback");})).hasMessage("rollback");
        jdbc.update("UPDATE ai_quota_reservations SET expires_at=? WHERE id=?",NOW.get().minusSeconds(1),r);
        recovery.recover();
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("98");
        assertThat(wallets.wallet(id).heldBalance()).isZero();
        assertThat(jdbc.queryForObject("SELECT settled_units FROM ai_quota_reservations WHERE id=?",Integer.class,r)).isEqualTo(1);
    }
    @Test void personalChannelBypassesBothBalancesAndExpiresWithoutFallback() {
        String id=account(); String channel=Ids.newId(); Instant now=NOW.get();
        jdbc.update("INSERT INTO ai_channel(id,scope,owner_account_id,name,provider_code,base_url,protocol,api_key_ciphertext,api_key_masked,channel_rate,status,version_no,created_at,updated_at) VALUES(?,'PERSONAL',?,'Own','','https://provider.example','OPENAI_CHAT','test','***',1,'ACTIVE',0,?,?)",channel,id,now,now);
        jdbc.update("INSERT INTO personal_channel_subscription(id,account_id,status,price,currency,current_period_start,current_period_end,auto_renew,idempotency_key,version_no,created_at,updated_at) VALUES(?,?,'ACTIVE',5,'CNY',?,?,FALSE,?,0,?,?)",Ids.newId(),id,now,now.plusSeconds(3600),Ids.newId(),now,now);
        jdbc.update("INSERT INTO ai_channel_preferences(account_id,personal_channel_id,version_no) VALUES(?,?,0)",id,channel);
        int before=quotas.current(id).remainingUnits(); var quote=quote(id); assertThat(quote.source()).isEqualTo("PERSONAL");
        try(var scope=use(id,quote,false)) { var r=quotas.reserve(id,"own","RESUME_SUMMARY",1);quotas.settle(id,r.id(),1); }
        assertThat(quotas.current(id).remainingUnits()).isEqualTo(before);
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("100");
        NOW.set(now.plusSeconds(3601)); assertThatThrownBy(()->quote(id)).hasMessageContaining("订阅已到期");
    }
    @Test void unsetPriceReturnsUnavailableWithoutCreatingCharge() {
        String id=account(); exhaust(id); jdbc.update("UPDATE commerce_prices SET amount=NULL,enabled=FALSE WHERE code='RESUME_SUMMARY'");
        var quote=quote(id); assertThat(quote.id()).isNull(); assertThat(quote.unavailable()).contains("尚未配置");
        assertThat(wallets.wallet(id).heldBalance()).isZero();
    }
    @Test void connectionsUseReadCommittedWithExplicitFinancialLocks() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getTransactionIsolation()).isEqualTo(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        }
    }
    @Test void activeTaskRetainsExpiredHoldAndCancelledTaskReleasesItImmediately() {
        String id=account(); exhaust(id); var q=quote(id); String taskId; String reservation;
        try(var scope=use(id,q,true)) { taskId=tasks.create(id,"AI_BILLING_TEST",Ids.newId(),"{}").id(); }
        try(var scope=tasks.aiScope(taskId)) { reservation=quotas.reserve(id,"async-one","RESUME_SUMMARY",1).id(); }
        assertThat(jdbc.queryForObject("SELECT ai_reservation_id FROM async_tasks WHERE id=?",String.class,taskId)).isEqualTo(reservation);
        jdbc.update("UPDATE ai_quota_reservations SET expires_at=? WHERE id=?",NOW.get().minusSeconds(1),reservation);
        recovery.recover();
        assertThat(wallets.wallet(id).heldBalance()).isEqualByComparingTo("2");
        tasks.cancel(id,taskId); recovery.recover();
        assertThat(wallets.wallet(id).heldBalance()).isZero();
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("100");
        assertThatThrownBy(()->quotas.settle(id,reservation,1)).hasMessageContaining("预占已结束");
    }
    @Test void cancelledBatchSettlesSavedUnitsBeforeReleasingRemainder() {
        String id=account(); exhaust(id); var q=quote(id); String taskId; String reservation;
        jdbc.update("UPDATE ai_charge_quotes SET units=3 WHERE id=?",q.id());
        try(var scope=use(id,q,true)) { taskId=tasks.create(id,"AI_BILLING_TEST",Ids.newId(),"{}").id(); }
        try(var scope=tasks.aiScope(taskId)) { reservation=quotas.reserve(id,"async-partial","RESUME_SUMMARY",3).id(); }
        quotas.successfulUnit(id,reservation,"saved-answer");
        tasks.cancel(id,taskId); recovery.recover(); recovery.recover();
        assertThat(wallets.wallet(id).heldBalance()).isZero();
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("98");
        assertThat(jdbc.queryForObject("SELECT successful_units FROM ai_usage_record WHERE id=?",Integer.class,reservation)).isEqualTo(1);
    }
    @Test void cancelledBeforeExecutionCannotCreateAHoldOrUsage() {
        String id=account(); exhaust(id); var q=quote(id); String taskId;
        try(var scope=use(id,q,true)) { taskId=tasks.create(id,"AI_BILLING_TEST",Ids.newId(),"{}").id(); }
        tasks.cancel(id,taskId);
        try(var scope=tasks.aiScope(taskId)) {
            assertThatThrownBy(()->quotas.reserve(id,"late-worker","RESUME_SUMMARY",1)).hasMessageContaining("任务已结束");
        }
        assertThat(wallets.wallet(id).heldBalance()).isZero();
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_usage_record WHERE account_id=?",Integer.class,id)).isZero();
    }
    @Test void cancelledTaskCannotReuseItsAlreadyHeldReservation() {
        String id=account(); var q=quote(id); String taskId;
        try(var scope=use(id,q,false)) { taskId=tasks.create(id,"AI_BILLING_TEST",Ids.newId(),"{}").id(); }
        String reservation;
        try(var scope=tasks.aiScope(taskId)) { reservation=quotas.reserve(id,"held-before-cancel","RESUME_SUMMARY",1).id(); }
        tasks.cancel(id,taskId);
        try(var scope=tasks.aiScope(taskId)) {
            assertThatThrownBy(()->quotas.reserve(id,"held-before-cancel","RESUME_SUMMARY",1)).hasMessageContaining("任务已结束");
            assertThatThrownBy(()->quotas.restoreReservation(id,reservation)).hasMessageContaining("任务已结束");
        }
    }

    @Test void completionFenceRejectsCancellationAndReplacedWorkerBeforeSavingOrCharging() {
        for (boolean cancelled : java.util.List.of(true,false)) {
            String id=account(); exhaust(id); var q=quote(id); String taskId; String reservation;
            try(var scope=use(id,q,true)) { taskId=tasks.create(id,"FENCE_TEST",Ids.newId(),"{}").id(); }
            tasks.claimNext("FENCE_TEST").orElseThrow();
            try(var scope=tasks.aiScope(taskId)) { reservation=quotas.reserve(id,"fenced","RESUME_SUMMARY",1).id(); }
            if(cancelled) tasks.cancel(id,taskId);
            else jdbc.update("UPDATE async_tasks SET lease_owner='replacement-worker',lease_expires_at=? WHERE id=?",NOW.get().plusSeconds(600),taskId);
            try(var scope=tasks.aiScope(taskId)) {
                assertThatThrownBy(()->new TransactionTemplate(tx).executeWithoutResult(status->{
                    wallets.lock(id); tasks.fenceAiCompletion(id);
                    quotas.successfulUnit(id,reservation,"must-not-save");
                    quotas.settle(id,reservation,1); tasks.completeAiResult(id);
                })).hasMessageContaining("其他执行接管");
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_charge_successes WHERE reservation_id=?",Integer.class,reservation)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_billing_record WHERE account_id=?",Integer.class,id)).isZero();
            quotas.release(id,reservation);
            assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("100");
        }
    }

    @Test void restoringContinuationKeepsOriginalPersonalSourceAfterPreferenceChanges() {
        String id=account(); String channel=Ids.newId(); Instant now=NOW.get();
        jdbc.update("INSERT INTO ai_channel(id,scope,owner_account_id,name,provider_code,base_url,protocol,api_key_ciphertext,api_key_masked,channel_rate,status,version_no,created_at,updated_at) VALUES(?,'PERSONAL',?,'Own','','https://provider.example','OPENAI_CHAT','test','***',1,'ACTIVE',0,?,?)",channel,id,now,now);
        jdbc.update("INSERT INTO personal_channel_subscription(id,account_id,status,price,currency,current_period_start,current_period_end,auto_renew,idempotency_key,version_no,created_at,updated_at) VALUES(?,?,'ACTIVE',5,'CNY',?,?,FALSE,?,0,?,?)",Ids.newId(),id,now,now.plusSeconds(3600),Ids.newId(),now,now);
        jdbc.update("INSERT INTO ai_channel_preferences(account_id,personal_channel_id,version_no) VALUES(?,?,0)",id,channel);
        var q=quote(id); String reservation;
        try(var scope=use(id,q,false)) { reservation=quotas.reserve(id,"continuation","RESUME_SUMMARY",1).id(); }
        jdbc.update("UPDATE ai_channel_preferences SET personal_channel_id=NULL WHERE account_id=?",id);
        try(var scope=AiExecutionContext.use(new AiExecutionContext.State(null,"/clarifications","new-body",false,id,null,null))) {
            quotas.restoreReservation(id,reservation);
            var task=tasks.create(id,"CONTINUATION_TEST",Ids.newId(),"{}");
            try(var restored=tasks.aiScope(task.id())) {
                assertThat(AiExecutionContext.current().reservationId()).isEqualTo(reservation);
                assertThat(AiExecutionContext.current().personalChannelId()).isEqualTo(channel);
                assertThat(AiExecutionContext.current().quoteId()).isEqualTo(q.id());
            }
        }
        quotas.release(id,reservation);
        assertThatThrownBy(()->quotas.restoreReservation(id,reservation)).hasMessageContaining("预占已结束");
        assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo("100");
    }

    @Test void retryEndpointCreatesFreshAuthorizedBusinessAttemptForQuotaWalletAndPersonal() throws Exception {
        for(String source:java.util.List.of("QUOTA","WALLET","PERSONAL")) {
            String id=account();
            if(source.equals("WALLET")) exhaust(id);
            if(source.equals("PERSONAL")) {
                String channel=Ids.newId(); Instant now=NOW.get();
                jdbc.update("INSERT INTO ai_channel(id,scope,owner_account_id,name,provider_code,base_url,protocol,api_key_ciphertext,api_key_masked,channel_rate,status,version_no,created_at,updated_at) VALUES(?,'PERSONAL',?,'Own','','https://provider.example','OPENAI_CHAT','test','***',1,'ACTIVE',0,?,?)",channel,id,now,now);
                jdbc.update("INSERT INTO personal_channel_subscription(id,account_id,status,price,currency,current_period_start,current_period_end,auto_renew,idempotency_key,version_no,created_at,updated_at) VALUES(?,?,'ACTIVE',5,'CNY',?,?,FALSE,?,0,?,?)",Ids.newId(),id,now,now.plusSeconds(3600),Ids.newId(),now,now);
                jdbc.update("INSERT INTO ai_channel_preferences(account_id,personal_channel_id,version_no) VALUES(?,?,0)",id,channel);
            }
            String operation="CAREER_PLANNING_VALIDATION_BATCH";
            jdbc.update("UPDATE commerce_prices SET amount=2,enabled=TRUE WHERE code=?",operation);
            String originalPath="/api/v1/career-planning/sessions/session/validation-batches";
            var originalQuote=charges.quote(id,originalPath,"{}",quotas.current(id).remainingUnits());
            com.jobproof.modules.task.application.TaskView old;
            try(var scope=AiExecutionContext.use(new AiExecutionContext.State(originalQuote.id(),originalPath,AiOperationCatalog.hash("{}"),true,id,null,originalQuote.personalChannelId()))) {
                old=tasks.create(id,operation,Ids.newId(),"{\"sessionId\":\"session\",\"batchId\":\"old-batch\",\"command\":{\"requestId\":\"old-request\"}}");
            }
            String oldReservation;
            try(var scope=tasks.aiScope(old.id())) {
                oldReservation=quotas.reserve(id,"career-planning:"+operation+":old-request",operation,1).id();
                quotas.release(id,oldReservation);
            }
            tasks.markFailed(old.id(),"UPSTREAM_FAILED","synthetic");
            String path="/api/v1/tasks/"+old.id()+"/retry", body="{}", operationId=Ids.newId();
            var q=charges.quote(id,path,body,quotas.current(id).remainingUnits());
            assertThat(q.source()).isEqualTo(source);
            var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new com.jobproof.shared.auth.CurrentAccount(id,"","SEEKER",""),"",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SEEKER")));
            var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)
                .header("X-Ai-Quote",q.id()).header("X-Ai-Operation-Id",operationId).header("X-Ai-Payment-Confirmed","true"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
            String taskId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("data").path("id").asText();
            String payload=jdbc.queryForObject("SELECT payload_json FROM async_tasks WHERE id=?",String.class,taskId);
            assertThat(payload).contains(operationId).doesNotContain("old-request","old-batch");
            try(var scope=tasks.aiScope(taskId)) {
                var r=quotas.reserve(id,"career-planning:"+operation+":"+operationId,operation,1);
                quotas.settle(id,r.id(),1);
            }
            assertThat(wallets.wallet(id).availableBalance()).isEqualByComparingTo(source.equals("WALLET")?"98":"100");
            assertThat(jdbc.queryForObject("SELECT status FROM ai_quota_reservations WHERE id=?",String.class,oldReservation)).isEqualTo("RELEASED");
        }
    }
    @Test void httpAuthorizationIsBoundToReceivedBytesBeforeBusinessWrites() throws Exception {
        String id=account();exhaust(id);var q=quote(id);
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new com.jobproof.shared.auth.CurrentAccount(id,id+"@example.test","SEEKER",""),"",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SEEKER")));
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(PATH)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"clientRequestId\":\"tampered\"}")
                .header("X-Ai-Quote",q.id()).header("X-Ai-Payment-Confirmed","true");
        mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.reason").value("AI_QUOTE_CHANGED"));
        assertThat(wallets.wallet(id).heldBalance()).isZero();
        assertThat(AiExecutionContext.current()).isNull();
    }
}
