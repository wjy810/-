package com.jobproof.modules.resume.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobproof.modules.resume.domain.ResumeTemplatePreviewRenderer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WordResumeTemplatePreviewRenderer implements ResumeTemplatePreviewRenderer {
    public static final String VERSION = "word-office16-pdfbox3-120dpi-v1";
    private static final Logger log = LoggerFactory.getLogger(WordResumeTemplatePreviewRenderer.class);
    private static final int MAX_PAGES = 100;
    private static final int DPI = 120;
    private final ObjectMapper mapper;
    private final String powershellExecutable;
    private final String officeExecutable;
    private final Path scriptPath;
    private final Duration timeout;

    public WordResumeTemplatePreviewRenderer(ObjectMapper mapper,
            @Value("${jobproof.templates.preview.powershell-executable:powershell.exe}") String powershellExecutable,
            @Value("${jobproof.templates.preview.office-executable:soffice}") String officeExecutable,
            @Value("${jobproof.templates.preview.script-path:scripts/render-docx-previews.ps1}") String scriptPath,
            @Value("${jobproof.templates.preview.batch-timeout-seconds:600}") long timeoutSeconds) {
        this.mapper = mapper;
        this.powershellExecutable = powershellExecutable;
        this.officeExecutable = officeExecutable;
        this.scriptPath = Path.of(scriptPath).toAbsolutePath().normalize();
        this.timeout = Duration.ofSeconds(Math.max(30, timeoutSeconds));
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public List<RenderResult> render(List<RenderRequest> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        Path work = null;
        try {
            work = Files.createTempDirectory("jobproof-template-preview-");
            List<Map<String, String>> manifest = new ArrayList<>();
            Map<String, Path> pdfById = new HashMap<>();
            for (int index = 0; index < requests.size(); index++) {
                RenderRequest request = requests.get(index);
                Path docx = work.resolve("input-" + index + ".docx");
                Path pdf = work.resolve("output-" + index + ".pdf");
                Files.write(docx, request.docx());
                manifest.add(Map.of("id", request.catalogEntryId(), "inputPath", docx.toString(), "outputPath", pdf.toString()));
                pdfById.put(request.catalogEntryId(), pdf);
            }
            List<BridgeResult> bridgeResults = isWindows()
                    ? renderWithWordBridge(work, manifest)
                    : renderWithLibreOffice(work, requests, pdfById);
            Map<String, BridgeResult> bridgeById = new HashMap<>();
            for (BridgeResult result : bridgeResults) bridgeById.put(result.id(), result);
            List<RenderResult> results = new ArrayList<>();
            for (RenderRequest request : requests) {
                BridgeResult bridge = bridgeById.get(request.catalogEntryId());
                if (bridge == null || !bridge.success()) {
                    results.add(RenderResult.failure(request.catalogEntryId(), bridge == null ? "WORD_RENDER_RESULT_MISSING" : safeCode(bridge.errorCode())));
                    continue;
                }
                try {
                    results.add(RenderResult.success(request.catalogEntryId(), renderPdf(pdfById.get(request.catalogEntryId()))));
                } catch (Exception exception) {
                    log.warn("Unable to rasterize template preview catalogId={}", request.catalogEntryId(), exception);
                    results.add(RenderResult.failure(request.catalogEntryId(), "PDF_RASTERIZATION_FAILED"));
                }
            }
            return results;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failures(requests, "WORD_RENDER_INTERRUPTED");
        } catch (Exception exception) {
            log.warn("Unable to run Word template preview renderer", exception);
            return failures(requests, "WORD_RENDER_FAILED");
        } finally {
            deleteTree(work);
        }
    }

    private List<BridgeResult> renderWithWordBridge(Path work, List<Map<String, String>> manifest) throws Exception {
        if (!Files.isRegularFile(scriptPath)) {
            return manifest.stream().map(item -> new BridgeResult(item.get("id"), false,
                    "WORD_RENDER_SCRIPT_MISSING")).toList();
        }
        Path manifestPath = work.resolve("manifest.json");
        mapper.writeValue(manifestPath.toFile(), manifest);
        Process process = new ProcessBuilder(powershellExecutable, "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-File", scriptPath.toString(), "-ManifestPath", manifestPath.toString())
                .redirectErrorStream(true).start();
        ProcessResult result = waitFor(process, timeout);
        if (!result.finished()) {
            return manifest.stream().map(item -> new BridgeResult(item.get("id"), false,
                    "WORD_RENDER_TIMEOUT")).toList();
        }
        if (result.exitCode() != 0) {
            log.warn("Word preview renderer exited with code {}: {}", result.exitCode(), compact(result.output()));
            return manifest.stream().map(item -> new BridgeResult(item.get("id"), false,
                    "WORD_RENDER_PROCESS_FAILED")).toList();
        }
        return parseBridgeResults(result.output());
    }

    private List<BridgeResult> renderWithLibreOffice(Path work, List<RenderRequest> requests,
            Map<String, Path> pdfById) throws Exception {
        List<BridgeResult> results = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        for (int index = 0; index < requests.size(); index++) {
            RenderRequest request = requests.get(index);
            Duration remaining = Duration.ofNanos(Math.max(0, deadline - System.nanoTime()));
            if (remaining.isZero()) {
                results.add(new BridgeResult(request.catalogEntryId(), false, "WORD_RENDER_TIMEOUT"));
                continue;
            }
            Path input = work.resolve("input-" + index + ".docx");
            Path generated = work.resolve("input-" + index + ".pdf");
            Path profile = work.resolve("lo-profile-" + index);
            Files.createDirectories(profile);
            Process process = new ProcessBuilder(officeExecutable, "--headless", "--nologo", "--nodefault",
                    "--nolockcheck", "--nofirststartwizard", "-env:UserInstallation=" + profile.toUri(),
                    "--convert-to", "pdf", "--outdir", work.toString(), input.toString())
                    .redirectErrorStream(true).start();
            ProcessResult result = waitFor(process, remaining);
            if (!result.finished()) {
                results.add(new BridgeResult(request.catalogEntryId(), false, "WORD_RENDER_TIMEOUT"));
            } else if (result.exitCode() != 0 || !Files.isRegularFile(generated)) {
                log.warn("LibreOffice preview renderer failed catalogId={} code={} output={}",
                        request.catalogEntryId(), result.exitCode(), compact(result.output()));
                results.add(new BridgeResult(request.catalogEntryId(), false, "WORD_RENDER_PROCESS_FAILED"));
            } else {
                Files.move(generated, pdfById.get(request.catalogEntryId()), StandardCopyOption.REPLACE_EXISTING);
                results.add(new BridgeResult(request.catalogEntryId(), true, null));
            }
        }
        return results;
    }

    private static ProcessResult waitFor(Process process, Duration timeout) throws Exception {
        long seconds = Math.max(1, timeout.toSeconds());
        boolean finished = process.waitFor(seconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            return new ProcessResult(false, -1, "");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return new ProcessResult(true, process.exitValue(), output);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    List<byte[]> renderPdf(Path pdf) throws Exception {
        if (pdf == null || !Files.isRegularFile(pdf) || Files.size(pdf) < 5) throw new IllegalArgumentException("PDF missing");
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1 || pageCount > MAX_PAGES) throw new IllegalArgumentException("PDF page count outside preview limits");
            PDFRenderer renderer = new PDFRenderer(document);
            List<byte[]> pages = new ArrayList<>(pageCount);
            for (int page = 0; page < pageCount; page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, DPI, ImageType.RGB);
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("PNG writer unavailable");
                    pages.add(output.toByteArray());
                } finally {
                    image.flush();
                }
            }
            return pages;
        }
    }

    private List<BridgeResult> parseBridgeResults(String output) throws Exception {
        String json = output.lines().filter(line -> !line.isBlank()).reduce((first, second) -> second).orElse("[]");
        if (json.startsWith("{")) {
            BridgeResult single = mapper.readValue(json, BridgeResult.class);
            return List.of(single);
        }
        return mapper.readValue(json, new TypeReference<List<BridgeResult>>() {});
    }

    private static List<RenderResult> failures(List<RenderRequest> requests, String code) {
        return requests.stream().map(request -> RenderResult.failure(request.catalogEntryId(), code)).toList();
    }

    private static String safeCode(String value) {
        return value == null || value.isBlank() ? "WORD_EXPORT_FAILED" : value.replaceAll("[^A-Z0-9_]", "_").substring(0, Math.min(128, value.length()));
    }

    private static String compact(String value) {
        if (value == null) return "";
        String compact = value.replaceAll("[\\r\\n]+", " ").trim();
        return compact.substring(0, Math.min(500, compact.length()));
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (Exception exception) { log.debug("Unable to clean preview temp path {}", path, exception); }
            });
        } catch (Exception exception) {
            log.debug("Unable to clean preview temp root {}", root, exception);
        }
    }

    private record BridgeResult(String id, boolean success, String errorCode) {}
    private record ProcessResult(boolean finished, int exitCode, String output) {}
}
