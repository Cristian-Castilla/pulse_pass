package com.pulsepass.pass;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Soporte base para pruebas de integración con PostgreSQL real.
 * <p>
 * Inicia un contenedor de PostgreSQL 16 y lo registra como un ServiceConnection
 * para que Spring Boot lo descubra automáticamente durante las pruebas.
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresContainerSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    // Slow Docker Desktop hosts need more time; this does not delay a ready database.
                    .withStartupTimeout(java.time.Duration.ofMinutes(3));

    static {
        postgres.start();
    }
}
