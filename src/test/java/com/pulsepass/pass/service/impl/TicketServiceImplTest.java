package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Ticket;
import com.pulsepass.pass.domain.TicketStatus;
import com.pulsepass.pass.domain.TicketType;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.dto.request.PurchaseTicketRequest;
import com.pulsepass.pass.dto.response.TicketResponse;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.TicketMapper;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.TicketRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.service.PriceCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketMapper ticketMapper;

    @Mock
    private PriceCalculator priceCalculator;

    @InjectMocks
    private TicketServiceImpl ticketService;

    @Test
    void purchase_compraValida() {
        // ARRANGE
        PurchaseTicketRequest request = new PurchaseTicketRequest(
                "andrea@example.com", "CMF-2026", TicketType.VIP);

        User user = new User("andrea", "andrea@example.com", true);
        UserProfile profile = new UserProfile(
                "Andrea", "García", "3001234567", "Bogotá",
                LocalDate.of(1995, 5, 15), user);
        user.assignProfile(profile);

        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);

        Ticket ticket = new Ticket("TCK-001", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.PAID,
                LocalDateTime.now(), user, event);
        TicketResponse expectedResponse = new TicketResponse(
                1L, "TCK-001", TicketType.VIP, new BigDecimal("250000"),
                TicketStatus.PAID, LocalDateTime.now(),
                "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026");

        when(userRepository.findByEmailIgnoreCase("andrea@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.of(event));
        when(ticketRepository.countByEventEventCodeAndStatus("CMF-2026", TicketStatus.PAID)).thenReturn(0L);
        when(priceCalculator.calculatePrice(TicketType.VIP)).thenReturn(new BigDecimal("250000"));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
        when(ticketMapper.toResponse(ticket)).thenReturn(expectedResponse);

        // ACT
        TicketResponse result = ticketService.purchase(request);

        // ASSERT
        assertThat(result).isEqualTo(expectedResponse);
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void purchase_usuarioInexistente() {
        // ARRANGE
        PurchaseTicketRequest request = new PurchaseTicketRequest(
                "ghost@example.com", "CMF-2026", TicketType.GENERAL);

        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(ResourceNotFoundException.class,
                () -> ticketService.purchase(request));

        verify(userRepository).findByEmailIgnoreCase("ghost@example.com");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void purchase_usuarioInactivo() {
        // ARRANGE
        PurchaseTicketRequest request = new PurchaseTicketRequest(
                "miguel@example.com", "CMF-2026", TicketType.GENERAL);

        User user = new User("miguel", "miguel@example.com", false);

        when(userRepository.findByEmailIgnoreCase("miguel@example.com")).thenReturn(Optional.of(user));

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> ticketService.purchase(request));

        verify(userRepository).findByEmailIgnoreCase("miguel@example.com");
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void purchase_eventoDraft() {
        // ARRANGE
        PurchaseTicketRequest request = new PurchaseTicketRequest(
                "andrea@example.com", "CMF-2026", TicketType.GENERAL);

        User user = new User("andrea", "andrea@example.com", true);
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusMonths(3), null, null, venue);

        when(userRepository.findByEmailIgnoreCase("andrea@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.of(event));

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> ticketService.purchase(request));

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void purchase_eventoCancelled() {
        // ARRANGE
        PurchaseTicketRequest request = new PurchaseTicketRequest(
                "andrea@example.com", "CMF-2026", TicketType.GENERAL);

        User user = new User("andrea", "andrea@example.com", true);
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.CANCELLED,
                LocalDate.now().plusMonths(3), null, null, venue);

        when(userRepository.findByEmailIgnoreCase("andrea@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.of(event));

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> ticketService.purchase(request));

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void purchase_menorDeEdad() {
        // ARRANGE
        PurchaseTicketRequest request = new PurchaseTicketRequest(
                "laura@example.com", "CMF-2026", TicketType.GENERAL);

        User user = new User("laura", "laura@example.com", true);
        UserProfile profile = new UserProfile(
                "Laura", "Pérez", "3001234567", "Bogotá",
                LocalDate.of(2010, 1, 1), user);
        user.assignProfile(profile);

        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), 18, null, venue);

        when(userRepository.findByEmailIgnoreCase("laura@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.of(event));

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> ticketService.purchase(request));

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void purchase_sinCapacidad() {
        // ARRANGE
        PurchaseTicketRequest request = new PurchaseTicketRequest(
                "andrea@example.com", "CMF-2026", TicketType.GENERAL);

        User user = new User("andrea", "andrea@example.com", true);
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 2L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);

        when(userRepository.findByEmailIgnoreCase("andrea@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.of(event));
        when(ticketRepository.countByEventEventCodeAndStatus("CMF-2026", TicketStatus.PAID)).thenReturn(2L);

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> ticketService.purchase(request));

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void purchase_ultimoTicket() {
        // ARRANGE
        PurchaseTicketRequest request = new PurchaseTicketRequest(
                "carlos@example.com", "CMF-2026", TicketType.GENERAL);

        User user = new User("carlos", "carlos@example.com", true);
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 2L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);

        Ticket ticket = new Ticket("TCK-002", TicketType.GENERAL,
                new BigDecimal("100000"), TicketStatus.PAID,
                LocalDateTime.now(), user, event);
        TicketResponse expectedResponse = new TicketResponse(
                2L, "TCK-002", TicketType.GENERAL, new BigDecimal("100000"),
                TicketStatus.PAID, LocalDateTime.now(),
                "carlos@example.com", "CMF-2026", "Caribbean Music Fest 2026");

        when(userRepository.findByEmailIgnoreCase("carlos@example.com")).thenReturn(Optional.of(user));
        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.of(event));
        when(ticketRepository.countByEventEventCodeAndStatus("CMF-2026", TicketStatus.PAID))
                .thenReturn(1L)  // antes de guardar
                .thenReturn(2L); // después de guardar
        when(priceCalculator.calculatePrice(TicketType.GENERAL)).thenReturn(new BigDecimal("100000"));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
        when(eventRepository.save(any(Event.class))).thenReturn(event);
        when(ticketMapper.toResponse(ticket)).thenReturn(expectedResponse);

        // ACT
        TicketResponse result = ticketService.purchase(request);

        // ASSERT
        assertThat(result).isEqualTo(expectedResponse);
        assertThat(event.getStatus()).isEqualTo(EventStatus.SOLD_OUT);
        verify(ticketRepository).save(any(Ticket.class));
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void cancel_ticketPaid() {
        // ARRANGE
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        User user = new User("andrea", "andrea@example.com", true);
        Ticket ticket = new Ticket("TCK-001", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.PAID,
                LocalDateTime.now(), user, event);
        TicketResponse expectedResponse = new TicketResponse(
                1L, "TCK-001", TicketType.VIP, new BigDecimal("250000"),
                TicketStatus.CANCELLED, LocalDateTime.now(),
                "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026");

        when(ticketRepository.findByTicketCode("TCK-001")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
        when(ticketMapper.toResponse(ticket)).thenReturn(expectedResponse);

        // ACT
        TicketResponse result = ticketService.cancel("TCK-001");

        // ASSERT
        assertThat(result.status()).isEqualTo(TicketStatus.CANCELLED);
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void cancel_ticketUsed() {
        // ARRANGE
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        User user = new User("andrea", "andrea@example.com", true);
        Ticket ticket = new Ticket("TCK-001", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.USED,
                LocalDateTime.now(), user, event);

        when(ticketRepository.findByTicketCode("TCK-001")).thenReturn(Optional.of(ticket));

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> ticketService.cancel("TCK-001"));

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void markAsUsed_ticketPaid() {
        // ARRANGE
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        User user = new User("andrea", "andrea@example.com", true);
        Ticket ticket = new Ticket("TCK-001", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.PAID,
                LocalDateTime.now(), user, event);
        TicketResponse expectedResponse = new TicketResponse(
                1L, "TCK-001", TicketType.VIP, new BigDecimal("250000"),
                TicketStatus.USED, LocalDateTime.now(),
                "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026");

        when(ticketRepository.findByTicketCode("TCK-001")).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);
        when(ticketMapper.toResponse(ticket)).thenReturn(expectedResponse);

        // ACT
        TicketResponse result = ticketService.markAsUsed("TCK-001");

        // ASSERT
        assertThat(result.status()).isEqualTo(TicketStatus.USED);
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void markAsUsed_ticketCancelled() {
        // ARRANGE
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        User user = new User("andrea", "andrea@example.com", true);
        Ticket ticket = new Ticket("TCK-001", TicketType.VIP,
                new BigDecimal("250000"), TicketStatus.CANCELLED,
                LocalDateTime.now(), user, event);

        when(ticketRepository.findByTicketCode("TCK-001")).thenReturn(Optional.of(ticket));

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> ticketService.markAsUsed("TCK-001"));

        verify(ticketRepository, never()).save(any(Ticket.class));
    }
}
