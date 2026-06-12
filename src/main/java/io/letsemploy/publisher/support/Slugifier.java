package io.letsemploy.publisher.support;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugifier {

    private Slugifier() {
    }

    public static String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "item";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "item" : normalized;
    }
}
