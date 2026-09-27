package org.letsemploy.ojobpub_publisher.location;

import com.neovisionaries.i18n.CountryCode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The country choices for a location, for the native select every location field
 * uses - browsers already provide type-ahead, so no library (spec 7.8). One list,
 * shared by the location form and the headquarters on the employer form.
 */
public final class Countries {

    private Countries() {
    }

    /** Officially assigned ISO 3166-1 codes, by name: alpha-2 code → name. */
    public static Map<String, String> options() {
        Map<String, String> countries = new LinkedHashMap<>();
        Arrays.stream(CountryCode.values())
                .filter(c -> c != CountryCode.UNDEFINED && c.getAssignment() == CountryCode.Assignment.OFFICIALLY_ASSIGNED)
                .sorted(Comparator.comparing(CountryCode::getName))
                .forEach(c -> countries.put(c.getAlpha2().toUpperCase(), c.getName()));
        return countries;
    }
}
