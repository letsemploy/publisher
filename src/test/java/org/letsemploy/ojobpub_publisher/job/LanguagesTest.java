package org.letsemploy.ojobpub_publisher.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The language list of a job (spec 3.3): every ISO 639-1 code, none withdrawn. No Spring. */
class LanguagesTest {

    @Test
    void everyIso639Dash1CodeAndNoWithdrawnOne() {
        assertThat(Languages.CODES)
                .hasSize(184)
                .allMatch(code -> code.matches("[a-z]{2}"))
                .contains("en", "de", "he", "id", "yi", "ht", "zu")
                .doesNotContain("iw", "in", "ji", "mo");
    }

    @Test
    void onlyAListedCodeIsKnown() {
        assertThat(Languages.isKnown("en")).isTrue();
        assertThat(Languages.isKnown("zz")).isFalse();
        assertThat(Languages.isKnown("iw")).isFalse();
        assertThat(Languages.isKnown(null)).isFalse();
    }

    @Test
    void theOptionsAreNamedAndSortedInTheReadersLanguage() {
        Map<String, String> german = Languages.options(Locale.GERMAN);
        assertThat(german).hasSize(184).containsEntry("de", "Deutsch").containsEntry("en", "Englisch");
        List<String> codes = new ArrayList<>(german.keySet());
        assertThat(codes.indexOf("de")).as("Deutsch before Englisch").isLessThan(codes.indexOf("en"));
        assertThat(Languages.options(Locale.ENGLISH)).containsEntry("de", "German");
    }
}
