package org.letsemploy.ojobpub_publisher.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The message bundles, at parity.
 *
 * <p>Both CLAUDE.md and the specification have long claimed that "a key added to
 * one and not the other fails the build". No test enforced it: the only cover was
 * {@code ScreenRenderingTest}'s check that no {@code ??key??} marker reaches a
 * page, which runs in the default locale and only for screens it enumerates — so
 * a missing German key on a flash message shipped silently.
 */
class MessageBundleTest {

    private static Properties bundle(String name) throws Exception {
        Properties properties = new Properties();
        try (InputStream in = MessageBundleTest.class.getResourceAsStream("/" + name)) {
            assertThat(in).as(name + " is on the classpath").isNotNull();
            properties.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void everyKeyExistsInBothLanguages() throws Exception {
        Set<String> english = new TreeSet<>(bundle("messages.properties").stringPropertyNames());
        Set<String> german = new TreeSet<>(bundle("messages_de.properties").stringPropertyNames());

        assertThat(new TreeSet<>(difference(english, german)))
                .as("keys in messages.properties with no German translation")
                .isEmpty();
        assertThat(new TreeSet<>(difference(german, english)))
                .as("keys in messages_de.properties with no English original")
                .isEmpty();
    }

    /** No value may be empty: a blank translation renders as a blank screen label. */
    @Test
    void noTranslationIsBlank() throws Exception {
        for (String name : new String[]{"messages.properties", "messages_de.properties"}) {
            Properties properties = bundle(name);
            for (String key : properties.stringPropertyNames()) {
                assertThat(properties.getProperty(key).trim())
                        .as(name + " key " + key)
                        .isNotEmpty();
            }
        }
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }
}
