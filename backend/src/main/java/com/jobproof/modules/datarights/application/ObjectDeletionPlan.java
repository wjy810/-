package com.jobproof.modules.datarights.application;

import com.jobproof.modules.datarights.domain.DeletionTargetType;
import java.util.List;

public record ObjectDeletionPlan(
        DeletionTargetType targetType,
        String targetId,
        String impactSummary,
        List<ImpactItem> impacts,
        List<String> blockers,
        boolean canProceed) {

    public record ImpactItem(String kind, String id, String status, String relation, String label) {
    }
}
