package com.pulsepass.pass;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationRuntimeConfigurationTest extends PostgresContainerSupport {
    @Autowired private EntityManagerFactory emf;
    @Autowired private Environment environment;
    @Autowired private DataSource dataSource;

    @Test void integrationUsesProductionJpaConfigurationAndOnlyTheEphemeralDatabase() throws Exception {
        assertThat(environment.getActiveProfiles()).contains("test");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(emf.getProperties()).containsEntry("hibernate.hbm2ddl.auto", "validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
        }
    }
}
