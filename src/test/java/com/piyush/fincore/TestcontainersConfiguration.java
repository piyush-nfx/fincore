package com.piyush.fincore;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Starts a throwaway MySQL in Docker for tests that need a real database.
 *
 * <p>@ServiceConnection points Spring's datasource at the container automatically (URL, user, password), so tests
 * never touch the developer's own database. Flyway then builds the schema from the migration scripts, which also
 * proves that V1 + V2 work on an empty database. The container is created once and reused by every test class that
 * imports this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.4");
    }
}
