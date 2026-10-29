package org.letsemploy.ojobpub_publisher;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Every foreign-key column leads an index (spec 9.3). InnoDB makes one for each
 * foreign key by itself; SQLite does not, and without one every delete of the
 * referenced row scans the whole referencing table to cascade or to check. A
 * new foreign key in a SQLite migration needs its {@code CREATE INDEX}, as V18
 * added for the seven that had none.
 *
 * <p>Read through JDBC metadata, so the same test runs on both databases.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
class ForeignKeyIndexTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void everyForeignKeyColumnLeadsAnIndex() throws Exception {
        List<String> unindexed = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (String table : tables(meta, catalog)) {
                Set<String> leading = leadingIndexColumns(meta, catalog, table);
                try (ResultSet keys = meta.getImportedKeys(catalog, null, table)) {
                    while (keys.next()) {
                        // Single-column keys only, which is every key in this schema.
                        String column = keys.getString("FKCOLUMN_NAME").toLowerCase();
                        if (!leading.contains(column)) {
                            unindexed.add(table + "." + column);
                        }
                    }
                }
            }
        }
        assertThat(unindexed).as("foreign-key columns without an index").isEmpty();
    }

    private static List<String> tables(DatabaseMetaData meta, String catalog) throws Exception {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                // Flyway's own history table and SQLite's internals are not the schema.
                if (!name.equals("migrations") && !name.startsWith("sqlite_")) {
                    tables.add(name);
                }
            }
        }
        return tables;
    }

    /** The first column of every index on the table, the primary key's included. */
    private static Set<String> leadingIndexColumns(DatabaseMetaData meta, String catalog, String table)
            throws Exception {
        Set<String> leading = new HashSet<>();
        try (ResultSet rs = meta.getIndexInfo(catalog, null, table, false, false)) {
            while (rs.next()) {
                if (rs.getShort("ORDINAL_POSITION") == 1 && rs.getString("COLUMN_NAME") != null) {
                    leading.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        try (ResultSet rs = meta.getPrimaryKeys(catalog, null, table)) {
            while (rs.next()) {
                if (rs.getShort("KEY_SEQ") == 1) {
                    leading.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        return leading;
    }
}
