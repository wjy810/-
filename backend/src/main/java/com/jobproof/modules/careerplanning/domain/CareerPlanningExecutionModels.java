package com.jobproof.modules.careerplanning.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CareerPlanningExecutionModels {
    private CareerPlanningExecutionModels() {}

    public record GenerateProposalCommand(String requestId, String instruction, Integer expectedVersion) {}
    public record GenerateNodeInferenceCommand(String requestId, String targetNodeId,
            String direction, String depth, String instruction, Integer expectedVersion) {}
    public record ProposalDecision(String itemId, String decision, String rejectionReason) {}
    public record DecideProposalCommand(List<ProposalDecision> decisions, Integer expectedVersion) {}

    public record ProposalItemView(String id, int sequence, String proposalKey, String operation,
            String targetNodeId, String parentNodeId, JsonNode before, JsonNode after, String reason,
            List<String> sourceRefs, List<String> impactNodeIds, String decision,
            String rejectionReason, Instant decidedAt) {}
    public record ProposalView(String id, String sessionId, String goalId, String baseVersionId,
            int baseVersion, String status, String proposalType, String targetNodeId,
            String direction, String depth, String taskId, String instruction, String promptVersion,
            String schemaVersion, String model, List<ProposalItemView> items,
            Instant createdAt, Instant decidedAt) {}

    public record CreateLearningPlanCommand(int durationWeeks, String intensity, int weeklyHours,
            List<Integer> learningDays, LocalDate startDate, Integer expectedCanvasVersion) {}
    public record UpdatePlanCommand(String status, Integer expectedVersion) {}
    public record UpdateTaskCommand(String status, LocalDate dueDate, Integer estimatedMinutes,
            Integer targetWeek, Integer sortOrder, Integer expectedVersion) {}
    public record CreateEvidenceCommand(String taskId, String nodeId, String sourceType,
            String sourceId, String title, String note, Integer expectedTaskVersion) {}
    public record WeeklyReviewCommand(String completedSummary, String blockers,
            String adjustment, String nextWeekFocus) {}

    public record PlanTaskView(String id, String nodeId, String taskType, int week,
            String title, String description, String priority, int estimatedMinutes,
            LocalDate dueDate, String status, boolean evidenceRequired, int sortOrder,
            int version, Instant createdAt, Instant updatedAt, Instant completedAt) {}
    public record LearningEvidenceView(String id, String planId, String taskId, String nodeId,
            String sourceType, String sourceId, String title, String note,
            String verificationStatus, Instant createdAt, Instant confirmedAt) {}
    public record WeeklyReviewView(String id, int week, String completedSummary,
            String blockers, String adjustment, String nextWeekFocus,
            Instant createdAt, Instant updatedAt) {}
    public record LearningPlanView(String id, String sessionId, String goalId,
            String canvasVersionId, int canvasVersion, int durationWeeks, String intensity,
            int weeklyHours, List<Integer> learningDays, LocalDate startDate,
            LocalDate targetDate, String status, int version, String generationMethod,
            String promptVersion, String schemaVersion, String model,
            List<PlanTaskView> tasks, List<LearningEvidenceView> evidences,
            List<WeeklyReviewView> reviews, Instant createdAt, Instant updatedAt,
            Instant completedAt, Instant archivedAt, String currentRevisionId,
            int currentRevision) {}
    public record LearningPlanRevisionView(String id, String planId, int revision,
            String parentRevisionId, String restoredFromRevisionId, String reason,
            String planHash, JsonNode snapshot, String createdBy, Instant createdAt) {}

    public record StartValidationCommand(String requestId, String nodeId, String method,
            List<String> evidenceIds, JsonNode submission, Integer expectedCanvasVersion) {}
    public record StartValidationBatchItem(String nodeId, List<String> evidenceIds, JsonNode submission) {}
    public record StartValidationBatchCommand(String requestId, Integer expectedCanvasVersion,
            String method, List<StartValidationBatchItem> items) {}
    public record ConfirmValidationCommand(boolean accepted, Integer expectedCanvasVersion) {}
    public record ValidationView(String id, String batchId, String sessionId, String goalId, String planId,
            String nodeId, String canvasVersionId, String method, String status,
            JsonNode submission, JsonNode score, String result, JsonNode feedback,
            boolean userConfirmed, List<String> evidenceIds, String promptVersion,
            String schemaVersion, String model, Instant createdAt, Instant evaluatedAt,
            Instant confirmedAt) {}

    public record RestoreCanvasCommand(Integer expectedVersion) {}
    public record ExecutionOverview(ProposalView pendingProposal, LearningPlanView activePlan,
            List<ValidationView> validations) {}
    public record ProposalApplyResult(ProposalView proposal,
            CareerPlanningModels.CanvasView canvas) {}
}
