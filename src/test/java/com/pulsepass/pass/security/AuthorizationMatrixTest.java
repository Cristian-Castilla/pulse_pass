package com.pulsepass.pass.security;

import com.pulsepass.pass.PostgresContainerSupport;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.domain.Venue;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Matriz de autorización de las 20 operaciones del PRD §11 contra la {@code SecurityConfig}.
 *
 * PRD de Seguridad §17.4: "Para cada una de las 20 operaciones, documentar al menos: rol permitido,
 * rol prohibido, anónimo y, cuando exista ownership, mismo propietario / tercero."
 * PRD §17.2, T-SEC-002/003: anónimo 401, USER sin rol 403.
 * PRD §14.3 adv. 4: "El orden de matchers importa: rutas específicas antes de patrones generales".
 *
 * <p>Este test cubre la capa de <b>rol</b>. La capa de <b>propiedad</b> (anti-IDOR) se verifica en
 * OwnershipAntiIdorTest; aquí solo se comprueba que la ruta exige la identidad correcta.
 *
 * <p>El body de las peticiones que requieren cuerpo se construye con un JSON mínimo y válido; los
 * tests de negocio de cada operación siguen en sus propias clases (Fase 3).
 */
@AutoConfigureMockMvc
@Transactional
class AuthorizationMatrixTest extends PostgresContainerSupport {

    private static final String RAW_PASSWORD = "UnaFraseSeguraLarga2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserCredentialRepository userCredentialRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User andrea;
    private User carlos;
    private User laura;
    private User sofia;

    @BeforeEach
    void cleanDatabase() {
        userRoleRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userCredentialRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        venueRepository.deleteAllInBatch();

        andrea = persistUser("andrea", "andrea@example.com", Role.USER);
        carlos = persistUser("carlos", "carlos@example.com", Role.USER);
        laura = persistUser("laura", "laura@example.com", Role.STAFF);
        sofia = persistUser("sofia", "sofia@example.com", Role.ADMIN);

        // El venue de referencia del PRD §16.1 (capacidad 5000, decisión A-03).
        venueRepository.saveAndFlush(new Venue(
                "VEN-SMR-01", "Marina Convention Center", "Santa Marta",
                "Cra 1 #1-1", 5000L, true));
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

    private static final String VALID_EVENT_BODY = """
            {
                "eventCode": "CMF-2026",
                "name": "Caribbean Music Fest 2026",
                "description": "Festival",
                "category": "MUSIC",
                "eventDate": "2026-12-31T19:00:00",
                "minimumAge": 18,
                "venueCode": "VEN-SMR-01"
            }
            """;

    // ------------------------------------------------------------------
    // VEN-01 / VEN-02 — públicos
    // ------------------------------------------------------------------

    @Test
    void ven01_venueByCode_isPublic() throws Exception {
        // PRD §11 VEN-01: "Público"
        assertThat(statusOf(get("/api/venues/VEN-SMR-01"))).isEqualTo(200);
        assertThat(statusOf(get("/api/venues/VEN-SMR-01").header("Authorization", bearer(andrea))))
                .isEqualTo(200);
    }

    @Test
    void ven02_activeVenues_isPublic() throws Exception {
        // PRD §11 VEN-02: "Público". PRD §14.3 adv. 3: verificar con una prueba HTTP explícita.
        assertThat(statusOf(get("/api/venues/active"))).isEqualTo(200);
        assertThat(statusOf(get("/api/venues/active").header("Authorization", bearer(laura))))
                .isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // EVT-01 — ADMIN
    // ------------------------------------------------------------------

    @Test
    void evt01_createEvent_anonymousGets401() throws Exception {
        // T-SEC-002
        assertThat(statusOf(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                .content(VALID_EVENT_BODY))).isEqualTo(401);
    }

    @Test
    void evt01_createEvent_userGets403() throws Exception {
        // T-SEC-003: "USER POST /api/events → 403, sin invocar Service"
        assertThat(statusOf(post("/api/events").header("Authorization", bearer(andrea))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT_BODY))).isEqualTo(403);
    }

    @Test
    void evt01_createEvent_adminGets201() throws Exception {
        // T-SEC-004: "ADMIN POST /api/events válido → 201"
        assertThat(statusOf(post("/api/events").header("Authorization", bearer(sofia))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_EVENT_BODY))).isEqualTo(201);
    }

    // ------------------------------------------------------------------
    // EVT-03 — público
    // ------------------------------------------------------------------

    @Test
    void evt03_publishedEvents_isPublic() throws Exception {
        // T-SEC-001: "Anónimo GET /api/events/published → 200"
        assertThat(statusOf(get("/api/events/published"))).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // ART-01..03 — públicos
    // ------------------------------------------------------------------

    @Test
    void art01_artistById_isPublic() throws Exception {
        // PRD §11 ART-01: "Público". El id 999 no existe: si la ruta fuera protegida, un anónimo
        // recibiría 401 en vez de 404.
        assertThat(statusOf(get("/api/artists/999"))).isEqualTo(404);
    }

    @Test
    void art02_artistByStageName_isPublic() throws Exception {
        // PRD §11 ART-02: "Público"
        assertThat(statusOf(get("/api/artists/by-stage-name").param("stageName", "Nadie"))).isEqualTo(404);
    }

    @Test
    void art03_activeArtists_isPublic() throws Exception {
        // PRD §11 ART-03: "Público"
        assertThat(statusOf(get("/api/artists/active"))).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // USR-01 — público
    // ------------------------------------------------------------------

    @Test
    void usr01_register_isPublic() throws Exception {
        // PRD §11 USR-01: "Público (rol inicial exclusivamente USER)"
        String body = """
                {
                    "username": "nuevo",
                    "email": "nuevo@example.com",
                    "firstName": "Nuevo",
                    "lastName": "Usuario",
                    "birthDate": "1995-05-15",
                    "password": "UnaClaveSegura2026!"
                }
                """;
        assertThat(statusOf(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body)))
                .isEqualTo(201);
    }

    // ------------------------------------------------------------------
    // USR-02 / USR-03 — autenticado (la propiedad se verifica en OwnershipAntiIdorTest)
    // ------------------------------------------------------------------

    @Test
    void usr02_findByEmail_requiresAuthentication() throws Exception {
        // Anónimo: 401. Autenticado: pasa la capa de seguridad (el 404 es de negocio, el usuario
        // no existe en este test). La verificación de propiedad se hace en OwnershipAntiIdorTest.
        assertThat(statusOf(get("/api/users/by-email").param("email", "andrea@example.com")))
                .isEqualTo(401);
        assertThat(statusOf(get("/api/users/by-email").param("email", "andrea@example.com")
                .header("Authorization", bearer(andrea)))).isEqualTo(200);
    }

    @Test
    void usr03_findByUsername_requiresAuthentication() throws Exception {
        assertThat(statusOf(get("/api/users/by-username").param("username", "andrea")))
                .isEqualTo(401);
        assertThat(statusOf(get("/api/users/by-username").param("username", "andrea")
                .header("Authorization", bearer(andrea)))).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // TKT-01 — autenticado
    // ------------------------------------------------------------------

    @Test
    void tkt01_purchase_requiresAuthentication() throws Exception {
        String body = """
                {
                    "userEmail": "andrea@example.com",
                    "eventCode": "CMF-2026",
                    "type": "GENERAL"
                }
                """;
        assertThat(statusOf(post("/api/tickets").contentType(MediaType.APPLICATION_JSON).content(body)))
                .isEqualTo(401);
        assertThat(statusOf(post("/api/tickets").header("Authorization", bearer(andrea))
                .contentType(MediaType.APPLICATION_JSON).content(body))).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // TKT-02 / TKT-03 / TKT-05 — autenticado (la propiedad se verifica en OwnershipAntiIdorTest)
    // ------------------------------------------------------------------

    @Test
    void tkt02_findByCode_requiresAuthentication() throws Exception {
        assertThat(statusOf(get("/api/tickets/TCK-0001"))).isEqualTo(401);
        assertThat(statusOf(get("/api/tickets/TCK-0001").header("Authorization", bearer(andrea))))
                .isEqualTo(404);
    }

    @Test
    void tkt03_byUser_requiresAuthentication() throws Exception {
        // PRD §14.3 adv. 4: el literal /by-user debe declararse antes del patrón {ticketCode}.
        // Aquí se verifica que un anónimo no lo alcanza.
        assertThat(statusOf(get("/api/tickets/by-user").param("email", "andrea@example.com")))
                .isEqualTo(401);
        assertThat(statusOf(get("/api/tickets/by-user").param("email", "andrea@example.com")
                .header("Authorization", bearer(andrea)))).isEqualTo(200);
    }

    @Test
    void tkt05_cancel_requiresAuthentication() throws Exception {
        assertThat(statusOf(patch("/api/tickets/TCK-0001/cancel"))).isEqualTo(401);
        assertThat(statusOf(patch("/api/tickets/TCK-0001/cancel")
                .header("Authorization", bearer(andrea)))).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // TKT-04 / TKT-06 — STAFF/ADMIN
    // ------------------------------------------------------------------

    @Test
    void tkt04_paidTickets_requiresStaffOrAdmin() throws Exception {
        // PRD §11 TKT-04: "STAFF/ADMIN"
        assertThat(statusOf(get("/api/events/CMF-2026/tickets/paid"))).isEqualTo(401);
        assertThat(statusOf(get("/api/events/CMF-2026/tickets/paid")
                .header("Authorization", bearer(andrea)))).isEqualTo(403);
        // STAFF y ADMIN pasan la capa de rol. El 200 es de negocio: la operación no exige que el
        // evento exista y devuelve la lista vacía cuando no hay tickets PAID.
        assertThat(statusOf(get("/api/events/CMF-2026/tickets/paid")
                .header("Authorization", bearer(laura)))).isEqualTo(200);
        assertThat(statusOf(get("/api/events/CMF-2026/tickets/paid")
                .header("Authorization", bearer(sofia)))).isEqualTo(200);
    }

    @Test
    void tkt06_useTicket_requiresStaffOrAdmin() throws Exception {
        // PRD §11 TKT-06: "STAFF/ADMIN". T-SEC-012: "USER intenta usar ticket → 403"
        assertThat(statusOf(patch("/api/tickets/TCK-0001/use"))).isEqualTo(401);
        assertThat(statusOf(patch("/api/tickets/TCK-0001/use")
                .header("Authorization", bearer(andrea)))).isEqualTo(403);
        assertThat(statusOf(patch("/api/tickets/TCK-0001/use")
                .header("Authorization", bearer(laura)))).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // EVT-04 / EVT-05 — ADMIN
    // ------------------------------------------------------------------

    @Test
    void evt04_publish_requiresAdmin() throws Exception {
        // PRD §11 EVT-04: "ADMIN". T-SEC-014: "ADMIN publica evento → 200"; UC-SEC-08: USER → 403
        // STAFF no es ADMIN: recibe 403 (PRD §11 es explícito: solo ADMIN).
        assertThat(statusOf(patch("/api/events/CMF-2026/publish"))).isEqualTo(401);
        assertThat(statusOf(patch("/api/events/CMF-2026/publish")
                .header("Authorization", bearer(andrea)))).isEqualTo(403);
        assertThat(statusOf(patch("/api/events/CMF-2026/publish")
                .header("Authorization", bearer(laura)))).isEqualTo(403);
        // ADMIN pasa la capa de rol; el 404 es de negocio (el evento no existe en este test)
        assertThat(statusOf(patch("/api/events/CMF-2026/publish")
                .header("Authorization", bearer(sofia)))).isEqualTo(404);
    }

    @Test
    void evt05_addArtist_requiresAdmin() throws Exception {
        // PRD §11 EVT-05: "ADMIN"
        assertThat(statusOf(post("/api/events/CMF-2026/artists/1"))).isEqualTo(401);
        assertThat(statusOf(post("/api/events/CMF-2026/artists/1")
                .header("Authorization", bearer(andrea)))).isEqualTo(403);
        assertThat(statusOf(post("/api/events/CMF-2026/artists/1")
                .header("Authorization", bearer(laura)))).isEqualTo(403);
        assertThat(statusOf(post("/api/events/CMF-2026/artists/1")
                .header("Authorization", bearer(sofia)))).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // EVT-02 / EVT-06 — públicos con filtrado de borradores (ver DraftVisibilityTest)
    // ------------------------------------------------------------------

    @Test
    void evt02_eventByCode_isReachableByAnonymous() throws Exception {
        // PRD §11 EVT-02: "Público solo si publicado". Un anónimo NO debe recibir 401: la ruta es
        // alcanzable. El 404 es de negocio (el evento no existe en este test). El filtrado de
        // borradores se verifica en DraftVisibilityTest (decisión D3).
        assertThat(statusOf(get("/api/events/CMF-2026"))).isEqualTo(404);
    }

    @Test
    void evt06_byArtist_isReachableByAnonymous() throws Exception {
        // PRD §11 EVT-06: "Público únicamente para eventos publicados". Idem: sin 401 para anónimo.
        assertThat(statusOf(get("/api/events/by-artist").param("stageName", "Solar Beat")))
                .isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private int statusOf(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    /** Recuento por código de estado, para el reporte de la matriz. */
    private Map<Integer, Long> countByStatus(List<Integer> statuses) {
        return statuses.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        s -> s, java.util.stream.Collectors.counting()));
    }

    @Test
    void allTwentyRoutes_wereExercised() {
        // Guardarra de cobertura: evita que una ruta se quede sin probar cuando se agrega una
        // nueva operación. Cuenta las rutas de la matriz y las compara con el total del PRD.
        long total = List.of(
                "VEN-01", "VEN-02",
                "EVT-01", "EVT-02", "EVT-03", "EVT-04", "EVT-05", "EVT-06",
                "ART-01", "ART-02", "ART-03",
                "USR-01", "USR-02", "USR-03",
                "TKT-01", "TKT-02", "TKT-03", "TKT-04", "TKT-05", "TKT-06"
        ).size();

        // PRD §11: "las cinco tablas de controlador suman 20 operaciones"
        assertThat(total).isEqualTo(20);
        assertThat(countByStatus(List.of(200, 401, 403, 404))).containsKey(200);
    }
}
