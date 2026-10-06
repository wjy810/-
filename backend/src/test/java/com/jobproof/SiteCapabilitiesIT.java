package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.modules.site.application.PolicyDocumentService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Public deployment facts: feature switches, registration, operator details and legal texts. */
@SpringBootTest(properties = {
        "jobproof.job-match.enabled=true",
        "jobproof.career-planning.enabled=false",
        "jobproof.operator.name=示例科技有限公司",
        "jobproof.operator.icp-record=京ICP备00000000号",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SiteCapabilitiesIT {

    @Autowired MockMvc mockMvc;

    @Test
    void capabilitiesArePublicAndReflectConfiguration() throws Exception {
        mockMvc.perform(get("/api/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.features.jobMatch").value(true))
                .andExpect(jsonPath("$.data.features.careerPlanning").value(false))
                .andExpect(jsonPath("$.data.registrationOpen").value(true))
                .andExpect(jsonPath("$.data.policies.published").value(false))
                .andExpect(jsonPath("$.data.operator.name").value("示例科技有限公司"))
                .andExpect(jsonPath("$.data.operator.icpRecord").value("京ICP备00000000号"));
    }

    @Test
    void builtInPolicyTextIsServedAsUnpublishedReference() throws Exception {
        mockMvc.perform(get("/api/v1/policies/privacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("隐私政策"))
                .andExpect(jsonPath("$.data.published").value(false))
                .andExpect(jsonPath("$.data.sections[0].heading").value("1. 收集的信息"))
                .andExpect(jsonPath("$.data.sections[0].blocks[0].type").value("paragraph"));
        mockMvc.perform(get("/api/v1/policies/cookies")).andExpect(status().isBadRequest());
    }

    @Test
    void officialTextIsParsedAndRegistrationWaitsForIt(@TempDir Path directory) throws Exception {
        Path terms = directory.resolve("terms.md");
        Files.writeString(terms, "# 服务协议\n\n## 一、总则\n第一段第一行\n第一段第二行\n\n- 条目一\n- 条目二\n\n<script>alert(1)</script>\n",
                StandardCharsets.UTF_8);
        JobProofProperties properties = new JobProofProperties();
        properties.getPolicies().setRegistrationEnabled(true);
        properties.getPolicies().setRequirePublished(true);
        properties.getPolicies().setTermsPath(terms.toString());
        assertThat(properties.getPolicies().isRegistrationOpen()).isFalse();

        var document = new PolicyDocumentService(properties).document("terms");
        assertThat(document.published()).isTrue();
        assertThat(document.title()).isEqualTo("服务协议");
        assertThat(document.sections()).hasSize(1);
        var blocks = document.sections().get(0).blocks();
        assertThat(blocks).extracting(PolicyDocumentService.Block::type).containsExactly("paragraph", "item", "item", "paragraph");
        assertThat(blocks.get(0).text()).isEqualTo("第一段第一行第一段第二行");
        // Markup is kept as plain text; the page renders it with text interpolation only.
        assertThat(blocks.get(3).text()).isEqualTo("<script>alert(1)</script>");

        Path privacy = directory.resolve("privacy.md");
        Files.writeString(privacy, "# 隐私政策\n\n## 1\n正文\n", StandardCharsets.UTF_8);
        properties.getPolicies().setPrivacyPath(privacy.toString());
        assertThat(properties.getPolicies().isRegistrationOpen()).isTrue();
    }
}
