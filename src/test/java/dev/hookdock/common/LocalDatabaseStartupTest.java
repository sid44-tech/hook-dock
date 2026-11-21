package dev.hookdock.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the whole app with no database URL, as a first run without
 * PostgreSQL would, and checks that it creates a local H2 file and applies
 * every migration to it.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=",
        "hookdock.local-db.path=./target/local-db-test/hookdock-${random.uuid}" })
class LocalDatabaseStartupTest {

    @Autowired
    DataSource dataSource;

    @Value("${spring.datasource.url}")
    String url;

    @Test
    void startsOnALocalFileDatabaseWithAllMigrations() throws Exception {
        assertThat(url).startsWith("jdbc:h2:file:./target/local-db-test/hookdock-");

        String path = url.substring("jdbc:h2:file:".length(), url.indexOf(';'));
        assertThat(Files.exists(Path.of(path + ".mv.db"))).isTrue();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer failed = jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"success\" = false", Integer.class);
        String latest = jdbc.queryForObject(
                "select max(cast(\"version\" as int)) from \"flyway_schema_history\"", String.class);
        assertThat(failed).isZero();
        assertThat(latest).isEqualTo("7");
        // A table from the latest migration is usable.
        assertThat(jdbc.queryForObject("select count(*) from webhook_endpoint where rate_limit_requests is null",
                Integer.class)).isZero();
    }
}
