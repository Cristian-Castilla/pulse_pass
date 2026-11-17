package com.pulsepass.pass;

import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.repository.UserProfileRepository;
import com.pulsepass.pass.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifica la relación 1:1 entre User y UserProfile.
 *
 * PRD: "User 1:1 UserProfile", "user_id FK UNIQUE NOT NULL"
 * "el user_id debe ser FK, UNIQUE, NOT NULL para garantizar que un
 * usuario tenga como máximo un perfil"
 */
@Transactional
class UserProfileTest extends PostgresContainerSupport {

    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.saveAndFlush(
                new User("testuser", "test@example.com", true));
    }

    @Test
    void shouldCreateOneToOneUserProfile() {
        // User 1:1 UserProfile — el perfil se persiste y se asocia al usuario
        UserProfile profile = new UserProfile(
                "Test", "User", "123456789", "Bogotá",
                LocalDate.of(1990, 1, 1), savedUser);
        userProfileRepository.saveAndFlush(profile);

        entityManager.clear();
        User reloaded = userRepository.findById(savedUser.getId()).orElseThrow();
        UserProfile persisted = reloaded.getProfile();
        assertThat(persisted.getId()).isEqualTo(profile.getId());
        assertThat(persisted.getFirstName()).isEqualTo("Test");
        assertThat(persisted.getLastName()).isEqualTo("User");
        assertThat(persisted.getPhone()).isEqualTo("123456789");
        assertThat(persisted.getCity()).isEqualTo("Bogotá");
        assertThat(persisted.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(persisted.getUser().getId()).isEqualTo(reloaded.getId());
        assertThat(persisted.getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void shouldPreventTwoProfilesForSameUser() {
        // user_id UNIQUE — no se puede tener dos perfiles para el mismo usuario
        UserProfile profile1 = new UserProfile(
                "Profile", "One", "111", "City",
                LocalDate.of(1990, 1, 1), savedUser);
        userProfileRepository.saveAndFlush(profile1);

        UserProfile profile2 = new UserProfile(
                "Profile", "Two", "222", "City",
                LocalDate.of(1990, 1, 1), savedUser);

        assertThrows(DataIntegrityViolationException.class,
                () -> userProfileRepository.saveAndFlush(profile2));
    }
}
