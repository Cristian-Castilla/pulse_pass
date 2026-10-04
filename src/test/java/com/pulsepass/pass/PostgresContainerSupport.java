package com.pulsepass.pass;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Soporte base para pruebas de integración con PostgreSQL real.
 * <p>
 * Inicia un contenedor de PostgreSQL 16 y lo registra como un ServiceConnection
 * para que Spring Boot lo descubra automáticamente durante las pruebas.
 * </p>
 */
@SpringBootTest
public abstract class PostgresContainerSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }
}
