package org.letsemploy.ojobpub_publisher.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Slug generation (spec 3.6). A slug only makes the public URL readable; identity
 * lives in the UUID beside it, so slugs need not be unique.
 */
public final class Slugs {

    public static final int MAX_LENGTH = 64;

    private Slugs() {
    }

    /**
     * Lower-cases, folds accents to ASCII, collapses every run of other characters
     * to a single hyphen and truncates on a hyphen boundary.
     *
     * <p>Underscores become hyphens: the underscore separates slug from UUID in the
     * public URL, so admitting one would make that URL ambiguous to parse (spec 5.1).
     */
    public static String slugify(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String folded = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("ß", "ss");
        String slug = folded.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH);
            int lastHyphen = slug.lastIndexOf('-');
            if (lastHyphen > 0) {
                slug = slug.substring(0, lastHyphen);
            }
        }
        // A name written entirely in a non-Latin script normalizes to nothing.
        return slug.isBlank() ? fallback : slug;
    }
}
