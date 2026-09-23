package org.letsemploy.ojobpub_publisher;

import java.util.ArrayList;
import java.util.List;
import org.springframework.test.context.ActiveProfilesResolver;

/**
 * The profiles a Spring test runs under, and the one place that decides which
 * database it runs against (spec 9.3).
 *
 * <p>{@code dev} provides the authentication bypass, {@code test} the test
 * database and settings, listed second so it outranks dev. With
 * {@code TEST_DB=sqlite} in the environment, {@code sqlite} is appended last, so
 * its datasource outranks the MariaDB one in application-test.yml - which is how
 * CI runs the whole suite a second time on SQLite. A profile cannot be added from
 * outside a test that names its own with {@code @ActiveProfiles}, hence a resolver
 * rather than a property.
 */
public class TestProfiles implements ActiveProfilesResolver {

    @Override
    public String[] resolve(Class<?> testClass) {
        return withDatabase("dev", "test");
    }

    /** For a slice test that wants the test database but not the dev bypass. */
    public static class WithoutDev implements ActiveProfilesResolver {
        @Override
        public String[] resolve(Class<?> testClass) {
            return withDatabase("test");
        }
    }

    static String[] withDatabase(String... profiles) {
        List<String> active = new ArrayList<>(List.of(profiles));
        if ("sqlite".equalsIgnoreCase(System.getenv("TEST_DB"))) {
            active.add("sqlite");
        }
        return active.toArray(String[]::new);
    }
}
