package com.jobproof.modules.resumeimport.domain;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/** Extracts text only after the career-file safety gate has accepted the original bytes. */
public final class ResumeTextExtractor {
    private ResumeTextExtractor() {}

    public static String extract(String contentType, byte[] bytes) {
        try {
            String text;
            if ("application/pdf".equals(contentType)) {
                try (PDDocument document = Loader.loadPDF(bytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    text = stripper.getText(document);
                }
            } else if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)) {
                text = extractDocx(bytes);
            } else {
                throw new IllegalArgumentException("RESUME_IMPORT_TYPE_UNSUPPORTED");
            }
            return normalize(text);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("RESUME_IMPORT_TEXT_EXTRACTION_FAILED", exception);
        }
    }

    private static String extractDocx(byte[] bytes) throws Exception {
        List<String> lines = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    add(lines, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) add(cells, cell.getText());
                        add(lines, String.join(" | ", cells));
                    }
                }
            }
        }
        return String.join("\n", lines);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        List<String> lines = new ArrayList<>();
        for (String line : value.replace('\u00a0', ' ').replace("\r", "").split("\n")) {
            String clean = line.replaceAll("[\\t ]+", " ").trim();
            if (!clean.isEmpty()) lines.add(clean);
        }
        return String.join("\n", lines).trim();
    }

    private static void add(List<String> target, String value) {
        if (value != null && !value.trim().isEmpty()) target.add(value.trim());
    }
}
