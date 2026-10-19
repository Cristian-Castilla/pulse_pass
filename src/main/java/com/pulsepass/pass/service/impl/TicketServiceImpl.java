package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.Ticket;
import com.pulsepass.pass.domain.TicketStatus;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.dto.request.PurchaseTicketRequest;
import com.pulsepass.pass.dto.response.TicketResponse;
import com.pulsepass.pass.exception.AccessDeniedException;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.TicketMapper;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.TicketRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.service.AuthorizationService;
import com.pulsepass.pass.service.PriceCalculator;
import com.pulsepass.pass.service.TicketService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.UUID;
@Service
@Transactional(readOnly = true)
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final TicketMapper mapper;
    private final PriceCalculator priceCalculator;
    private final AuthorizationService authorizationService;

    public TicketServiceImpl(TicketRepository ticketRepository,
                             UserRepository userRepository,
                             EventRepository eventRepository,
                             TicketMapper mapper,
                             PriceCalculator priceCalculator,
                             AuthorizationService authorizationService) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.mapper = mapper;
        this.priceCalculator = priceCalculator;
        this.authorizationService = authorizationService;
    }

    @Transactional
    @Override
    public TicketResponse purchase(PurchaseTicketRequest request) {
        // BR-TICKET-001: Usuario existente
        User user = userRepository.findByEmailIgnoreCase(request.userEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.userEmail()));

        // PRD §11.1: "POST /api/tickets debe comprobar que el userEmail del DTO representa al
        // principal (para USER). Nunca confiar en el email del request como comprobación de
        // autorización". La comparación es por id persistente, no por string: PRD §9.1 vincula la
        // identidad de login al ID, no al correo.
        //
        // Se evalúa ANTES de las reglas de negocio: si la petición no es del propio usuario, no se
        // ejecuta ninguna validación sobre el evento y no se filtra información de su existencia.
        authorizationService.requireSelf(user.getId());

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
        if (event.getEventDate().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Event already occurred: " + request.eventCode());
        }

        // BR-TICKET-006: Edad mínima
        if (event.getMinimumAge() != null && event.getMinimumAge() > 0) {
            UserProfile profile = user.getProfile();
            if (profile == null || profile.getBirthDate() == null) {
                throw new BusinessRuleException("User profile required to verify age: " + request.userEmail());
            }
            // BR-TICKET-006: la edad se evalúa sobre la fecha del evento (sin la hora)
            int age = Period.between(profile.getBirthDate(), event.getEventDate().toLocalDate()).getYears();
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
        Ticket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketCode));

        // PRD §11.1: "GET /api/tickets/{ticketCode} debe determinar el usuario propietario desde la
        // base de datos y contrastarlo con principal.userId; jamás basta el ticketCode".
        // STAFF/ADMIN también pasan (PRD §11 TKT-02: "Propietario, o STAFF/ADMIN en operación
        // autorizada").
        authorizationService.requireOwnerOrPrivileged(ticket.getUser().getId());

        return mapper.toResponse(ticket);
    }

    @Override
    public List<TicketResponse> findByUserEmail(String email) {
        // PRD §11 TKT-03: "Propietario, o ADMIN autorizado".
        //
        // La identidad se resuelve a partir del email pedido, cargando el usuario real desde la
        // base, y se compara por ID persistente (PRD §11.1: "las dos búsquedas de usuario deben
        // comparar el usuario solicitado con la identidad autenticada usando ID persistente, no solo
        // un string de email"). Un email que no existe no revela nada: 404 de negocio.
        User requested = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        authorizationService.requireOwnerOrPrivileged(requested.getId());

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

        // PRD §11 TKT-05: "Propietario (USER) o ADMIN expresamente autorizado; sigue vigente la
        // regla de cancelación del Service".
        //
        // La autorización va ANTES de las reglas de negocio: si el ticket no es del principal, no
        // se ejecuta ninguna validación y no se filtra el estado real del ticket.
        authorizationService.requireOwnerOrPrivileged(ticket.getUser().getId());

        // BR-TICKET-010: Solo PAID puede cancelarse
        if (ticket.getStatus() != TicketStatus.PAID) {
            throw new BusinessRuleException("Only PAID tickets can be cancelled. Current status: "
                    + ticket.getStatus());
        }

        // BR-TICKET-012: No cancelar después de la fecha del evento
        if (ticket.getEvent().getEventDate().isBefore(LocalDateTime.now())) {
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

        // PRD §11 TKT-06: "STAFF/ADMIN; conserva transición válida del Service".
        //
        // El rol lo exige la matriz de rutas de SecurityConfig, pero la autorización se repite aquí
        // porque el PRD §3.2 pide que la propiedad no dependa "solamente del Controller" ni de la
        // capa web: una invocación directa al servicio debe seguir sin poder marcar un ticket ajeno.
        // Un USER que llegara hasta aquí (p. ej. por una ruta mal configurada) recibe 403.
        if (!authorizationService.hasRole(Role.STAFF) && !authorizationService.hasRole(Role.ADMIN)) {
            throw new AccessDeniedException(
                    "No tiene permisos para realizar esta operación");
        }

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
