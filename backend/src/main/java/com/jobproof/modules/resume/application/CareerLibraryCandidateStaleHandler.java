package com.jobproof.modules.resume.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.modules.career.domain.CareerLibraryEventTypes;
import com.jobproof.shared.event.OutboxEventHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CareerLibraryCandidateStaleHandler implements OutboxEventHandler {

    private final JdbcTemplate jdbc;

    public CareerLibraryCandidateStaleHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean supports(String eventType) {
        return CareerLibraryEventTypes.SNAPSHOT_CHANGED.equals(eventType);
    }

    @Override
    public void handle(String eventType, JsonNode payload) {
        String accountId = payload.path("accountId").asText();
        if (accountId.isBlank() || !payload.path("snapshotVersion").canConvertToInt()) return;
        int snapshotVersion = payload.path("snapshotVersion").asInt();
        jdbc.update("UPDATE resume_candidates SET source_stale=1,version_no=version_no+1 "
                        + "WHERE account_id=? AND status='PENDING' AND source_stale=0 "
                        + "AND career_library_snapshot_version IS NOT NULL "
                        + "AND career_library_snapshot_version<?",
                accountId, snapshotVersion);
    }
}
