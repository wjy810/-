package com.jobproof.shared.auth;

public record CurrentAccount(String accountId, String email, String role, String sessionId) {

    public boolean operator() {
        return "OPERATOR".equals(role);
    }
}
