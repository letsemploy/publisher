package org.letsemploy.ojobpub_publisher.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.letsemploy.ojobpub_publisher.config.WebLangConfig;

/**
 * The message bundles, at parity (spec 8.1): every language there is a bundle
 * for has every key, none of them empty, and every message that takes arguments
 * still shows each of them.
 *
 * <p>Both CLAUDE.md and the specification have long claimed that "a key added to
 * one and not the other fails the build". No test enforced it: the only cover was
 * {@code ScreenRenderingTest}'s check that no {@code ??key??} marker reaches a
 * page, which runs in the default locale and only for screens it enumerates — so
 * a missing German key on a flash message shipped silently.
 */
class MessageBundleTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    /** Every language but English, whose bundle is the original. */
    static Stream<String> translations() {
        return WebLangConfig.LANGUAGES.stream().filter(language -> !language.equals("en"));
    }

    private static Properties bundle(String name) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = MessageBundleTest.class.getResourceAsStream("/" + name)) {
            assertThat(in).as(name + " is on the classpath").isNotNull();
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @ParameterizedTest
    @MethodSource("translations")
    void everyKeyExistsInEveryLanguage(String language) throws Exception {
        Set<String> english = new TreeSet<>(bundle("messages.properties").stringPropertyNames());
        Set<String> translated = new TreeSet<>(bundle("messages_" + language + ".properties").stringPropertyNames());

        assertThat(difference(english, translated))
                .as("keys in messages.properties with no " + language + " translation")
                .isEmpty();
        assertThat(difference(translated, english))
                .as("keys in messages_" + language + ".properties with no English original")
                .isEmpty();
    }

    /** No value may be empty: a blank translation renders as a blank screen label. */
    @ParameterizedTest
    @MethodSource("translations")
    void noTranslationIsBlank(String language) throws Exception {
        for (String name : new String[]{"messages.properties", "messages_" + language + ".properties"}) {
            Properties properties = bundle(name);
            for (String key : properties.stringPropertyNames()) {
                assertThat(properties.getProperty(key).trim())
                        .as(name + " key " + key)
                        .isNotEmpty();
            }
        }
    }

    /**
     * Formatted the way Spring formats a message with arguments - by
     * MessageFormat, where a lone ASCII apostrophe starts a quotation and silently
     * swallows what follows - each argument the English shows must still show.
     * Numbers are passed, so a {@code choice} lands on its plural branch.
     */
    @ParameterizedTest
    @MethodSource("translations")
    void everyArgumentSurvivesFormatting(String language) throws Exception {
        Properties english = bundle("messages.properties");
        Properties translated = bundle("messages_" + language + ".properties");
        for (String key : english.stringPropertyNames()) {
            Set<Integer> indexes = placeholders(english.getProperty(key));
            if (indexes.isEmpty() || translated.getProperty(key) == null) {
                continue;
            }
            Object[] arguments = new Object[indexes.stream().mapToInt(i -> i).max().orElse(0) + 1];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = 701 + i;
            }
            String formatted = new MessageFormat(translated.getProperty(key), Locale.forLanguageTag(language))
                    .format(arguments);
            for (int index : indexes) {
                assertThat(formatted).as(language + " key " + key + " shows argument {" + index + "}")
                        .contains(String.valueOf(701 + index));
            }
        }
    }

    private static Set<Integer> placeholders(String value) {
        Set<Integer> found = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) {
            found.add(Integer.parseInt(m.group(1)));
        }
        return found;
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }
}
