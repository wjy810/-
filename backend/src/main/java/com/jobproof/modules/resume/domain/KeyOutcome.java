package com.jobproof.modules.resume.domain;

public record KeyOutcome(String id, String text, String evidenceId, boolean waiveNoEvidence) {

    public boolean resolved() {
        return (evidenceId != null && !evidenceId.isBlank()) || waiveNoEvidence;
    }

    public KeyOutcome withEvidence(String newEvidenceId) {
        return new KeyOutcome(id, text, newEvidenceId, false);
    }

    public KeyOutcome waived() {
        return new KeyOutcome(id, text, evidenceId, true);
    }
}
