package com.jobproof.modules.mockinterview.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.mockinterview.application.MockInterviewService;
import com.jobproof.modules.mockinterview.application.MockInterviewService.AnswerCommand;
import com.jobproof.modules.mockinterview.application.MockInterviewService.AudioChunkView;
import com.jobproof.modules.mockinterview.application.MockInterviewService.CreateSessionCommand;
import com.jobproof.modules.mockinterview.application.MockInterviewService.DashboardView;
import com.jobproof.modules.mockinterview.application.MockInterviewService.DraftCommand;
import com.jobproof.modules.mockinterview.application.MockInterviewService.DraftView;
import com.jobproof.modules.mockinterview.application.MockInterviewService.ModeCommand;
import com.jobproof.modules.mockinterview.application.MockInterviewService.ReportView;
import com.jobproof.modules.mockinterview.application.MockInterviewService.SessionSummary;
import com.jobproof.modules.mockinterview.application.MockInterviewService.SessionView;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/mock-interviews")
public class MockInterviewController {

    private final MockInterviewService service;

    public MockInterviewController(MockInterviewService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(service.dashboard(SecurityConfig.currentAccount()));
    }

    @PostMapping("/drafts")
    public ApiResponse<DraftView> createDraft() {
        return ApiResponse.ok(service.createDraft(SecurityConfig.currentAccount()));
    }

    @GetMapping("/drafts/{draftId}")
    public ApiResponse<DraftView> draft(@PathVariable String draftId) {
        return ApiResponse.ok(service.getDraft(SecurityConfig.currentAccount(), draftId));
    }

    @PatchMapping("/drafts/{draftId}")
    public ApiResponse<DraftView> saveDraft(@PathVariable String draftId, @RequestBody DraftRequest request) {
        return ApiResponse.ok(service.saveDraft(SecurityConfig.currentAccount(), draftId,
                new DraftCommand(request.step(), request.payload(), request.expectedVersion())));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionSummary>> sessions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.list(SecurityConfig.currentAccount(), status, mode, type, limit));
    }

    @PostMapping("/sessions")
    public ApiResponse<SessionView> createSession(@RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(service.createSession(SecurityConfig.currentAccount(), new CreateSessionCommand(
                request.draftId(), request.resumeId(), request.taxonomyNodeId(),
                request.taxonomyCategoryId(), request.taxonomyGroupId(), request.careerRecordIds(),
                request.careerFileIds(), request.positionName(), request.companyName(), request.userNote(),
                request.mode(), request.interviewType(), request.difficulty(), request.durationMinutes(),
                request.questionCount(), request.languageCode(), request.feedbackMode(), request.followUpEnabled(),
                request.consentConfirmed(), request.jobMatchId())));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<SessionView> session(@PathVariable String sessionId) {
        return ApiResponse.ok(service.get(SecurityConfig.currentAccount(), sessionId));
    }

    @PutMapping("/sessions/{sessionId}/text-drafts/{questionId}")
    public ApiResponse<MockInterviewService.AnswerView> saveTextDraft(
            @PathVariable String sessionId, @PathVariable String questionId, @RequestBody AnswerRequest request) {
        return ApiResponse.ok(service.saveTextDraft(SecurityConfig.currentAccount(), sessionId, questionId,
                new AnswerCommand(request.answer(), request.expectedVersion())));
    }

    @PostMapping("/sessions/{sessionId}/answers/{questionId}")
    public ApiResponse<SessionView> submitAnswer(
            @PathVariable String sessionId, @PathVariable String questionId, @RequestBody AnswerRequest request) {
        return ApiResponse.ok(service.submitAnswer(SecurityConfig.currentAccount(), sessionId, questionId,
                new AnswerCommand(request.answer(), request.expectedVersion())));
    }

    @PatchMapping("/sessions/{sessionId}/transcript/{questionId}")
    public ApiResponse<MockInterviewService.AnswerView> transcript(
            @PathVariable String sessionId, @PathVariable String questionId, @RequestBody AnswerRequest request) {
        return ApiResponse.ok(service.saveTranscript(SecurityConfig.currentAccount(), sessionId, questionId,
                new AnswerCommand(request.answer(), request.expectedVersion())));
    }

    @PostMapping("/sessions/{sessionId}/audio/chunks")
    public ApiResponse<AudioChunkView> audioChunk(
            @PathVariable String sessionId,
            @RequestParam String questionId,
            @RequestParam int sequence,
            @RequestParam(defaultValue = "0") int durationMs,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.uploadAudio(SecurityConfig.currentAccount(), sessionId, questionId,
                sequence, durationMs, file));
    }

    @PostMapping("/sessions/{sessionId}/switch-mode")
    public ApiResponse<SessionView> switchMode(@PathVariable String sessionId, @RequestBody ModeRequest request) {
        return ApiResponse.ok(service.switchMode(SecurityConfig.currentAccount(), sessionId, new ModeCommand(request.mode())));
    }

    @PostMapping("/sessions/{sessionId}/pause")
    public ApiResponse<SessionView> pause(@PathVariable String sessionId) {
        return ApiResponse.ok(service.pause(SecurityConfig.currentAccount(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/resume")
    public ApiResponse<SessionView> resume(@PathVariable String sessionId) {
        return ApiResponse.ok(service.resume(SecurityConfig.currentAccount(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public ApiResponse<ReportView> complete(@PathVariable String sessionId) {
        return ApiResponse.ok(service.complete(SecurityConfig.currentAccount(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/abandon")
    public ApiResponse<SessionView> abandon(@PathVariable String sessionId) {
        return ApiResponse.ok(service.abandon(SecurityConfig.currentAccount(), sessionId));
    }

    @PostMapping("/sessions/{sessionId}/retry")
    public ApiResponse<SessionView> retry(@PathVariable String sessionId) {
        return ApiResponse.ok(service.retry(SecurityConfig.currentAccount(), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/report")
    public ApiResponse<ReportView> sessionReport(@PathVariable String sessionId) {
        return ApiResponse.ok(service.report(SecurityConfig.currentAccount(), sessionId));
    }

    @GetMapping("/reports/{sessionId}")
    public ApiResponse<ReportView> report(@PathVariable String sessionId) {
        return ApiResponse.ok(service.report(SecurityConfig.currentAccount(), sessionId));
    }

    public record DraftRequest(int step, Map<String, Object> payload, Integer expectedVersion) {}
    public record CreateSessionRequest(String draftId, String resumeId, String taxonomyNodeId,
            String taxonomyCategoryId, String taxonomyGroupId,
            List<String> careerRecordIds, List<String> careerFileIds, String positionName, String companyName,
            String userNote, String mode, String interviewType, String difficulty, int durationMinutes,
            int questionCount, String languageCode, String feedbackMode, boolean followUpEnabled,
            boolean consentConfirmed, String jobMatchId) {}
    public record AnswerRequest(String answer, Integer expectedVersion) {}
    public record ModeRequest(String mode) {}
}
