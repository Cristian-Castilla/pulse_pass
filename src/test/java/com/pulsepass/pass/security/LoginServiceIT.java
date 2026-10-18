package com.pulsepass.pass.security;

import com.pulsepass.pass.PostgresContainerSupport;
import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.dto.response.AuthResponse;
import com.pulsepass.pass.exception.AuthenticationException;
import com.pulsepass.pass.repository.UserCredentialRepository;
import com.pulsepass.pass.repository.UserProfileRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.UserRoleRepository;
import com.pulsepass.pass.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Login de punta a punta contra servicios reales y PostgreSQL, con el {@code JwtService} real.
 *
 * PRD de Seguridad §23, escenario 2: "Andrea inicia sesión y recibe JWT con expiresIn = 900".
 * PRD §10 FR-SEC-AUTH-002: "Autentica usuario/correo + contraseña; 200 con token y metadatos".
 *
 * <p>Es la evidencia de entrega de la Etapa 4 ("POST /api/auth/login probado") que no pudo correrse
 * hasta que la Etapa 5 proveyó el {@code JwtService} real.
 *
 * <p><b>Sin MockMvc a propósito.</b> El filtro de seguridad todavía no existe (Etapa 6), así que
 * Spring Security aplica sus defaults y bloquearía la ruta de login con 403. Probar el login a
 * nivel de servicio verifica la cadena completa que sí existe hoy — UserDetailsService,
 * PasswordEncoder y JwtService — y deja la verificación HTTP para la Etapa 6, cuando la
 * {@code SecurityFilterChain} declare {@code /api/auth/login} como público.
 */
@Transactional
class LoginServiceIT extends PostgresContainerSupport {

    private static final String RAW_PASSWORD = "UnaFraseSeguraLarga2026!";

    @Autowired private AuthService authService;
    @Autowired private JwtServiceImpl jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private UserCredentialRepository userCredentialRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRoleRepository.deleteAllInBatch();
        userProfileRepository.deleteAllInBatch();
        userCredentialRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User persistUser(String username, String email, boolean active) {
        User user = userRepository.saveAndFlush(new User(username, email, active));
        userProfileRepository.saveAndFlush(new UserProfile(
                "Andrea", "Gomez", "3001234567", "Santa Marta",
                LocalDate.of(1995, 5, 15), user));
        userCredentialRepository.saveAndFlush(new UserCredential(
                user, passwordEncoder.encode(RAW_PASSWORD), null));
        userRoleRepository.saveAndFlush(new UserRole(user, Role.USER));
        return user;
    }

    @Test
    void login_withUsername_returnsRealSignedJwt() {
        User andrea = persistUser("andrea", "andrea@example.com", true);

        AuthResponse response = authService.login("andrea", RAW_PASSWORD);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.accessToken()).isNotBlank();

        // El token es un JWT real de tres segmentos, firmado con HS256
        assertThat(response.accessToken().split("\\.")).hasSize(3);

        // Y es válido: la validación acepta el sujeto que emitió
        assertThat(jwtService.validateAndExtractSubject(response.accessToken()))
                .isEqualTo(andrea.getId());
    }

    @Test
    void login_withEmail_returnsRealSignedJwt() {
        // PRD §10 FR-SEC-AUTH-002: "Autentica usuario/correo + contraseña"
        User andrea = persistUser("andrea", "andrea@example.com", true);

        AuthResponse byExactEmail = authService.login("andrea@example.com", RAW_PASSWORD);
        assertThat(jwtService.validateAndExtractSubject(byExactEmail.accessToken()))
                .isEqualTo(andrea.getId());

        // El email se resuelve ignore-case
        AuthResponse byUpperCaseEmail = authService.login("ANDREA@EXAMPLE.COM", RAW_PASSWORD);
        assertThat(jwtService.validateAndExtractSubject(byUpperCaseEmail.accessToken()))
                .isEqualTo(andrea.getId());
    }

    @Test
    void login_withWrongPassword_throwsGenericUnauthorized() {
        // PRD §10 FR-SEC-AUTH-003: "Retorna 401 genérico, no informa si el usuario existe"
        persistUser("andrea", "andrea@example.com", true);

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authService.login("andrea", "wrong-password"));

        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void login_withUnknownUser_throwsSameMessageAsWrongPassword() {
        persistUser("andrea", "andrea@example.com", true);

        AuthenticationException unknown = assertThrows(AuthenticationException.class,
                () -> authService.login("ghost", RAW_PASSWORD));
        AuthenticationException wrongPassword = assertThrows(AuthenticationException.class,
                () -> authService.login("andrea", "wrong-password"));

        // El mensaje es idéntico: no se puede distinguir un usuario inexistente de una contraseña
        // incorrecta (PRD §10 FR-SEC-AUTH-003)
        assertThat(unknown.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(unknown.getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void login_withInactiveUser_throwsUnauthorized() {
        // PRD §10 FR-SEC-AUTH-004: "No obtiene token ... si se verifica estado en cada solicitud"
        persistUser("miguel", "miguel@example.com", false);

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authService.login("miguel", RAW_PASSWORD));

        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void login_neverIssuesTokenForRejectedCredentials() {
        // PRD §10 FR-SEC-AUTH-006: "JWT inválido o vencido no concede acceso"
        persistUser("andrea", "andrea@example.com", true);

        assertThrows(AuthenticationException.class,
                () -> authService.login("andrea", "wrong-password"));

        // Un token emitido antes del fallo sigue siendo válido (no se revoca nada por un intento
        // fallido), pero el intento fallido no debe haber emitido ninguno
        assertThat(authService.login("andrea", RAW_PASSWORD).accessToken()).isNotBlank();
    }

    @Test
    void issuedToken_carriesExpectedClaims() {
        User andrea = persistUser("andrea", "andrea@example.com", true);

        String token = authService.login("andrea", RAW_PASSWORD).accessToken();

        io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        io.jsonwebtoken.io.Decoders.BASE64.decode(
                                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVm")))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // PRD §12.1: sub, iss, iat, exp, jti
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(andrea.getId()));
        assertThat(claims.getIssuer()).isEqualTo("pulsepass-api");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getId()).isNotBlank();
        assertThat(jwtService.expectedAlgorithm()).isEqualTo("HS256");
    }

    @Test
    void tamperedToken_isRejectedByValidation() {
        // PRD §12.2: "verificar criptográficamente firma antes de confiar en claims"
        persistUser("andrea", "andrea@example.com", true);
        String token = authService.login("andrea", RAW_PASSWORD).accessToken();
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".invalidsignature";

        assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject(tampered));
    }

    @Test
    void currentUser_returnsIdentityWithoutSecrets() {
        // PRD §10 FR-SEC-AUTH-010: "GET /api/auth/me retorna información mínima del principal"
        //
        // El principal lo pone el filtro de seguridad (Etapa 6). Aquí se simula con el contexto, que
        // es lo que haría el filtro con un Bearer válido.
        User andrea = persistUser("andrea", "andrea@example.com", true);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new SecurityPrincipal(andrea.getId(), "andrea", true), null));

        var response = authService.currentUser();

        assertThat(response.username()).isEqualTo("andrea");
        assertThat(response.email()).isEqualTo("andrea@example.com");
        assertThat(response.roles()).containsExactly("ROLE_USER");
        assertThat(response.toString()).doesNotContain("password");
        assertThat(response.toString()).doesNotContain("hash");
    }

    @Test
    void currentUser_withoutPrincipal_throwsUnauthorized() {
        // PRD §8 SEC-006: no se puede confiar en una identidad que no está en el contexto
        persistUser("andrea", "andrea@example.com", true);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        assertThrows(AuthenticationException.class, () -> authService.currentUser());
    }
}
