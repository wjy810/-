package com.jobproof.modules.site.application;

import com.jobproof.infrastructure.config.JobProofProperties;
import com.jobproof.shared.error.AppException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Terms and privacy texts. The operator's official Markdown files win when configured
 * ({@code jobproof.policies.terms-path} / {@code privacy-path}); otherwise the built-in reference
 * text is served and marked as not published. Only {@code #}, {@code ##}, paragraphs and
 * {@code -} list items are recognised, so the page never renders operator-supplied HTML.
 */
@Service
public class PolicyDocumentService {
    private static final int MAX_BYTES = 256 * 1024;
    private final JobProofProperties properties;
    private final Map<String, PolicyDocument> cache = new ConcurrentHashMap<>();

    public PolicyDocumentService(JobProofProperties properties) {
        this.properties = properties;
    }

    public PolicyDocument document(String kind) {
        String key = kind == null ? "" : kind.trim().toLowerCase(java.util.Locale.ROOT);
        if (!key.equals("terms") && !key.equals("privacy")) {
            throw AppException.user("POLICY_NOT_FOUND", "没有这份文档");
        }
        return cache.computeIfAbsent(key, this::load);
    }

    private PolicyDocument load(String kind) {
        JobProofProperties.Policies policies = properties.getPolicies();
        String path = kind.equals("terms") ? policies.getTermsPath() : policies.getPrivacyPath();
        String version = kind.equals("terms") ? policies.getTermsVersion() : policies.getPrivacyVersion();
        String fallbackTitle = kind.equals("terms") ? "用户协议" : "隐私政策";
        String official = path.isBlank() ? null : readFile(Path.of(path));
        String text = official != null ? official : readBuiltIn(kind);
        return parse(kind, version, official != null, fallbackTitle, text);
    }

    private static String readFile(Path path) {
        try {
            if (!Files.isReadable(path) || Files.size(path) > MAX_BYTES) return null;
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }

    private static String readBuiltIn(String kind) {
        try (InputStream input = new ClassPathResource("policies/" + kind + ".md").getInputStream()) {
            return new String(input.readNBytes(MAX_BYTES), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Built-in policy text missing: " + kind, exception);
        }
    }

    static PolicyDocument parse(String kind, String version, boolean published, String fallbackTitle, String text) {
        String title = fallbackTitle;
        List<Section> sections = new ArrayList<>();
        String heading = null;
        List<Block> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String raw : text.replace("\r\n", "\n").split("\n")) {
            String line = raw.strip();
            if (line.startsWith("## ")) {
                flush(paragraph, blocks);
                if (heading != null || !blocks.isEmpty()) sections.add(new Section(heading, List.copyOf(blocks)));
                heading = line.substring(3).strip();
                blocks.clear();
            } else if (line.startsWith("# ")) {
                title = line.substring(2).strip();
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                flush(paragraph, blocks);
                blocks.add(new Block("item", line.substring(2).strip()));
            } else if (line.isEmpty()) {
                flush(paragraph, blocks);
            } else {
                if (!paragraph.isEmpty()) paragraph.append(line.matches("^[\\x00-\\x7F].*") ? " " : "");
                paragraph.append(line);
            }
        }
        flush(paragraph, blocks);
        if (heading != null || !blocks.isEmpty()) sections.add(new Section(heading, List.copyOf(blocks)));
        return new PolicyDocument(kind, title, version, published, List.copyOf(sections));
    }

    private static void flush(StringBuilder paragraph, List<Block> blocks) {
        if (paragraph.isEmpty()) return;
        blocks.add(new Block("paragraph", paragraph.toString()));
        paragraph.setLength(0);
    }

    /** {@code published}: the operator's official text; false means the built-in reference text. */
    public record PolicyDocument(String kind, String title, String version, boolean published, List<Section> sections) {}
    public record Section(String heading, List<Block> blocks) {}
    public record Block(String type, String text) {}
}
