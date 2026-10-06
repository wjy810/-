package com.jobproof.modules.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionPolicyTest {

    @Test
    void logoutOnlyRevokesCurrentSession() {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        SessionRecord current = new SessionRecord("s1", "a1", "h1", now.plusSeconds(60), null);
        SessionRecord other = new SessionRecord("s2", "a1", "h2", now.plusSeconds(60), null);
        List<SessionRecord> after = SessionPolicy.logoutCurrent(List.of(current, other), "s1", now);
        assertTrue(after.stream().filter(s -> s.id().equals("s1")).noneMatch(s -> s.active(now)));
        assertTrue(after.stream().filter(s -> s.id().equals("s2")).allMatch(s -> s.active(now)));
    }

    @Test
    void changePasswordRevokesAllSessions() {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        List<SessionRecord> sessions = List.of(
                new SessionRecord("s1", "a1", "h1", now.plusSeconds(60), null),
                new SessionRecord("s2", "a1", "h2", now.plusSeconds(60), null));
        List<SessionRecord> after = SessionPolicy.revokeAll(sessions, now);
        assertEquals(2, after.size());
        assertTrue(SessionPolicy.allRevoked(after, now));
    }
}
