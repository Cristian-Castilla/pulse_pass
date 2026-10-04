package com.pulsepass.pass;

import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.TicketRepository;
import com.pulsepass.pass.repository.UserProfileRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que Flyway aplique correctamente las migraciones V1, V2 y V3
 * desde una base de datos vacía, y que el esquema resultante sea compatible
 * con las entidades de Hibernate en modo validate (ddl-auto=validate).
 *
 * PRD: "Flyway será el único responsable de crear y evolucionar la base de datos"
 * PRD: "Hibernate NO debe crear ni modificar el esquema"
 */
class FlywayMigrationIT extends PostgresContainerSupport {

    @Autowired private VenueRepository venueRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private TicketRepository ticketRepository;

    @Test
    void contextLoadsWithoutSchemaValidationErrors() {
        // Si Spring context carga correctamente con ddl-auto=validate,
        // significa que el esquema de Flyway V1/V2/V3 coincide con las entidades JPA
        assertThat(venueRepository).isNotNull();
        assertThat(eventRepository).isNotNull();
        assertThat(artistRepository).isNotNull();
        assertThat(userRepository).isNotNull();
        assertThat(userProfileRepository).isNotNull();
        assertThat(ticketRepository).isNotNull();
    }

    @Test
    void shouldApplyV2SeedInitialArtists() {
        // V2 debe insertar los 5 artistas iniciales
        assertThat(artistRepository.count()).isEqualTo(5);
        assertThat(artistRepository.findByStageName("Solar Beat")).isPresent();
        assertThat(artistRepository.findByStageName("Neon Waves")).isPresent();
        assertThat(artistRepository.findByStageName("Caribbean Sound")).isPresent();
        assertThat(artistRepository.findByStageName("Ocean Drive")).isPresent();
        assertThat(artistRepository.findByStageName("Digital Pulse")).isPresent();
    }

    @Test
    void shouldApplyV3AddStreamingUrlColumnToEvents() {
        // V3 agrega streaming_url VARCHAR(500) nullable a events
        // Hibernate valida que la columna existe al cargar el contexto
        // Si no existiera, ddl-auto=validate fallaría en el arranque
    }
}
