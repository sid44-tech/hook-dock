package dev.hookdock.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.mock.env.MockEnvironment;

class LocalDatabaseFallbackTest {

    private final LocalDatabaseFallback fallback = new LocalDatabaseFallback(new DeferredLogs());

    @Test
    void noUrlUsesTheDefaultLocalFile() {
        MockEnvironment environment = new MockEnvironment();

        fallback.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:h2:file:./data/hookdock" + LocalDatabaseFallback.H2_OPTIONS);
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("sa");
        assertThat(environment.getProperty("spring.datasource.password")).isEmpty();
    }

    @Test
    void blankUrlCountsAsNotSet() {
        // HOOKDOCK_DB_URL= in .env, or the empty default in application.yml.
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "  ")
                .withProperty("spring.datasource.username", "hookdock")
                .withProperty("spring.datasource.password", "postgres-password");

        fallback.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:file:");
        // The PostgreSQL credentials are not used for the local file.
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("sa");
        assertThat(environment.getProperty("spring.datasource.password")).isEmpty();
    }

    @Test
    void customPathIsUsed() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(LocalDatabaseFallback.PATH_PROPERTY, "/var/lib/hookdock/db");

        fallback.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:h2:file:/var/lib/hookdock/db" + LocalDatabaseFallback.H2_OPTIONS);
    }

    @Test
    void configuredUrlIsLeftAlone() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/hookdock")
                .withProperty("spring.datasource.username", "hookdock");

        fallback.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/hookdock");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("hookdock");
        assertThat(environment.getPropertySources().contains("hookdockLocalDatabase")).isFalse();
    }
}
