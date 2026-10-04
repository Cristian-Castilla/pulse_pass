package com.pulsepass.pass;

import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Ticket;
import com.pulsepass.pass.domain.TicketStatus;
import com.pulsepass.pass.domain.TicketType;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.TicketRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica relaciones (Ticket -> User, Ticket -> Event),
 * query methods, JPQL y restricciones de Ticket.
 *
 * PRD relaciones:
 * - User 1:N Ticket → Ticket.N:1 User
 * - Event 1:N Ticket → Ticket.N:1 Event
 *
 * PRD restricciones:
 * - ticketCode UNIQUE, NOT NULL
 * - price >= 0 (BigDecimal)
 * - user_id NOT NULL
 * - event_id NOT NULL
 *
 * Query Methods:
 * - findByTicketCode
 * - existsByTicketCode
 *
 * JPQL:
 * - findByUserEmail (JOIN FETCH)
 * - findByUserEmailAndStatus
 * - findTicketsForFutureEvents
 * - findPaidTicketsByEventCode
 * - countPaidTicketsByEventCode
 */
@Transactional
class TicketRepositoryIT extends PostgresContainerSupport {

    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private ArtistRepository artistRepository;

    private Venue venue;
    private Event event;
    private User andrea;
    private User carlos;

    @BeforeEach
    void setUpScenario() {
        venue = venueRepository.save(new Venue("VEN-TCK-01", "Test Venue",
                "Bogotá", "Dirección test", 1000L, true));

        Artist solarBeat = artistRepository.findByStageName("Solar Beat").orElseThrow();

        event = new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival de música caribeña", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        event.addArtist(solarBeat);
        event = eventRepository.saveAndFlush(event);

        andrea = userRepository.saveAndFlush(new User("andrea", "andrea@example.com", true));
        carlos = userRepository.saveAndFlush(new User("carlos", "carlos@example.com", true));

        ticketRepository.saveAndFlush(new Ticket("TCK-ANDREA-01", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.PAID, LocalDateTime.now(), andrea, event));
        ticketRepository.saveAndFlush(new Ticket("TCK-CARLOS-01", TicketType.GENERAL,
                new BigDecimal("120000"), TicketStatus.PAID, LocalDateTime.now(), carlos, event));
        ticketRepository.saveAndFlush(new Ticket("TCK-LAURA-01", TicketType.GENERAL,
                new BigDecimal("120000"), TicketStatus.RESERVED, LocalDateTime.now(),
                userRepository.saveAndFlush(new User("laura", "laura@example.com", true)), event));
    }

    @Test
    void shouldLinkTicketToUserAndEvent() {
        // Ticket N:1 User, Ticket N:1 Event
        // Verificación mediante equals (ticketCode es el identificador de negocio)
        Ticket ticket = ticketRepository.findByTicketCode("TCK-ANDREA-01").orElseThrow();
        Ticket expectedTicket = new Ticket("TCK-ANDREA-01", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.PAID,
                LocalDateTime.now(), andrea, event);
        assertThat(ticket.equals(expectedTicket)).isTrue();
    }

    @Test
    void shouldFindTicketsByUserEmailAndStatusUsingJPQL() {
        // JPQL: JOIN FETCH + filter by user.email and status
        List<Ticket> paidByAndrea = ticketRepository.findByUserEmailAndStatus(
                "andrea@example.com", TicketStatus.PAID);
        assertThat(paidByAndrea).hasSize(1);
        Ticket expected = new Ticket("TCK-ANDREA-01", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.PAID,
                LocalDateTime.now(), andrea, event);
        assertThat(paidByAndrea.get(0).equals(expected)).isTrue();
    }

    @Test
    void shouldFindTicketsByUserEmailUsingJPQL() {
        // JPQL: JOIN FETCH by user.email
        List<Ticket> tickets = ticketRepository.findByUserEmail("andrea@example.com");
        assertThat(tickets).hasSize(1);
    }

    @Test
    void shouldCountPaidTicketsByEventCodeUsingJPQL() {
        // JPQL: COUNT where event.eventCode and status = PAID
        Long paidCount = ticketRepository.countPaidTicketsByEventCode("CMF-2026");
        assertThat(paidCount).isEqualTo(2L); // andrea (PAID) + carlos (PAID)
    }

    @Test
    void shouldFindPaidTicketsByEventCodeUsingJPQLJoinFetch() {
        // JPQL: JOIN FETCH + filter event.eventCode and status = PAID
        List<Ticket> paidTickets = ticketRepository.findPaidTicketsByEventCode("CMF-2026");
        assertThat(paidTickets).hasSize(2);
    }

    @Test
    void shouldFindTicketsForFutureEventsUsingJPQL() {
        // JPQL: JOIN FETCH t.event WHERE event.eventDate > :date ORDER BY eventDate
        LocalDate today = LocalDate.now();
        Venue futureVenue = venueRepository.saveAndFlush(new Venue("VEN-FUT", "Future Venue",
                "Medellín", "Dir", 500L, true));
        Event pastEvent = eventRepository.saveAndFlush(new Event("EVT-PAST", "Evento pasado",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                today.minusDays(1), null, null, futureVenue));
        Event futureEvent = eventRepository.saveAndFlush(new Event("EVT-FUT", "Evento futuro",
                "Desc", EventCategory.MUSIC, EventStatus.PUBLISHED,
                today.plusDays(1), null, null, futureVenue));

        User testUser = userRepository.saveAndFlush(new User("future-user", "future@example.com", true));
        ticketRepository.saveAndFlush(new Ticket("TCK-PAST", TicketType.GENERAL,
                new BigDecimal("50000"), TicketStatus.PAID, LocalDateTime.now(), testUser, pastEvent));
        ticketRepository.saveAndFlush(new Ticket("TCK-FUT", TicketType.GENERAL,
                new BigDecimal("50000"), TicketStatus.PAID, LocalDateTime.now(), testUser, futureEvent));

        List<Ticket> futureTickets = ticketRepository.findTicketsForFutureEvents(today);
        assertThat(futureTickets).hasSize(1);
    }

    @Test
    void shouldEnforceUniqueTicketCodeConstraintOnSaveAndFlush() {
        // PRD: ticketCode UNIQUE, NOT NULL
        Ticket duplicate = new Ticket("TCK-ANDREA-01", TicketType.GENERAL,
                new BigDecimal("100000"), TicketStatus.RESERVED,
                LocalDateTime.now(), carlos, event);

        assertThrows(DataIntegrityViolationException.class,
                () -> ticketRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldEnforceNotNullPriceConstraintOnSaveAndFlush() {
        // PRD: price >= 0 (NOT NULL)
        Ticket ticket = new Ticket("TCK-NULL-PRICE", TicketType.GENERAL,
                null, TicketStatus.PAID, LocalDateTime.now(), andrea, event);

        assertThrows(DataIntegrityViolationException.class,
                () -> ticketRepository.saveAndFlush(ticket));
    }

    @Test
    void shouldReturnFalseWhenTicketCodeDoesNotExist() {
        // Query Method: existsByTicketCode
        assertThat(ticketRepository.existsByTicketCode("NO-EXISTE")).isFalse();
    }

    @Test
    void shouldReturnTrueWhenTicketCodeExists() {
        assertThat(ticketRepository.existsByTicketCode("TCK-ANDREA-01")).isTrue();
    }
}
