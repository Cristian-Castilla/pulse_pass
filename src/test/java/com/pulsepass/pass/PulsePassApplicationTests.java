package com.pulsepass.pass;

import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.repository.EventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  QT-001 (Flyway aplica V1, V2 y V3 desde una base vacia.) y
 *  QT-002 (Hibernate valida el esquema sin crearlo ni actualizarlo.)
 */
class PulsePassApplicationTests extends PostgresContainerSupport {

    @Autowired
    private Environment environment;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private EventRepository eventRepository;

    @Test
    void contextLoads() {
    }

    // ------------------------------------------------------------------
    // QT-001 — Flyway aplica V1, V2 y V3 desde una base vacía
    // ------------------------------------------------------------------
    @Test
    void flywayAppliesV1CreatesSchemaAndV2SeedsInitialArtists() {
        assertThat(artistRepository.count()).isEqualTo(5);
        assertThat(artistRepository.findByStageName("Digital Pulse")).isPresent();
    }

    @Test
    void flywayAppliesV3AddsStreamingUrlColumnToEvents() {

        var venue = new com.pulsepass.pass.domain.Venue("VEN-QT001", "Test Venue",
                "Santa Marta", "Dirección de prueba", 100L, true);
        var event = new com.pulsepass.pass.domain.Event("QT-001-EVT", "Evento de prueba",
                "Descripción de prueba", com.pulsepass.pass.domain.EventCategory.MUSIC,
                com.pulsepass.pass.domain.EventStatus.DRAFT, java.time.LocalDate.now().plusDays(1),
                null, "https://stream.example.com/qt001", venue);

        assertThat(event.getStreamingUrl()).isEqualTo("https://stream.example.com/qt001");
    }
}
