package com.jobproof.modules.jobapply.application;

import com.jobproof.shared.id.Ids;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** What happened to one application, in order: stage moves, links, first openings. */
@Component
public class ApplicationTimeline {

    static final String CREATED = "CREATED";
    static final String STAGE_CHANGED = "STAGE_CHANGED";
    static final String LINK_CREATED = "LINK_CREATED";
    static final String LINK_REVOKED = "LINK_REVOKED";
    static final String FIRST_VIEWED = "FIRST_VIEWED";
    static final String NEW_VISITOR = "NEW_VISITOR";
    static final String LINK_UPDATED = "LINK_UPDATED";
    static final String OWN_VIEW_MARKED = "OWN_VIEW_MARKED";
    static final String NEXT_STEP_DONE = "NEXT_STEP_DONE";
    static final String ARCHIVED = "ARCHIVED";
    static final String RESTORED = "RESTORED";

    private static final int MAX_DETAIL = 300;

    private final JdbcTemplate jdbc;

    public ApplicationTimeline(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void record(String accountId, String applicationId, String type, String fromStage, String toStage,
            String detail, Instant at) {
        if (applicationId == null) return;
        String clipped = detail == null || detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
        jdbc.update("INSERT INTO job_application_events(id,application_id,account_id,event_type,from_stage,to_stage,detail,created_at) VALUES(?,?,?,?,?,?,?,?)",
                Ids.newId(), applicationId, accountId, type, fromStage, toStage, clipped, at);
    }
}
