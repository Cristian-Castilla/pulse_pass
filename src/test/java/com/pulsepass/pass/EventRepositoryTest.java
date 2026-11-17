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

import java.time.LocalDateTime;
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
class EventRepositoryTest extends PostgresContainerSupport {

    @Autowired private EventRepository eventRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

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
                LocalDateTime.of(2030, 6, 15, 19, 30, 45), 18, "https://events.example.test/live", venue);
        eventRepository.saveAndFlush(event);

        entityManager.clear();
        List<Event> events = eventRepository.findByVenueCode("VEN-EVT-01");
        assertThat(events).hasSize(1);
        Event reloaded = events.get(0);
        assertThat(reloaded.getId()).isEqualTo(event.getId());
        assertThat(reloaded.getName()).isEqualTo("Evento de prueba");
        assertThat(reloaded.getDescription()).isEqualTo("Descripción");
        assertThat(reloaded.getCategory()).isEqualTo(EventCategory.MUSIC);
        assertThat(reloaded.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(reloaded.getEventDate()).isEqualTo(event.getEventDate());
        assertThat(reloaded.getMinimumAge()).isEqualTo(18);
        assertThat(reloaded.getStreamingUrl()).isEqualTo("https://events.example.test/live");
        assertThat(reloaded.getVenue().getId()).isEqualTo(venue.getId());
        assertThat(reloaded.getVenue().getCity()).isEqualTo("Bogotá");
    }

    @Test
    void shouldFindPublishedEventsOrderedByDateAscending() {
        // Query Method: findByStatusOrderByEventDateAsc
        Event evt1 = new Event("EVT-002", "Evento posterior",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDateTime.now().plusMonths(5), null, null, venue);
        Event evt2 = new Event("EVT-001", "Evento anterior",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDateTime.now().plusMonths(1), null, null, venue);
        Event evt3 = new Event("EVT-003", "Evento draft",
                "Desc", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDateTime.now().plusMonths(3), null, null, venue);
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
                LocalDateTime.now().plusMonths(3), null, null, venue));

        assertThat(eventRepository.findByEventCode("CMF-2026")).isPresent();
    }

    @Test
    void shouldEnforceUniqueEventCodeConstraintOnSaveAndFlush() {
        // PRD: eventCode UNIQUE, eventCode NOT NULL
        eventRepository.saveAndFlush(new Event("EVT-DUP", "Evento original",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDateTime.now().plusMonths(1), null, null, venue));

        Event duplicate = new Event("EVT-DUP", "Evento duplicado",
                "Desc", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDateTime.now().plusMonths(2), null, null, venue);

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
                LocalDateTime.now().plusMonths(3), null, null, venue);
        event.addArtist(solarBeat);
        event.addArtist(neonWaves);
        event.addArtist(artistRepository.findByStageName("Ocean Drive").orElseThrow());
        event.addArtist(solarBeat);
        eventRepository.saveAndFlush(event);
        Event second = new Event("EVT-ART-SECOND", "Second event", "Desc", EventCategory.MUSIC,
                EventStatus.PUBLISHED, LocalDateTime.of(2031, 1, 2, 19, 0), null, null, venue);
        second.addArtist(solarBeat);
        eventRepository.saveAndFlush(second);
        entityManager.clear();
        Event reloaded = eventRepository.findByEventCode("EVT-ART").orElseThrow();
        assertThat(reloaded.getArtists()).extracting(Artist::getStageName)
                .containsExactlyInAnyOrder("Solar Beat", "Neon Waves", "Ocean Drive");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_artists WHERE event_id=?", Long.class, event.getId())).isEqualTo(3L);
        assertThat(eventRepository.findByArtistStageName("Solar Beat")).extracting(Event::getEventCode)
                .containsExactlyInAnyOrder("EVT-ART", "EVT-ART-SECOND");
        assertThat(artistRepository.findByStageName("Solar Beat").orElseThrow().getEvents())
                .extracting(Event::getEventCode).containsExactlyInAnyOrder("EVT-ART", "EVT-ART-SECOND");

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
                LocalDateTime.now().plusMonths(3), null, null, venue);
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
                LocalDateTime.now().plusMonths(3), null, null, venue);
        event.addArtist(solarBeat);
        eventRepository.saveAndFlush(event);

        Venue elsewhere = venueRepository.saveAndFlush(new Venue("CITY-OTHER", "Other", "Medellín", "Dir", 200L, true));
        recommendationFixture("CITY-WRONG", EventStatus.PUBLISHED, LocalDateTime.of(2030, 1, 1, 19, 0), elsewhere, solarBeat);
        recommendationFixture("ART-WRONG", EventStatus.PUBLISHED, LocalDateTime.of(2030, 1, 1, 19, 0), venue,
                artistRepository.findByStageName("Neon Waves").orElseThrow());
        eventRepository.flush();
        entityManager.clear();
        List<Event> events = eventRepository.findByCityAndArtist("Bogotá", "Solar Beat");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).equals(event)).isTrue();
    }

    @Test
    void shouldFindRecommendedEventsUsingJPQLWithCaseInsensitiveLike() {
        LocalDateTime cutoff = LocalDateTime.of(2030, 1, 1, 12, 0);
        Artist neon = artistRepository.findByStageName("Neon Waves").orElseThrow();
        Artist neonTwo = artistRepository.saveAndFlush(new Artist("NEON Echo", "Colombia", "Pop", true));
        Artist other = artistRepository.findByStageName("Solar Beat").orElseThrow();
        Venue elsewhere = venueRepository.saveAndFlush(new Venue("REC-OTHER", "Other", "Medellín", "Dir", 200L, true));
        recommendationFixture("REC-LATE", EventStatus.PUBLISHED, cutoff.plusDays(3), venue, neon, neonTwo);
        recommendationFixture("REC-EARLY", EventStatus.PUBLISHED, cutoff.plusDays(1), venue, neon);
        recommendationFixture("REC-PAST", EventStatus.PUBLISHED, cutoff.minusSeconds(1), venue, neon);
        recommendationFixture("REC-BOUNDARY", EventStatus.PUBLISHED, cutoff, venue, neon);
        recommendationFixture("REC-CITY", EventStatus.PUBLISHED, cutoff.plusDays(1), elsewhere, neon);
        recommendationFixture("REC-ARTIST", EventStatus.PUBLISHED, cutoff.plusDays(1), venue, other);
        for (EventStatus status : EventStatus.values()) {
            if (status != EventStatus.PUBLISHED) recommendationFixture("REC-" + status, status, cutoff.plusDays(1), venue, neon);
        }
        eventRepository.flush();
        entityManager.clear();
        assertThat(eventRepository.findRecommendedEvents(cutoff, "Bogotá", "nEoN"))
                .extracting(Event::getEventCode).containsExactly("REC-EARLY", "REC-LATE");
        assertThat(eventRepository.findByArtistStageNameAndStatus("Neon Waves", EventStatus.DRAFT))
                .extracting(Event::getEventCode).containsExactly("REC-DRAFT");
        assertThat(eventRepository.findByEventCodeAndStatus("REC-DRAFT", EventStatus.PUBLISHED)).isEmpty();
        assertThat(eventRepository.findByEventCodeAndStatus("REC-EARLY", EventStatus.PUBLISHED)).isPresent();
    }

    private void recommendationFixture(String code, EventStatus status, LocalDateTime date, Venue location, Artist... artists) {
        Event event = new Event(code, code, "Desc", EventCategory.MUSIC, status, date, null, null, location);
        for (Artist artist : artists) event.addArtist(artist);
        eventRepository.save(event);
    }
}
