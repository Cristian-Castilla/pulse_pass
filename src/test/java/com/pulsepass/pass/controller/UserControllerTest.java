package com.pulsepass.pass.controller;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * USR-01..03 — registro público y búsquedas por identidad.
 *
 * PRD de Seguridad §11:
 *   USR-01 POST /api/users              → "Público (rol inicial exclusivamente USER)"
 *   USR-02 GET  /api/users/by-email     → "Propio usuario, o ADMIN con propósito autorizado"
 *   USR-03 GET  /api/users/by-username  → "Propio usuario, o ADMIN con propósito autorizado"
 *
 * <p>Migrado de {@code @WebMvcTest} a HTTP real. USR-01 es público y se prueba sin token; USR-02
 * y USR-03 son "Autenticado" y se prueban con el Bearer del propio usuario.
 *
 * <p><b>La capa de propiedad está implementada</b> en AuthorizationService y se verifica en
 * OwnershipAntiIdorTest. Aquí se prueba la capa de autenticación (401 sin token, 200 con token).
 */
class UserControllerTest extends ControllerHttpSupport {

    @Autowired private AuthService authService;

    private String bearer(String username) {
        return "Bearer " + authService.login(username, RAW_PASSWORD).accessToken();
    }

    // ------------------------------------------------------------------
    // USR-01 — registro público
    // ------------------------------------------------------------------

    @Test
    void register_validRequest_returns201() throws Exception {
        // PRD §10 FR-SEC-AUTH-001: "Crea User + credencial BCrypt + ROLE_USER de forma atómica; 201"
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "nuevo",
                                    "email": "nuevo@example.com",
                                    "firstName": "Nuevo",
                                    "lastName": "Usuario",
                                    "phone": "3001234567",
                                    "city": "Santa Marta",
                                    "birthDate": "1995-05-15",
                                    "password": "UnaFraseSeguraLarga2026!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("nuevo"))
                .andExpect(jsonPath("$.email").value("nuevo@example.com"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void register_neverReturnsPasswordOrHash() throws Exception {
        // PRD §3.4: "UserResponse — Se mantiene, sin hash, contraseña ni secretos"
        String body = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "secreto",
                                    "email": "secreto@example.com",
                                    "firstName": "Secreto",
                                    "lastName": "Usuario",
                                    "birthDate": "1995-05-15",
                                    "password": "UnaFraseSeguraLarga2026!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("$2a$");
        assertThat(body).doesNotContain("hash");
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        // T-SEC-018: "Request inválido pero autorizado → 400 previo de Bean Validation".
        // El registro es público: no hay token que valide, así que el 400 sale directo.
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "andrea",
                                    "email": "invalid-email",
                                    "firstName": "Andrea",
                                    "lastName": "Gomez",
                                    "birthDate": "1990-01-01",
                                    "password": "UnaFraseSeguraLarga2026!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.email").value("Email must be valid"));
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        // El usuario ya existe en la base: el servicio real lanza DuplicateResourceException
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "andrea",
                                    "email": "otro@example.com",
                                    "firstName": "Andrea",
                                    "lastName": "Gomez",
                                    "birthDate": "1990-01-01",
                                    "password": "UnaFraseSeguraLarga2026!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Username andrea already exists. Please, choose another username."));
    }

    // ------------------------------------------------------------------
    // USR-02 / USR-03 — autenticado
    // ------------------------------------------------------------------

    @Test
    void findByEmail_anonymousGets401() throws Exception {
        // PRD §11 USR-02: no es pública. Un anónimo recibe 401, no 404.
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(get("/api/users/by-email").param("email", "andrea@example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void findByEmail_authenticatedUser_returns200() throws Exception {
        // PRD §11 USR-02: "Propio usuario, o ADMIN". Con la identidad propia la ruta responde.
        // El contraste de propiedad (que el email sea el del principal) se verifica en
        // OwnershipAntiIdorTest.
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(get("/api/users/by-email").param("email", "andrea@example.com")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("andrea"))
                .andExpect(jsonPath("$.email").value("andrea@example.com"));
    }

    @Test
    void findByUsername_anonymousGets401() throws Exception {
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(get("/api/users/by-username").param("username", "andrea"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findByUsername_authenticatedUser_returns200() throws Exception {
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(get("/api/users/by-username").param("username", "andrea")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("andrea"));
    }

    @Test
    void findByUsername_unknownUser_returns404() throws Exception {
        // La autenticación pasa y el 404 es de negocio (el usuario no existe en la base)
        persistUser("andrea", "andrea@example.com", Role.USER);

        mockMvc.perform(get("/api/users/by-username").param("username", "ghost")
                        .header("Authorization", bearer("andrea")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Username doesn't exist: ghost"));
    }
}
