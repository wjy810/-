package com.jobproof.shared.privacy;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which resume fields are contact details or personal traits, shared by every place that strips them: the
 * share page with contact hidden, and what job match sends to a model. One list, so the two cannot drift.
 */
public final class ResumeContactFields {
    private ResumeContactFields() {}

    /** Ways to reach the person, kept under {@code basics} in the resume JSON. */
    public static final List<String> BASICS_CONTACT = List.of("phone", "email", "wechat", "qq", "links", "personalLinks",
            "website", "url", "github", "linkedin");

    /** Identity and protected traits, removed wherever they appear. */
    public static final List<String> PERSONAL = List.of("fullAddress", "identityNumber", "age", "gender", "maritalStatus",
            "ethnicity", "birthDate", "photoRef", "photo", "photoFileId", "avatar");

    private static final Set<String> PERSONAL_LOWER = PERSONAL.stream().map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> CONTACT_LOWER = BASICS_CONTACT.stream().map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    public static boolean isPersonal(String key) {
        return key != null && PERSONAL_LOWER.contains(key.toLowerCase(Locale.ROOT));
    }

    public static boolean isBasicsContact(String key) {
        return key != null && CONTACT_LOWER.contains(key.toLowerCase(Locale.ROOT));
    }
}
