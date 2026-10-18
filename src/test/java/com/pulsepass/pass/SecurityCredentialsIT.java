package com.pulsepass.pass;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.repository.UserCredentialRepository;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica las entidades de seguridad de la Fase 4 contra PostgreSQL real.
 *
 * PRD de Seguridad §9.1:
 * - "UserCredential (NUEVA, 1:1 con User) ├── user_id (PK y FK unique)
 *    ├── password_hash (NOT NULL) └── password_changed_at (opcional)"
 * - "UserRole (NUEVA, N:1 con User) ├── user_id (FK) └── role (USER | STAFF | ADMIN)
 *    PK(user_id, role)"
 *
 * PRD §9.2: "Los registros legados sin credenciales quedarán sin posibilidad de login".
 * Eso se prueba en shouldAllowUserWithoutCredential: un User puede existir sin credencial.
 *
 * PRD §17.3: integración selectiva con PostgreSQL/Testcontainers.
 */
@Transactional
class SecurityCredentialsIT extends PostgresContainerSupport {

    @Autowired private UserRepository userRepository;
    @Autowired private UserCredentialRepository userCredentialRepository;
    @Autowired private UserRoleRepository userRoleRepository;

    @BeforeEach
    void cleanDatabase() {
        userRoleRepository.deleteAllInBatch();
        userCredentialRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void shouldPersistCredentialWithSharedUserId() {
        // user_id es PK y FK a la vez (@MapsId): el id de la credencial ES el del usuario
        User user = userRepository.saveAndFlush(new User("andrea", "andrea@example.com", true));
        assertThat(user.getId()).isNotNull();

        UserCredential credential = userCredentialRepository.saveAndFlush(
                new UserCredential(user, "$2a$10$fixedhashvalue00000000000000000000000000000000000000", null));

        assertThat(credential.getId()).isEqualTo(user.getId());

        UserCredential reloaded = userCredentialRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getPasswordHash()).startsWith("$2a$10$");
        assertThat(reloaded.getUser().getUsername()).isEqualTo("andrea");
    }

    @Test
    void shouldEnforceOneToOneOnCredentials() {
        // Un segundo intento de credencial para el mismo usuario debe fallar por PK
        User user = userRepository.saveAndFlush(new User("carlos", "carlos@example.com", true));
        userCredentialRepository.saveAndFlush(
                new UserCredential(user, "$2a$10$firsthash000000000000000000000000000000000000000000", null));

        UserCredential duplicate = new UserCredential(
                user, "$2a$10$secondhash000000000000000000000000000000000000000000", null);

        assertThrows(DataIntegrityViolationException.class,
                () -> userCredentialRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldEnforcePasswordHashNotNull() {
        User user = userRepository.saveAndFlush(new User("laura", "laura@example.com", true));

        assertThrows(DataIntegrityViolationException.class,
                () -> userCredentialRepository.saveAndFlush(new UserCredential(user, null, null)));
    }

    @Test
    void shouldAssignMultipleRolesToSameUser() {
        User user = userRepository.saveAndFlush(new User("miguel", "miguel@example.com", true));

        userRoleRepository.saveAndFlush(new UserRole(user, Role.USER));
        userRoleRepository.saveAndFlush(new UserRole(user, Role.STAFF));

        List<UserRole> roles = userRoleRepository.findByUserUsername("miguel");
        assertThat(roles).hasSize(2);
        assertThat(roles).extracting(UserRole::getRole)
                .containsExactlyInAnyOrder(Role.USER, Role.STAFF);
    }

    @Test
    void shouldPreventDuplicateRoleForUser() {
        // uk_user_role UNIQUE(user_id, role) — el PRD pide PK(user_id, role)
        User user = userRepository.saveAndFlush(new User("diana", "diana@example.com", true));
        userRoleRepository.saveAndFlush(new UserRole(user, Role.ADMIN));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRoleRepository.saveAndFlush(new UserRole(user, Role.ADMIN)));
    }

    @Test
    void shouldRejectRoleOutsideCatalogue() {
        // chk_user_role CHECK (role IN ('USER','STAFF','ADMIN'))
        User user = userRepository.saveAndFlush(new User("pablo", "pablo@example.com", true));

        UserRole invalid = new UserRole(user, null);
        assertThrows(DataIntegrityViolationException.class,
                () -> userRoleRepository.saveAndFlush(invalid));
    }

    @Test
    void shouldFindCredentialByUsernameAndByEmail() {
        // El login se resuelve por username o por email (PRD §10 FR-SEC-AUTH-002)
        User user = userRepository.saveAndFlush(new User("andrea", "andrea@example.com", true));
        userCredentialRepository.saveAndFlush(
                new UserCredential(user, "$2a$10$somehash0000000000000000000000000000000000000000000000", null));

        assertThat(userCredentialRepository.findByUserUsername("andrea")).isPresent();
        assertThat(userCredentialRepository.findByUserEmailIgnoreCase("ANDREA@EXAMPLE.COM")).isPresent();
    }

    @Test
    void shouldAllowUserWithoutCredential() {
        // PRD §9.2: los registros legados sin credenciales quedan sin login, no se borran
        User legacy = userRepository.saveAndFlush(new User("legacy", "legacy@example.com", true));

        assertThat(userRepository.findById(legacy.getId())).isPresent();
        assertThat(userCredentialRepository.findById(legacy.getId())).isEmpty();
        assertThat(userCredentialRepository.existsByUserUsername("legacy")).isFalse();
    }
}
