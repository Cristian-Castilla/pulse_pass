package com.pulsepass.pass;

import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QT-001 (Flyway aplica V1, V2, V3) y QT-002 (Hibernate valida esquema).
 * Verifica que el contexto cargue con éxito y que los datos sembrados por V2 existan.
 */
@SpringBootTest
class FlywayMigrationIT extends PostgresContainerSupport {

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Test
    void contextLoadsWithValidatedSchema() {
        // Si el contexto cargó, Hibernate validó el esquema correctamente
        assertThat(venueRepository).isNotNull();
        assertThat(eventRepository).isNotNull();
        assertThat(artistRepository).isNotNull();
    }

    @Test
    void shouldApplyV2SeedData() {
        assertThat(artistRepository.findByStageName("Solar Beat")).isPresent();
        assertThat(artistRepository.findByStageName("Neon Waves")).isPresent();
        assertThat(artistRepository.findByStageName("Caribbean Sound")).isPresent();
        assertThat(artistRepository.findByStageName("Ocean Drive")).isPresent();
        assertThat(artistRepository.findByStageName("Digital Pulse")).isPresent();
    }
}
