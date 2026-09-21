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
 * QT-006 (Ticket -> User, Ticket -> Event), QT-008 (JPQL con JOIN FETCH y COUNT)
 * y QT-009 (UNIQUE en ticket_code).
 */

@Transactional
class TicketRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private ArtistRepository artistRepository;

    private Venue venue;
    private Event event;
    private User andrea;
    private User carlos;
    private User laura;
    private User miguel;

    @BeforeEach
    void setUpScenario() {
        venue = venueRepository.save(new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1 # 1-01", 5000L, true));

        Artist solarBeat = artistRepository.findByStageName("Solar Beat").orElseThrow();

        event = new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival de música caribeña", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        event.addArtist(solarBeat);
        event = eventRepository.save(event);

        andrea = userRepository.save(new User("andrea", "andrea@example.com", true));
        carlos = userRepository.save(new User("carlos", "carlos@example.com", true));
        laura = userRepository.save(new User("laura", "laura@example.com", true));
        miguel = userRepository.save(new User("miguel", "miguel@example.com", true));

        ticketRepository.save(new Ticket("TCK-ANDREA-01", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.PAID, LocalDateTime.now(), andrea, event));
        ticketRepository.save(new Ticket("TCK-CARLOS-01", TicketType.GENERAL,
                new BigDecimal("120000"), TicketStatus.PAID, LocalDateTime.now(), carlos, event));
        ticketRepository.save(new Ticket("TCK-LAURA-01", TicketType.GENERAL,
                new BigDecimal("120000"), TicketStatus.RESERVED, LocalDateTime.now(), laura, event));
        ticketRepository.save(new Ticket("TCK-MIGUEL-01", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.CANCELLED, LocalDateTime.now(), miguel, event));

        ticketRepository.flush();
    }


    @Test
    void shouldLinkTicketToUserAndEvent() {
        Ticket ticket = ticketRepository.findByTicketCode("TCK-ANDREA-01").orElseThrow();

        assertThat(ticket.getUser().getUsername()).isEqualTo("andrea");
        assertThat(ticket.getEvent().getEventCode()).isEqualTo("CMF-2026");
        assertThat(ticket.getType()).isEqualTo(TicketType.VIP);
        assertThat(ticket.getPrice()).isEqualByComparingTo("250000");
    }

    @Test
    void shouldFindTicketsByUserEmailAndStatus() {
        List<Ticket> paidByCarlos = ticketRepository.findByUserEmailAndStatus(
                "carlos@example.com", TicketStatus.PAID);

        assertThat(paidByCarlos).extracting(Ticket::getTicketCode).containsExactly("TCK-CARLOS-01");
    }

    @Test
    void shouldCountPaidTicketsByEventCodeUsingJpql() {
        Long paidCount = ticketRepository.countPaidTicketsByEventCode("CMF-2026");

        assertThat(paidCount).isEqualTo(2L); // Andrea + Carlos
    }

    @Test
    void shouldFindPaidTicketsWithJoinFetch() {
        List<Ticket> paidTickets = ticketRepository.findPaidTicketsByEventCode("CMF-2026");

        assertThat(paidTickets).hasSize(2);
        assertThat(paidTickets)
                .extracting(t -> t.getUser().getUsername())
                .containsExactlyInAnyOrder("andrea", "carlos");
    }

    @Test
    void shouldEnforceUniqueTicketCodeConstraint() {
        Ticket duplicate = new Ticket("TCK-ANDREA-01", TicketType.GENERAL,
                new BigDecimal("120000"), TicketStatus.RESERVED, LocalDateTime.now(), laura, event);

        assertThrows(DataIntegrityViolationException.class,
                () -> ticketRepository.saveAndFlush(duplicate));
    }
}