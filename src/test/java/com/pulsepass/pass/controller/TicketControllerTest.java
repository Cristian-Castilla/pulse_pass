package com.pulsepass.pass.controller;

import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.Ticket;
import com.pulsepass.pass.domain.TicketStatus;
import com.pulsepass.pass.domain.TicketType;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.TicketRepository;
import com.pulsepass.pass.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TKT-01..06 — operaciones de ticket, con la matriz de roles del PRD §11.
 *
 *   TKT-01 POST  /api/tickets                     → USER; userEmail debe pertenecer al principal
 *   TKT-02 GET   /api/tickets/{ticketCode}        → propietario, o STAFF/ADMIN
 *   TKT-03 GET   /api/tickets/by-user             → propietario, o ADMIN
 *   TKT-04 GET   /api/events/{code}/tickets/paid  → STAFF/ADMIN
 *   TKT-05 PATCH /api/tickets/{code}/cancel       → propietario (USER) o ADMIN
 *   TKT-06 PATCH /api/tickets/{code}/use          → STAFF/ADMIN
 *
 * <p>Migrado de {@code @WebMvcTest} a HTTP real. La compra se prueba contra el servicio real, con
 * el evento PUBLISHED y el venue persistidos, de modo que se verifican también las reglas de negocio
 * (BR-TICKET-004..009) que antes se mockeaban.
 *
 * <p><b>La capa de propiedad está implementada</b> en AuthorizationService y se verifica en
 * OwnershipAntiIdorTest. Aquí se prueba la capa de autenticación y rol por HTTP.
 */
class TicketControllerTest extends ControllerHttpSupport {

    @Autowired private AuthService authService;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketRepository ticketRepository;

    private String bearer(String username) {
        return "Bearer " + authService.login(username, RAW_PASSWORD).accessToken();
    }

    /**
     * Devuelve el evento publicado de referencia, creándolo solo la primera vez.
     *
     * <p>Se cachea por test porque {@code events.event_code} es UNIQUE: llamar a este método dos
     * veces en el mismo test (p. ej. al crear dos tickets para listarlos) inserta el mismo código
     * dos veces y choca con {@code events_event_code_key}. Cada test arranca con
     * {@code @BeforeEach}, así que el caché es por método de test y no sobrevive entre ellos.
     */
    private Event publishedEvent;

    private Event persistPublishedEvent() {
        if (publishedEvent != null) {
            return publishedEvent;
        }
        Venue venue = persistReferenceVenue();
        publishedEvent = eventRepository.saveAndFlush(new Event(
                "CMF-2026", "Caribbean Music Fest 2026", "Test description",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDateTime.now().plusDays(30), 0, null, venue));
        return publishedEvent;
    }

    private Ticket persistTicket(com.pulsepass.pass.domain.User owner, String ticketCode,
                                 TicketType type, TicketStatus status) {
        Event event = persistPublishedEvent();
        return ticketRepository.saveAndFlush(new Ticket(
                ticketCode, type, new BigDecimal("250000.00"),
                status, LocalDateTime.now(), owner, event));
    }

    private String purchaseBody(String email) {
        return """
                {
                    "userEmail": "%s",
                    "eventCode": "CMF-2026",
                    "type": "VIP"
                }
                """.formatted(email);
    }

    // ------------------------------------------------------------------
    // TKT-01 — compra
    // ------------------------------------------------------------------

    @Test
    void purchase_anonymousGets401() throws Exception {
        // PRD §11 TKT-01: requiere identidad
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody("andrea@example.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void purchase_authenticatedUser_returns201() throws Exception {
        // El servicio real valida usuario, evento y capacidad antes de crear el ticket
        persistPublishedEvent();
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", bearer("andrea"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody("andrea@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.type").value("VIP"))
                .andExpect(jsonPath("$.userEmail").value("andrea@example.com"))
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"));
    }

    @Test
    void purchase_invalidRequest_returns400() throws Exception {
        // T-SEC-018: Bean Validation corre antes que el servicio
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", bearer("andrea"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userEmail": "",
                                    "eventCode": "",
                                    "type": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.userEmail").value("User email is required"))
                .andExpect(jsonPath("$.details.eventCode").value("Event code is required"))
                .andExpect(jsonPath("$.details.type").value("Ticket type is required"));
    }

    @Test
    void purchase_nonExistingUser_returns404() throws Exception {
        // BR-TICKET-001: usuario inexistente
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", bearer("andrea"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody("unknown@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found: unknown@example.com"));
    }

    @Test
    void purchase_draftEvent_returns409() throws Exception {
        // BR-TICKET-004: "Solo se pueden comprar tickets cuando event.status == PUBLISHED"
        Venue venue = persistReferenceVenue();
        eventRepository.saveAndFlush(new Event(
                "CMF-2026", "Caribbean Music Fest 2026", "Test description",
                EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDateTime.now().plusDays(30), 0, null, venue));
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", bearer("andrea"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseBody("andrea@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Event is not PUBLISHED: CMF-2026 (status: DRAFT)"));
    }

    // ------------------------------------------------------------------
    // TKT-02 / TKT-03 / TKT-05 — autenticado (la propiedad se verifica en OwnershipAntiIdorTest)
    // ------------------------------------------------------------------

    @Test
    void findByCode_anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketCode}", "TCK-0001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findByCode_authenticatedUser_returns200() throws Exception {
        com.pulsepass.pass.domain.User andrea = persistUser("andrea", "andrea@example.com", Role.USER);
        persistTicket(andrea, "TCK-0001", TicketType.VIP, TicketStatus.PAID);

        mockMvc.perform(get("/api/tickets/{ticketCode}", "TCK-0001")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value("TCK-0001"))
                .andExpect(jsonPath("$.userEmail").value("andrea@example.com"));
    }

    @Test
    void findByCode_nonExisting_returns404() throws Exception {
        // La autenticación pasa y el 404 es de negocio
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(get("/api/tickets/{ticketCode}", "TCK-9999")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket not found: TCK-9999"));
    }

    @Test
    void findByUserEmail_anonymousGets401() throws Exception {
        // PRD §14.3 adv. 4: el literal /by-user debe declararse antes del patrón {ticketCode}
        mockMvc.perform(get("/api/tickets/by-user").param("email", "andrea@example.com"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findByUserEmail_authenticatedUser_returns200() throws Exception {
        com.pulsepass.pass.domain.User andrea = persistUser("andrea", "andrea@example.com", Role.USER);
        persistTicket(andrea, "TCK-0001", TicketType.VIP, TicketStatus.PAID);
        persistTicket(andrea, "TCK-0002", TicketType.GENERAL, TicketStatus.PAID);

        mockMvc.perform(get("/api/tickets/by-user").param("email", "andrea@example.com")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void cancel_anonymousGets401() throws Exception {
        mockMvc.perform(patch("/api/tickets/{ticketCode}/cancel", "TCK-0001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_ownerGets200() throws Exception {
        // BR-TICKET-010: "Solo puede cancelarse un ticket PAID"
        com.pulsepass.pass.domain.User andrea = persistUser("andrea", "andrea@example.com", Role.USER);
        persistTicket(andrea, "TCK-0001", TicketType.VIP, TicketStatus.PAID);

        mockMvc.perform(patch("/api/tickets/{ticketCode}/cancel", "TCK-0001")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value("TCK-0001"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_usedTicket_returns409() throws Exception {
        // BR-TICKET-011: "No puede cancelarse: USED, CANCELLED"
        com.pulsepass.pass.domain.User andrea = persistUser("andrea", "andrea@example.com", Role.USER);
        persistTicket(andrea, "TCK-0002", TicketType.VIP, TicketStatus.USED);

        mockMvc.perform(patch("/api/tickets/{ticketCode}/cancel", "TCK-0002")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Only PAID tickets can be cancelled. Current status: USED"));
    }

    // ------------------------------------------------------------------
    // TKT-06 — STAFF/ADMIN
    // ------------------------------------------------------------------

    @Test
    void markAsUsed_userGets403() throws Exception {
        // T-SEC-012: "USER intenta usar ticket → 403"
        com.pulsepass.pass.domain.User andrea = persistUser("andrea", "andrea@example.com", Role.USER);
        persistTicket(andrea, "TCK-0001", TicketType.VIP, TicketStatus.PAID);

        mockMvc.perform(patch("/api/tickets/{ticketCode}/use", "TCK-0001")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void markAsUsed_staffGets200() throws Exception {
        // T-SEC-013: "STAFF marca ticket válido como usado → 200"
        com.pulsepass.pass.domain.User andrea = persistUser("andrea", "andrea@example.com", Role.USER);
        persistTicket(andrea, "TCK-0001", TicketType.VIP, TicketStatus.PAID);
        persistUser("laura", "laura@example.com", Role.STAFF);

        mockMvc.perform(patch("/api/tickets/{ticketCode}/use", "TCK-0001")
                        .header("Authorization", bearer("laura")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value("TCK-0001"))
                .andExpect(jsonPath("$.status").value("USED"));
    }

    @Test
    void markAsUsed_cancelledTicket_returns409() throws Exception {
        // BR-TICKET-014: "Un ticket CANCELLED nunca puede utilizarse"
        com.pulsepass.pass.domain.User andrea = persistUser("andrea", "andrea@example.com", Role.USER);
        persistTicket(andrea, "TCK-0003", TicketType.VIP, TicketStatus.CANCELLED);
        persistUser("laura", "laura@example.com", Role.STAFF);

        mockMvc.perform(patch("/api/tickets/{ticketCode}/use", "TCK-0003")
                        .header("Authorization", bearer("laura")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Only PAID tickets can be marked as used. Current status: CANCELLED"));
    }

    @Test
    void markAsUsed_anonymousGets401() throws Exception {
        mockMvc.perform(patch("/api/tickets/{ticketCode}/use", "TCK-0001"))
                .andExpect(status().isUnauthorized());
    }
}
