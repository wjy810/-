package com.jobproof.modules.aibilling.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.shared.error.AppException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Prices describe user actions; provider retries and repair calls never create another action. */
@Component
public class AiOperationCatalog {
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    public AiOperationCatalog(ObjectMapper json, JdbcTemplate jdbc) { this.json = json; this.jdbc = jdbc; }

    public Operation resolve(String accountId, String path, String body) {
        if (path == null || path.length() > 512 || path.contains("?") || path.contains("..")) {
            throw AppException.user("AI_OPERATION_INVALID", "AI 请求地址无效");
        }
        JsonNode payload;
        try { payload = body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body); }
        catch (Exception e) { throw AppException.user("AI_OPERATION_INVALID", "AI 请求格式无效"); }
        String code = null;
        if (path.matches("/api/v1/tasks/[^/]+/retry")) {
            String taskId=path.split("/")[4];
            var types=jdbc.queryForList("SELECT task_type FROM async_tasks WHERE id=? AND account_id=? AND status IN ('FAILED','CANCELLED')",String.class,taskId,accountId);
            if (types.isEmpty()) return null;
            return types.getFirst().startsWith("CAREER_PLANNING_") ? new Operation(types.getFirst(),1) : null;
        }
        if (path.matches("/api/v1/ai-resume/conversations/[^/]+/messages/(respond|stream)")) code = "RESUME_CHAT";
        else if (path.matches("/api/v1/ai-resume/conversations/[^/]+/cards/[^/]+/records/\\d+/description-suggestion")) code = "RESUME_DESCRIPTION";
        else if (path.matches("/api/v1/ai-resume/conversations/[^/]+/cards/[^/]+/summary-suggestions")) code = "RESUME_SUMMARY";
        else if (path.matches("/api/v1/ai-resume/conversations/[^/]+/cards/[^/]+/skill-suggestions")) code = "NAMES".equals(payload.path("phase").asText("NAMES")) ? "RESUME_SKILL_NAMES" : "RESUME_SKILL_DETAILS";
        else if (path.matches("/api/v1/ai-resume/conversations/[^/]+/cards/[^/]+/certificate-suggestions")) code = freeNames(payload) ? null : "RESUME_CERTIFICATE_DETAILS";
        else if (path.matches("/api/v1/ai-resume/conversations/[^/]+/cards/[^/]+/honor-suggestions")) code = freeNames(payload) ? null : "RESUME_HONOR_DETAILS";
        else if (path.matches("/api/v1/ai-resume/conversations/[^/]+/cards/[^/]+/credential-recommendations")) code = "RESUME_CREDENTIAL_RECOMMENDATIONS";
        else if (path.matches("/api/v1/ai-resume/conversations/[^/]+/branches/[^/]+/translate")) code = "RESUME_TRANSLATION";
        else if (path.matches("/api/v1/resumes/[^/]+/ai-candidates")) code = "RESUME_CANDIDATE";
        else if (path.matches("/api/v1/career-library/records/[^/]+/ai-candidates")) code = "CAREER_RECORD_OPTIMIZE";
        else if (path.matches("/api/v1/job-matches/[^/]+/(analyze|resume-analysis)")) code = "JOB_MATCH_ANALYSIS";
        else if (path.matches("/api/v1/career-planning/sessions/[^/]+/.*")) {
            String action = path.substring(path.indexOf("/sessions/") + 10).replaceFirst("^[^/]+/", "");
            code = switch (action) {
                case "interviews", "interviews/tasks" -> "CAREER_PLANNING_INTERVIEW";
                case "recommendations", "recommendations/tasks" -> "CAREER_PLANNING_RECOMMENDATIONS";
                case "canvas/generate", "canvas/tasks" -> "CAREER_PLANNING_CANVAS";
                case "canvas/proposals", "canvas/proposals/tasks" -> "CAREER_PLANNING_CANVAS_PROPOSAL";
                case "validations/quiz" -> "CAREER_PLANNING_QUIZ";
                case "validations" -> "CAREER_PLANNING_VALIDATION";
                case "validation-batches" -> "CAREER_PLANNING_VALIDATION_BATCH";
                default -> null;
            };
        } else if (path.equals("/api/v1/mock-interviews/sessions")) code = "MOCK_INTERVIEW_QUESTIONS";
        else if (path.matches("/api/v1/mock-interviews/sessions/[^/]+/answers/[^/]+")) code = "MOCK_INTERVIEW_EVALUATE";
        else if (path.matches("/api/v1/mock-interviews/sessions/[^/]+/evaluate")) {
            String sessionId = path.split("/")[5];
            int units = jdbc.queryForObject("SELECT COUNT(*) FROM mock_interview_answers a JOIN mock_interview_sessions s ON s.id=a.session_id WHERE s.id=? AND s.account_id=? AND a.status='SUBMITTED' AND (a.score_json IS NULL OR a.score_json='{}')", Integer.class, sessionId, accountId);
            return new Operation("MOCK_INTERVIEW_EVALUATE", units);
        }
        return code == null ? null : new Operation(code, 1);
    }

    private static boolean freeNames(JsonNode payload) {
        String phase = payload.path("phase").asText("").trim();
        return phase.isEmpty() || "NAMES".equalsIgnoreCase(phase);
    }

    public static String normalize(String operation) {
        return operation != null && operation.startsWith("RESUME_") && operation.endsWith("_RECOMMENDATIONS")
                ? "RESUME_CREDENTIAL_RECOMMENDATIONS" : operation;
    }

    public static String hash(String body) { return hash((body == null ? "" : body).getBytes(StandardCharsets.UTF_8)); }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public record Operation(String code, int units) {}
}
