package com.khetisetu.event.notifications.util;

import java.util.Locale;
import java.util.Set;

/**
 * Normalizes the language code carried on notification events to a template
 * folder name under {@code src/main/resources/templates/}.
 *
 * <p>Hindi was historically stored as {@code "hn"} across the KhetiSetu services,
 * while the web app and the ISO 639-1 code use {@code "hi"}. The template folders
 * are now named {@code hi}; this maps the legacy value so events from older
 * producers or older user rows still render Hindi instead of failing.
 */
public final class LanguageCodes {

    public static final String DEFAULT = "en";
    private static final String LEGACY_HINDI = "hn";
    private static final String HINDI = "hi";
    private static final Set<String> SUPPORTED = Set.of("en", "hi", "mr", "kn", "ta", "te");

    private LanguageCodes() {
    }

    /** Returns a supported template language code, defaulting to English. */
    public static String normalize(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT;
        }
        String code = language.trim().toLowerCase(Locale.ROOT);
        if (LEGACY_HINDI.equals(code)) {
            code = HINDI;
        }
        return SUPPORTED.contains(code) ? code : DEFAULT;
    }
}
