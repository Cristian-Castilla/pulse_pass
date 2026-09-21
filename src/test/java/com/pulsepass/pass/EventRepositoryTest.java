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
 * QT-003 (Venue 1:N Event), QT-005 (Event N:M Artist), QT-007 (query methods
 * con navegación de relaciones), QT-008 (JPQL con JOIN) y QT-009 (UNIQUE en
 * event_code).
 */

@Transactional
class EventRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private ArtistRepository artistRepository;

    private Venue venue;

    @BeforeEach
    void setUpVenue() {
        venue = venueRepository.save(new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1 # 1-01", 5000L, true));
    }

    @Test
    void shouldFindEventsByVenueCode() {
        eventRepository.save(new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival de música caribeña", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue));

        List<Event> events = eventRepository.findByVenue_Code("VEN-SMR-01");

        assertThat(events).extracting(Event::getEventCode).containsExactly("CMF-2026");
    }

    @Test
    void shouldFindPublishedEventsOrderedByDate() {
        eventRepository.save(new Event("EVT-002", "Evento posterior",
                "Descripción", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(5), null, null, venue));
        eventRepository.save(new Event("EVT-001", "Evento anterior",
                "Descripción", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(1), null, null, venue));
        eventRepository.save(new Event("EVT-003", "Evento draft",
                "Descripción", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusMonths(3), null, null, venue));

        List<Event> published = eventRepository.findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED);

        assertThat(published).hasSize(2);
        assertThat(published.get(0).getEventCode()).isEqualTo("EVT-001");
        assertThat(published.get(1).getEventCode()).isEqualTo("EVT-002");
    }

    @Test
    void shouldFindEventsByCityAndArtist() {
        Artist solarBeat = artistRepository.findByStageName("Solar Beat").orElseThrow();
        eventRepository.save(new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue));
        eventRepository.findByEventCode("CMF-2026").ifPresent(e -> {
            e.addArtist(solarBeat);
            eventRepository.save(e);
        });

        List<Event> found = eventRepository.findByCityAndArtist("Santa Marta", "Solar Beat");

        assertThat(found).extracting(Event::getEventCode).containsExactly("CMF-2026");
    }

    @Test
    void shouldAssociateEventWithMultipleArtists() {
        Artist solarBeat = artistRepository.findByStageName("Solar Beat").orElseThrow();
        Artist neonWaves = artistRepository.findByStageName("Neon Waves").orElseThrow();
        Artist caribbeanSound = artistRepository.findByStageName("Caribbean Sound").orElseThrow();

        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival de música caribeña", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        event.addArtist(solarBeat);
        event.addArtist(neonWaves);
        event.addArtist(caribbeanSound);
        eventRepository.save(event);

        Event reloaded = eventRepository.findByEventCode("CMF-2026").orElseThrow();

        assertThat(reloaded.getArtists())
                .extracting(Artist::getStageName)
                .containsExactlyInAnyOrder("Solar Beat", "Neon Waves", "Caribbean Sound");
    }

    @Test
    void shouldFindEventsByArtistStageName() {
        Artist caribbeanSound = artistRepository.findByStageName("Caribbean Sound").orElseThrow();
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival de música caribeña", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        event.addArtist(caribbeanSound);
        eventRepository.save(event);

        List<Event> events = eventRepository.findByArtistStageName("Caribbean Sound");

        assertThat(events).extracting(Event::getEventCode).contains("CMF-2026");
    }

    @Test
    void shouldFindRecommendedEventsWithJpqlJoinAndLike() {
        Artist neonWaves = artistRepository.findByStageName("Neon Waves").orElseThrow();
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival de música caribeña", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        event.addArtist(neonWaves);
        eventRepository.save(event);

        List<Event> recommended = eventRepository.findRecommendedEvents(
                LocalDate.now(), "Santa Marta", "neon");

        assertThat(recommended).extracting(Event::getEventCode).containsExactly("CMF-2026");
    }

    @Test
    void shouldEnforceUniqueEventCodeConstraint() {
        eventRepository.save(new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival de música caribeña", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue));
        eventRepository.flush();

        Event duplicate = new Event("CMF-2026", "Otro festival",
                "Descripción distinta", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusMonths(4), null, null, venue);

        assertThrows(DataIntegrityViolationException.class,
                () -> eventRepository.saveAndFlush(duplicate));
    }
}