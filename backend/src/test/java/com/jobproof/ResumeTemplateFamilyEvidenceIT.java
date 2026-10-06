package com.jobproof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeDocumentModel;
import com.jobproof.modules.resume.domain.ResumeDocxRenderer;
import com.jobproof.modules.resume.domain.ResumeLayoutDefinition;
import com.jobproof.modules.resume.domain.ResumeLayoutProtocol;
import com.jobproof.modules.resume.domain.ResumeOverflowEngine;
import com.jobproof.modules.resume.domain.ResumePdfRenderer;
import com.jobproof.modules.resume.domain.ResumePdfPreflight;
import com.jobproof.modules.resume.domain.ResumeTemplateEvidenceFixtures;
import com.jobproof.modules.resume.domain.ResumeTemplateEvidenceFixtures.Scenario;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateJpaRepository;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionEntity;
import com.jobproof.modules.resume.infra.ResumeLayoutTemplateVersionJpaRepository;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ResumeTemplateFamilyEvidenceIT {
    private static final boolean GENERATE_VISUAL_EVIDENCE =
            Boolean.getBoolean("jobproof.generateTemplateVisualEvidence");
    private static final Set<String> SLOT_KEYS = Set.of(
            "summary", "education", "experience", "projects", "organizations",
            "skills", "certificates", "honors", "languages");

    @Autowired ObjectMapper objectMapper;
    @Autowired ResumeLayoutTemplateJpaRepository templates;
    @Autowired ResumeLayoutTemplateVersionJpaRepository versions;

    @Test
    void allTwelveFamiliesHaveDistinctValidatedV3Definitions() throws Exception {
        List<Family> families = families();

        assertThat(families).hasSize(12);
        assertThat(families).extracting(family -> family.version().getDefinitionJson()).doesNotHaveDuplicates();
        assertThat(families).extracting(family -> family.definition().visual().effectiveSubtitle())
                .doesNotHaveDuplicates();

        for (Family family : families) {
            ResumeLayoutDefinition definition = family.definition();
            assertThat(family.version().getRendererProtocol())
                    .as(family.template().getId())
                    .isEqualTo(ResumeLayoutProtocol.V3);
            assertThat(definition.slots()).extracting(ResumeLayoutDefinition.Slot::key)
                    .as(family.template().getId())
                    .containsExactlyInAnyOrderElementsOf(SLOT_KEYS);
            assertThat(definition.columns().stream().flatMap(column -> column.slotKeys().stream()).toList())
                    .as(family.template().getId())
                    .containsExactlyInAnyOrderElementsOf(SLOT_KEYS);
            assertThat(definition.page().maxPages()).as(family.template().getId()).isEqualTo(2);
            assertThat(variants(family.template())).as(family.template().getId()).isNotEmpty();
            assertThat(family.version().getThumbnailUri())
                    .as(family.template().getId())
                    .isEqualTo(thumbnailUri(family));
        }
    }

    @Test
    void shortMediumLongAndBoundaryFixturesRenderForEveryFamily() throws Exception {
        Set<String> visualFingerprints = new HashSet<>();
        for (Family family : families()) {
            for (Scenario scenario : Scenario.values()) {
                ResumeDocumentModel document = ResumeTemplateEvidenceFixtures.document(scenario);
                ResumeOverflowEngine.Report report = ResumeOverflowEngine.evaluate(document, family.definition());
                assertThat(report.valid())
                        .as(family.template().getId() + " " + scenario + " logical capacity")
                        .isTrue();

                byte[] bytes = ResumePdfRenderer.render(
                        "JobProof controlled family evidence",
                        document,
                        family.definition(),
                        variants(family.template()).get(0),
                        family.version().getRendererProtocol());
                try (PDDocument pdf = Loader.loadPDF(bytes)) {
                    assertThat(pdf.getNumberOfPages())
                            .as(family.template().getId() + " " + scenario + " page count")
                            .isBetween(1, family.definition().page().maxPages());
                    assertThat(pdf.getPage(0).getMediaBox().getWidth()).isEqualTo(595.27563f);
                    assertThat(pdf.getPage(0).getMediaBox().getHeight()).isEqualTo(841.8898f);
                    assertThat(pdf.getPage(0).getResources().getFontNames())
                            .anySatisfy(name -> assertThat(pdf.getPage(0).getResources().getFont(name).isEmbedded())
                                    .isTrue());
                    String text = new PDFTextStripper().getText(pdf);
                    assertThat(text).contains("JobProof controlled family evidence");
                    for (String marker : ResumeTemplateEvidenceFixtures.populatedMarkers(
                            family.definition(), document)) {
                        assertThat(text).as(family.template().getId() + " " + scenario).contains(marker);
                    }
                    if (scenario == Scenario.SHORT || scenario == Scenario.MEDIUM) {
                        assertControlledOrder(family, document, text, scenario);
                    }
                    if (scenario == Scenario.MEDIUM && GENERATE_VISUAL_EVIDENCE) {
                        writeVisualEvidence(family);
                    }
                    if (scenario == Scenario.BOUNDARY) {
                        assertContainsIgnoringLayoutWhitespace(
                                text, "超长组织名称", "Project 6", "English mixed content");
                        assertThat(text).doesNotContain(
                                slot(family.definition(), "summary").effectiveLabel(),
                                slot(family.definition(), "certificates").effectiveLabel());
                        BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
                        assertThat(nonWhitePixels(image)).as(family.template().getId()).isGreaterThan(1_000);
                        visualFingerprints.add(pixelFingerprint(image));
                    }
                }
            }
        }
        assertThat(visualFingerprints).hasSize(12);
    }

    @Test
    void declaredSlotBoundariesFailAtExactlyOneUnitOverCapacity() throws Exception {
        for (Family family : families()) {
            for (ResumeLayoutDefinition.Slot slot : family.definition().slots()) {
                ResumeDocumentModel exact = new ResumeDocumentModel(List.of(new ResumeDocumentModel.Section(
                        slot.key(), "X".repeat(slot.capacityUnits() - 8), 1)));
                assertThat(ResumeOverflowEngine.evaluate(exact, family.definition()).valid())
                        .as(family.template().getId() + " " + slot.key() + " exact capacity")
                        .isTrue();

                ResumeDocumentModel over = new ResumeDocumentModel(List.of(new ResumeDocumentModel.Section(
                        slot.key(), "X".repeat(slot.capacityUnits() - 7), 1)));
                ResumeOverflowEngine.Report report = ResumeOverflowEngine.evaluate(over, family.definition());
                assertThat(report.valid()).as(family.template().getId() + " " + slot.key()).isFalse();
                assertThat(report.items()).anySatisfy(item -> {
                    assertThat(item.slotKey()).isEqualTo(slot.key());
                    assertThat(item.excessUnits()).isEqualTo(1);
                });
            }
        }
    }

    @Test
    void everyFamilyCanProduceControlledTwoPagePdfForLongContent() throws Exception {
        List<Family> twoPageFamilies = families().stream()
                .filter(family -> family.definition().page().maxPages() == 2)
                .toList();
        assertThat(twoPageFamilies).hasSize(12);

        for (Family family : twoPageFamilies) {
            ResumeDocumentModel document = ResumeTemplateEvidenceFixtures.paginatedDocument();
            assertThat(ResumePdfPreflight.evaluate(
                    document, family.definition(), variants(family.template()).get(0),
                    family.version().getRendererProtocol()).valid())
                    .as(family.template().getId())
                    .isTrue();
            byte[] bytes = ResumePdfRenderer.render(
                    "Controlled two page evidence",
                    document,
                    family.definition(),
                    variants(family.template()).get(0),
                    family.version().getRendererProtocol());
            try (PDDocument pdf = Loader.loadPDF(bytes)) {
                assertThat(pdf.getNumberOfPages()).as(family.template().getId()).isEqualTo(2);
                assertThat(new PDFTextStripper().getText(pdf))
                        .contains("EXP03", "PRO04", "第 1 / 2 页", "第 2 / 2 页");
            }
        }
    }

    @Test
    void physicalRendererFailsClosedForEveryFamilyBeyondMaxPages() throws Exception {
        for (Family family : families()) {
            String slotKey = family.definition().columns().get(0).slotKeys().get(0);
            ResumeDocumentModel oversized = new ResumeDocumentModel(List.of(
                    new ResumeDocumentModel.Section(slotKey, "OVER_LIMIT 中英文混排 ".repeat(2_000), 1)));
            assertThat(ResumeOverflowEngine.evaluate(oversized, family.definition()).valid())
                    .as(family.template().getId() + " logical overflow")
                    .isFalse();
            assertThatThrownBy(() -> ResumePdfRenderer.render(
                    "Controlled max page failure",
                    oversized,
                    family.definition(),
                    variants(family.template()).get(0),
                    family.version().getRendererProtocol()))
                    .as(family.template().getId())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("maxPages=" + family.definition().page().maxPages());
        }
    }

    @Test
    void allTwelveFamiliesProduceSafeCompleteDocxArtifacts() throws Exception {
        Path output = Files.createTempDirectory(Path.of("target"), "docx-acceptance-v3-");
        ResumeDocumentModel document = ResumeTemplateEvidenceFixtures.completeStructuredDocument();

        for (Family family : families()) {
            String title = "JobProof 九模块简历 · " + family.template().getDisplayName();
            byte[] bytes = ResumeDocxRenderer.render(
                    title,
                    document,
                    family.definition(),
                    variants(family.template()).get(0),
                    family.version().getRendererProtocol(),
                    family.template().getId());
            ResumeDocxRenderer.Inspection inspection = ResumeDocxRenderer.inspect(
                    bytes, title, document, family.definition());
            assertThat(inspection.valid()).as(family.template().getId() + " " + inspection).isTrue();
            assertThat(inspection.macroFree()).isTrue();
            assertThat(inspection.externalRelationshipFree()).isTrue();
            assertThat(inspection.activeContentFree()).isTrue();
            assertThat(inspection.textOrder()).isTrue();
            Files.write(output.resolve(family.template().getId() + ".docx"), bytes);
        }

        assertThat(Files.list(output).filter(path -> path.toString().endsWith(".docx")).toList()).hasSize(12);
    }

    private List<Family> families() throws Exception {
        List<ResumeLayoutTemplateEntity> templateRows = templates.findAllByOrderByDisplayNameAsc();
        List<ResumeLayoutTemplateVersionEntity> versionRows = versions.findAll();
        Map<String, ResumeLayoutTemplateVersionEntity> seededRevisions = new HashMap<>();
        for (ResumeLayoutTemplateVersionEntity version : versionRows) {
            if (version.getRevisionNo() == 1) {
                seededRevisions.put(version.getTemplateId(), version);
            }
        }
        List<Family> result = new ArrayList<>();
        for (ResumeLayoutTemplateEntity template : templateRows) {
            ResumeLayoutTemplateVersionEntity version = seededRevisions.get(template.getId());
            assertThat(version).as(template.getId() + " seeded R1").isNotNull();
            ResumeLayoutDefinition definition = ResumeLayoutProtocol.validate(
                    version.getRendererProtocol(),
                    objectMapper.readValue(version.getDefinitionJson(), ResumeLayoutDefinition.class));
            result.add(new Family(template, version, definition));
        }
        result.sort(Comparator.comparing(family -> family.template().getId()));
        return List.copyOf(result);
    }

    private List<String> variants(ResumeLayoutTemplateEntity template) throws Exception {
        return objectMapper.readValue(template.getVariantsJson(), new TypeReference<>() {});
    }

    private static ResumeLayoutDefinition.Slot slot(ResumeLayoutDefinition definition, String key) {
        return definition.slots().stream().filter(value -> key.equals(value.key())).findFirst().orElseThrow();
    }

    private static void assertControlledOrder(
            Family family,
            ResumeDocumentModel document,
            String text,
            Scenario scenario) {
        int previous = -1;
        for (String marker : ResumeTemplateEvidenceFixtures.populatedMarkers(family.definition(), document)) {
            int current = text.indexOf(marker);
            assertThat(current)
                    .as(family.template().getId() + " " + scenario + " marker " + marker)
                    .isGreaterThan(previous);
            previous = current;
        }
    }

    private static long nonWhitePixels(BufferedImage image) {
        long result = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xff;
                int green = (rgb >> 8) & 0xff;
                int blue = rgb & 0xff;
                if (red < 248 || green < 248 || blue < 248) result++;
            }
        }
        return result;
    }

    private static void assertContainsIgnoringLayoutWhitespace(String text, String... expectedValues) {
        String compactText = text.replaceAll("\\s+", "");
        for (String expected : expectedValues) {
            assertThat(compactText).contains(expected.replaceAll("\\s+", ""));
        }
    }

    private void writeVisualEvidence(Family family) throws Exception {
        Path backendRoot = Path.of("").toAbsolutePath().normalize();
        assertThat(backendRoot.resolve("pom.xml")).isRegularFile();
        Path workspaceRoot = backendRoot.getParent();
        Path pdfDirectory = workspaceRoot.resolve("tmp/pdfs/resume-template-evidence");
        Path thumbnailDirectory = workspaceRoot.resolve("frontend/public/resume-template-thumbnails");
        Files.createDirectories(pdfDirectory);
        Files.createDirectories(thumbnailDirectory);

        ResumeDocumentModel document = ResumeTemplateEvidenceFixtures.thumbnailDocument();
        byte[] pdfBytes = ResumePdfRenderer.render(
                "JobProof 模板预览",
                document,
                family.definition(),
                variants(family.template()).get(0),
                family.version().getRendererProtocol());
        String basename = family.template().getId() + "-r" + family.version().getRevisionNo();
        Files.write(pdfDirectory.resolve(basename + ".pdf"), pdfBytes);
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
            assertThat(image.getWidth()).isGreaterThanOrEqualTo(595);
            assertThat(image.getHeight()).isGreaterThanOrEqualTo(841);
            assertThat(nonWhitePixels(image)).isGreaterThan(1_000);
            assertThat(javax.imageio.ImageIO.write(
                            image, "png", thumbnailDirectory.resolve(basename + ".png").toFile()))
                    .isTrue();
        }
    }

    private static String thumbnailUri(Family family) {
        return "/resume-template-thumbnails/" + family.template().getId()
                + "-r" + family.version().getRevisionNo() + ".png";
    }

    private static String pixelFingerprint(BufferedImage image) {
        long hash = 1469598103934665603L;
        for (int y = 0; y < image.getHeight(); y += 3) {
            for (int x = 0; x < image.getWidth(); x += 3) {
                hash ^= image.getRGB(x, y);
                hash *= 1099511628211L;
            }
        }
        return Long.toUnsignedString(hash, 16);
    }

    private record Family(
            ResumeLayoutTemplateEntity template,
            ResumeLayoutTemplateVersionEntity version,
            ResumeLayoutDefinition definition) {
    }
}
