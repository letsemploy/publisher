package io.letsemploy.publisher.support;

import java.util.Locale;

public final class EnumLabels {

    private EnumLabels() {
    }

    public static String pretty(Enum<?> value) {
        if (value == null) {
            return "";
        }
        return value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
