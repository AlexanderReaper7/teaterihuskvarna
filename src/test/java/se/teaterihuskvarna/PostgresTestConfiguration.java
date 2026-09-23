package se.teaterihuskvarna;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/// The PostgreSQL every integration test runs against: the image production runs,
/// pinned in `docs/decisions/0007-postgres-in-a-container.md`.
///
/// A bean rather than a `static @Container` field per class. Spring caches an
/// application context by its configuration and closes the container with the
/// context, so every test class that ends up with the same context also shares
/// one container. A `@Container` field belongs to one class, so each class
/// started its own container and, with it, its own context.
///
/// `PostgreSQLContainer` is the Testcontainers 1.21.4 class, the version the pom
/// pins. Testcontainers 2 moves it to `org.testcontainers.postgresql`.
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    /// @return the container, which Spring Boot starts and reads the connection details from
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:18.2-alpine");
    }
}
