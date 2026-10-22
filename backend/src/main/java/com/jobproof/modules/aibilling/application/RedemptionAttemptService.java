package com.jobproof.modules.aibilling.application;

import com.jobproof.shared.id.Ids;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RedemptionAttemptService {
    private final JdbcTemplate jdbc;

    public RedemptionAttemptService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String accountId, Instant now) {
        jdbc.update(
                "INSERT INTO redeem_attempts(id,account_id,succeeded,created_at)"
                    + " VALUES(?,?,FALSE,?)",
                Ids.newId(),
                accountId,
                now);
    }
}
