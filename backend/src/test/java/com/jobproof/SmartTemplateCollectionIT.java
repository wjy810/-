package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.resume.application.BuiltInTemplateCatalog;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "jobproof.ai.workbench.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmartTemplateCollectionIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BuiltInTemplateCatalog catalog;

    @Test
    void everyNewTemplateCanBeAppliedAndItsDesignSurvivesSwitchingWithoutChangingContent() throws Exception {
        assertThat(catalog.all()).hasSize(32);
        String email = "collection-" + System.nanoTime() + "@example.com";
        String credentials = mapper.writeValueAsString(Map.of("email", email, "password", "Passw0rd!"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk());
        Cookie cookie = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("jobproof_session");
        for (var entry : catalog.all().subList(16, 32)) {
            String templateId = entry.manifest().id();
            JsonNode created = data(mvc.perform(post("/api/v1/ai-resume/conversations").cookie(cookie)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("identityType", "PROFESSIONAL", "title", "模板验收-" + templateId))))
                    .andExpect(status().isOk()).andReturn());
            String root = "/api/v1/ai-resume/conversations/" + created.path("id").asText();
            JsonNode original = created.path("content").deepCopy();
            JsonNode selected = choose(root, cookie, templateId, created);
            assertThat(selected.path("layout").path("templateId").asText()).isEqualTo(templateId);
            assertThat(selected.path("content")).isEqualTo(original);
            ObjectNode settings = (ObjectNode) selected.path("activeDesign").path("settings").deepCopy();
            settings.put("paletteId", entry.manifest().palettes().get(1).id());
            settings.put("headerVariant", "compact");
            settings.put("decorations", false);
            settings.put("paperSize", "en".equals(entry.manifest().locale()) ? "LETTER" : "A4");
            mvc.perform(put(root + "/design/" + templateId).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("settings", settings, "expectedVersion",
                                    selected.path("activeDesign").path("versionNo").asInt()))))
                    .andExpect(status().isOk());
            JsonNode refreshed = data(mvc.perform(get(root).cookie(cookie)).andExpect(status().isOk()).andReturn());
            JsonNode other = choose(root, cookie, "classic", refreshed);
            JsonNode restored = choose(root, cookie, templateId, other);
            assertThat(restored.path("activeDesign").path("settings").path("paletteId").asText())
                    .isEqualTo(entry.manifest().palettes().get(1).id());
            assertThat(restored.path("activeDesign").path("settings").path("headerVariant").asText()).isEqualTo("compact");
            assertThat(restored.path("activeDesign").path("settings").path("decorations").asBoolean()).isFalse();
            assertThat(restored.path("content")).as(templateId).isEqualTo(original);
        }
    }

    private JsonNode choose(String root, Cookie cookie, String templateId, JsonNode current) throws Exception {
        return data(mvc.perform(post(root + "/template").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("templateId", templateId, "expectedLayoutVersion",
                                current.path("layout").path("version").asInt()))))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
