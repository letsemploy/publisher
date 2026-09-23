package org.letsemploy.ojobpub_publisher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The two migration folders advance together (spec 9.3).
 *
 * <p>SQLite arrived when MariaDB was at V6 and starts from a single baseline at
 * that version, so a SQLite database and a MariaDB one report the same schema
 * version. From there every migration is a pair. Without this, a migration added
 * to one folder only would pass on the database the author happened to use and
 * fail on the other the first time someone deployed it.
 *
 * <p>Plain unit test: no Spring context, no database.
 */
class MigrationParityTest {

    /** Where SQLite joined; everything after it must exist in both folders. */
    private static final int SQLITE_BASELINE = 6;

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.+\\.sql$");

    private static SortedSet<Integer> versions(String vendor) throws IOException {
        Resource[] scripts = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/" + vendor + "/V*__*.sql");
        SortedSet<Integer> found = new TreeSet<>();
        Arrays.stream(scripts).map(Resource::getFilename).filter(Objects::nonNull).forEach(name -> {
            Matcher m = VERSION.matcher(name);
            assertThat(m.matches()).as("%s/%s is a versioned migration", vendor, name).isTrue();
            found.add(Integer.parseInt(m.group(1)));
        });
        return found;
    }

    @Test
    void sqliteStartsFromOneBaselineAtTheVersionItJoined() throws IOException {
        assertThat(versions("sqlite").first()).isEqualTo(SQLITE_BASELINE);
    }

    @Test
    void everyMigrationAfterTheBaselineExistsForBothDatabases() throws IOException {
        SortedSet<Integer> mariadb = versions("mariadb");
        SortedSet<Integer> sqlite = versions("sqlite");

        assertThat(mariadb.last())
                .as("MariaDB must be at or past the SQLite baseline")
                .isGreaterThanOrEqualTo(SQLITE_BASELINE);
        assertThat(sqlite)
                .as("migrations after V%d, per database", SQLITE_BASELINE)
                .isEqualTo(mariadb.tailSet(SQLITE_BASELINE));
    }
}
