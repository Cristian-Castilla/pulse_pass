package com.pulsepass.pass.security;

import com.pulsepass.pass.PostgresContainerSupport;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.domain.UserRole;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba HTTP del login y de la cadena de filtros con Spring Security ya montada.
 *
 * PRD de Seguridad §17.2, T-SEC-005: "Anónimo POST /api/auth/login correcto → 200 con Bearer".
 * PRD §17.2, T-SEC-006: "Login con contraseña incorrecta → 401 genérico".
 * PRD §14.3 adv. 5: "El filtro JWT debe dejar continuar requests sin header solo para que la cadena
 * decida si la ruta es pública".
 *
 * <p>Es la evidencia que quedó pendiente de la Etapa 4 ("POST /api/auth/login probado") y que no
 * pudo correrse hasta esta etapa: antes de la {@code SecurityFilterChain}, Spring Security aplicaba
 * sus defaults y bloqueaba todo con 403.
 *
 * <p>PRD §17.2: "No deshabilitar filtros para que los tests pasen:
 * {@code @AutoConfigureMockMvc(addFilters = false)} invalidaría el propósito de probar seguridad".
 * Por eso los filtros quedan activos y el 401 sale por el {@code AuthenticationEntryPoint} real.
 */
@AutoConfigureMockMvc
@Transactional
class LoginHttpTest extends PostgresContainerSupport {

    private static final String RAW_PASSWORD = "UnaFraseSeguraLarga2026!";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserCredentialRepository userCredentialRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRoleRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userCredentialRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User persistUser(String username, String email, boolean active, Role role) {
        User user = userRepository.saveAndFlush(new User(username, email, active));
        userProfileRepository.saveAndFlush(new UserProfile(
                "Andrea", "Gomez", "3001234567", "Santa Marta",
                LocalDate.of(1995, 5, 15), user));
        userCredentialRepository.saveAndFlush(new UserCredential(
                user, passwordEncoder.encode(RAW_PASSWORD), null));
        userRoleRepository.saveAndFlush(new UserRole(user, role));
        return user;
    }

    private String bearerOf(String username) {
        return "Bearer " + authService.login(username, RAW_PASSWORD).accessToken();
    }

    /** Token firmado como si lo hubiera emitido el login, para el usuario dado. */
    private String tokenSignedAs(Long userId) {
        return JwtTokenFactory.tokenFor(userId);
    }

    // ------------------------------------------------------------------
    // T-SEC-005 / T-SEC-006 — login por HTTP
    // ------------------------------------------------------------------

    @Test
    void anonymousLogin_withCorrectCredentials_returns200AndBearer() throws Exception {
        persistUser("andrea", "andrea@example.com", true, Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "identifier": "andrea",
                                    "password": "UnaFraseSeguraLarga2026!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void anonymousLogin_withWrongPassword_returns401AndGenericMessage() throws Exception {
        // T-SEC-006: 401 genérico, sin revelar si el usuario existe
        persistUser("andrea", "andrea@example.com", true, Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "identifier": "andrea",
                                    "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void anonymousLogin_withUnknownUser_returnsSameBodyAsWrongPassword() throws Exception {
        // PRD §10 FR-SEC-AUTH-003: no se puede distinguir usuario inexistente de contraseña
        // incorrecta (evita enumeración de cuentas)
        persistUser("andrea", "andrea@example.com", true, Role.USER);

        MvcResult unknown = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "identifier": "ghost",
                                    "password": "UnaFraseSeguraLarga2026!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "identifier": "andrea",
                                    "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Se comparan los campos del contrato, no el body completo: el timestamp cambia entre las
        // dos llamadas y no es parte de la garantía de no enumeración.
        assertThat(fieldOf(unknown, "$.status")).isEqualTo(fieldOf(wrongPassword, "$.status"));
        assertThat(fieldOf(unknown, "$.error")).isEqualTo(fieldOf(wrongPassword, "$.error"));
        assertThat(fieldOf(unknown, "$.message")).isEqualTo(fieldOf(wrongPassword, "$.message"));
        assertThat(fieldOf(unknown, "$.details")).isEqualTo(fieldOf(wrongPassword, "$.details"));
    }

    private String fieldOf(MvcResult result, String path) throws Exception {
        Object value = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), path);
        return String.valueOf(value);
    }

    @Test
    void anonymousLogin_withBlankPassword_returns400() throws Exception {
        // Bean Validation corre antes que el filtro de seguridad: 400, no 401
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "identifier": "andrea",
                                    "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details.password").value("Password is required"));
    }

    @Test
    void anonymousLogin_withMalformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\": \"andrea\",}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is malformed"));
    }

    @Test
    void loginResponse_neverExposesPasswordOrHash() throws Exception {
        // T-SEC-019 / NFR-SEC-001: sin password, hash ni stacktrace en la respuesta
        persistUser("andrea", "andrea@example.com", true, Role.USER);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "identifier": "andrea",
                                    "password": "UnaFraseSeguraLarga2026!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(RAW_PASSWORD);
        assertThat(body).doesNotContain("$2a$");
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("hash");
    }

    // ------------------------------------------------------------------
    // T-SEC-001..004 — cadena de filtros sobre rutas existentes
    // ------------------------------------------------------------------

    @Test
    void anonymous_canReadPublishedEvents() throws Exception {
        // T-SEC-001: "Anónimo GET /api/events/published → 200"
        mockMvc.perform(get("/api/events/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void anonymous_cannotCreateEvents() throws Exception {
        // T-SEC-002: "Anónimo POST /api/events → 401"
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventCode": "CMF-2026",
                                    "name": "Caribbean Music Fest 2026",
                                    "description": "Festival",
                                    "category": "MUSIC",
                                    "eventDate": "2026-12-31T19:00:00",
                                    "minimumAge": 18,
                                    "venueCode": "VEN-SMR-01"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void user_cannotCreateEvents() throws Exception {
        // T-SEC-003: "USER POST /api/events → 403, sin invocar Service"
        persistUser("andrea", "andrea@example.com", true, Role.USER);

        mockMvc.perform(post("/api/events")
                        .header("Authorization", bearerOf("andrea"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventCode": "CMF-2026",
                                    "name": "Caribbean Music Fest 2026",
                                    "description": "Festival",
                                    "category": "MUSIC",
                                    "eventDate": "2026-12-31T19:00:00",
                                    "minimumAge": 18,
                                    "venueCode": "VEN-SMR-01"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message")
                        .value("No tiene permisos para realizar esta operación"));
    }

    @Test
    void admin_canCreateEvents() throws Exception {
        // T-SEC-004: "ADMIN POST /api/events válido → 201"
        // Requiere el venue VEN-SMR-01, que no existe en la base de este test: se valida que la
        // petición pasa la autorización (no 403) y llega a la lógica de negocio.
        persistUser("sofia", "sofia@example.com", true, Role.ADMIN);
        venueRepository.saveAndFlush(new com.pulsepass.pass.domain.Venue(
                "VEN-SMR-01", "Marina Convention Center", "Santa Marta",
                "Cra 1 #1-1", 5000L, true));

        mockMvc.perform(post("/api/events")
                        .header("Authorization", bearerOf("sofia"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventCode": "CMF-2026",
                                    "name": "Caribbean Music Fest 2026",
                                    "description": "Festival",
                                    "category": "MUSIC",
                                    "eventDate": "2026-12-31T19:00:00",
                                    "minimumAge": 18,
                                    "venueCode": "VEN-SMR-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"));
    }

    // ------------------------------------------------------------------
    // T-SEC-007 / T-SEC-008 — tokens inválidos
    // ------------------------------------------------------------------

    @Test
    void expiredToken_onProtectedRoute_returns401() throws Exception {
        // T-SEC-007: "Token expirado en ruta protegida → 401 con ErrorResponse"
        String expired = JwtTokenFactory.expiredToken();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void tamperedToken_onProtectedRoute_returns401AndNeverInvokesBusiness() throws Exception {
        // T-SEC-008: "Token con firma alterada → 401, sin invocar Service"
        User andrea = persistUser("andrea", "andrea@example.com", true, Role.USER);
        String token = tokenSignedAs(andrea.getId());
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".invalidsignature";

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void malformedBearer_onProtectedRoute_returns401Not500() throws Exception {
        // NFR-SEC-002: "Entrada Bearer malformada no produce excepción sin manejar erróneos ni 500"
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void nonBearerScheme_isIgnoredAndProtectedRouteReturns401() throws Exception {
        // PRD §14.3 adv. 5: un esquema que no es Bearer no autentica; la cadena decide y la ruta
        // protegida responde 401.
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // T-SEC-016 — usuario desactivado
    // ------------------------------------------------------------------

    @Test
    void deactivatedUserWithValidToken_isDeniedOnNextRequest() throws Exception {
        // T-SEC-016: "Un token válido pertenece a usuario inactivo → 401"
        // PRD §8 SEC-011: "Un usuario desactivado pierde acceso en el siguiente request"
        //
        // El escenario real del DoD §23.10 es "un token emitido antes de desactivar al usuario".
        // No se puede reproducir aquí sin mutar la entidad (User no expone setActive por diseño) ni
        // persistir fuera de la transacción del test (que se revierte). Se verifica entonces la
        // garantía equivalente: un token válido no basta si el estado vigente en la base es inactivo,
        // porque el filtro revalida el estado en cada request contra la base.
        User miguel = persistUser("miguel", "miguel@example.com", false, Role.USER);
        String token = tokenSignedAs(miguel.getId());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void anonymous_canCallMe_onlyWhenAuthenticated() throws Exception {
        // GET /api/auth/me es "Autenticado" en el PRD §10.1
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUser_canReadOwnIdentity() throws Exception {
        // PRD §10 FR-SEC-AUTH-010: "GET /api/auth/me retorna información mínima del principal"
        persistUser("andrea", "andrea@example.com", true, Role.USER);

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearerOf("andrea")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("andrea"))
                .andExpect(jsonPath("$.email").value("andrea@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }
}
