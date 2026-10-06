package com.jobproof.modules.identity.domain;

import com.jobproof.shared.error.AppException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话不变量：退出只结束当前会话；改密后全部会话失效。
 */
public final class SessionPolicy {

    private SessionPolicy() {
    }

    public static List<SessionRecord> logoutCurrent(List<SessionRecord> sessions, String currentSessionId, Instant now) {
        List<SessionRecord> copy = new ArrayList<>();
        boolean found = false;
        for (SessionRecord session : sessions) {
            if (session.id().equals(currentSessionId) && session.active(now)) {
                copy.add(session.revoke(now));
                found = true;
            } else {
                copy.add(session);
            }
        }
        if (!found) {
            throw AppException.unauthenticated();
        }
        return copy;
    }

    public static List<SessionRecord> revokeAll(List<SessionRecord> sessions, Instant now) {
        return sessions.stream().map(session -> session.active(now) ? session.revoke(now) : session).toList();
    }

    public static boolean allRevoked(List<SessionRecord> sessions, Instant now) {
        return sessions.stream().noneMatch(session -> session.active(now));
    }
}
