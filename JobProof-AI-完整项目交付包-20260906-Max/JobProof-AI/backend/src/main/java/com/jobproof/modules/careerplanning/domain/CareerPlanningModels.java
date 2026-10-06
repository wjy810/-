package com.jobproof.modules.careerplanning.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.modules.airesume.application.AiQuotaService.QuotaView;
import java.time.Instant;
import java.util.List;

public final class CareerPlanningModels {
    private CareerPlanningModels() {}

    public static final List<String> PROFILE_STATUSES = List.of(
            "DRAFT", "INTERVIEWING", "PENDING_CONFIRMATION", "CONFIRMED");
    public static final List<String> CLAIM_TYPES = List.of("FACT", "SELF_REPORTED", "INFERENCE");
    public static final List<String> RECOMMENDATION_TIERS = List.of(
            "READY_NOW", "AFTER_SMALL_GAP", "EXPLORATORY");
    public static final List<String> NODE_TYPES = List.of(
            "CAREER", "DOMAIN", "SKILL", "KNOWLEDGE", "TASK", "EVIDENCE");
    public static final List<String> NODE_STATUSES = List.of(
            "NOT_STARTED", "PLANNED", "LEARNING", "PENDING_VALIDATION", "MASTERED", "PAUSED");

    public record StartCommand(String entryMode, boolean aiConsent, String objectiveTaxonomyId) {}
    public record CreateCareerCanvasCommand(String taxonomyNodeId, boolean aiConsent) {}
    public record ProfileItemWrite(String id, String section, String claimType, String title,
            JsonNode payload, JsonNode sourceRefs, boolean locked, int sortOrder) {}
    public record ProfileWrite(JsonNode basics, JsonNode preferences, JsonNode constraints,
            String objectiveTaxonomyId, List<ProfileItemWrite> items, Integer expectedVersion) {}
    public record EvidenceSelection(String sourceId, List<String> scopes) {}
    public record EvidenceAuthorizationCommand(List<EvidenceSelection> selections) {}
    public record InterviewStartCommand(String requestId) {}
    public record InterviewAnswer(String questionId, String question, String answer) {}
    public record InterviewAnswerCommand(List<InterviewAnswer> answers, Integer expectedProfileVersion) {}
    public record ReviewProfileCommand(Integer expectedVersion) {}
    public record ConfirmProfileCommand(List<String> confirmedItemIds, Integer expectedVersion) {}
    public record GenerateRecommendationsCommand(String requestId) {}
    public record FavoriteCommand(boolean favorite) {}
    public record ConfirmationTokenCommand(String recommendationId) {}
    public record ConfirmGoalCommand(String recommendationId, String confirmationToken,
            Integer expectedSessionVersion) {}
    public record GenerateCanvasCommand(String requestId, Integer expectedVersion, String generationScale) {
        public GenerateCanvasCommand(String requestId, Integer expectedVersion) {
            this(requestId, expectedVersion, "STANDARD");
        }
    }
    public record CanvasNodeCreateCommand(String type, String title, String status,
            String parentNodeId, JsonNode detail, List<String> sourceRefs, boolean locked,
            Integer expectedVersion) {}
    public record CanvasNodeUpdateCommand(String title, String status, String parentNodeId,
            JsonNode detail, List<String> sourceRefs, Boolean locked, Integer x, Integer y,
            Integer expectedVersion) {}
    public record CanvasNodeBatchUpdateCommand(List<String> nodeIds, String status, Boolean locked,
            Integer expectedVersion) {}
    public record CanvasNodeDeleteCommand(boolean cascade, Integer expectedVersion) {}
    public record CanvasSplitItem(String type, String title, String status, JsonNode detail,
            List<String> sourceRefs, boolean locked) {}
    public record CanvasNodeSplitCommand(List<CanvasSplitItem> items, Integer expectedVersion) {}
    public record CanvasNodeMergeCommand(List<String> nodeIds, String title, JsonNode detail,
            Integer expectedVersion) {}
    public record CanvasRelationCommand(String fromNodeId, String toNodeId, String type,
            Integer expectedVersion) {}

    public record ProfileItemView(String id, String section, String claimType, String title,
            JsonNode payload, JsonNode sourceRefs, String status, boolean confirmed, boolean locked,
            int sortOrder, int version, Instant updatedAt) {}
    public record ProfileView(String id, String status, String entryMode, String objectiveTaxonomyId,
            JsonNode basics, JsonNode preferences, JsonNode constraints, String snapshotHash,
            int snapshotVersion, int version, List<ProfileItemView> items, Instant updatedAt,
            Instant confirmedAt) {}
    public record EvidenceOption(String sourceId, String sourceType, int sourceVersion, String title,
            String subtitle, String excerpt, String strength, boolean selected, List<String> scopes) {}
    public record PermissionView(String id, String sourceType, String sourceId, int sourceVersion,
            List<String> scopes, String status, int permissionVersion, Instant createdAt,
            Instant revokedAt) {}
    public record InterviewQuestion(String id, String text, String purpose, String claimType) {}
    public record InterviewRoundView(String id, int roundNo, String status, List<InterviewQuestion> questions,
            List<InterviewAnswer> answers, String model, String promptVersion, Instant createdAt,
            Instant completedAt) {}
    public record MessageView(String id, long sequence, String role, String type, String body,
            JsonNode payload, Instant createdAt) {}
    public record RecommendationView(String id, String taxonomyNodeId, String title, String tier,
            String fitSummary, List<String> rationale, List<String> gaps, List<String> sourceRefs,
            boolean favorite, int sortOrder) {}
    public record RecommendationSetView(String id, String status, String profileSnapshotHash,
            List<RecommendationView> recommendations, List<String> insufficientReasons,
            String confirmationToken, String model, String promptVersion, Instant createdAt,
            Instant completedAt) {}
    public record GoalView(String id, String recommendationId, String taxonomyNodeId, String title,
            String status, int version, Instant confirmedAt, Instant archivedAt) {}
    public record CanvasNodeView(String logicalNodeId, String type, String status, String title,
            JsonNode detail, List<String> sourceRefs, int x, int y, boolean locked, int sortOrder) {}
    public record CanvasRelationView(String id, String fromNodeId, String toNodeId, String type) {}
    public record CanvasView(String versionId, int version, String parentVersionId, String reason,
            String changeSummary, String graphHash, String promptVersion, String schemaVersion,
            String model, List<CanvasNodeView> nodes, List<CanvasRelationView> relations,
            Instant createdAt) {}
    public record CanvasVersionView(String versionId, int version, String parentVersionId,
            String reason, String changeSummary, String graphHash, String createdBy,
            String promptVersion, String schemaVersion, String model, Instant createdAt) {}
    public record CanvasVersionPage(List<CanvasVersionView> items, int page, int size,
            long totalElements, int totalPages, boolean hasNext) {}
    public record CanvasFieldChange(String field, JsonNode beforeValue, JsonNode afterValue) {}
    public record CanvasNodeDiff(String logicalNodeId, List<String> changeTypes,
            String beforeTitle, String afterTitle, List<CanvasFieldChange> fields) {}
    public record CanvasRelationDiff(String changeType, String type, String fromNodeId,
            String toNodeId) {}
    public record CanvasVersionDiff(int fromVersion, int toVersion, int addedNodes,
            int removedNodes, int updatedNodes, int movedNodes, int addedRelations,
            int removedRelations, List<CanvasNodeDiff> nodes, List<CanvasRelationDiff> relations) {}
    public record SessionView(String id, String status, String phase, String entryMode, boolean aiConsent,
            int version, ProfileView profile, List<PermissionView> permissions,
            List<InterviewRoundView> interviewRounds, List<MessageView> messages,
            RecommendationSetView recommendationSet, GoalView activeGoal, CanvasView canvas,
            Instant createdAt, Instant updatedAt) {}
    public record OverviewView(boolean enabled, SessionView session, QuotaView quota) {}
    public record CareerCanvasStats(int canvasCount, int primaryCount, int abilityNodeCount,
            int pendingValidationCount, int versionCount) {}
    public record CareerCanvasSummary(String sessionId, String goalId, String title, String status,
            boolean primary, int overallProgress, int nodeCount, int domainCount,
            int pendingValidationCount, int canvasVersion, String planStatus,
            Integer currentWeek, Integer durationWeeks, int planRevision,
            String currentFocus, List<String> recentChanges, Instant createdAt, Instant updatedAt) {}
    public record CareerCanvasDashboard(CareerCanvasStats stats, List<CareerCanvasSummary> items) {}
    public record ConfirmationTokenView(String setId, String recommendationId, String token,
            Instant expiresAt) {}
}
