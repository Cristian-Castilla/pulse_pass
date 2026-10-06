package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Ticket;
import com.pulsepass.pass.domain.TicketStatus;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.dto.request.PurchaseTicketRequest;
import com.pulsepass.pass.dto.response.TicketResponse;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.TicketMapper;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.TicketRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.service.PriceCalculator;
import com.pulsepass.pass.service.TicketService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.UUID;

@Transactional(readOnly = true)
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final TicketMapper mapper;
    private final PriceCalculator priceCalculator;

    public TicketServiceImpl(TicketRepository ticketRepository,
                             UserRepository userRepository,
                             EventRepository eventRepository,
                             TicketMapper mapper,
                             PriceCalculator priceCalculator) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
        this.priceCalculator = priceCalculator;
    }

    @Transactional
    @Override
    public TicketResponse purchase(PurchaseTicketRequest request) {
        // BR-TICKET-001: Usuario existente
        User user = userRepository.findByEmailIgnoreCase(request.userEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userEmail()));

        // BR-TICKET-002: Usuario activo
        if (!user.getActive()) {
            throw new BusinessRuleException("User is inactive: " + request.userEmail());
        }

        // BR-TICKET-003: Evento existente
        Event event = eventRepository.findByEventCode(request.eventCode())
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + request.eventCode()));

        // BR-TICKET-004: Evento publicado
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessRuleException("Event is not PUBLISHED: " + request.eventCode()
                    + " (status: " + event.getStatus() + ")");
        }

        // BR-TICKET-005: Fecha futura
        if (event.getEventDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Event already occurred: " + request.eventCode());
        }

        // BR-TICKET-006: Edad mínima
        if (event.getMinimumAge() != null && event.getMinimumAge() > 0) {
            UserProfile profile = user.getProfile();
            if (profile == null || profile.getBirthDate() == null) {
                throw new BusinessRuleException("User profile required to verify age: " + request.userEmail());
            }
            int age = Period.between(profile.getBirthDate(), event.getEventDate()).getYears();
            if (age < event.getMinimumAge()) {
                throw new BusinessRuleException("User does not meet minimum age. Required: "
                        + event.getMinimumAge() + ", actual: " + age);
            }
        }

        // BR-TICKET-007: Capacidad
        long paidTickets = ticketRepository.countByEventEventCodeAndStatus(
                request.eventCode(), TicketStatus.PAID);
        if (paidTickets >= event.getVenue().getCapacity()) {
            throw new BusinessRuleException("Event is sold out: " + request.eventCode());
        }

        // BR-TICKET-009: Calcular precio
        BigDecimal price = priceCalculator.calculatePrice(request.type());

        // Crear ticket
        String ticketCode = "TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Ticket ticket = new Ticket(
                ticketCode,
                request.type(),
                price,
                TicketStatus.PAID,
                LocalDateTime.now(),
                user,
                event
        );

        Ticket saved = ticketRepository.save(ticket);

        // BR-TICKET-008: Actualizar SOLD_OUT si se completa la capacidad
        long newPaidCount = ticketRepository.countByEventEventCodeAndStatus(
                request.eventCode(), TicketStatus.PAID);
        if (newPaidCount == event.getVenue().getCapacity()) {
            event.setStatus(EventStatus.SOLD_OUT);
            eventRepository.save(event);
        }

        return mapper.toResponse(saved);
    }

    @Override
    public TicketResponse findByCode(String ticketCode) {
        return ticketRepository.findByTicketCode(ticketCode)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketCode));
    }

    @Override
    public List<TicketResponse> findByUserEmail(String email) {
        return ticketRepository.findByUserEmailIgnoreCaseOrderByPurchaseDateDesc(email)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public List<TicketResponse> findPaidTicketByEvent(String eventCode) {
        return ticketRepository.findPaidTicketsByEventCode(eventCode)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    @Override
    public TicketResponse cancel(String ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketCode));

        // BR-TICKET-010: Solo PAID puede cancelarse
        if (ticket.getStatus() != TicketStatus.PAID) {
            throw new BusinessRuleException("Only PAID tickets can be cancelled. Current status: "
                    + ticket.getStatus());
        }

        // BR-TICKET-012: No cancelar después de la fecha del evento
        if (ticket.getEvent().getEventDate().isBefore(LocalDate.now())) {
            throw new BusinessRuleException("Cannot cancel ticket after event date: " + ticketCode);
        }

        ticket.setStatus(TicketStatus.CANCELLED);
        Ticket saved = ticketRepository.save(ticket);
        return mapper.toResponse(saved);
    }

    @Transactional
    @Override
    public TicketResponse markAsUsed(String ticketCode) {
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketCode));

        // BR-TICKET-013: Solo PAID puede marcarse como usado
        if (ticket.getStatus() != TicketStatus.PAID) {
            throw new BusinessRuleException("Only PAID tickets can be marked as used. Current status: "
                    + ticket.getStatus());
        }

        // BR-TICKET-014: CANCELLED nunca puede usarse
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new BusinessRuleException("Cancelled tickets cannot be used: " + ticketCode);
        }

        ticket.setStatus(TicketStatus.USED);
        Ticket saved = ticketRepository.save(ticket);
        return mapper.toResponse(saved);
    }
}
