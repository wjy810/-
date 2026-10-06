package com.jobproof.modules.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ResumeTemplateDocxInspectorTest {

    @Test
    void rejectsMacroActiveContentAndZipTraversal() throws Exception {
        Map<String, String> entries = baseEntries();
        entries.put("word/vbaProject.bin", "macro");
        entries.put("../outside.exe", "payload");

        ResumeTemplateDocxInspector.Inspection inspection = ResumeTemplateDocxInspector.inspect(zip(entries));

        assertThat(inspection.accepted()).isFalse();
        assertThat(inspection.findings()).extracting(ResumeTemplateDocxInspector.Finding::code)
                .contains("MACRO_BINARY", "UNSAFE_ENTRY_PATH", "EXECUTABLE_CONTENT");
    }

    @Test
    void rejectsDoctypeInsteadOfResolvingExternalEntities() throws Exception {
        Map<String, String> entries = baseEntries();
        entries.put("word/_rels/document.xml.rels", """
                <!DOCTYPE Relationships [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">&xxe;</Relationships>
                """);

        ResumeTemplateDocxInspector.Inspection inspection = ResumeTemplateDocxInspector.inspect(zip(entries));

        assertThat(inspection.accepted()).isFalse();
        assertThat(inspection.findings()).extracting(ResumeTemplateDocxInspector.Finding::code)
                .contains("INVALID_DOCX_PACKAGE");
    }

    private static Map<String, String> baseEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", """
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Override PartName="/word/document.xml"
                    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                </Types>
                """);
        entries.put("_rels/.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                """);
        entries.put("word/document.xml", """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body/></w:document>
                """);
        entries.put("word/_rels/document.xml.rels", """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                """);
        return entries;
    }

    private static byte[] zip(Map<String, String> entries) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }
}
