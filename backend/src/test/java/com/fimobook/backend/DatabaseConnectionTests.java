package com.fimobook.backend;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "fimo.price-refresh.enabled=false")
@EnabledIfEnvironmentVariable(named = "FIMO_DB_TEST", matches = "true")
class DatabaseConnectionTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void connectsToMariaDbAndExecutesQuery() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.isValid(2)).isTrue();
            assertThat(connection.getMetaData().getDatabaseProductName()).containsIgnoringCase("MariaDB");
        }

        var jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }
}
