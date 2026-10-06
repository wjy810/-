package com.jobproof.modules.resume.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.resume.application.BuiltInTemplateCatalog;
import com.jobproof.modules.resume.application.ResumeRenderPort;
import com.jobproof.modules.resume.domain.ResumeAtsTextCheck;
import com.jobproof.modules.resume.domain.ResumeDesignV2;
import com.jobproof.modules.resume.domain.ResumeTemplateManifest;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The real renderer service against the server-side checks: every built-in template with the shared
 * sample resumes must stay within its page limit, embed its fonts and pass the ATS text check.
 * Runs when JP_RENDERER_URL (and JP_RENDERER_TOKEN) point at a running renderer, e.g. in CI.
 */
@EnabledIfEnvironmentVariable(named = "JP_RENDERER_URL", matches = ".+")
class RendererContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void everyBuiltInTemplateRendersReadableEmbeddedPdfWithinItsPageLimit() throws Exception {
        BuiltInTemplateCatalog catalog = new BuiltInTemplateCatalog(MAPPER);
        JsonNode samples;
        try (InputStream input = getClass().getResourceAsStream("/templates/render-samples.json")) {
            samples = MAPPER.readTree(input);
        }
        HttpResumeRenderClient client = new HttpResumeRenderClient(MAPPER, System.getenv("JP_RENDERER_URL"),
                System.getenv().getOrDefault("JP_RENDERER_TOKEN", "dev-renderer-token-0123456789"), 30000, 1);
        List<String> failures = new ArrayList<>();
        for (BuiltInTemplateCatalog.Entry entry : catalog.all()) {
            ResumeTemplateManifest manifest = entry.manifest();
            List<String> sampleIds = "en".equals(manifest.locale()) ? List.of("english")
                    : List.of("professional", "campus", "minimal");
            for (String sampleId : sampleIds) {
                String name = manifest.id() + "/" + sampleId;
                JsonNode content = samples.path(sampleId);
                ResumeDesignV2 design = ResumeDesignV2.defaults(manifest);
                int limit = design.pageLimit(manifest);
                ResumeRenderPort.RenderedDocument pdf = client.render(ResumeRenderPort.RenderRequest.pdf(
                        manifest.id(), design.toJson(MAPPER), content, null, name, limit));
                if (pdf.pageCount() > limit) failures.add(name + ": " + pdf.pageCount() + " pages > " + limit);
                ResumeAtsTextCheck.Result ats = ResumeAtsTextCheck.check(pdf.body(),
                        ResumeAtsTextCheck.expectation(manifest, design, content));
                if (!ats.passed()) failures.add(name + ": " + ats.checks().stream().filter(check -> !check.passed()).toList());
                try (PDDocument document = Loader.loadPDF(pdf.body())) {
                    assertThat(document.getNumberOfPages()).as(name).isEqualTo(pdf.pageCount());
                    for (PDPage page : document.getPages()) {
                        for (var fontName : page.getResources().getFontNames()) {
                            PDFont font = page.getResources().getFont(fontName);
                            if (!font.isEmbedded()) failures.add(name + ": font not embedded " + font.getName());
                        }
                    }
                }
            }
            // Long content in a one-page target reports the overflow instead of clipping.
            ObjectNode tight = design(manifest, "ONE");
            ResumeRenderPort.RenderedDocument overflow = client.render(ResumeRenderPort.RenderRequest.pdf(
                    manifest.id(), tight, samples.path("en".equals(manifest.locale()) ? "english" : "long"),
                    null, manifest.id() + "/long", 1));
            if ("long".equals("en".equals(manifest.locale()) ? "english" : "long")
                    && overflow.pageCount() <= 1 && overflow.overflowMm() <= 0) {
                failures.add(manifest.id() + "/long: overflow not reported (pages=" + overflow.pageCount() + ")");
            }
        }
        assertThat(failures).isEmpty();
    }

    private static ObjectNode design(ResumeTemplateManifest manifest, String pageTarget) {
        ObjectNode design = ResumeDesignV2.defaults(manifest).toJson(MAPPER);
        design.put("pageTarget", pageTarget);
        return design;
    }
}
