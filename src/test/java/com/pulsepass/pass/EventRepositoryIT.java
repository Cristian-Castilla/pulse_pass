package com.pulsepass.pass;

import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica relaciones (Venue 1:N Event, Event N:M Artist),
 * query methods, JPQL y restricciones de Event.
 *
 * PRD relaciones:
 * - Venue 1:N Event → Event.N:1 Venue
 * - Event N:M Artist → tabla event_artists
 *
 * Query Methods:
 * - findByEventCode
 * - findByStatusOrderByEventDateAsc
 * - findByVenue_Code
 *
 * JPQL:
 * - findByArtistStageName
 * - findByCityAndArtist
 * - findRecommendedEvents
 */
@Transactional
class EventRepositoryIT extends PostgresContainerSupport {

    @Autowired private EventRepository eventRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private ArtistRepository artistRepository;

    private Venue venue;

    @BeforeEach
    void setUpVenue() {
        venue = venueRepository.save(new Venue("VEN-EVT-01", "Test Venue",
                "Bogotá", "Dirección test", 1000L, true));
    }

    @Test
    void shouldSaveEventWithVenueAndRetrieveByVenueCode() {
        // Venue 1:N Event — Event debe estar asociado a un venue
        Event event = new Event("EVT-01", "Evento de prueba",
                "Descripción", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(1), null, null, venue);
        eventRepository.saveAndFlush(event);

        // Verificar vía query method que busca por venue.code
        List<Event> events = eventRepository.findByVenueCode("VEN-EVT-01");
        assertThat(events).hasSize(1);
        // equals() de Event usa eventCode, que es el identificador de negocio
        assertThat(events.get(0).equals(event)).isTrue();
    }

    @Test
    void shouldFindPublishedEventsOrderedByDateAscending() {
        // Query Method: findByStatusOrderByEventDateAsc
        Event evt1 = new Event("EVT-002", "Evento posterior",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(5), null, null, venue);
        Event evt2 = new Event("EVT-001", "Evento anterior",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(1), null, null, venue);
        Event evt3 = new Event("EVT-003", "Evento draft",
                "Desc", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusMonths(3), null, null, venue);
        eventRepository.saveAllAndFlush(List.of(evt1, evt2, evt3));

        List<Event> published = eventRepository.findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED);
        assertThat(published).hasSize(2);
        // Verificación por equals() usando eventCode (identificador de negocio)
        assertThat(published.get(0).equals(evt2)).isTrue();
        assertThat(published.get(1).equals(evt1)).isTrue();
    }

    @Test
    void shouldFindEventByEventCode() {
        // Query Method: findByEventCode
        eventRepository.saveAndFlush(new Event("CMF-2026", "Caribbean Music Fest",
                "Festival", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue));

        assertThat(eventRepository.findByEventCode("CMF-2026")).isPresent();
    }

    @Test
    void shouldEnforceUniqueEventCodeConstraintOnSaveAndFlush() {
        // PRD: eventCode UNIQUE, eventCode NOT NULL
        eventRepository.saveAndFlush(new Event("EVT-DUP", "Evento original",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(1), null, null, venue));

        Event duplicate = new Event("EVT-DUP", "Evento duplicado",
                "Desc", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusMonths(2), null, null, venue);

        assertThrows(DataIntegrityViolationException.class,
                () -> eventRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldAssociateEventWithMultipleArtistsViaEventArtistsTable() {
        // Event N:M Artist — tabla event_artists (composición PK)
        Artist solarBeat = artistRepository.findByStageName("Solar Beat").orElseThrow();
        Artist neonWaves = artistRepository.findByStageName("Neon Waves").orElseThrow();

        Event event = new Event("EVT-ART", "Evento con artistas",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        event.addArtist(solarBeat);
        event.addArtist(neonWaves);
        eventRepository.saveAndFlush(event);

        // Verificar mediante JPQL que ambos artistas pueden encontrar el evento
        assertThat(eventRepository.findByArtistStageName("Solar Beat"))
                .anyMatch(e -> e.equals(event));
        assertThat(eventRepository.findByArtistStageName("Neon Waves"))
                .anyMatch(e -> e.equals(event));
    }

    @Test
    void shouldFindEventsByArtistStageNameUsingJPQLJoin() {
        // JPQL: SELECT DISTINCT e FROM Event e JOIN e.artists a WHERE a.stageName = :stageName
        Artist caribbeanSound = artistRepository.findByStageName("Caribbean Sound").orElseThrow();

        Event event = new Event("EVT-ART-JPQL", "Evento JPQL",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        event.addArtist(caribbeanSound);
        eventRepository.saveAndFlush(event);

        List<Event> events = eventRepository.findByArtistStageName("Caribbean Sound");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).equals(event)).isTrue();
    }

    @Test
    void shouldFindEventsByCityAndArtistUsingJPQLJoin() {
        // JPQL: venue.city + artist.stageName
        Artist solarBeat = artistRepository.findByStageName("Solar Beat").orElseThrow();

        Event event = new Event("EVT-CITY-ART", "Evento ciudad+artista",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        event.addArtist(solarBeat);
        eventRepository.saveAndFlush(event);

        List<Event> events = eventRepository.findByCityAndArtist("Bogotá", "Solar Beat");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).equals(event)).isTrue();
    }

    @Test
    void shouldFindRecommendedEventsUsingJPQLWithCaseInsensitiveLike() {
        // JPQL: DISTINCT, PUBLISHED, future date, city, case-insensitive artist LIKE, ORDER BY eventDate
        Artist neonWaves = artistRepository.findByStageName("Neon Waves").orElseThrow();

        Event event = new Event("EVT-REC", "Evento recomendado",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        event.addArtist(neonWaves);
        eventRepository.saveAndFlush(event);

        List<Event> recommended = eventRepository.findRecommendedEvents(
                LocalDate.now(), "Bogotá", "neon");
        assertThat(recommended).hasSize(1);
        assertThat(recommended.get(0).equals(event)).isTrue();
    }
}
