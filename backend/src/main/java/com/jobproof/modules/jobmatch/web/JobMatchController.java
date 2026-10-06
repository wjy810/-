package com.jobproof.modules.jobmatch.web;

import com.jobproof.infrastructure.security.SecurityConfig;
import com.jobproof.infrastructure.web.ApiResponse;
import com.jobproof.modules.career.application.CareerFileService;
import com.jobproof.modules.jobmatch.application.JobMatchService;
import com.jobproof.modules.jobmatch.application.JobMatchExportService;
import com.jobproof.modules.jobmatch.application.JobMatchService.AnalysisStart;
import com.jobproof.modules.jobmatch.application.JobMatchService.AnalyzeCommand;
import com.jobproof.modules.jobmatch.application.JobMatchService.AuthorizationCommand;
import com.jobproof.modules.jobmatch.application.JobMatchService.CapabilitiesView;
import com.jobproof.modules.jobmatch.application.JobMatchService.ClarificationCommand;
import com.jobproof.modules.jobmatch.application.JobMatchService.ClaimView;
import com.jobproof.modules.jobmatch.application.JobMatchService.CreateCommand;
import com.jobproof.modules.jobmatch.application.JobMatchService.DashboardView;
import com.jobproof.modules.jobmatch.application.JobMatchService.FeedbackCommand;
import com.jobproof.modules.jobmatch.application.JobMatchService.ImprovementCommand;
import com.jobproof.modules.jobmatch.application.JobMatchService.ImprovementView;
import com.jobproof.modules.jobmatch.application.JobMatchService.HistoryPage;
import com.jobproof.modules.jobmatch.application.JobMatchService.ReportComparisonView;
import com.jobproof.modules.jobmatch.application.JobMatchService.ReportVersionView;
import com.jobproof.modules.jobmatch.application.JobMatchService.JdStructureCommand;
import com.jobproof.modules.jobmatch.application.JobMatchService.MatchSummary;
import com.jobproof.modules.jobmatch.application.JobMatchService.MutationCommand;
import com.jobproof.modules.jobmatch.application.JobMatchService.MatchView;
import com.jobproof.modules.jobmatch.application.JobMatchService.OptimizationLink;
import com.jobproof.modules.jobmatch.application.JobMatchService.RedactionPreview;
import com.jobproof.modules.jobmatch.application.JobMatchService.ReportView;
import com.jobproof.modules.jobmatch.application.JobMatchService.ResumeOption;
import com.jobproof.modules.jobmatch.application.JobMatchService.ResumeSelectionCommand;
import com.jobproof.modules.jobmatch.application.JobMatchService.SimilarDirectionsView;
import com.jobproof.modules.jobmatch.application.JobMatchSseService;
import com.jobproof.modules.jobmatch.domain.JobMatchModels.EvidenceCandidate;
import com.jobproof.modules.resumeimport.application.ResumeImportService;
import com.jobproof.shared.auth.CurrentAccount;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/job-matches")
public class JobMatchController {
    private final JobMatchService matches;
    private final JobMatchSseService events;
    private final CareerFileService careerFiles;
    private final ResumeImportService resumeImports;
    private final JobMatchExportService exports;

    public JobMatchController(JobMatchService matches, JobMatchSseService events,
            CareerFileService careerFiles, ResumeImportService resumeImports, JobMatchExportService exports) {
        this.matches = matches;
        this.events = events;
        this.careerFiles = careerFiles;
        this.resumeImports = resumeImports;
        this.exports = exports;
    }

    @GetMapping("/capabilities")
    public ApiResponse<CapabilitiesView> capabilities() {
        return ApiResponse.ok(matches.capabilities(current()));
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(matches.dashboard(current()));
    }

    @GetMapping
    public ApiResponse<List<MatchSummary>> history(@RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean archived) {
        return ApiResponse.ok(matches.history(current(), status, query, archived));
    }

    @GetMapping("/history")
    public ApiResponse<HistoryPage> historyPage(@RequestParam(required = false) String status,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(matches.historyPage(current(), status, query, archived, page, size));
    }

    @PostMapping
    public ApiResponse<MatchView> create(@RequestBody CreateCommand command) {
        return ApiResponse.ok(matches.create(current(), command));
    }

    @PostMapping(value = "/jd/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MatchView> uploadJd(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String requestId) throws Exception {
        return ApiResponse.ok(matches.createFromUpload(current(), file.getOriginalFilename(), file.getBytes(), requestId));
    }

    @PostMapping("/jd/fetch-url")
    public ApiResponse<MatchView> fetchJd(@RequestBody UrlCommand command) {
        return ApiResponse.ok(matches.createFromUrl(current(), command.url(), command.requestId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<MatchView> get(@PathVariable String id) {
        return ApiResponse.ok(matches.get(current(), id));
    }

    @PostMapping("/{id}/jd/parse")
    public ApiResponse<MatchView> parse(@PathVariable String id) {
        return ApiResponse.ok(matches.get(current(), id));
    }

    @PatchMapping("/{id}/jd-structure")
    public ApiResponse<MatchView> updateJd(@PathVariable String id, @RequestBody JdStructureCommand command) {
        return ApiResponse.ok(matches.updateJd(current(), id, command));
    }

    @GetMapping("/{id}/resume-options")
    public ApiResponse<List<ResumeOption>> resumeOptions(@PathVariable String id) {
        return ApiResponse.ok(matches.resumeOptions(current(), id));
    }

    @PutMapping("/{id}/resume-selection")
    public ApiResponse<MatchView> selectResume(@PathVariable String id,
            @RequestBody ResumeSelectionCommand command) {
        return ApiResponse.ok(matches.selectResume(current(), id, command));
    }

    @PostMapping(value = "/{id}/resume-imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ResumeImportUpload>> uploadResume(@PathVariable String id,
            @RequestParam("file") MultipartFile file) throws Exception {
        matches.get(current(), id);
        CareerFileService.UploadView upload = careerFiles.upload(current(), "RESUME", null, null,
                file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.accepted().body(ApiResponse.ok(new ResumeImportUpload(upload, null)));
    }

    @PostMapping("/{id}/resume-imports")
    public ResponseEntity<ApiResponse<ResumeImportService.ImportStartView>> createResumeImport(
            @PathVariable String id, @RequestBody ResumeImportCreate command) {
        matches.get(current(), id);
        var created = resumeImports.create(current(),
                new ResumeImportService.CreateCommand(command.careerFileId(), command.pastedText()));
        return ResponseEntity.accepted().body(ApiResponse.ok(created));
    }

    @GetMapping("/{id}/evidence-recommendations")
    public ApiResponse<List<EvidenceCandidate>> evidence(@PathVariable String id) {
        return ApiResponse.ok(matches.evidenceRecommendations(current(), id));
    }

    @PutMapping("/{id}/evidence-selection")
    public ApiResponse<MatchView> authorize(@PathVariable String id, @RequestBody AuthorizationCommand command) {
        return ApiResponse.ok(matches.authorizeEvidence(current(), id, command));
    }

    @GetMapping("/{id}/redaction-preview")
    public ApiResponse<RedactionPreview> redaction(@PathVariable String id) {
        return ApiResponse.ok(matches.redactionPreview(current(), id));
    }

    @PostMapping("/{id}/authorization")
    public ApiResponse<MatchView> authorization(@PathVariable String id,
            @RequestBody AuthorizationCommand command) {
        return ApiResponse.ok(matches.authorizeEvidence(current(), id, command));
    }

    @DeleteMapping("/{id}/authorization")
    public ApiResponse<MatchView> revoke(@PathVariable String id,
            @RequestBody(required = false) MutationCommand command) {
        return ApiResponse.ok(matches.revokeAuthorization(current(), id, command));
    }

    @PostMapping("/{id}/analyze")
    public ResponseEntity<ApiResponse<AnalysisStart>> analyze(@PathVariable String id,
            @RequestBody AnalyzeCommand command) {
        return ResponseEntity.accepted().body(ApiResponse.ok(matches.analyze(current(), id, command)));
    }

    @GetMapping("/{id}/status")
    public ApiResponse<MatchView> status(@PathVariable String id) {
        return ApiResponse.ok(matches.get(current(), id));
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(required = false) Long after) {
        long sequence = after == null ? parseSequence(lastEventId) : Math.max(0, after);
        return events.subscribe(current().accountId(), id, sequence);
    }

    @PostMapping("/{id}/clarifications")
    public ApiResponse<MatchView> clarifications(@PathVariable String id,
            @RequestBody ClarificationCommand command) {
        return ApiResponse.ok(matches.answerClarifications(current(), id, command));
    }

    @PostMapping("/{id}/resume-analysis")
    public ApiResponse<MatchView> resume(@PathVariable String id,
            @RequestBody(required = false) MutationCommand command) {
        return ApiResponse.ok(matches.resumeAnalysis(current(), id, command));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<MatchView> cancel(@PathVariable String id,
            @RequestBody(required = false) MutationCommand command) {
        return ApiResponse.ok(matches.cancel(current(), id, command));
    }

    @GetMapping("/{id}/report")
    public ApiResponse<ReportView> report(@PathVariable String id) {
        return ApiResponse.ok(matches.report(current(), id));
    }

    @GetMapping("/{id}/report/versions")
    public ApiResponse<List<ReportVersionView>> reportVersions(@PathVariable String id) {
        return ApiResponse.ok(matches.reportVersions(current(), id));
    }

    @GetMapping("/{id}/report/compare")
    public ApiResponse<ReportComparisonView> compareReportVersions(@PathVariable String id,
            @RequestParam int fromVersion, @RequestParam int toVersion) {
        return ApiResponse.ok(matches.compareReportVersions(current(), id, fromVersion, toVersion));
    }

    @GetMapping("/{id}/report/{section:strengths|gaps|evidence-matrix|learning-plan}")
    public ApiResponse<Object> reportSection(@PathVariable String id, @PathVariable String section) {
        ReportView report = matches.report(current(), id);
        Object value = switch (section) {
            case "learning-plan" -> report.learningPlan();
            case "strengths" -> nested(report.report(), "ai", "strengths");
            case "gaps" -> nested(report.report(), "ai", "gaps");
            default -> nested(report.report(), "ai", "evidenceMatrix");
        };
        return ApiResponse.ok(value);
    }

    @GetMapping("/{id}/report/claims/{claimId}")
    public ApiResponse<ClaimView> claim(@PathVariable String id, @PathVariable String claimId) {
        return ApiResponse.ok(matches.getClaim(current(), id, claimId));
    }

    @PostMapping("/{id}/report/claims/{claimId}/feedback")
    public ApiResponse<ClaimView> feedback(@PathVariable String id, @PathVariable String claimId,
            @RequestBody FeedbackCommand command) {
        return ApiResponse.ok(matches.feedback(current(), id, claimId, command));
    }

    @PatchMapping("/{id}/learning-tasks/{taskId}")
    public ApiResponse<ImprovementView> improvement(@PathVariable String id, @PathVariable String taskId,
            @RequestBody ImprovementCommand command) {
        return ApiResponse.ok(matches.updateImprovement(current(), id, taskId, command));
    }

    @PostMapping("/{id}/actions/resume-optimization")
    public ApiResponse<OptimizationLink> optimization(@PathVariable String id,
            @RequestBody(required = false) MutationCommand command) {
        return ApiResponse.ok(matches.createOptimization(current(), id, command));
    }

    @GetMapping("/{id}/similar-jobs")
    public ApiResponse<SimilarDirectionsView> similarJobs(@PathVariable String id) {
        return ApiResponse.ok(matches.careerDirections(current(), id));
    }

    @PostMapping("/{id}/exports")
    public ApiResponse<JobMatchExportService.ExportView> createExport(@PathVariable String id,
            @RequestBody JobMatchExportService.ExportCommand command) {
        return ApiResponse.ok(exports.create(current(), id, command));
    }

    @GetMapping("/exports/{exportId}")
    public ApiResponse<JobMatchExportService.ExportView> export(@PathVariable String exportId) {
        return ApiResponse.ok(exports.get(current(), exportId));
    }

    @GetMapping("/exports/{exportId}/download")
    public ResponseEntity<byte[]> downloadExport(@PathVariable String exportId) {
        JobMatchExportService.Download download = exports.download(current(), exportId);
        String encoded = java.net.URLEncoder.encode(download.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.body().length)
                .body(download.body());
    }

    private static CurrentAccount current() { return SecurityConfig.currentAccount(); }
    private static long parseSequence(String value) {
        try { return value == null ? 0 : Math.max(0, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }
    @SuppressWarnings("unchecked")
    private static Object nested(Map<String, Object> source, String parent, String key) {
        Object value = source.get(parent);
        return value instanceof Map<?, ?> map ? ((Map<String, Object>) map).getOrDefault(key, List.of()) : List.of();
    }

    public record UrlCommand(String url, String requestId) {}
    public record ResumeImportCreate(String careerFileId, String pastedText) {}
    public record ResumeImportUpload(CareerFileService.UploadView upload, String resumeImportId) {}
}
