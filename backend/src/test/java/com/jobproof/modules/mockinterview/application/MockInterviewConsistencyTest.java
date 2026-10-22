package com.jobproof.modules.mockinterview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

class MockInterviewConsistencyTest {
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");
    private final CurrentAccount current = new CurrentAccount("owner", "owner@example.com", "SEEKER", "session");
    private final AtomicReference<Runnable> competingWrite = new AtomicReference<>();
    private JdbcTemplate jdbc;
    private JdbcTemplate otherConnection;
    private TransactionTemplate tx;
    private MockInterviewService service;

    @BeforeEach
    void setup() {
        String url = "jdbc:h2:mem:mock-consistency-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        var source = new DriverManagerDataSource(url, "sa", "");
        jdbc = new JdbcTemplate(source);
        otherConnection = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V30__mock_interview_training.sql"),
                new ClassPathResource("db/migration/V69__mock_interview_honest_scoring.sql"),
                new ClassPathResource("db/migration/V81__mock_interview_follow_ups.sql"),
                new ClassPathResource("db/migration/V82__mock_interview_activity.sql")).execute(source);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(source);
        tx = new TransactionTemplate(transactionManager);
        service = new MockInterviewService(jdbc, new ObjectMapper(), () -> {
            Runnable competitor = competingWrite.getAndSet(null);
            if (competitor != null) competitor.run();
            return NOW;
        }, mock(MockInterviewAiService.class),
                transactionManager, quotaStub());
        jdbc.update("INSERT INTO mock_interview_sessions(id,account_id,title,position_name,mode,interview_type,difficulty,duration_minutes,question_count,language_code,feedback_mode,follow_up_enabled,status,current_question_index,elapsed_seconds,resume_snapshot_json,jd_snapshot_json,materials_snapshot_json,settings_snapshot_json,version_no,created_at,updated_at) VALUES('interview','owner','Interview','Engineer','TEXT','COMPREHENSIVE','STANDARD',30,2,'zh-CN','AFTER_EACH',0,'IN_PROGRESS',0,0,'{}','{}','{}','{}',0,?,?)", NOW, NOW);
        for (int i = 1; i <= 2; i++) {
            jdbc.update("INSERT INTO mock_interview_questions(id,session_id,order_no,question_type,prompt_text,source_label,source_refs_json,status,created_at) VALUES(?, 'interview',?,'PROFESSIONAL','Question','source','[]','PENDING',?)", "question-" + i, i, NOW);
        }
    }

    @Test
    void answerUpdateRejectsCommittedWriteAfterVersionRead() {
        insertAnswer(jdbc, "answer-1", "question-1", "original", "{}");
        competingWrite.set(() -> otherConnection.update("UPDATE mock_interview_answers SET answer_text='winner',version_no=1 WHERE id='answer-1'"));
        AppException error = assertThrows(AppException.class, () -> tx.execute(status -> service.saveTextDraft(current,
                "interview", "question-1", new MockInterviewService.AnswerCommand("loser", 0))));
        assertEquals("VERSION_CONFLICT", error.reason());
        assertEquals("winner", jdbc.queryForObject("SELECT answer_text FROM mock_interview_answers WHERE id='answer-1'", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT version_no FROM mock_interview_answers WHERE id='answer-1'", Integer.class));
    }

    @Test
    void concurrentFirstAnswerReturnsVersionConflictInsteadOfDatabaseError() {
        competingWrite.set(() -> insertAnswer(otherConnection, "winner", "question-1", "winner", "{}"));
        AppException error = assertThrows(AppException.class, () -> tx.execute(status -> service.saveTextDraft(current,
                "interview", "question-1", new MockInterviewService.AnswerCommand("loser", null))));
        assertEquals("VERSION_CONFLICT", error.reason());
        assertEquals("winner", jdbc.queryForObject("SELECT answer_text FROM mock_interview_answers", String.class));
    }

    @Test
    void draftUpdateRejectsCommittedWriteAfterVersionRead() {
        jdbc.update("INSERT INTO mock_interview_drafts VALUES('draft','owner',1,'DRAFT','{}',0,?,?)", NOW, NOW);
        competingWrite.set(() -> otherConnection.update("UPDATE mock_interview_drafts SET step_no=2,version_no=1 WHERE id='draft'"));
        AppException error = assertThrows(AppException.class, () -> tx.execute(status -> service.saveDraft(current,
                "draft", new MockInterviewService.DraftCommand(3, Map.of(), 0))));
        assertEquals("VERSION_CONFLICT", error.reason());
        assertEquals(2, service.getDraft(current, "draft").step());
    }

    @Test
    void reportIncludesValidZeroScoresAndSkipsOnlyMissingDimensions() {
        insertAnswer(jdbc, "answer-1", "question-1", "first", "{\"structure\":0,\"relevance\":0,\"evidence\":0,\"expression\":0,\"professional\":0}");
        insertAnswer(jdbc, "answer-2", "question-2", "second", "{\"structure\":100,\"relevance\":100}");
        var report = tx.execute(status -> service.complete(current, "interview"));
        assertEquals(Map.of("structure", 50, "relevance", 50, "evidence", 0, "expression", 0, "professional", 0), report.dimensions());
        assertEquals(20, report.overallScore());
    }

    @Test
    void reportWithOnlyZeroScoresRemainsZero() {
        insertAnswer(jdbc, "answer-1", "question-1", "first", "{\"structure\":0,\"relevance\":0,\"evidence\":0,\"expression\":0,\"professional\":0}");
        var report = tx.execute(status -> service.complete(current, "interview"));
        assertEquals(0, report.overallScore());
    }

    @Test
    void reportWithoutEvaluatedAnswersHasNoScore() {
        var report = tx.execute(status -> service.complete(current, "interview"));
        assertNull(report.overallScore());
        assertEquals(Map.of(), report.dimensions());
        assertEquals(0, report.evaluatedCount());
    }

    @Test
    void pendingEvaluationWaitsForAccountBeforeTouchingAnswerAndPreservesConcurrentSubmission() throws Exception {
        jdbc.execute("CREATE TABLE accounts(id VARCHAR(36) PRIMARY KEY)");
        jdbc.update("INSERT INTO accounts VALUES('owner')");
        insertAnswer(jdbc,"answer-1","question-1","pending","{}");
        jdbc.update("UPDATE mock_interview_answers SET status='SUBMITTED' WHERE id='answer-1'");
        var quota=quotaStub();
        var tryingAccount=new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call->{
            tryingAccount.countDown();
            jdbc.queryForObject("SELECT id FROM accounts WHERE id='owner' FOR UPDATE",String.class);
            return null;
        }).when(quota).lockForSettlement("owner");
        var ai=mock(MockInterviewAiService.class);
        org.mockito.Mockito.when(ai.evaluateAnswer(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyMap(),org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new MockInterviewAiService.Evaluation(Map.of("overall",80),Map.of("generationMode","AI_MODEL"),"synthetic","channel",0,0));
        var evaluator=new MockInterviewService(jdbc,new ObjectMapper(),()->NOW,ai,
                new DataSourceTransactionManager(jdbc.getDataSource()),quota);
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        var result=new AtomicReference<java.util.concurrent.Future<MockInterviewService.EvaluationResult>>();
        try {
            tx.executeWithoutResult(status->{
                jdbc.queryForObject("SELECT id FROM accounts WHERE id='owner' FOR UPDATE",String.class);
                result.set(pool.submit(()->evaluator.evaluatePending(current,"interview")));
                try { org.junit.jupiter.api.Assertions.assertTrue(tryingAccount.await(5,java.util.concurrent.TimeUnit.SECONDS)); }
                catch(InterruptedException e) {throw new RuntimeException(e);}
                jdbc.update("UPDATE mock_interview_answers SET answer_text='new submission',version_no=version_no+1 WHERE id='answer-1'");
            });
            assertEquals(0,result.get().get(5,java.util.concurrent.TimeUnit.SECONDS).evaluated());
            assertEquals("new submission",jdbc.queryForObject("SELECT answer_text FROM mock_interview_answers WHERE id='answer-1'",String.class));
            org.mockito.Mockito.verify(quota,org.mockito.Mockito.never()).successfulUnit(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        } finally {pool.shutdownNow();}
    }

    @Test
    void abandonmentCommittedAfterFinalStateReadPreventsAnswerAndSettlement() {
        var quota=quotaStub();
        org.mockito.Mockito.when(quota.reserveExclusive(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new com.jobproof.modules.airesume.application.AiQuotaService.Reservation("reservation", "request", 1, 0, "HELD"));
        var otherTx=new TransactionTemplate(new DataSourceTransactionManager(otherConnection.getDataSource()));
        var competingService=new MockInterviewService(otherConnection,new ObjectMapper(),()->NOW,mock(MockInterviewAiService.class),
                new DataSourceTransactionManager(otherConnection.getDataSource()),quotaStub());
        var ai=mock(MockInterviewAiService.class);
        org.mockito.Mockito.when(ai.evaluateAnswer(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyBoolean(),org.mockito.ArgumentMatchers.anyMap(),org.mockito.ArgumentMatchers.anyMap()))
                .thenAnswer(call->{
                    // The next clock read is after the completion transaction's active/version read.
                    competingWrite.set(()->otherTx.executeWithoutResult(status->competingService.abandon(current,"interview")));
                    return new MockInterviewAiService.Evaluation(Map.of("overall",80),Map.of("generationMode","AI_MODEL"),"synthetic","channel",0,0);
                });
        var submitting=new MockInterviewService(jdbc,new ObjectMapper(),()->{
            Runnable competitor=competingWrite.getAndSet(null);
            if(competitor!=null) competitor.run();
            return NOW;
        },ai,new DataSourceTransactionManager(jdbc.getDataSource()),quota);
        AppException error=assertThrows(AppException.class,()->submitting.submitAnswer(current,"interview","question-1",
                new MockInterviewService.AnswerCommand("answer finishing too late",null)));
        assertEquals("VERSION_CONFLICT",error.reason());
        assertEquals("ABANDONED",jdbc.queryForObject("SELECT status FROM mock_interview_sessions WHERE id='interview'",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mock_interview_answers",Integer.class));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM mock_interview_questions WHERE id='question-1'",String.class));
        org.mockito.Mockito.verify(quota,org.mockito.Mockito.never()).settle(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.verify(quota).release("owner","reservation");
    }

    private static com.jobproof.modules.airesume.application.AiQuotaService quotaStub() {
        var quota = org.mockito.Mockito.mock(com.jobproof.modules.airesume.application.AiQuotaService.class);
        org.mockito.Mockito.when(quota.reserve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new com.jobproof.modules.airesume.application.AiQuotaService.Reservation("reservation", "request", 1, 0, "HELD"));
        return quota;
    }
    private static void insertAnswer(JdbcTemplate database, String id, String questionId, String text, String scores) {
        database.update("INSERT INTO mock_interview_answers(id,session_id,question_id,answer_mode,answer_text,status,score_json,feedback_json,version_no,created_at,updated_at) VALUES(?,'interview',?,'TEXT',?,'DRAFT',?,'{}',0,?,?)", id, questionId, text, scores, NOW, NOW);
    }
}
