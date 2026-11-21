package dev.hookdock.common;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Uses a local H2 file database when no database URL is configured, so the
 * app runs without PostgreSQL or Docker.
 * <p>
 * Only an unset or blank {@code spring.datasource.url} (normally from
 * {@code HOOKDOCK_DB_URL}) switches to H2. A PostgreSQL URL that can't be
 * reached still fails at startup: quietly writing to a different, empty
 * database would make captured webhooks look lost.
 * <p>
 * H2 runs in PostgreSQL mode, so the same Flyway migrations apply as-is, as
 * they do in the tests. It has its own fixed credentials because the database
 * is a local file, not a server; the PostgreSQL username and password are not
 * used for it.
 * <p>
 * Registered in {@code META-INF/spring.factories}. Runs after
 * {@code application.yml} and the environment have been loaded.
 */
public class LocalDatabaseFallback implements EnvironmentPostProcessor, Ordered {

    static final String PATH_PROPERTY = "hookdock.local-db.path";
    static final String DEFAULT_PATH = "./data/hookdock";
    static final String H2_OPTIONS = ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    static final String USERNAME = "sa";

    private final Log log;

    public LocalDatabaseFallback(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(LocalDatabaseFallback.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty("spring.datasource.url"))) {
            return;
        }
        String path = environment.getProperty(PATH_PROPERTY);
        if (!StringUtils.hasText(path)) {
            path = DEFAULT_PATH;
        }
        String url = "jdbc:h2:file:" + path.trim() + H2_OPTIONS;
        environment.getPropertySources().addFirst(new MapPropertySource("hookdockLocalDatabase", Map.of(
                "spring.datasource.url", url,
                "spring.datasource.username", USERNAME,
                "spring.datasource.password", "")));
        log.warn("HOOKDOCK_DB_URL is not set, so data is stored in a local H2 database at " + path.trim()
                + ".mv.db. Set HOOKDOCK_DB_URL to use PostgreSQL.");
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
