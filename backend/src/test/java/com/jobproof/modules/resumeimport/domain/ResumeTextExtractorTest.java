package com.jobproof.modules.resumeimport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class ResumeTextExtractorTest {

    @Test
    void extractsTextFromPdf() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("Java Backend Engineer");
                stream.endText();
            }
            document.save(output);
            bytes = output.toByteArray();
        }

        assertThat(ResumeTextExtractor.extract("application/pdf", bytes))
                .isEqualTo("Java Backend Engineer");
    }

    @Test
    void preservesParagraphAndTableOrderInDocx() throws Exception {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Professional Summary");
            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Education");
            table.getRow(0).getCell(1).setText("Example University");
            document.createParagraph().createRun().setText("Java, Spring Boot, SQL");
            document.write(output);
            bytes = output.toByteArray();
        }

        assertThat(ResumeTextExtractor.extract(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes))
                .isEqualTo("Professional Summary\nEducation | Example University\nJava, Spring Boot, SQL");
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertThatThrownBy(() -> ResumeTextExtractor.extract("text/plain", new byte[] {1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RESUME_IMPORT_TYPE_UNSUPPORTED");
    }
}
