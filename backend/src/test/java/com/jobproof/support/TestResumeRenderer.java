package com.jobproof.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobproof.modules.resume.application.BuiltInTemplateCatalog;
import com.jobproof.modules.resume.application.ResumeRenderPort;
import com.jobproof.modules.resume.domain.ResumeDesignV2;
import com.jobproof.modules.resume.domain.ResumeGenericLayout;
import com.jobproof.modules.resume.domain.ResumeTemplateManifest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the renderer service in integration tests: writes the resume text in reading order
 * (header, then sections) with real pagination, so page limits, ATS checks and payloads can be
 * asserted without Chromium. The real renderer has its own tests (renderer/test) and contract test.
 */
@Component
@Primary
public class TestResumeRenderer implements ResumeRenderPort {
    private static final int LINES_PER_PAGE = 46;

    public static final List<RenderRequest> REQUESTS = new CopyOnWriteArrayList<>();
    private static volatile RenderFailedException nextFailure;

    private final BuiltInTemplateCatalog catalog;

    public TestResumeRenderer(BuiltInTemplateCatalog catalog) {
        this.catalog = catalog;
    }

    public static void failNext(String code, boolean retryable) {
        nextFailure = new RenderFailedException(code, "test renderer failure", retryable);
    }

    public static RenderRequest last() {
        return REQUESTS.isEmpty() ? null : REQUESTS.get(REQUESTS.size() - 1);
    }

    @Override
    public RenderedDocument render(RenderRequest request) {
        REQUESTS.add(request);
        RenderFailedException failure = nextFailure;
        if (failure != null) {
            nextFailure = null;
            throw failure;
        }
        ResumeTemplateManifest manifest = catalog.find(request.templateId())
                .orElseThrow(() -> new RenderFailedException("RENDERER_TEMPLATE_UNKNOWN", "unknown template", false))
                .manifest();
        ResumeDesignV2 design = ResumeDesignV2.coerce(manifest, request.design());
        List<String> lines = lines(manifest, design, request.content());
        int pages = Math.max(1, (lines.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
        byte[] pdf = pdf(lines, request.title());
        double overflow = pages > request.pageLimit()
                ? (lines.size() - request.pageLimit() * LINES_PER_PAGE) * 5.0 : 0;
        return new RenderedDocument(pdf, "application/pdf", pages, overflow,
                pages > request.pageLimit() ? design.visibleSections().get(design.visibleSections().size() - 1) : null,
                7);
    }

    private List<String> lines(ResumeTemplateManifest manifest, ResumeDesignV2 design, JsonNode content) {
        List<String> lines = new ArrayList<>();
        JsonNode basics = content.path("basics");
        add(lines, basics.path("name").asText(""));
        add(lines, content.path("intentions").path("targetJob").asText(""));
        add(lines, String.join("  ", List.of(basics.path("email").asText(""), basics.path("phone").asText(""),
                basics.path("location").asText("")).stream().filter(value -> !value.isBlank()).toList()));
        for (String key : design.visibleSections()) {
            JsonNode value = content.path("experience".equals(key) ? "experiences" : key);
            boolean present = value.isTextual() ? !value.asText().isBlank()
                    : value.isArray() && !value.isEmpty();
            if (!present) continue;
            add(lines, ResumeGenericLayout.title(manifest, design, key));
            if (value.isTextual()) {
                for (String line : value.asText().split("\\R")) add(lines, line);
                continue;
            }
            for (JsonNode item : value) {
                if (item.isTextual()) {
                    for (String line : item.asText().split("\\R")) add(lines, line);
                    continue;
                }
                List<String> head = new ArrayList<>();
                for (String field : List.of("company", "school", "name", "language", "category", "role", "major",
                        "degree", "issuer", "level")) {
                    String text = item.path(field).asText("");
                    if (!text.isBlank()) head.add(text);
                }
                if (item.path("items").isArray()) {
                    List<String> skills = new ArrayList<>();
                    item.path("items").forEach(skill -> skills.add(skill.asText()));
                    head.add(String.join("、", skills));
                }
                add(lines, String.join(" · ", head));
                for (String line : item.path("description").asText("").split("\\R")) add(lines, line);
            }
        }
        return lines;
    }

    private static void add(List<String> lines, String text) {
        String value = text == null ? "" : text.replace("\t", " ").strip();
        if (value.isEmpty()) return;
        // ~40 CJK characters per line on A4 at body size.
        for (int start = 0; start < value.length(); start += 40) {
            lines.add(value.substring(start, Math.min(value.length(), start + 40)));
        }
    }

    private static byte[] pdf(List<String> lines, String title) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream();
                InputStream fontInput = TestResumeRenderer.class.getResourceAsStream("/fonts/NotoSansSC-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(document, fontInput, true);
            document.getDocumentInformation().setTitle(title);
            for (int start = 0; start < Math.max(1, lines.size()); start += LINES_PER_PAGE) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 10);
                    stream.setLeading(16);
                    stream.newLineAtOffset(48, 790);
                    for (String line : lines.subList(start, Math.min(lines.size(), start + LINES_PER_PAGE))) {
                        stream.showText(line);
                        stream.newLine();
                    }
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
