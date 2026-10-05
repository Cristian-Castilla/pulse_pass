package com.pulsepass.pass;

import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica query methods y restricciones de User.
 *
 * PRD: "username UNIQUE", "username NOT NULL", "email UNIQUE", "email NOT NULL"
 * Query Methods:
 * - findByUsername
 * - findByEmailIgnoreCase
 * - existsByUsername
 * - existsByEmail
 */
class UserRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAllInBatch();
    }

    @Test
    void shouldSaveAndFindUserByUsername() {
        User user = new User("andrea", "andrea@example.com", true);
        userRepository.saveAndFlush(user);

        assertThat(userRepository.findByUsername("andrea")).isPresent();
    }

    @Test
    void shouldReturnEmptyWhenUsernameDoesNotExist() {
        assertThat(userRepository.findByUsername("no-existe")).isEmpty();
    }

    @Test
    void shouldFindUserByEmailIgnoringCase() {
        userRepository.saveAndFlush(new User("carlos", "carlos@example.com", true));

        assertThat(userRepository.findByEmailIgnoreCase("CARLOS@EXAMPLE.COM")).isPresent();
    }

    @Test
    void shouldReturnFalseWhenUsernameDoesNotExist() {
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    @Test
    void shouldReturnTrueWhenUsernameExists() {
        userRepository.saveAndFlush(new User("dave", "dave@example.com", true));

        assertThat(userRepository.existsByUsername("dave")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenEmailDoesNotExist() {
        assertThat(userRepository.existsByEmail("ghost@example.com")).isFalse();
    }

    @Test
    void shouldReturnTrueWhenEmailExists() {
        userRepository.saveAndFlush(new User("eve", "eve@example.com", true));

        assertThat(userRepository.existsByEmail("eve@example.com")).isTrue();
    }

    @Test
    void shouldEnforceUniqueEmailConstraintOnSaveAndFlush() {
        userRepository.saveAndFlush(new User("andrea", "andrea@example.com", true));

        User duplicate = new User("otro-usuario", "andrea@example.com", true);

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldEnforceUniqueUsernameConstraintOnSaveAndFlush() {
        userRepository.saveAndFlush(new User("andrea", "andrea@example.com", true));

        User duplicate = new User("andrea", "otro-email@example.com", true);

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(duplicate));
    }
}
