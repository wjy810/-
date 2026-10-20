package com.jobproof.modules.mockinterview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.aigateway.application.AiGatewayException;
import com.jobproof.shared.auth.CurrentAccount;
import java.sql.Timestamp;
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

/** The interview clock counts only time a page was there: a page that went quiet stops it at its last report. */
class MockInterviewClockTest {
    private static final Instant STARTED = Instant.parse("2026-10-20T08:00:00Z");
    private final CurrentAccount current = new CurrentAccount("owner", "owner@example.com", "SEEKER", "session");
    private final AtomicReference<Instant> now = new AtomicReference<>(STARTED);
    private final MockInterviewAiService ai = mock(MockInterviewAiService.class);
    private JdbcTemplate jdbc;
    private MockInterviewService service;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:mock-clock-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V30__mock_interview_training.sql"),
                new ClassPathResource("db/migration/V69__mock_interview_honest_scoring.sql"),
                new ClassPathResource("db/migration/V81__mock_interview_follow_ups.sql"),
                new ClassPathResource("db/migration/V82__mock_interview_activity.sql")).execute(source);
        service = new MockInterviewService(jdbc, new ObjectMapper(), now::get, ai, new DataSourceTransactionManager(source));
        when(ai.evaluateAnswer(anyString(), anyString(), anyString(), any(), anyList(), anyString(), anyBoolean(), anyMap(), anyMap()))
                .thenThrow(new AiGatewayException("down", true, false));
        jdbc.update("INSERT INTO mock_interview_sessions(id,account_id,title,position_name,mode,interview_type,difficulty,duration_minutes,question_count,language_code,feedback_mode,follow_up_enabled,status,current_question_index,elapsed_seconds,resume_snapshot_json,jd_snapshot_json,materials_snapshot_json,settings_snapshot_json,version_no,created_at,updated_at,resumed_at,last_active_at) VALUES('interview','owner','Interview','Engineer','TEXT','COMPREHENSIVE','STANDARD',30,2,'zh-CN','AFTER_SESSION',0,'IN_PROGRESS',0,60,'{}','{}','{}','{}',0,?,?,?,?)",
                ts(STARTED), ts(STARTED), ts(STARTED), ts(STARTED.plusSeconds(120)));
        for (int i = 1; i <= 2; i++) {
            jdbc.update("INSERT INTO mock_interview_questions(id,session_id,order_no,question_type,prompt_text,source_label,source_refs_json,status,created_at) VALUES(?, 'interview',?,'PROFESSIONAL','Question','source','[]','PENDING',?)",
                    "question-" + i, i, ts(STARTED));
        }
    }

    @Test
    void aPageThatWentQuietStopsTheClockAtItsLastReport() {
        now.set(STARTED.plusSeconds(30 * 60));
        var session = service.get(current, "interview").session();
        assertThat(session.status()).isEqualTo("PAUSED");
        assertThat(session.elapsedSeconds()).as("60 banked + 120 until the last report, not 30 minutes").isEqualTo(180);
        Map<String, Object> row = jdbc.queryForMap("SELECT resumed_at,paused_at FROM mock_interview_sessions WHERE id='interview'");
        assertThat(row.get("resumed_at")).isNull();
        assertThat(((Timestamp) row.get("paused_at")).toInstant()).isEqualTo(STARTED.plusSeconds(120));
    }

    @Test
    void aPageThatKeepsReportingKeepsTheClockRunning() {
        now.set(STARTED.plusSeconds(240));
        var summary = service.heartbeat(current, "interview");
        assertThat(summary.status()).isEqualTo("IN_PROGRESS");
        assertThat(summary.elapsedSeconds()).isEqualTo(60 + 240);
        now.set(STARTED.plusSeconds(240 + 150));
        assertThat(service.heartbeat(current, "interview").status()).as("within the idle limit of the last report").isEqualTo("IN_PROGRESS");
    }

    @Test
    void theListStopsCountingAtTheLastReportBeforeTheSessionIsOpened() {
        now.set(STARTED.plusSeconds(3 * 3600));
        var item = service.list(current, null, null, null, 10).get(0);
        assertThat(item.elapsedSeconds()).isEqualTo(180);
    }

    @Test
    void answeringAfterTheClockStoppedStartsItAgain() {
        now.set(STARTED.plusSeconds(30 * 60));
        service.get(current, "interview");
        Instant back = STARTED.plusSeconds(31 * 60);
        now.set(back);
        var view = service.submitAnswer(current, "interview", "question-1", new MockInterviewService.AnswerCommand("我的回答", null));
        assertThat(view.session().status()).isEqualTo("IN_PROGRESS");
        assertThat(((Timestamp) jdbc.queryForObject("SELECT resumed_at FROM mock_interview_sessions WHERE id='interview'", Timestamp.class)).toInstant())
                .isEqualTo(back);
        now.set(back.plusSeconds(60));
        assertThat(service.heartbeat(current, "interview").elapsedSeconds()).isEqualTo(180 + 60);
    }

    private static Timestamp ts(Instant value) {
        return Timestamp.from(value);
    }
}
