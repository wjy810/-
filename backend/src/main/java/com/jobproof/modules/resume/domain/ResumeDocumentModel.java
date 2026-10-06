package com.jobproof.modules.resume.domain;

import java.util.ArrayList;
import java.util.List;

/** Content-only renderer input; template samples must never populate this model. */
public record ResumeDocumentModel(Header header, List<Section> sections) {
    public ResumeDocumentModel {
        header = header == null ? Header.empty() : header;
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    public ResumeDocumentModel(List<Section> sections) {
        this(Header.empty(), sections);
    }

    public record Header(
            String name,
            String targetJob,
            String email,
            String phone,
            String location,
            List<String> links,
            Photo photo) {
        public Header {
            name = normalize(name);
            targetJob = normalize(targetJob);
            email = normalize(email);
            phone = normalize(phone);
            location = normalize(location);
            List<String> normalizedLinks = new ArrayList<>();
            if (links != null) {
                for (String link : links) {
                    String normalized = normalize(link);
                    if (!normalized.isEmpty()) normalizedLinks.add(normalized);
                }
            }
            links = List.copyOf(normalizedLinks);
        }

        public Header(
                String name,
                String targetJob,
                String email,
                String phone,
                String location,
                List<String> links) {
            this(name, targetJob, email, phone, location, links, null);
        }

        public static Header empty() {
            return new Header("", "", "", "", "", List.of(), null);
        }

        public Header withPhoto(Photo value) {
            return new Header(name, targetJob, email, phone, location, links, value);
        }

        public String contactLine() {
            List<String> values = new ArrayList<>();
            add(values, email);
            add(values, phone);
            add(values, location);
            links.forEach(value -> add(values, value));
            return String.join(" · ", values);
        }

        public int deterministicUnits() {
            return List.of(name, targetJob, contactLine()).stream()
                    .mapToInt(value -> value.codePointCount(0, value.length()))
                    .sum();
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }

        private static void add(List<String> values, String value) {
            if (value != null && !value.isBlank()) values.add(value.trim());
        }
    }

    public record Photo(String contentType, byte[] bytes) {
        public Photo {
            contentType = contentType == null ? "" : contentType.trim().toLowerCase(java.util.Locale.ROOT);
            bytes = bytes == null ? new byte[0] : bytes.clone();
            if (!("image/png".equals(contentType) || "image/jpeg".equals(contentType)) || bytes.length == 0) {
                throw new IllegalArgumentException("Resume photo must be a non-empty PNG or JPEG");
            }
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record Section(String slotKey, String plainText, int itemCount, List<Entry> entries) {
        public Section {
            slotKey = normalize(slotKey);
            plainText = plainText == null ? "" : plainText;
            entries = entries == null ? List.of() : List.copyOf(entries);
        }

        public Section(String slotKey, String plainText, int itemCount) {
            this(slotKey, plainText, itemCount, List.of());
        }

        public int deterministicUnits() {
            return (plainText == null ? 0 : plainText.codePointCount(0, plainText.length()))
                    + Math.max(0, itemCount) * 8;
        }
    }

    public record Entry(
            String primary,
            String secondary,
            String date,
            String location,
            String description,
            List<String> highlights) {
        public Entry {
            primary = normalize(primary);
            secondary = normalize(secondary);
            date = normalize(date);
            location = normalize(location);
            description = normalize(description);
            List<String> normalizedHighlights = new ArrayList<>();
            if (highlights != null) {
                for (String highlight : highlights) {
                    String normalized = normalize(highlight);
                    if (!normalized.isEmpty()) normalizedHighlights.add(normalized);
                }
            }
            highlights = List.copyOf(normalizedHighlights);
        }

        public boolean isEmpty() {
            return primary.isEmpty() && secondary.isEmpty() && date.isEmpty() && location.isEmpty()
                    && description.isEmpty() && highlights.isEmpty();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
