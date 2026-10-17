package com.jobproof.modules.task.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The resource ids a task works on, read from its input payload. Task events carry them so a
 * notification can link straight back to the match, plan, file or resume the task was about.
 */
public final class TaskRefs {

    public static final List<String> KEYS = List.of("matchId", "sessionId", "careerFileId", "resumeImportId", "resumeVersionId");

    private static final ObjectMapper JSON = new ObjectMapper();
    /** Ids end up inside links, so anything but a plain id is dropped. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private TaskRefs() {
    }

    public static Map<String, String> from(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return Map.of();
        JsonNode node;
        try {
            node = JSON.readTree(payloadJson);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
        if (node == null || !node.isObject()) return Map.of();
        Map<String, String> refs = new LinkedHashMap<>();
        for (String key : KEYS) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual() && SAFE_ID.matcher(value.asText()).matches()) {
                refs.put(key, value.asText());
            }
        }
        return Map.copyOf(refs);
    }

    public static boolean isSafeId(String value) {
        return value != null && SAFE_ID.matcher(value).matches();
    }
}
