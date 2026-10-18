package com.pulsepass.pass.security;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.repository.UserCredentialRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.UserRoleRepository;
import com.pulsepass.pass.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del UserDetailsService de la Fase 4.
 *
 * PRD de Seguridad §17.1: "CustomUserDetailsServiceTest: usuario existente, inexistente, roles
 * actualizados, estado inactivo".
 * PRD §12.3: "consultar roles actuales en persistencia. De este modo, un token no conserva
 * privilegios después de una revocación de rol".
 */
@Transactional
class CustomUserDetailsServiceIT extends PostgresContainerSupport {

    @Autowired private CustomUserDetailsService userDetailsService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserCredentialRepository userCredentialRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRoleRepository.deleteAllInBatch();
        userCredentialRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    private User persistUser(String username, String email, boolean active) {
        return userRepository.saveAndFlush(new User(username, email, active));
    }

    private UserCredential persistCredential(User user, String rawPassword) {
        return userCredentialRepository.saveAndFlush(
                new UserCredential(user, passwordEncoder.encode(rawPassword), null));
    }

    @Test
    void shouldLoadExistingUserByUsername() {
        User user = persistUser("andrea", "andrea@example.com", true);
        persistCredential(user, "UnaFraseSeguraLarga2026!");
        userRoleRepository.saveAndFlush(new UserRole(user, Role.USER));

        UserDetails details = userDetailsService.loadUserByUsername("andrea");

        assertThat(details.getUsername()).isEqualTo("andrea");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getPassword()).startsWith("$2a$");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldLoadExistingUserByEmail() {
        // PRD §10 FR-SEC-AUTH-002: "Autentica usuario/correo + contraseña"
        User user = persistUser("carlos", "carlos@example.com", true);
        persistCredential(user, "OtraFraseSeguraLarga2026!");
        userRoleRepository.saveAndFlush(new UserRole(user, Role.ADMIN));

        UserDetails byExactEmail = userDetailsService.loadUserByUsername("carlos@example.com");
        assertThat(byExactEmail.getUsername()).isEqualTo("carlos");

        // El email se resuelve ignore-case
        UserDetails byUpperCaseEmail = userDetailsService.loadUserByUsername("CARLOS@EXAMPLE.COM");
        assertThat(byUpperCaseEmail.getUsername()).isEqualTo("carlos");
    }

    @Test
    void shouldThrowGenericMessageWhenUserDoesNotExist() {
        // PRD §10 FR-SEC-AUTH-003: "Retorna 401 genérico, no informa si el usuario existe"
        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("ghost"));

        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void shouldExposeAllRolesAsAuthorities() {
        User user = persistUser("laura", "laura@example.com", true);
        persistCredential(user, "FraseSeguraLarga2026!x");
        userRoleRepository.saveAndFlush(new UserRole(user, Role.USER));
        userRoleRepository.saveAndFlush(new UserRole(user, Role.STAFF));

        UserDetails details = userDetailsService.loadUserByUsername("laura");

        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_STAFF");
    }

    @Test
    void shouldReflectRoleChangesImmediately() {
        // PRD §12.3: los roles se leen de la base en cada request, no del token
        User user = persistUser("miguel", "miguel@example.com", true);
        persistCredential(user, "FraseSeguraLarga2026!y");
        UserRole staff = userRoleRepository.saveAndFlush(new UserRole(user, Role.STAFF));

        assertThat(userDetailsService.loadUserByUsername("miguel").getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STAFF");

        // Se retira el rol: el cambio debe reflejarse sin recargar el contexto
        userRoleRepository.delete(staff);
        userRoleRepository.flush();

        assertThat(userDetailsService.loadUserByUsername("miguel").getAuthorities()).isEmpty();
    }

    @Test
    void shouldMarkInactiveUserAsDisabled() {
        // PRD §7.1 SEC-011: "Un usuario desactivado pierde acceso en el siguiente request"
        User user = persistUser("diana", "diana@example.com", false);
        persistCredential(user, "FraseSeguraLarga2026!z");
        userRoleRepository.saveAndFlush(new UserRole(user, Role.USER));

        UserDetails details = userDetailsService.loadUserByUsername("diana");

        assertThat(details.isEnabled()).isFalse();
        // Los roles se siguen exponiendo: es el estado activo el que niega el acceso
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldResolveUserIdFromIdentifier() {
        User user = persistUser("pablo", "pablo@example.com", true);
        persistCredential(user, "FraseSeguraLarga2026!w");

        assertThat(userDetailsService.resolveUserId("pablo")).isEqualTo(user.getId());
        assertThat(userDetailsService.resolveUserId("PABLO@EXAMPLE.COM")).isEqualTo(user.getId());
        assertThat(userDetailsService.resolveUserId("ghost")).isNull();
    }

    @Test
    void shouldCheckRoleFromPersistence() {
        User user = persistUser("sofia", "sofia@example.com", true);
        persistCredential(user, "FraseSeguraLarga2026!v");
        userRoleRepository.saveAndFlush(new UserRole(user, Role.ADMIN));

        assertThat(userDetailsService.hasRole("sofia", Role.ADMIN)).isTrue();
        assertThat(userDetailsService.hasRole("sofia", Role.USER)).isFalse();
    }

    @Test
    void shouldEncodePasswordWithBcrypt() {
        // PRD §8 SEC-003: "Contraseñas BCrypt, nunca en texto plano"
        String raw = "UnaFraseSeguraLarga2026!";
        String hash = passwordEncoder.encode(raw);

        assertThat(hash).startsWith("$2a$");
        assertThat(hash).isNotEqualTo(raw);
        assertThat(passwordEncoder.matches(raw, hash)).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", hash)).isFalse();
    }
}
