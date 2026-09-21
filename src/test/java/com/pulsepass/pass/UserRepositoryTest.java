package com.pulsepass.pass;

import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * QT-004 (User 1:1 UserProfile), QT-007 (query methods simples)
 * y QT-009 (restricción UNIQUE en email).
 */

class UserRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void shouldFindUserByUsername() {
        userRepository.save(new User("andrea", "andrea@example.com", true));

        Optional<User> found = userRepository.findByUsername("andrea");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("andrea@example.com");
    }

    @Test
    void shouldPersistOneToOneUserProfileViaCascade() {
        User andrea = new User("andrea", "andrea@example.com", true);
        UserProfile profile = new UserProfile("Andrea", "Gómez", "3001112233",
                "Santa Marta", LocalDate.of(1995, 4, 10), andrea);
        andrea.setProfile(profile);

        userRepository.saveAndFlush(andrea);

        User reloaded = userRepository.findByUsername("andrea").orElseThrow();
        assertThat(reloaded.getProfile()).isNotNull();
        assertThat(reloaded.getProfile().getFirstName()).isEqualTo("Andrea");
        assertThat(reloaded.getProfile().getUser().getUsername()).isEqualTo("andrea");
    }

    @Test
    void shouldReturnEmptyWhenUsernameDoesNotExist() {
        assertThat(userRepository.findByUsername("no-existe")).isEmpty();
    }

    @Test
    void shouldFindUserByEmailIgnoringCase() {
        userRepository.save(new User("carlos", "carlos@example.com", true));

        assertThat(userRepository.findByEmailIgnoreCase("CARLOS@EXAMPLE.COM")).isPresent();
    }

    @Test
    void shouldEnforceUniqueEmailConstraint() {
        userRepository.save(new User("andrea", "andrea@example.com", true));
        userRepository.flush();

        User duplicate = new User("otro-usuario", "andrea@example.com", true);

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(duplicate));
    }
}
