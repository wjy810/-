package com.jobproof.modules.resume.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeTemplatePreviewRenderer.RenderRequest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "jobproof.word-preview.it", matches = "true")
class WordResumeTemplatePreviewRendererIT {
    @Test
    void rendersARealWordDocumentToPngPages() throws Exception {
        Path script = Path.of("scripts/render-docx-previews.ps1").toAbsolutePath().normalize();
        assertThat(Files.isRegularFile(script)).isTrue();
        WordResumeTemplatePreviewRenderer renderer = new WordResumeTemplatePreviewRenderer(
                new ObjectMapper(), "powershell.exe", "soffice", script.toString(), 120);

        var result = renderer.render(List.of(new RenderRequest("catalog-test", UUID.randomUUID().toString(), docx("真实 Word 分页预览"))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).successful()).isTrue();
        assertThat(result.get(0).pages()).hasSize(1);
        assertThat(result.get(0).pages().get(0)).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
    }

    private static byte[] docx(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            write(zip, "[Content_Types].xml", "<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            write(zip, "_rels/.rels", "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            write(zip, "word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>" + text + "</w:t></w:r></w:p><w:sectPr/></w:body></w:document>");
        }
        return output.toByteArray();
    }

    private static void write(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
