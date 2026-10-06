package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumePermanentDeletionIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void permanentlyDeletingFrozenVersionRemovesArtifactsButKeepsMasterAndOtherVersions() throws Exception {
        Session owner = registerAndLogin("delete-version+" + System.nanoTime() + "@example.com");
        String masterId = createMaster(owner.cookie(), "版本删除测试");
        String deletedVersion = seedVersion(owner.accountId(), masterId);
        String keptVersion = seedVersion(owner.accountId(), masterId);
        String layoutId = seedLayout(owner.accountId(), masterId, deletedVersion);
        String fileId = seedArtifact(owner.accountId(), layoutId, deletedVersion);
        String refId = id();
        jdbc.update("INSERT INTO career_library_record_refs(id,record_id,resume_version_id,active,created_at) VALUES(?,?,?,?,?)",
                refId, id(), deletedVersion, 1, Instant.now());
        String historyId = "APPLICATION:" + id();
        jdbc.update("INSERT INTO retired_career_history_records(id,account_id,record_type,source_id,parent_source_id,secondary_parent_id,status,label,detail_text,content_text,payload_json_a,payload_json_b,reference_id_a,reference_id_b,reference_id_c,event_time,sequence_no,archived_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                historyId, owner.accountId(), "APPLICATION", id(), null, null, "ARCHIVED", "历史投递", null, null,
                null, null, deletedVersion, null, null, Instant.now(), 0, Instant.now());

        JsonNode preview = preview(owner.cookie(), "RESUME_VERSION", deletedVersion);
        assertThat(preview.path("canProceed").asBoolean()).isTrue();
        assertThat(preview.path("impactSummary").asText()).contains("永久删除");

        JsonNode done = deleteAndAwait(owner.cookie(), "RESUME_VERSION", deletedVersion);
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(count("resume_versions", deletedVersion)).isZero();
        assertThat(count("resume_versions", keptVersion)).isOne();
        assertThat(count("resume_masters", masterId)).isOne();
        assertThat(count("resume_render_artifacts", deletedVersion, "content_version_id")).isZero();
        assertThat(count("private_files", fileId)).isZero();
        assertThat(count("career_library_record_refs", refId)).isZero();
        assertThat(count("retired_career_history_records", historyId)).isZero();
    }

    @Test
    void permanentlyDeletingMasterCascadesAiResumeAndMatchingData() throws Exception {
        Session owner = registerAndLogin("delete-master+" + System.nanoTime() + "@example.com");
        String masterId = createMaster(owner.cookie(), "主简历删除测试");
        String versionId = seedVersion(owner.accountId(), masterId);
        String branchId = id();
        String revisionId = id();
        String conversationId = id();
        String messageId = id();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO resume_branches(id,account_id,master_id,parent_branch_id,branch_type,title,language_code,job_version_id,current_revision_id,source_revision_id,status,version_no,created_at,updated_at,archived_at,review_metadata_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                branchId, owner.accountId(), masterId, null, "BASE", "基础分支", "zh-CN", null, revisionId, null,
                "ACTIVE", 0, now, now, null, null);
        jdbc.update("INSERT INTO resume_revisions(id,account_id,master_id,branch_id,revision_no,source,source_object_id,content_schema_version,content_json,layout_instance_id,content_hash,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                revisionId, owner.accountId(), masterId, branchId, 1, "USER", null, "resume-content-v3", "{}", null,
                "0".repeat(64), now);
        jdbc.update("INSERT INTO ai_resume_conversations(id,account_id,master_id,active_branch_id,status,onboarding_stage,identity_type,last_sequence,summary_json,version_no,created_at,updated_at,archived_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                conversationId, owner.accountId(), masterId, branchId, "ACTIVE", "TARGET_JOB", "GRADUATE", 1, null, 0, now, now, null);
        jdbc.update("INSERT INTO ai_resume_messages(id,account_id,conversation_id,sequence_no,role,message_type,status,content_text,client_message_id,parent_message_id,attempt_no,current_attempt,error_code,model_code,input_tokens,output_tokens,prompt_version,response_hash,created_at,completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                messageId, owner.accountId(), conversationId, 1L, "USER", "TEXT", "COMPLETED", "需要被永久删除的正文",
                "delete-test", null, 1, 1, null, null, 0L, 0L, null, null, now, now);
        String candidateId = id();
        jdbc.update("INSERT INTO resume_candidates(id,account_id,master_id,field_key,proposed_value_json,status,correction_value_json,version_no,created_at,decided_at,candidate_source,ai_action,reason_text,diff_json,source_facts_json,generation_metadata_json,career_library_snapshot_version,career_library_sources_json,source_stale) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                candidateId, owner.accountId(), masterId, "SELF_INTRO", "\"候选正文\"", "PENDING", null, 0, now,
                null, "AI_MODEL", "POLISH", "测试", "{}", "[]", "{}", null, "[]", 0);
        String reportId = seedMatchReport(owner.accountId(), masterId, branchId, revisionId);

        JsonNode preview = preview(owner.cookie(), "RESUME_MASTER", masterId);
        assertThat(preview.path("impactSummary").asText()).contains("1 个版本", "1 个 AI 对话", "1 份匹配报告");

        JsonNode done = deleteAndAwait(owner.cookie(), "RESUME_MASTER", masterId);
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(count("resume_masters", masterId)).isZero();
        assertThat(count("resume_versions", versionId)).isZero();
        assertThat(count("resume_branches", branchId)).isZero();
        assertThat(count("resume_revisions", revisionId)).isZero();
        assertThat(count("ai_resume_conversations", conversationId)).isZero();
        assertThat(count("ai_resume_messages", messageId)).isZero();
        assertThat(count("resume_candidates", candidateId)).isZero();
        assertThat(count("match_reports", reportId)).isZero();
    }

    @Test
    void runningResumeTaskBlocksPermanentDeletion() throws Exception {
        Session owner = registerAndLogin("delete-blocked+" + System.nanoTime() + "@example.com");
        String masterId = createMaster(owner.cookie(), "运行中任务");
        String versionId = seedVersion(owner.accountId(), masterId);
        String taskId = id();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO async_tasks(id,account_id,task_type,status,idempotency_key,input_version,result_version,failure_reason,payload_json,attempt_count,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                taskId, owner.accountId(), "RESUME_PDF_EXPORT", "RUNNING", id(), "v1", null, null,
                "{\"resumeVersionId\":\"" + versionId + "\"}", 1, now, now);

        mockMvc.perform(get("/api/v1/data-rights/deletions/preview").cookie(owner.cookie())
                        .param("scope", "OBJECT").param("targetType", "RESUME_VERSION").param("targetId", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canProceed").value(false))
                .andExpect(jsonPath("$.data.blockers[0]").value("RESUME_TASK_IN_PROGRESS"));
        mockMvc.perform(post("/api/v1/data-rights/deletions").cookie(owner.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody("RESUME_VERSION", versionId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.reason").value("RESUME_TASK_IN_PROGRESS"));
        assertThat(count("resume_versions", versionId)).isOne();
    }

    private JsonNode preview(Cookie cookie, String type, String targetId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/data-rights/deletions/preview").cookie(cookie)
                        .param("scope", "OBJECT").param("targetType", type).param("targetId", targetId))
                .andExpect(status().isOk()).andReturn();
        return data(result);
    }

    private JsonNode deleteAndAwait(Cookie cookie, String type, String targetId) throws Exception {
        MvcResult submitted = mockMvc.perform(post("/api/v1/data-rights/deletions").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(deleteBody(type, targetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn();
        String requestId = data(submitted).path("id").asText();
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> mockMvc.perform(
                        get("/api/v1/data-rights/deletions/{id}", requestId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED")));
        return data(mockMvc.perform(get("/api/v1/data-rights/deletions/{id}", requestId).cookie(cookie))
                .andReturn());
    }

    private String createMaster(Cookie cookie, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/resumes").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BLANK\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return data(result).path("id").asText();
    }

    private String seedVersion(String accountId, String masterId) {
        String versionId = id();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO resume_versions(id,account_id,master_id,status,source,customize_task_id,job_version_id,snapshot_json,version_no,created_at,updated_at,frozen_at,archived_at,layout_instance_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                versionId, accountId, masterId, "FROZEN", "USER_FREEZE", null, null, "{}", 0, now, now, now, null, null);
        return versionId;
    }

    private String seedLayout(String accountId, String masterId, String versionId) {
        String layoutId = id();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO resume_layout_instances(id,account_id,master_id,content_version_id,template_version_id,variant_code,status,overflow_json,content_snapshot_json,template_snapshot_json,version_no,created_at,updated_at,frozen_at,archived_at,branch_id,design_schema_version,design_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                layoutId, accountId, masterId, versionId, id(), "MONO", "FROZEN", "{}", "{}", "{}", 0, now,
                now, now, null, null, "resume-design-v1", "{}");
        jdbc.update("UPDATE resume_versions SET layout_instance_id=? WHERE id=?", layoutId, versionId);
        return layoutId;
    }

    private String seedArtifact(String accountId, String layoutId, String versionId) {
        String fileId = id();
        String artifactId = id();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO private_files(id,owner_id,object_key,content_type,size_bytes,created_at) VALUES(?,?,?,?,?,?)",
                fileId, accountId, "private/" + accountId + "/" + fileId + ".pdf", "application/pdf", 12L, now);
        jdbc.update("INSERT INTO resume_render_artifacts(id,account_id,layout_instance_id,format,status,renderer_version,content_version_id,template_version_id,file_id,file_hash,validation_json,failure_code,failure_message,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                artifactId, accountId, layoutId, "PDF", "READY", "test", versionId, id(), fileId,
                "0".repeat(64), "{}", null, null, now, now);
        return fileId;
    }

    private String seedMatchReport(String accountId, String masterId, String branchId, String revisionId) {
        String reportId = id();
        jdbc.update("INSERT INTO match_reports(id,account_id,job_id,job_version_id,match_task_id,rule_snapshot_id,status,level,total_score,hard_gate_score,skill_score,experience_score,constraint_score,weights_json,hard_gap_count,bonus_applied,confidence,explanation_json,profile_snapshot_version,created_at,archived_at,report_protocol_version,resume_master_id,resume_branch_id,resume_revision_id,resume_content_hash,resume_source_type,resume_analysis_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                reportId, accountId, id(), id(), id(), id(), "CURRENT", "良好", 80, 80, 80, 80, 80, "{}", 0, 0,
                "HIGH", "{}", 1, Instant.now(), null, "match-report-v2", masterId, branchId, revisionId,
                "0".repeat(64), "AI_RESUME", "{}");
        jdbc.update("INSERT INTO match_requirement_items(id,report_id,account_id,sequence_no,requirement_type,requirement_text,resume_refs_json,evidence_refs_json,judgement_status,rule_basis,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                id(), reportId, accountId, 1, "SKILL", "Java", "[]", "[]", "SUPPORTED", "测试", Instant.now());
        return reportId;
    }

    private Session registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = login.getResponse().getCookie("jobproof_session");
        assertThat(cookie).isNotNull();
        String accountId = data(mockMvc.perform(get("/api/v1/me").cookie(cookie)).andReturn()).path("id").asText();
        return new Session(cookie, accountId);
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private int count(String table, String id) {
        return count(table, id, "id");
    }

    private int count(String table, String value, String column) {
        Integer result = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Integer.class, value);
        return result == null ? 0 : result;
    }

    private static String deleteBody(String type, String targetId) {
        return "{\"scope\":\"OBJECT\",\"targetType\":\"" + type + "\",\"targetId\":\""
                + targetId + "\",\"confirmationAck\":true}";
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private record Session(Cookie cookie, String accountId) {}
}
