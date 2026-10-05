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
class UserProfileIT extends PostgresContainerSupport {

    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;

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

        // Verificar que el perfil fue persistido
        assertThat(userProfileRepository.findAll()).hasSize(1);
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
