package com.pulsepass.pass.security;

import com.pulsepass.pass.PostgresContainerSupport;
import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.repository.EventRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtrado de eventos no publicados en EVT-02 y EVT-06. PRD §11 y §20.
 *
 * PRD §11 EVT-02: "GET /api/events/{eventCode} — Público solo si publicado; para otros estados
 * STAFF/ADMIN, evitando revelar borradores".
 * PRD §11 EVT-06: "GET /api/events/by-artist?stageName=... — Público únicamente para eventos
 * publicados; otros estados requieren STAFF/ADMIN".
 * PRD §20: "Riesgo: permitAll() de un GET de eventos → Filtración de borradores → Filtrado por
 * publicación y test específico".
 *
 * <p>Decisión D3 del plan: el filtrado va en la capa de servicio, no en la ruta.
 * {@code SecurityConfig} no puede expresar "público si el recurso cumple una condición de estado",
 * y el PRD §14.3 adv. 1 lo dice: "la visibilidad de eventos no publicados deben implementarse en
 * Services/seguridad a nivel de método".
 *
 * <p>Un borrador se reporta como 404 y no como 403: la decisión D1 fija 403 para recursos ajenos,
 * y aquí no hay conflicto de propiedad, sino que el recurso no debe existir para quien no tiene rol.
 * Reportarlo como inexistente es lo que evita revelar su existencia.
 */
@AutoConfigureMockMvc
@Transactional
class DraftVisibilityTest extends PostgresContainerSupport {

    private static final String RAW_PASSWORD = "UnaFraseSeguraLarga2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserCredentialRepository userCredentialRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Venue venue;

    @BeforeEach
    void setUp() {
        userRoleRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userCredentialRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        venueRepository.deleteAllInBatch();
        artistRepository.deleteAllInBatch();

        venue = venueRepository.saveAndFlush(new Venue(
                "VEN-SMR-01", "Marina Convention Center", "Santa Marta",
                "Cra 1 #10-20", 5000L, true));

        // Un evento DRAFT y uno PUBLISHED, ambos del mismo artista, para comparar
        Artist solarBeat = artistRepository.saveAndFlush(
                new Artist("Solar Beat", "Colombia", "Electrónica", true));
        eventRepository.saveAndFlush(event("CMF-DRAFT-2026", EventStatus.DRAFT, solarBeat));
        eventRepository.saveAndFlush(event("CMF-2026", EventStatus.PUBLISHED, solarBeat));
    }

    private Event event(String code, EventStatus status, Artist artist) {
        Event event = new Event(code, "Caribbean Music Fest 2026", "Test description",
                EventCategory.MUSIC, status, LocalDateTime.now().plusDays(30), 0, null, venue);
        event.addArtist(artist);
        return event;
    }

    private String bearer(String username) {
        return "Bearer " + authService.login(username, RAW_PASSWORD).accessToken();
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

    // ------------------------------------------------------------------
    // EVT-02 — evento por código
    // ------------------------------------------------------------------

    @Test
    void publishedEvent_byCode_isVisibleToAnonymous() throws Exception {
        mockMvc.perform(get("/api/events/{eventCode}", "CMF-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void draftEvent_byCode_isHiddenFromAnonymous() throws Exception {
        // PRD §11 EVT-02: "evitando revelar borradores". Un anónimo no lo alcanza.
        mockMvc.perform(get("/api/events/{eventCode}", "CMF-DRAFT-2026"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event code doesn't exist: CMF-DRAFT-2026"));
    }

    @Test
    void draftEvent_byCode_isHiddenFromRegularUser() throws Exception {
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(get("/api/events/{eventCode}", "CMF-DRAFT-2026")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftEvent_byCode_isVisibleToStaff() throws Exception {
        // PRD §11 EVT-02: "para otros estados STAFF/ADMIN"
        persistUser("laura", "laura@example.com", Role.STAFF);

        mockMvc.perform(get("/api/events/{eventCode}", "CMF-DRAFT-2026")
                        .header("Authorization", bearer("laura")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("CMF-DRAFT-2026"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void draftEvent_byCode_isVisibleToAdmin() throws Exception {
        persistUser("sofia", "sofia@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/events/{eventCode}", "CMF-DRAFT-2026")
                        .header("Authorization", bearer("sofia")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    // ------------------------------------------------------------------
    // EVT-06 — eventos por artista
    // ------------------------------------------------------------------

    @Test
    void eventsByArtist_forAnonymous_excludesDrafts() throws Exception {
        // PRD §11 EVT-06: "Público únicamente para eventos publicados"
        mockMvc.perform(get("/api/events/by-artist").param("stageName", "Solar Beat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventCode").value("CMF-2026"))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void eventsByArtist_forStaff_includesDrafts() throws Exception {
        persistUser("laura", "laura@example.com", Role.STAFF);

        mockMvc.perform(get("/api/events/by-artist").param("stageName", "Solar Beat")
                        .header("Authorization", bearer("laura")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].eventCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "CMF-2026", "CMF-DRAFT-2026")));
    }

    @Test
    void eventsByArtist_forRegularUser_excludesDrafts() throws Exception {
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(get("/api/events/by-artist").param("stageName", "Solar Beat")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ------------------------------------------------------------------
    // EVT-03 — la cartelera pública ya filtraba; se re-verifica
    // ------------------------------------------------------------------

    @Test
    void publishedEvents_listingNeverIncludesDrafts() throws Exception {
        // FR-EVT-005 / AC-006: "Solo se retornan eventos publicados"
        mockMvc.perform(get("/api/events/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    // ------------------------------------------------------------------
    // T-SEC-017 — un borrador no queda expuesto por ninguna vía pública
    // ------------------------------------------------------------------

    @Test
    void draftEvent_isNotExposedByAnyPublicListing() throws Exception {
        // T-SEC-017: "Anónimo consulta evento no publicado → No expone datos del borrador"
        //
        // Se verifica que el borrador no aparece en la cartelera pública ni en el listado por
        // artista, y que su consulta directa responde 404.
        mockMvc.perform(get("/api/events/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventCode").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CMF-DRAFT-2026"))));

        mockMvc.perform(get("/api/events/by-artist").param("stageName", "Solar Beat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventCode").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CMF-DRAFT-2026"))));

        mockMvc.perform(get("/api/events/{eventCode}", "CMF-DRAFT-2026"))
                .andExpect(status().isNotFound());
    }
}
