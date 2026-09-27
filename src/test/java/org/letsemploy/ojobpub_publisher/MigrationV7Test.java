package org.letsemploy.ojobpub_publisher;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * V7 divides the once-global locations and tags among the employers that use
 * them (spec 3.2, 3.4), on both databases.
 *
 * <p>A database is migrated to V6, given two employers that share a location and
 * a tag plus rows nobody uses, then migrated to V7. The oldest employer must keep
 * the original rows and ids, the other must get copies with its headquarters and
 * jobs pointed at them, the unused rows must go to the oldest employer, and
 * deleting either employer must then take its own rows with it.
 *
 * <p>Plain JUnit, no Spring: the migration is what is under test, not the
 * application. SQLite runs on a temporary file, always; MariaDB on a scratch
 * database of the test server, except where there is none (the SQLite CI leg).
 */
class MigrationV7Test {

    private static final String OLD = "00000000-0000-4000-8000-0000000000a1";
    private static final String NEW = "00000000-0000-4000-8000-0000000000a2";
    private static final String BERN = "00000000-0000-4000-8000-00000000000b";
    private static final String ZURICH = "00000000-0000-4000-8000-00000000000c";

    @Test
    void onSqlite(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("v7.db") + "?foreign_keys=on&date_class=TEXT";
        check(url, "", "", "classpath:db/migration/sqlite", "2026-01-01 00:00:00.000", "2026-02-01 00:00:00.000");
        try (Connection c = DriverManager.getConnection(url)) {
            assertThat(rows(c, "PRAGMA foreign_key_check")).as("no foreign key violations").isEmpty();
        }
        assertThat(Files.exists(dir.resolve("v7.db"))).isTrue();
    }

    @Test
    @DisabledIfEnvironmentVariable(named = "TEST_DB", matches = "sqlite")
    void onMariadb() throws Exception {
        String server = "jdbc:mariadb://127.0.0.1:" + System.getenv().getOrDefault("DB_PORT", "3307") + "/";
        try (Connection c = DriverManager.getConnection(server, "root", "root");
             Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS publisher_v7_test");
            s.execute("CREATE DATABASE publisher_v7_test");
        }
        try {
            check(server + "publisher_v7_test", "root", "root", "classpath:db/migration/mariadb",
                    "2026-01-01", "2026-02-01");
        } finally {
            try (Connection c = DriverManager.getConnection(server, "root", "root");
                 Statement s = c.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS publisher_v7_test");
            }
        }
    }

    private void check(String url, String user, String password, String location,
                       String oldCreated, String newCreated) throws Exception {
        flyway(url, user, password, location).target("6").load().migrate();
        try (Connection c = DriverManager.getConnection(url, user, password);
             Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO locations (id, city, country) VALUES ('" + BERN + "','Bern','CH'),"
                    + "('" + ZURICH + "','Zürich','CH'),('00000000-0000-4000-8000-00000000000e','Berlin','DE')");
            s.executeUpdate("INSERT INTO employers (id, created_at, name, slug, location_id) VALUES "
                    + "('" + OLD + "','" + oldCreated + "','Old Co','old','" + BERN + "'),"
                    + "('" + NEW + "','" + newCreated + "','New Co','new','" + BERN + "')");
            s.executeUpdate("INSERT INTO tags (id, name) VALUES (1,'java'),(2,'rust'),(3,'unused')");
            s.executeUpdate("INSERT INTO jobs (id, employer_id, title, url, language_code, job_type) VALUES "
                    + "('00000000-0000-4000-8000-0000000000f1','" + OLD + "','Old job','https://x','en','PERMANENT'),"
                    + "('00000000-0000-4000-8000-0000000000f2','" + NEW + "','New job','https://y','en','PERMANENT')");
            s.executeUpdate("INSERT INTO job_locations VALUES "
                    + "('00000000-0000-4000-8000-0000000000f1','" + ZURICH + "'),"
                    + "('00000000-0000-4000-8000-0000000000f2','" + ZURICH + "')");
            s.executeUpdate("INSERT INTO job_tags VALUES ('00000000-0000-4000-8000-0000000000f1',1),"
                    + "('00000000-0000-4000-8000-0000000000f2',1),('00000000-0000-4000-8000-0000000000f2',2)");
        }

        flyway(url, user, password, location).load().migrate();

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            // The oldest keeps the originals, ids and all, and the unused Berlin.
            assertThat(rows(c, "SELECT l.city FROM locations l WHERE l.employer_id = '" + OLD
                    + "' AND l.id LIKE '00000000-%' ORDER BY l.city"))
                    .containsExactly("Berlin", "Bern", "Zürich");
            // The other gets copies, with fresh ids.
            assertThat(rows(c, "SELECT l.city FROM locations l WHERE l.employer_id = '" + NEW
                    + "' AND l.id NOT LIKE '00000000-%' ORDER BY l.city"))
                    .containsExactly("Bern", "Zürich");
            // Every headquarters and job location is the employer's own.
            assertThat(rows(c, "SELECT e.name FROM employers e JOIN locations l ON l.id = e.location_id"
                    + " WHERE l.employer_id <> e.id")).isEmpty();
            assertThat(rows(c, "SELECT j.title FROM job_locations jl JOIN jobs j ON j.id = jl.job_id"
                    + " JOIN locations l ON l.id = jl.location_id WHERE l.employer_id <> j.employer_id")).isEmpty();
            // Tags: java split, rust kept by its only user, unused to the oldest.
            assertThat(rows(c, "SELECT t.name FROM tags t WHERE t.employer_id = '" + OLD + "' ORDER BY t.name"))
                    .containsExactly("java", "unused");
            assertThat(rows(c, "SELECT t.name FROM tags t WHERE t.employer_id = '" + NEW + "' ORDER BY t.name"))
                    .containsExactly("java", "rust");
            assertThat(rows(c, "SELECT CAST(t.id AS CHAR) FROM tags t WHERE t.employer_id = '" + OLD
                    + "' AND t.name = 'java'")).containsExactly("1");
            assertThat(rows(c, "SELECT j.title FROM job_tags jt JOIN jobs j ON j.id = jt.job_id"
                    + " JOIN tags t ON t.id = jt.tag_id WHERE t.employer_id <> j.employer_id")).isEmpty();
            assertThat(rows(c, "SELECT j.title FROM job_tags jt JOIN jobs j ON j.id = jt.job_id ORDER BY j.title"))
                    .as("no job lost a tag").containsExactly("New job", "New job", "Old job");

            // Deleting an employer takes its rows, and only its rows.
            try (Statement s = c.createStatement()) {
                s.executeUpdate("DELETE FROM employers WHERE id = '" + NEW + "'");
            }
            assertThat(rows(c, "SELECT city FROM locations ORDER BY city")).containsExactly("Berlin", "Bern", "Zürich");
            assertThat(rows(c, "SELECT name FROM tags ORDER BY name")).containsExactly("java", "unused");
            try (Statement s = c.createStatement()) {
                s.executeUpdate("DELETE FROM employers WHERE id = '" + OLD + "'");
            }
            assertThat(rows(c, "SELECT city FROM locations")).isEmpty();
            assertThat(rows(c, "SELECT name FROM tags")).isEmpty();
        }
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(
            String url, String user, String password, String location) {
        return Flyway.configure().dataSource(url, user, password).locations(location).table("migrations");
    }

    private static List<String> rows(Connection c, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) {
                values.add(r.getString(1));
            }
        }
        return values;
    }
}
