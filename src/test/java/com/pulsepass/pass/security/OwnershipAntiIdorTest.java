package com.pulsepass.pass.security;

import com.pulsepass.pass.PostgresContainerSupport;
import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.Ticket;
import com.pulsepass.pass.domain.TicketStatus;
import com.pulsepass.pass.domain.TicketType;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.TicketRepository;
import com.pulsepass.pass.repository.UserCredentialRepository;
import com.pulsepass.pass.repository.UserProfileRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.UserRoleRepository;
import com.pulsepass.pass.repository.VenueRepository;
import com.pulsepass.pass.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anti-IDOR sobre las rutas de ticket y usuario. PRD §11.1 y §17.4.
 *
 * <p>PRD §17.4: "Para cada una de las 20 operaciones, documentar al menos: rol permitido, rol
 * prohibido, anónimo y, cuando exista ownership, mismo propietario / tercero".
 *
 * <p>Este test cubre la mitad de ownership: que un usuario no pueda leer, cancelar ni comprar sobre
 * recursos de otro aunque conozca el código. Los escenarios del PRD §15:
 *   UC-SEC-05: "Andrea envía en userEmail el correo de Carlos → 403 y no se crea ticket".
 *   UC-SEC-06: "Andrea consulta el ticketCode de Carlos → 403 y no obtiene información".
 *   UC-SEC-07: "Laura (STAFF) marca un ticket válido como usado → 200; un USER recibe 403".
 *
 * <p>La capa de rol (401/403 por ruta) ya está cubierta por {@code AuthorizationMatrixTest}; aquí se
 * verifica la capa de propiedad, que es la que el PRD §3.2 exige poner en la frontera de negocio.
 */
@AutoConfigureMockMvc
@Transactional
class OwnershipAntiIdorTest extends PostgresContainerSupport {

    private static final String RAW_PASSWORD = "UnaFraseSeguraLarga2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserCredentialRepository userCredentialRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User andrea;
    private User carlos;

    @BeforeEach
    void setUp() {
        userRoleRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userCredentialRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        ticketRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        venueRepository.deleteAllInBatch();

        andrea = persistUser("andrea", "andrea@example.com", Role.USER);
        carlos = persistUser("carlos", "carlos@example.com", Role.USER);

        Venue venue = venueRepository.saveAndFlush(new Venue(
                "VEN-SMR-01", "Marina Convention Center", "Santa Marta",
                "Cra 1 #10-20", 5000L, true));
        eventRepository.saveAndFlush(new Event(
                "CMF-2026", "Caribbean Music Fest 2026", "Test description",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDateTime.now().plusDays(30), 0, null, venue));
    }

    private User persistUser(String username, String email, Role role) {
        User user = userRepository.saveAndFlush(new User(username, email, true));
        userProfileRepository.saveAndFlush(new UserProfile(
                "Nombre", "Apellido", "3001234567", "Santa Marta",
                LocalDate.of(1995, 5, 15), user));
        userCredentialRepository.saveAndFlush(new UserCredential(
                user, passwordEncoder.encode(RAW_PASSWORD), null));
        userRoleRepository.saveAndFlush(new UserRole(user, role));
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + authService.login(user.getUsername(), RAW_PASSWORD).accessToken();
    }

    private Ticket ticketOwnedBy(User owner, String code) {
        return ticketRepository.saveAndFlush(new Ticket(
                code, TicketType.VIP, new BigDecimal("250000.00"),
                TicketStatus.PAID, LocalDateTime.now(), owner,
                eventRepository.findByEventCode("CMF-2026").orElseThrow()));
    }

    // ------------------------------------------------------------------
    // UC-SEC-05 / T-SEC-010 — compra para un tercero
    // ------------------------------------------------------------------

    @Test
    void purchase_withAnotherUsersEmail_isDeniedAndCreatesNothing() throws Exception {
        // UC-SEC-05: "Andrea envía en userEmail el correo de Carlos → 403 y no se crea ticket"
        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", bearer(andrea))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userEmail": "carlos@example.com",
                                    "eventCode": "CMF-2026",
                                    "type": "VIP"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("No tiene permisos para realizar esta operación"));

        // T-SEC-010: "0 tickets creados"
        assertThat(ticketRepository.count()).isZero();
    }

    @Test
    void purchase_withOwnEmail_createsTicket() throws Exception {
        // El mismo request con el correo propio sí funciona
        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", bearer(andrea))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userEmail": "andrea@example.com",
                                    "eventCode": "CMF-2026",
                                    "type": "VIP"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userEmail").value("andrea@example.com"));
    }

    // ------------------------------------------------------------------
    // UC-SEC-06 / T-SEC-009 — consulta de ticket ajeno
    // ------------------------------------------------------------------

    @Test
    void findTicket_ofAnotherUser_isDenied() throws Exception {
        // UC-SEC-06: "Andrea consulta su ticketCode → 403 y no obtiene información del ticket"
        ticketOwnedBy(carlos, "TCK-CARLOS-01");

        mockMvc.perform(get("/api/tickets/{ticketCode}", "TCK-CARLOS-01")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("No tiene permisos para realizar esta operación"))
                // No se filtra el contenido del ticket
                .andExpect(jsonPath("$.ticketCode").doesNotExist());
    }

    @Test
    void findOwnTicket_isAllowed() throws Exception {
        ticketOwnedBy(andrea, "TCK-ANDREA-01");

        mockMvc.perform(get("/api/tickets/{ticketCode}", "TCK-ANDREA-01")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value("TCK-ANDREA-01"))
                .andExpect(jsonPath("$.userEmail").value("andrea@example.com"));
    }

    @Test
    void findTicket_ofAnotherUser_isAllowedForStaff() throws Exception {
        // PRD §11 TKT-02: "Propietario, o STAFF/ADMIN en operación autorizada"
        User laura = persistUser("laura", "laura@example.com", Role.STAFF);
        ticketOwnedBy(carlos, "TCK-CARLOS-01");

        mockMvc.perform(get("/api/tickets/{ticketCode}", "TCK-CARLOS-01")
                        .header("Authorization", bearer(laura)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("carlos@example.com"));
    }

    // ------------------------------------------------------------------
    // TKT-03 — listado por email ajeno
    // ------------------------------------------------------------------

    @Test
    void listTickets_ofAnotherUser_isDenied() throws Exception {
        // PRD §11 TKT-03: "Propietario, o ADMIN autorizado"
        ticketOwnedBy(carlos, "TCK-CARLOS-01");

        mockMvc.perform(get("/api/tickets/by-user").param("email", "carlos@example.com")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void listOwnTickets_isAllowed() throws Exception {
        ticketOwnedBy(andrea, "TCK-ANDREA-01");

        mockMvc.perform(get("/api/tickets/by-user").param("email", "andrea@example.com")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ------------------------------------------------------------------
    // TKT-05 — cancelación de ticket ajeno
    // ------------------------------------------------------------------

    @Test
    void cancelTicket_ofAnotherUser_isDenied() throws Exception {
        // PRD §11 TKT-05: "Propietario (USER) o ADMIN expresamente autorizado"
        Ticket carlosTicket = ticketOwnedBy(carlos, "TCK-CARLOS-01");

        mockMvc.perform(patch("/api/tickets/{ticketCode}/cancel", "TCK-CARLOS-01")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // El ticket no se canceló
        assertThat(ticketRepository.findById(carlosTicket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.PAID);
    }

    @Test
    void cancelOwnTicket_isAllowed() throws Exception {
        Ticket andreaTicket = ticketOwnedBy(andrea, "TCK-ANDREA-01");

        mockMvc.perform(patch("/api/tickets/{ticketCode}/cancel", "TCK-ANDREA-01")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(ticketRepository.findById(andreaTicket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.CANCELLED);
    }

    // ------------------------------------------------------------------
    // USR-02 / USR-03 — datos de otro usuario (T-SEC-015)
    // ------------------------------------------------------------------

    @Test
    void findUserByEmail_ofAnotherUser_isDenied() throws Exception {
        // T-SEC-015: "USER busca datos de otro usuario → 403/404"
        mockMvc.perform(get("/api/users/by-email").param("email", "carlos@example.com")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("No tiene permisos para realizar esta operación"));
    }

    @Test
    void findUserByUsername_ofAnotherUser_isDenied() throws Exception {
        mockMvc.perform(get("/api/users/by-username").param("username", "carlos")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isForbidden());
    }

    @Test
    void findOwnDataByEmail_isAllowed() throws Exception {
        mockMvc.perform(get("/api/users/by-email").param("email", "andrea@example.com")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("andrea"));
    }

    @Test
    void findUserByEmail_ofAnotherUser_isAllowedForAdmin() throws Exception {
        // PRD §11 USR-02: "Propio usuario, o ADMIN con propósito autorizado"
        User sofia = persistUser("sofia", "sofia@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/users/by-email").param("email", "carlos@example.com")
                        .header("Authorization", bearer(sofia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("carlos"));
    }

    // ------------------------------------------------------------------
    // TKT-06 — uso de ticket: USER vs STAFF (UC-SEC-07)
    // ------------------------------------------------------------------

    @Test
    void markTicketAsUsed_byOwnerUser_isDenied() throws Exception {
        // UC-SEC-07: "Un USER recibe 403"
        ticketOwnedBy(andrea, "TCK-ANDREA-01");

        mockMvc.perform(patch("/api/tickets/{ticketCode}/use", "TCK-ANDREA-01")
                        .header("Authorization", bearer(andrea)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void markTicketAsUsed_byStaff_isAllowed() throws Exception {
        // UC-SEC-07: "Laura (STAFF) ... invoca PATCH /api/tickets/{ticketCode}/use → se permite"
        User laura = persistUser("laura", "laura@example.com", Role.STAFF);
        Ticket ticket = ticketOwnedBy(andrea, "TCK-ANDREA-01");

        mockMvc.perform(patch("/api/tickets/{ticketCode}/use", "TCK-ANDREA-01")
                        .header("Authorization", bearer(laura)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"));

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.USED);
    }
}
