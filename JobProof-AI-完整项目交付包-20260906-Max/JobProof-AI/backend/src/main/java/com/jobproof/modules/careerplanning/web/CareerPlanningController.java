package com.jobproof.modules.careerplanning.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.careerplanning.application.CareerPlanningService;
import com.jobproof.modules.careerplanning.application.CareerPlanningExecutionService;
import com.jobproof.modules.careerplanning.application.CareerPlanningAsyncService;
import com.jobproof.modules.careerplanning.application.CareerPlanningSseService;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.ConfirmGoalCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.ConfirmProfileCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasNodeCreateCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasNodeBatchUpdateCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasNodeDeleteCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasNodeMergeCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasNodeSplitCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasNodeUpdateCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasRelationCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasVersionDiff;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasVersionPage;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CanvasView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CareerCanvasDashboard;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.ConfirmationTokenCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.ConfirmationTokenView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.CreateCareerCanvasCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.EvidenceAuthorizationCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.EvidenceOption;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.FavoriteCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.GenerateRecommendationsCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.GenerateCanvasCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.InterviewAnswerCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.InterviewStartCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.OverviewView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.ProfileWrite;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.RecommendationSetView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.ReviewProfileCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.SessionView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningModels.StartCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ConfirmValidationCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.CreateEvidenceCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.CreateLearningPlanCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.DecideProposalCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ExecutionOverview;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.GenerateProposalCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.GenerateNodeInferenceCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.LearningPlanRevisionView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.LearningPlanView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ProposalApplyResult;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ProposalView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.RestoreCanvasCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.StartValidationCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.StartValidationBatchCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.UpdatePlanCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.UpdateTaskCommand;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.ValidationView;
import com.jobproof.modules.careerplanning.domain.CareerPlanningExecutionModels.WeeklyReviewCommand;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.modules.task.application.TaskView;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/career-planning")
public class CareerPlanningController {
    private final CareerPlanningService planning;
    private final CareerPlanningExecutionService execution;
    private final CareerPlanningAsyncService async;
    private final CareerPlanningSseService events;

    public CareerPlanningController(CareerPlanningService planning, CareerPlanningExecutionService execution,
            CareerPlanningAsyncService async, CareerPlanningSseService events) {
        this.planning = planning;
        this.execution = execution;
        this.async = async;
        this.events = events;
    }

    @GetMapping
    public ApiResponse<OverviewView> overview() {
        return ApiResponse.ok(planning.overview(current()));
    }

    @GetMapping("/canvases")
    public ApiResponse<CareerCanvasDashboard> canvases(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "RECENT") String sort) {
        return ApiResponse.ok(planning.dashboard(current(), q, status, sort));
    }

    @PostMapping("/canvases")
    public ApiResponse<SessionView> createCanvas(@RequestBody CreateCareerCanvasCommand command) {
        return ApiResponse.ok(planning.createCanvas(current(), command));
    }

    @PutMapping("/canvases/{sessionId}/primary")
    public ApiResponse<CareerCanvasDashboard> makePrimary(@PathVariable String sessionId) {
        return ApiResponse.ok(planning.makePrimary(current(), sessionId));
    }

    @PostMapping("/sessions")
    public ApiResponse<SessionView> start(@RequestBody StartCommand command) {
        return ApiResponse.ok(planning.start(current(), command));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<SessionView> session(@PathVariable String sessionId) {
        return ApiResponse.ok(planning.get(current(), sessionId));
    }

    @GetMapping(value = "/sessions/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String sessionId,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        long resumeAfter = Math.max(Math.max(0L, afterSequence), eventSequence(lastEventId));
        return events.subscribe(current().accountId(), sessionId, resumeAfter);
    }

    @PutMapping("/sessions/{sessionId}/profile")
    public ApiResponse<SessionView> updateProfile(@PathVariable String sessionId,
            @RequestBody ProfileWrite command) {
        return ApiResponse.ok(planning.updateProfile(current(), sessionId, command));
    }

    @GetMapping("/sessions/{sessionId}/evidence-options")
    public ApiResponse<List<EvidenceOption>> evidenceOptions(@PathVariable String sessionId) {
        return ApiResponse.ok(planning.eligibleEvidence(current(), sessionId));
    }

    @PutMapping("/sessions/{sessionId}/evidence")
    public ApiResponse<SessionView> authorizeEvidence(@PathVariable String sessionId,
            @RequestBody EvidenceAuthorizationCommand command) {
        return ApiResponse.ok(planning.authorizeEvidence(current(), sessionId, command));
    }

    @PostMapping("/sessions/{sessionId}/interviews")
    public ApiResponse<SessionView> startInterview(@PathVariable String sessionId,
            @RequestBody(required = false) InterviewStartCommand command) {
        return ApiResponse.ok(planning.startInterview(current(), sessionId, command));
    }

    @PostMapping("/sessions/{sessionId}/interviews/tasks")
    public ResponseEntity<ApiResponse<TaskView>> startInterviewTask(@PathVariable String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) InterviewStartCommand command) {
        InterviewStartCommand resolved = command != null && command.requestId() != null && !command.requestId().isBlank()
                ? command : new InterviewStartCommand(idempotencyKey);
        return ResponseEntity.accepted().body(ApiResponse.ok(async.startInterviewGeneration(current(), sessionId, resolved)));
    }

    @PostMapping("/sessions/{sessionId}/interviews/{roundId}/answers")
    public ApiResponse<SessionView> answerInterview(@PathVariable String sessionId,
            @PathVariable String roundId, @RequestBody InterviewAnswerCommand command) {
        return ApiResponse.ok(planning.answerInterview(current(), sessionId, roundId, command));
    }

    @PutMapping("/sessions/{sessionId}/interviews/{roundId}/draft")
    public ApiResponse<SessionView> saveInterviewDraft(@PathVariable String sessionId,
            @PathVariable String roundId, @RequestBody InterviewAnswerCommand command) {
        return ApiResponse.ok(planning.saveInterviewDraft(current(), sessionId, roundId, command));
    }

    @PostMapping("/sessions/{sessionId}/profile/review")
    public ApiResponse<SessionView> reviewProfile(@PathVariable String sessionId,
            @RequestBody ReviewProfileCommand command) {
        return ApiResponse.ok(planning.reviewProfile(current(), sessionId, command));
    }

    @PostMapping("/sessions/{sessionId}/profile/confirm")
    public ApiResponse<SessionView> confirmProfile(@PathVariable String sessionId,
            @RequestBody ConfirmProfileCommand command) {
        return ApiResponse.ok(planning.confirmProfile(current(), sessionId, command));
    }

    @PostMapping("/sessions/{sessionId}/recommendations")
    public ApiResponse<RecommendationSetView> generateRecommendations(@PathVariable String sessionId,
            @RequestBody(required = false) GenerateRecommendationsCommand command) {
        return ApiResponse.ok(planning.generateRecommendations(current(), sessionId, command));
    }

    @PostMapping("/sessions/{sessionId}/recommendations/tasks")
    public ResponseEntity<ApiResponse<TaskView>> startRecommendationTask(@PathVariable String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) GenerateRecommendationsCommand command) {
        GenerateRecommendationsCommand resolved = command != null && command.requestId() != null
                && !command.requestId().isBlank() ? command : new GenerateRecommendationsCommand(idempotencyKey);
        return ResponseEntity.accepted().body(ApiResponse.ok(
                async.startRecommendationGeneration(current(), sessionId, resolved)));
    }

    @PutMapping("/sessions/{sessionId}/recommendations/{recommendationId}/favorite")
    public ApiResponse<RecommendationSetView> favorite(@PathVariable String sessionId,
            @PathVariable String recommendationId, @RequestBody FavoriteCommand command) {
        return ApiResponse.ok(planning.favorite(current(), sessionId, recommendationId, command));
    }

    @PostMapping("/sessions/{sessionId}/recommendation-sets/{setId}/goal-confirmation")
    public ApiResponse<ConfirmationTokenView> prepareGoalConfirmation(@PathVariable String sessionId,
            @PathVariable String setId, @RequestBody ConfirmationTokenCommand command) {
        return ApiResponse.ok(planning.prepareGoalConfirmation(current(), sessionId, setId, command));
    }

    @PostMapping("/sessions/{sessionId}/recommendation-sets/{setId}/goal")
    public ApiResponse<SessionView> confirmGoal(@PathVariable String sessionId,
            @PathVariable String setId, @RequestBody ConfirmGoalCommand command) {
        return ApiResponse.ok(planning.confirmGoal(current(), sessionId, setId, command));
    }

    @PostMapping("/sessions/{sessionId}/canvas/generate")
    public ApiResponse<CanvasView> generateCanvas(@PathVariable String sessionId,
            @RequestBody(required = false) GenerateCanvasCommand command) {
        return ApiResponse.ok(planning.generateCanvas(current(), sessionId, command));
    }

    @PostMapping("/sessions/{sessionId}/canvas/tasks")
    public ResponseEntity<ApiResponse<TaskView>> startCanvasGeneration(@PathVariable String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) GenerateCanvasCommand command) {
        GenerateCanvasCommand resolved = command != null && command.requestId() != null && !command.requestId().isBlank()
                ? command : new GenerateCanvasCommand(idempotencyKey,
                        command == null ? null : command.expectedVersion(),
                        command == null ? "STANDARD" : command.generationScale());
        return ResponseEntity.accepted().body(ApiResponse.ok(async.startCanvasGeneration(current(), sessionId, resolved)));
    }

    @PostMapping("/sessions/{sessionId}/canvas/nodes")
    public ApiResponse<CanvasView> createCanvasNode(@PathVariable String sessionId,
            @RequestBody CanvasNodeCreateCommand command) {
        return ApiResponse.ok(planning.createCanvasNode(current(), sessionId, command));
    }

    @PatchMapping("/sessions/{sessionId}/canvas/nodes/{logicalNodeId}")
    public ApiResponse<CanvasView> updateCanvasNode(@PathVariable String sessionId,
            @PathVariable String logicalNodeId, @RequestBody CanvasNodeUpdateCommand command) {
        return ApiResponse.ok(planning.updateCanvasNode(current(), sessionId, logicalNodeId, command));
    }

    @PatchMapping("/sessions/{sessionId}/canvas/nodes")
    public ApiResponse<CanvasView> updateCanvasNodes(@PathVariable String sessionId,
            @RequestBody CanvasNodeBatchUpdateCommand command) {
        return ApiResponse.ok(planning.updateCanvasNodes(current(), sessionId, command));
    }

    @DeleteMapping("/sessions/{sessionId}/canvas/nodes/{logicalNodeId}")
    public ApiResponse<CanvasView> deleteCanvasNode(@PathVariable String sessionId,
            @PathVariable String logicalNodeId, @RequestBody(required = false) CanvasNodeDeleteCommand command) {
        return ApiResponse.ok(planning.deleteCanvasNode(current(), sessionId, logicalNodeId, command));
    }

    @PostMapping("/sessions/{sessionId}/canvas/nodes/{logicalNodeId}/split")
    public ApiResponse<CanvasView> splitCanvasNode(@PathVariable String sessionId,
            @PathVariable String logicalNodeId, @RequestBody CanvasNodeSplitCommand command) {
        return ApiResponse.ok(planning.splitCanvasNode(current(), sessionId, logicalNodeId, command));
    }

    @PostMapping("/sessions/{sessionId}/canvas/nodes/merge")
    public ApiResponse<CanvasView> mergeCanvasNodes(@PathVariable String sessionId,
            @RequestBody CanvasNodeMergeCommand command) {
        return ApiResponse.ok(planning.mergeCanvasNodes(current(), sessionId, command));
    }

    @PostMapping("/sessions/{sessionId}/canvas/relations")
    public ApiResponse<CanvasView> addCanvasRelation(@PathVariable String sessionId,
            @RequestBody CanvasRelationCommand command) {
        return ApiResponse.ok(planning.addCanvasRelation(current(), sessionId, command));
    }

    @GetMapping("/sessions/{sessionId}/canvas/versions")
    public ApiResponse<CanvasVersionPage> canvasVersions(@PathVariable String sessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(planning.canvasVersions(current(), sessionId, page, size));
    }

    @GetMapping("/sessions/{sessionId}/canvas/versions/compare")
    public ApiResponse<CanvasVersionDiff> compareCanvasVersions(@PathVariable String sessionId,
            @RequestParam int from, @RequestParam int to) {
        return ApiResponse.ok(planning.compareCanvasVersions(current(), sessionId, from, to));
    }

    @GetMapping("/sessions/{sessionId}/canvas/versions/{version}")
    public ApiResponse<CanvasView> canvasVersion(@PathVariable String sessionId, @PathVariable int version) {
        return ApiResponse.ok(planning.canvasVersion(current(), sessionId, version));
    }

    @GetMapping("/sessions/{sessionId}/execution")
    public ApiResponse<ExecutionOverview> execution(@PathVariable String sessionId) {
        return ApiResponse.ok(execution.overview(current(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/canvas/proposals")
    public ApiResponse<ProposalView> generateProposal(@PathVariable String sessionId,
            @RequestBody GenerateProposalCommand command) {
        return ApiResponse.ok(execution.generateProposal(current(), sessionId, command));
    }

    @GetMapping("/sessions/{sessionId}/canvas/proposals")
    public ApiResponse<List<ProposalView>> proposals(@PathVariable String sessionId) {
        return ApiResponse.ok(execution.proposals(current(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/canvas/proposals/tasks")
    public ResponseEntity<ApiResponse<TaskView>> startNodeInference(@PathVariable String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody GenerateNodeInferenceCommand command) {
        GenerateNodeInferenceCommand resolved = command.requestId() != null && !command.requestId().isBlank()
                ? command : new GenerateNodeInferenceCommand(idempotencyKey, command.targetNodeId(),
                        command.direction(), command.depth(), command.instruction(), command.expectedVersion());
        return ResponseEntity.accepted().body(ApiResponse.ok(async.startNodeInference(current(), sessionId, resolved)));
    }

    @GetMapping("/sessions/{sessionId}/canvas/proposals/{proposalId}")
    public ApiResponse<ProposalView> proposal(@PathVariable String sessionId, @PathVariable String proposalId) {
        return ApiResponse.ok(execution.proposal(current(), sessionId, proposalId));
    }

    @PostMapping("/sessions/{sessionId}/canvas/proposals/{proposalId}/discard")
    public ApiResponse<ProposalView> discardProposal(@PathVariable String sessionId,
            @PathVariable String proposalId) {
        return ApiResponse.ok(execution.discardProposal(current(), sessionId, proposalId));
    }

    @PostMapping("/sessions/{sessionId}/canvas/proposals/{proposalId}/decide")
    public ApiResponse<ProposalApplyResult> decideProposal(@PathVariable String sessionId,
            @PathVariable String proposalId, @RequestBody DecideProposalCommand command) {
        return ApiResponse.ok(execution.decideProposal(current(), sessionId, proposalId, command));
    }

    @PostMapping("/sessions/{sessionId}/plans")
    public ApiResponse<LearningPlanView> createPlan(@PathVariable String sessionId,
            @RequestBody CreateLearningPlanCommand command) {
        return ApiResponse.ok(execution.createPlan(current(), sessionId, command));
    }

    @GetMapping("/sessions/{sessionId}/plans/active")
    public ApiResponse<LearningPlanView> activePlan(@PathVariable String sessionId) {
        return ApiResponse.ok(execution.activePlan(current(), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/plans/{planId}/revisions")
    public ApiResponse<List<LearningPlanRevisionView>> planRevisions(@PathVariable String sessionId,
            @PathVariable String planId) {
        return ApiResponse.ok(execution.planRevisions(current(), sessionId, planId));
    }

    @PatchMapping("/sessions/{sessionId}/plans/{planId}")
    public ApiResponse<LearningPlanView> updatePlan(@PathVariable String sessionId,
            @PathVariable String planId, @RequestBody UpdatePlanCommand command) {
        return ApiResponse.ok(execution.updatePlan(current(), sessionId, planId, command));
    }

    @PatchMapping("/sessions/{sessionId}/plans/{planId}/tasks/{taskId}")
    public ApiResponse<LearningPlanView> updateTask(@PathVariable String sessionId,
            @PathVariable String planId, @PathVariable String taskId,
            @RequestBody UpdateTaskCommand command) {
        return ApiResponse.ok(execution.updateTask(current(), sessionId, planId, taskId, command));
    }

    @PostMapping("/sessions/{sessionId}/plans/{planId}/evidences")
    public ApiResponse<LearningPlanView> addEvidence(@PathVariable String sessionId,
            @PathVariable String planId, @RequestBody CreateEvidenceCommand command) {
        return ApiResponse.ok(execution.addEvidence(current(), sessionId, planId, command));
    }

    @PutMapping("/sessions/{sessionId}/plans/{planId}/reviews/{week}")
    public ApiResponse<LearningPlanView> saveReview(@PathVariable String sessionId,
            @PathVariable String planId, @PathVariable int week,
            @RequestBody WeeklyReviewCommand command) {
        return ApiResponse.ok(execution.saveWeeklyReview(current(), sessionId, planId, week, command));
    }

    @PostMapping("/sessions/{sessionId}/validations")
    public ApiResponse<ValidationView> startValidation(@PathVariable String sessionId,
            @RequestBody StartValidationCommand command) {
        return ApiResponse.ok(execution.startValidation(current(), sessionId, command));
    }

    @PostMapping("/sessions/{sessionId}/validation-batches")
    public ResponseEntity<ApiResponse<TaskView>> startValidationBatch(@PathVariable String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody StartValidationBatchCommand command) {
        StartValidationBatchCommand resolved = command != null && command.requestId() != null
                && !command.requestId().isBlank() ? command
                : new StartValidationBatchCommand(idempotencyKey,
                        command == null ? null : command.expectedCanvasVersion(),
                        command == null ? null : command.method(), command == null ? null : command.items());
        return ResponseEntity.accepted().body(ApiResponse.ok(async.startValidationBatch(current(), sessionId, resolved)));
    }

    @GetMapping("/sessions/{sessionId}/validations")
    public ApiResponse<List<ValidationView>> validations(@PathVariable String sessionId,
            @RequestParam(required = false) String batchId) {
        return ApiResponse.ok(execution.validations(current(), sessionId, batchId));
    }

    @PostMapping("/sessions/{sessionId}/validations/{validationId}/confirm")
    public ApiResponse<ValidationView> confirmValidation(@PathVariable String sessionId,
            @PathVariable String validationId, @RequestBody ConfirmValidationCommand command) {
        return ApiResponse.ok(execution.confirmValidation(current(), sessionId, validationId, command));
    }

    @PostMapping("/sessions/{sessionId}/canvas/versions/{version}/restore")
    public ApiResponse<CanvasView> restoreCanvas(@PathVariable String sessionId,
            @PathVariable int version, @RequestBody RestoreCanvasCommand command) {
        return ApiResponse.ok(execution.restoreCanvas(current(), sessionId, version, command));
    }

    private static CurrentAccount current() {
        return SecurityConfig.currentAccount();
    }

    private static long eventSequence(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
