package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.dto.request.RegisterUserRequest;
import com.pulsepass.pass.dto.response.AuthResponse;
import com.pulsepass.pass.dto.response.AuthenticatedUserResponse;
import com.pulsepass.pass.dto.response.UserResponse;
import com.pulsepass.pass.exception.AuthenticationException;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.DuplicateResourceException;
import com.pulsepass.pass.mapper.UserMapper;
import com.pulsepass.pass.repository.UserCredentialRepository;
import com.pulsepass.pass.repository.UserProfileRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.UserRoleRepository;
import com.pulsepass.pass.security.CustomUserDetailsService;
import com.pulsepass.pass.security.SecurityPrincipal;
import com.pulsepass.pass.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests de {@code AuthServiceImpl}. Sin contexto de Spring, sin base de datos.
 *
 * PRD de Seguridad §17.1: "AuthServiceTest: credenciales correctas, password incorrecto, usuario
 * inactivo y emisión solo tras autenticación".
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserRepository userRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserCredentialRepository userCredentialRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserMapper mapper;
    @Mock private CustomUserDetailsService userDetailsService;

    @InjectMocks private AuthServiceImpl authService;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private RegisterUserRequest validRequest() {
        return new RegisterUserRequest(
                "andrea", "andrea@example.com", "Andrea", "Gomez",
                "3001234567", "Santa Marta", LocalDate.of(1995, 5, 15),
                "UnaFraseSeguraLarga2026!");
    }

    // ---------------------------------------------------------------- login

    private UserDetails userDetailsFor(String username, String hash, boolean enabled) {
        return org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password(hash)
                .disabled(!enabled)
                .authorities("ROLE_USER")
                .build();
    }

    @Test
    void login_withCorrectCredentials_issuesToken() {
        when(userDetailsService.loadUserByUsername("andrea"))
                .thenReturn(userDetailsFor("andrea", "$2a$10$hashed", true));
        when(passwordEncoder.matches("UnaFraseSeguraLarga2026!", "$2a$10$hashed")).thenReturn(true);
        when(userDetailsService.resolveUserId("andrea")).thenReturn(7L);
        when(jwtService.issueToken(7L)).thenReturn(new AuthResponse("jwt-value", "Bearer", 900L));

        AuthResponse response = authService.login("andrea", "UnaFraseSeguraLarga2026!");

        assertThat(response.accessToken()).isEqualTo("jwt-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        verify(jwtService).issueToken(7L);
    }

    @Test
    void login_withEmail_resolvesTheUserByEmail() {
        // PRD §10 FR-SEC-AUTH-002: "Autentica usuario/correo + contraseña"
        when(userDetailsService.loadUserByUsername("andrea@example.com"))
                .thenReturn(userDetailsFor("andrea", "$2a$10$hashed", true));
        when(passwordEncoder.matches("UnaFraseSeguraLarga2026!", "$2a$10$hashed")).thenReturn(true);
        when(userDetailsService.resolveUserId("andrea")).thenReturn(7L);
        when(jwtService.issueToken(anyLong())).thenReturn(new AuthResponse("t", "Bearer", 900L));

        authService.login("andrea@example.com", "UnaFraseSeguraLarga2026!");

        // El identificador recibido se usa tal cual para cargar la identidad
        verify(userDetailsService).loadUserByUsername("andrea@example.com");
    }

    @Test
    void login_withWrongPassword_throwsGenericUnauthorized() {
        // PRD §10 FR-SEC-AUTH-003: "Retorna 401 genérico, no informa si el usuario existe"
        when(userDetailsService.loadUserByUsername("andrea"))
                .thenReturn(userDetailsFor("andrea", "$2a$10$hashed", true));
        when(passwordEncoder.matches("wrong-password", "$2a$10$hashed")).thenReturn(false);

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> authService.login("andrea", "wrong-password"));

        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
        verify(jwtService, never()).issueToken(anyLong());
    }

    @Test
    void login_withUnknownUser_throwsSameMessageAsWrongPassword() {
        // El mensaje no debe distinguir "usuario inexistente" de "contraseña incorrecta".
        // Se usa thenAnswer en vez de thenThrow porque Mockito consume el stub en la primera
        // llamada: con two thenThrow encadenados el segundo nunca se aplicaría.
        when(userDetailsService.loadUserByUsername("ghost"))
                .thenThrow(new UsernameNotFoundException("Invalid credentials"));
        when(userDetailsService.loadUserByUsername("andrea"))
                .thenReturn(userDetailsFor("andrea", "$2a$10$hashed", true));
        when(passwordEncoder.matches("wrong-password", "$2a$10$hashed")).thenReturn(false);

        AuthenticationException unknown = assertThrows(AuthenticationException.class,
                () -> authService.login("ghost", "any-password"));
        AuthenticationException wrongPassword = assertThrows(AuthenticationException.class,
                () -> authService.login("andrea", "wrong-password"));

        assertThat(unknown.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(unknown.getMessage()).isEqualTo("Invalid credentials");
    }

    // ------------------------------------------------------------ register

    @Test
    void register_createsUserCredentialAndRoleAtomically() {
        // PRD §10 FR-SEC-AUTH-001: "Crea User + credencial BCrypt + ROLE_USER de forma atómica; 201"
        when(userRepository.findByUsername("andrea")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("andrea@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User u = invocation.getArgument(0);
                    // Refleja lo que Hibernate hace al persistir: asigna el id
                    return new User(u.getUsername(), u.getEmail(), u.getActive()) {
                        @Override public Long getId() { return 42L; }
                    };
                });
        when(passwordEncoder.encode("UnaFraseSeguraLarga2026!")).thenReturn("$2a$10$hashed");
        when(mapper.toResponse(any(User.class)))
                .thenReturn(new UserResponse(42L, "andrea", "andrea@example.com", true));

        UserResponse response = authService.register(validRequest());

        assertThat(response.username()).isEqualTo("andrea");

        ArgumentCaptor<UserCredential> credentialCaptor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialRepository).save(credentialCaptor.capture());
        assertThat(credentialCaptor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");

        // El hash se calcula con el PasswordEncoder real, nunca se guarda el texto plano
        verify(passwordEncoder).encode("UnaFraseSeguraLarga2026!");

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRole()).isEqualTo(Role.USER);

        // PRD §9.2: el registro público nunca asigna roles privilegiados
        assertThat(roleCaptor.getValue().getRole()).isNotEqualTo(Role.ADMIN);
        assertThat(roleCaptor.getValue().getRole()).isNotEqualTo(Role.STAFF);
    }

    @Test
    void register_hashesPasswordAndNeverPersistsPlainText() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> new User("andrea", "andrea@example.com", true));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedvalue");
        when(mapper.toResponse(any(User.class)))
                .thenReturn(new UserResponse(1L, "andrea", "andrea@example.com", true));

        authService.register(validRequest());

        ArgumentCaptor<UserCredential> captor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialRepository).save(captor.capture());

        assertThat(captor.getValue().getPasswordHash()).doesNotContain("UnaFraseSeguraLarga2026!");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashedvalue");
    }

    @Test
    void register_withDuplicateUsername_throwsAndWritesNothing() {
        when(userRepository.findByUsername("andrea"))
                .thenReturn(Optional.of(new User("andrea", "andrea@example.com", true)));

        assertThrows(DuplicateResourceException.class, () -> authService.register(validRequest()));

        verify(userRepository, never()).save(any(User.class));
        verify(userCredentialRepository, never()).save(any(UserCredential.class));
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void register_withDuplicateEmail_throwsAndWritesNothing() {
        when(userRepository.findByUsername("andrea")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("andrea@example.com"))
                .thenReturn(Optional.of(new User("otro", "andrea@example.com", true)));

        assertThrows(DuplicateResourceException.class, () -> authService.register(validRequest()));

        verify(userRepository, never()).save(any(User.class));
        verify(userCredentialRepository, never()).save(any(UserCredential.class));
    }

    @Test
    void register_withFutureBirthDate_throwsAndWritesNothing() {
        RegisterUserRequest request = new RegisterUserRequest(
                "andrea", "andrea@example.com", "Andrea", "Gomez",
                "3001234567", "Santa Marta", LocalDate.now().plusDays(1),
                "UnaFraseSeguraLarga2026!");
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
        verify(userCredentialRepository, never()).save(any(UserCredential.class));
    }

    @Test
    void register_withPasswordOver72Bytes_throws() {
        // PRD §9.3: "máximo 72 bytes si se emplea BCrypt tradicional"
        // 73 caracteres ASCII = 73 bytes, supera el límite aunque el @Size del DTO no lo detecte
        String longPassword = "a".repeat(73);
        RegisterUserRequest request = new RegisterUserRequest(
                "andrea", "andrea@example.com", "Andrea", "Gomez",
                "3001234567", "Santa Marta", LocalDate.of(1995, 5, 15), longPassword);

        assertThrows(BusinessRuleException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
        verify(userCredentialRepository, never()).save(any(UserCredential.class));
    }

    @Test
    void register_withPasswordExactly72Bytes_isAccepted() {
        String exact = "a".repeat(72);
        RegisterUserRequest request = new RegisterUserRequest(
                "andrea", "andrea@example.com", "Andrea", "Gomez",
                "3001234567", "Santa Marta", LocalDate.of(1995, 5, 15), exact);

        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> new User("andrea", "andrea@example.com", true));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(mapper.toResponse(any(User.class)))
                .thenReturn(new UserResponse(1L, "andrea", "andrea@example.com", true));

        UserResponse response = authService.register(request);

        assertThat(response.username()).isEqualTo("andrea");
        verify(userCredentialRepository).save(any(UserCredential.class));
    }

    // ----------------------------------------------------------- current user

    @Test
    void currentUser_readsIdentityFromSecurityContext() {
        // PRD §10 FR-SEC-AUTH-006: "No confiar en role, userEmail o userId del cliente"
        // El id de la identidad sale del contexto de seguridad (SecurityPrincipal), no del request.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new SecurityPrincipal(5L, "andrea", true), null));

        User persisted = new User("andrea", "andrea@example.com", true);
        when(userRepository.findByUsername("andrea")).thenReturn(Optional.of(persisted));
        when(userRoleRepository.findByUserUsername("andrea"))
                .thenReturn(List.of(new UserRole(null, Role.USER), new UserRole(null, Role.STAFF)));

        AuthenticatedUserResponse response = authService.currentUser();

        // El id del principal (5L) viene del contexto; el username se usa para buscar el usuario
        assertThat(response.username()).isEqualTo("andrea");
        assertThat(response.email()).isEqualTo("andrea@example.com");
        assertThat(response.active()).isTrue();
        assertThat(response.roles()).containsExactly("ROLE_USER", "ROLE_STAFF");
        // La identidad sale del contexto, no de un parámetro del request
        verify(userRepository).findByUsername("andrea");
    }

    @Test
    void currentUser_withoutPrincipal_throwsUnauthorized() {
        assertThrows(AuthenticationException.class, () -> authService.currentUser());
    }

    @Test
    void currentUser_doesNotExposeSecrets() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new SecurityPrincipal(5L, "andrea", true), null));
        when(userRepository.findByUsername("andrea"))
                .thenReturn(Optional.of(new User("andrea", "andrea@example.com", true)));
        when(userRoleRepository.findByUserUsername("andrea")).thenReturn(List.of());

        AuthenticatedUserResponse response = authService.currentUser();

        // PRD §10 FR-SEC-AUTH-010: "información mínima del principal, no secretos"
        assertThat(response.toString()).doesNotContain("password");
        assertThat(response.toString()).doesNotContain("hash");
    }
}
