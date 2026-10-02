package org.letsemploy.ojobpub_publisher;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Flyway migrates, then the seed runs, then Hibernate starts - fixed by the bean
 * definitions, not by luck.
 *
 * <p>With {@code spring.jpa.defer-datasource-initialization} set, the order
 * between Flyway and JPA was decided by the classpath order of the build. Maven's
 * order happened to work, so every test passed; the 1.0.0 image's jar listed its
 * libraries alphabetically, and Flyway and the entity manager factory then
 * depended on each other and the application did not start.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
class DatabaseInitOrderTest {

    private static final String EMF = "entityManagerFactory";

    @Autowired
    private ConfigurableListableBeanFactory beans;

    private List<String> dependsOn(String bean) {
        String[] names = beans.getBeanDefinition(bean).getDependsOn();
        return names == null ? List.of() : List.of(names);
    }

    private String only(Class<?> type) {
        String[] names = beans.getBeanNamesForType(type, true, false);
        assertThat(names).hasSize(1);
        return names[0];
    }

    @Test
    void flywayThenTheSeedThenHibernate() {
        String flyway = only(Flyway.class);
        // The bean that runs the migration; the Flyway bean only configures it.
        String migration = only(FlywayMigrationInitializer.class);
        String seed = only(DataSourceScriptDatabaseInitializer.class);
        assertThat(dependsOn(flyway)).doesNotContain(EMF);
        assertThat(dependsOn(migration)).doesNotContain(EMF, seed);
        assertThat(dependsOn(seed)).contains(migration).doesNotContain(EMF);
        assertThat(dependsOn(EMF)).contains(migration, seed);
    }
}
