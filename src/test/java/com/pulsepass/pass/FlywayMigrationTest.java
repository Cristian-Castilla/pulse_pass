package com.pulsepass.pass;

import com.pulsepass.pass.repository.ArtistRepository;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends PostgresContainerSupport {
    @Autowired private ArtistRepository artistRepository;
    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment environment;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void contextLoadsWithoutSchemaValidationErrors() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
        assertThat(flyway.info().applied()).extracting(i -> i.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void shouldApplyV2SeedInitialArtists() {
        assertThat(artistRepository.findAll()).hasSize(5).extracting(a -> a.getStageName())
                .contains("Solar Beat", "Neon Waves", "Caribbean Sound", "Ocean Drive", "Digital Pulse");
    }

    @Test
    void shouldApplyV3AddStreamingUrlColumnToEvents() {
        var column = jdbc.queryForMap("SELECT data_type,character_maximum_length,is_nullable FROM information_schema.columns "
                + "WHERE table_schema=current_schema() AND table_name='events' AND column_name='streaming_url'");
        assertThat(column).containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 500).containsEntry("is_nullable", "YES");
        assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema=current_schema() AND table_name='events' AND column_name='event_date'", String.class))
                .isEqualTo("timestamp without time zone");
    }
}