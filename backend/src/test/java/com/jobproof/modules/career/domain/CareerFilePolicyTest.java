package com.jobproof.modules.career.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobproof.shared.error.AppException;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.junit.jupiter.api.Test;

class CareerFilePolicyTest {

    @Test
    void acceptsResumePdfsWithLinksAndBrowserFontSubsetNames() throws Exception {
        byte[] pdf = pdf(document -> {
            PDPage page = document.getPage(0);
            // Chrome / Skia names embedded font subsets AAAAAA+Font, BAAAAA+Font, …
            COSDictionary descriptor = new COSDictionary();
            descriptor.setItem(COSName.TYPE, COSName.FONT_DESC);
            descriptor.setItem(COSName.FONT_NAME, COSName.getPDFName("AAAAAA+NotoSerifSC-Bold"));
            page.getCOSObject().setItem(COSName.getPDFName("TestFontDescriptor"), descriptor);
            page.getAnnotations().add(link(uri("mailto:seeker@example.com")));
            page.getAnnotations().add(link(uri("https://github.com/example")));
        });

        CareerFilePolicy.Accepted accepted = CareerFilePolicy.inspect("我的简历.pdf", pdf);
        assertThat(accepted.contentType()).isEqualTo("application/pdf");
        assertThat(accepted.originalName()).isEqualTo("我的简历.pdf");
    }

    @Test
    void keepsReadableNamesButDropsPathsReservedAndControlCharacters() {
        assertThat(CareerFilePolicy.readableFilename("C:\\Users\\a\\简历:v2?.pdf", "file.pdf")).isEqualTo("简历 v2 .pdf");
        assertThat(CareerFilePolicy.readableFilename("../../etc/passwd\u0000.pdf", "file.pdf")).isEqualTo("passwd .pdf");
        assertThat(CareerFilePolicy.readableFilename("  ", "file.pdf")).isEqualTo("file.pdf");
        assertThat(CareerFilePolicy.readableFilename("长".repeat(300) + ".pdf", "file.pdf").codePointCount(0, 160)).isEqualTo(160);
        assertThat(CareerFilePolicy.safeFilename("我的简历.pdf")).isEqualTo("____.pdf");
    }

    @Test
    void rejectsScriptsLaunchActionsAndEmbeddedFiles() throws Exception {
        byte[] script = pdf(document -> {
            PDActionJavaScript action = new PDActionJavaScript("app.alert('x')");
            document.getDocumentCatalog().setOpenAction(action);
        });
        byte[] launch = pdf(document -> {
            PDActionLaunch action = new PDActionLaunch();
            action.getCOSObject().setString(COSName.F, "calc.exe");
            document.getPage(0).getAnnotations().add(link(action));
        });
        byte[] embedded = pdf(document -> {
            PDComplexFileSpecification spec = new PDComplexFileSpecification();
            spec.setFile("payload.txt");
            spec.setEmbeddedFile(new PDEmbeddedFile(document, new java.io.ByteArrayInputStream(new byte[] {1, 2, 3})));
            PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
            tree.setNames(java.util.Map.of("payload.txt", spec));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(document.getDocumentCatalog());
            names.setEmbeddedFiles(tree);
            document.getDocumentCatalog().setNames(names);
        });

        for (byte[] pdf : new byte[][] {script, launch, embedded}) {
            assertThatThrownBy(() -> CareerFilePolicy.inspect("resume.pdf", pdf))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("主动内容");
        }
    }

    private static PDAnnotationLink link(org.apache.pdfbox.pdmodel.interactive.action.PDAction action) {
        PDAnnotationLink link = new PDAnnotationLink();
        link.setRectangle(new PDRectangle(50, 700, 200, 14));
        link.setAction(action);
        return link;
    }

    private static PDActionURI uri(String target) {
        PDActionURI action = new PDActionURI();
        action.setURI(target);
        return action;
    }

    private interface Customiser {
        void apply(PDDocument document) throws Exception;
    }

    private static byte[] pdf(Customiser customise) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(PDRectangle.A4));
            customise.apply(document);
            document.save(out);
            return out.toByteArray();
        }
    }
}
