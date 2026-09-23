package org.letsemploy.ojobpub_publisher.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Creates the directory the SQLite database file lives in (spec 9.3).
 *
 * <p>The driver creates the file but not its parent, so a fresh checkout or an
 * empty volume would otherwise fail at the first connection with an error that
 * does not name the directory. A {@link BeanFactoryPostProcessor} because it has
 * to run before the DataSource exists; the path is read from the datasource URL
 * itself, so it holds when a deployment sets {@code SPRING_DATASOURCE_URL}
 * directly rather than {@code app.sqlite.path}.
 */
@Component
@Profile("sqlite")
@Slf4j
public class SqliteConfig implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final String PREFIX = "jdbc:sqlite:";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        databaseFile(environment.getProperty("spring.datasource.url")).ifPresent(file -> {
            Path parent = file.toAbsolutePath().getParent();
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot create the SQLite directory " + parent, e);
            }
            log.info("SQLite database: {}", file.toAbsolutePath());
        });
    }

    /** The file a SQLite URL names, if it names one: not in-memory, not a {@code file:} URI. */
    static Optional<Path> databaseFile(String url) {
        if (url == null || !url.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String path = url.substring(PREFIX.length());
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.isBlank() || path.startsWith(":memory:") || path.startsWith("file:")) {
            return Optional.empty();
        }
        return Optional.of(Path.of(path));
    }
}
