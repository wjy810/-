package com.jobproof.modules.membership.domain;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

/**
 * Redeem code format: {@code JP-XXXX-XXXX-XXXX-XXXX}, 16 characters from an alphabet without the
 * look-alikes 0/O and 1/I/L (80 bits). People type them from paper and screenshots, so input is
 * forgiving: case, spaces, dashes and the JP prefix do not matter.
 */
public final class RedeemCodes {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RedeemCodes() {
    }

    /** A new code, formatted for display. */
    public static String generate() {
        StringBuilder raw = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) raw.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return format(raw.toString());
    }

    /** The canonical 16 characters, or empty when the input cannot be a code. */
    public static Optional<String> normalize(String input) {
        if (input == null) return Optional.empty();
        String compact = input.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-_]", "");
        if (compact.startsWith("JP") && compact.length() == LENGTH + 2) compact = compact.substring(2);
        if (compact.length() != LENGTH) return Optional.empty();
        for (char c : compact.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) return Optional.empty();
        }
        return Optional.of(compact);
    }

    public static String format(String canonical) {
        return "JP-" + canonical.substring(0, 4) + "-" + canonical.substring(4, 8) + "-"
                + canonical.substring(8, 12) + "-" + canonical.substring(12, 16);
    }

    /** The last four characters, enough for support to tell codes apart without revealing them. */
    public static String hint(String canonical) {
        return canonical.substring(LENGTH - 4);
    }
}
