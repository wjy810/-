package com.jobproof.modules.career.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobproof.modules.career.application.CareerLibraryService.RecordView;
import com.jobproof.modules.career.application.CareerLibraryService.RecordWrite;
import com.jobproof.shared.auth.CurrentAccount;
import com.jobproof.shared.error.AppException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the entries of one of the user's resumes into career-library records — only the ones the
 * user picks. Nothing is split automatically: the library stays the user's own, confirmed material.
 * Entries already in the library (same type, title and organisation) are marked and never imported twice.
 */
@Service
public class CareerResumeImportService {

    private static final int MAX_TEXT = 4000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CareerLibraryService library;

    public CareerResumeImportService(JdbcTemplate jdbc, ObjectMapper mapper, CareerLibraryService library) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.library = library;
    }

    @Transactional(readOnly = true)
    public CandidatesView candidates(CurrentAccount current, String masterId) {
        ResumeContent resume = resume(current, masterId);
        Set<String> existing = existingIdentities(current);
        List<Candidate> items = new ArrayList<>();
        for (Entry entry : entries(resume.content())) {
            items.add(new Candidate(entry.key(), entry.write().type(), entry.write().title(), entry.write().organization(),
                    entry.write().role(), entry.write().startDate(), entry.write().endDate(), entry.write().description(),
                    existing.contains(identity(entry.write()))));
        }
        return new CandidatesView(masterId, resume.title(), items);
    }

    @Transactional
    public ImportView importSelected(CurrentAccount current, String masterId, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw AppException.user("CAREER_IMPORT_EMPTY", "请至少选择一项要导入的内容");
        }
        ResumeContent resume = resume(current, masterId);
        Set<String> wanted = new LinkedHashSet<>(keys);
        Set<String> existing = existingIdentities(current);
        List<RecordWrite> writes = new ArrayList<>();
        int skipped = 0;
        for (Entry entry : entries(resume.content())) {
            if (!wanted.contains(entry.key())) continue;
            // A second import of the same resume (or a near-identical one) must not duplicate records.
            if (!existing.add(identity(entry.write()))) {
                skipped++;
                continue;
            }
            writes.add(entry.write());
        }
        List<RecordView> created = library.createRecordsFromResume(current, masterId, writes);
        return new ImportView(created.size(), skipped, created);
    }

    private ResumeContent resume(CurrentAccount current, String masterId) {
        if (masterId == null || masterId.isBlank()) {
            throw AppException.user("RESUME_REQUIRED", "请选择一份简历");
        }
        List<ResumeContent> rows = jdbc.query(
                "SELECT title, content_json FROM resume_masters WHERE id=? AND account_id=?",
                (rs, n) -> new ResumeContent(rs.getString("title"), read(rs.getString("content_json"))),
                masterId, current.accountId());
        if (rows.isEmpty()) {
            throw AppException.user("RESUME_NOT_FOUND", "简历不存在");
        }
        return rows.get(0);
    }

    private Set<String> existingIdentities(CurrentAccount current) {
        Set<String> identities = new HashSet<>();
        for (String[] row : library.recordIdentities(current)) identities.add(identity(row[0], row[1], row[2]));
        return identities;
    }

    /** Each section's entries in resume order, with a key that stays the same while the entry does. */
    List<Entry> entries(JsonNode content) {
        List<Entry> entries = new ArrayList<>();
        for (JsonNode item : items(content, "education")) {
            String school = text(item, "school");
            if (school.isEmpty()) continue;
            String major = text(item, "major");
            add(entries, write("EDUCATION", major.isEmpty() ? school : school + " · " + major, school,
                    text(item, "degree"), item, describe(item), null));
        }
        for (JsonNode item : items(content, "experiences")) {
            String company = text(item, "company");
            String role = text(item, "role");
            if (company.isEmpty() && role.isEmpty()) continue;
            add(entries, write("EXPERIENCE", role.isEmpty() ? company : role, company, role, item, describe(item), null));
        }
        for (JsonNode item : items(content, "projects")) {
            String name = text(item, "name");
            if (name.isEmpty()) continue;
            add(entries, write("PROJECT", name, null, text(item, "role"), item, describe(item), null));
        }
        for (JsonNode item : items(content, "organizations")) {
            String name = text(item, "name");
            if (name.isEmpty()) continue;
            add(entries, write("ORGANIZATION", name, name, text(item, "role"), item, describe(item), null));
        }
        for (JsonNode item : items(content, "skills")) {
            List<String> skills = new ArrayList<>();
            for (JsonNode skill : item.path("items")) {
                if (skill.isTextual() && !skill.asText().isBlank()) skills.add(skill.asText().trim());
            }
            String category = text(item, "category").isEmpty() ? text(item, "name") : text(item, "category");
            if (skills.isEmpty() && category.isEmpty()) continue;
            ObjectNode payload = mapper.createObjectNode();
            ArrayNode list = payload.putArray("skills");
            skills.forEach(list::add);
            add(entries, write("SKILL", category.isEmpty() ? "专业技能" : category, null, null, null,
                    String.join("、", skills), payload));
        }
        for (JsonNode item : items(content, "certificates")) {
            String name = text(item, "name");
            if (name.isEmpty()) continue;
            add(entries, write("CERTIFICATE", name, text(item, "issuer"), null, dated(item), text(item, "description"), null));
        }
        for (JsonNode item : items(content, "honors")) {
            String name = text(item, "name");
            if (name.isEmpty()) continue;
            add(entries, write("HONOR", name, text(item, "issuer"), null, dated(item), text(item, "description"), null));
        }
        for (JsonNode item : items(content, "languages")) {
            String language = text(item, "language");
            if (language.isEmpty()) continue;
            String level = String.join(" · ", nonEmpty(text(item, "level"), text(item, "score")));
            add(entries, write("LANGUAGE", language, null, level.isEmpty() ? null : level, null, null, null));
        }
        return entries;
    }

    private void add(List<Entry> entries, RecordWrite write) {
        String identity = identity(write);
        // Two identical entries in one resume become one candidate.
        if (entries.stream().anyMatch(entry -> identity(entry.write()).equals(identity))) return;
        entries.add(new Entry(write.type().toLowerCase(Locale.ROOT) + "-" + digest(identity), write));
    }

    private RecordWrite write(String type, String title, String organization, String role, JsonNode dates,
            String description, JsonNode payload) {
        String start = dates == null ? null : blankToNull(text(dates, "startDate"));
        String end = dates == null ? null : blankToNull(text(dates, "endDate"));
        if (dates != null && dates.path("current").asBoolean(false)) end = null;
        return new RecordWrite(type, clip(title, 200), blankToNull(clip(organization, 200)), blankToNull(clip(role, 200)),
                start, end, dates == null ? null : blankToNull(text(dates, "location")),
                blankToNull(clip(description, MAX_TEXT)), null, null,
                payload == null ? mapper.createObjectNode() : payload, null, null, null);
    }

    /** Certificates and honours carry one date; it becomes the record's start. */
    private ObjectNode dated(JsonNode item) {
        ObjectNode dates = mapper.createObjectNode();
        dates.put("startDate", text(item, "date"));
        return dates;
    }

    private static String describe(JsonNode item) {
        List<String> lines = new ArrayList<>();
        String description = text(item, "description");
        if (!description.isEmpty()) lines.add(description);
        for (JsonNode highlight : item.path("highlights")) {
            if (highlight.isTextual() && !highlight.asText().isBlank()) lines.add("- " + highlight.asText().trim());
        }
        return String.join("\n", lines);
    }

    private static Iterable<JsonNode> items(JsonNode content, String key) {
        JsonNode node = content.path(key);
        List<JsonNode> objects = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> { if (item.isObject()) objects.add(item); });
        return objects;
    }

    private static String identity(RecordWrite write) {
        return identity(write.type(), write.title(), write.organization());
    }

    private static String identity(String type, String title, String organization) {
        return normalize(type) + "|" + normalize(title) + "|" + normalize(organization);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s·・.,，、]+", "").toLowerCase(Locale.ROOT);
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> nonEmpty(String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) out.add(value);
        return out;
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        return value.isTextual() || value.isNumber() ? value.asText().trim() : "";
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private JsonNode read(String json) {
        try {
            JsonNode node = mapper.readTree(json == null ? "{}" : json);
            return node == null ? mapper.createObjectNode() : node;
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    record Entry(String key, RecordWrite write) {
    }

    private record ResumeContent(String title, JsonNode content) {
    }

    public record Candidate(String key, String type, String title, String organization, String role,
            String startDate, String endDate, String description, boolean alreadyInLibrary) {
    }

    public record CandidatesView(String masterId, String resumeTitle, List<Candidate> items) {
    }

    public record ImportView(int created, int skipped, List<RecordView> records) {
    }
}
