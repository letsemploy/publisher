package org.letsemploy.ojobpub_publisher.job;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The language choices for a job: every ISO 639-1:2002 code, which is what the
 * published document's {@code language} holds (spec 3.3, 6). One list for the
 * form's select and for the service, which refuses anything else, so the API
 * cannot store a code the form would not offer.
 */
public final class Languages {

    /**
     * The JDK's two-letter codes, less the four it keeps for old data: {@code iw},
     * {@code in} and {@code ji} were replaced by {@code he}, {@code id} and
     * {@code yi}, and {@code mo} was withdrawn.
     */
    private static final Set<String> WITHDRAWN = Set.of("iw", "in", "ji", "mo");

    public static final Set<String> CODES = Arrays.stream(Locale.getISOLanguages())
            .filter(code -> !WITHDRAWN.contains(code))
            .collect(Collectors.toUnmodifiableSet());

    private Languages() {
    }

    public static boolean isKnown(String code) {
        return code != null && CODES.contains(code);
    }

    /** Code → name in the reader's language, sorted by that name. */
    public static Map<String, String> options(Locale locale) {
        Collator collator = Collator.getInstance(locale);
        Map<String, String> languages = new LinkedHashMap<>();
        CODES.stream()
                .map(code -> Map.entry(code, Locale.of(code).getDisplayLanguage(locale)))
                .sorted(Comparator.comparing(Map.Entry::getValue, collator))
                .forEach(e -> languages.put(e.getKey(), e.getValue()));
        return languages;
    }
}
