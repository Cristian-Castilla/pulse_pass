package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.dto.request.RegisterUserRequest;
import com.pulsepass.pass.dto.response.UserResponse;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.DuplicateResourceException;
import com.pulsepass.pass.mapper.UserMapper;
import com.pulsepass.pass.repository.UserProfileRepository;
import com.pulsepass.pass.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void register_usuarioValido() {
        // ARRANGE
        RegisterUserRequest request = new RegisterUserRequest(
                "andrea", "andrea@example.com", "Andrea", "García",
                "3001234567", "Bogotá", LocalDate.of(1995, 5, 15));

        User user = new User("andrea", "andrea@example.com", true);
        UserProfile profile = new UserProfile(
                "Andrea", "García", "3001234567", "Bogotá",
                LocalDate.of(1995, 5, 15), user);
        UserResponse expectedResponse = new UserResponse(
                1L, "andrea", "andrea@example.com", true);

        when(userRepository.findByUsername("andrea")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("andrea@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        // ACT
        UserResponse result = userService.register(request);

        // ASSERT
        assertThat(result).isEqualTo(expectedResponse);
        verify(userRepository).findByUsername("andrea");
        verify(userRepository).findByEmailIgnoreCase("andrea@example.com");
        verify(userRepository).save(any(User.class));
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void register_usernameDuplicado() {
        // ARRANGE
        RegisterUserRequest request = new RegisterUserRequest(
                "andrea", "andrea@example.com", "Andrea", "García",
                "3001234567", "Bogotá", LocalDate.of(1995, 5, 15));

        when(userRepository.findByUsername("andrea")).thenReturn(Optional.of(new User("andrea", "andrea@example.com", true)));

        // ACT & ASSERT
        assertThrows(DuplicateResourceException.class,
                () -> userService.register(request));

        verify(userRepository).findByUsername("andrea");
        verify(userRepository, never()).save(any(User.class));
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void register_emailDuplicado() {
        // ARRANGE
        RegisterUserRequest request = new RegisterUserRequest(
                "andrea", "andrea@example.com", "Andrea", "García",
                "3001234567", "Bogotá", LocalDate.of(1995, 5, 15));

        when(userRepository.findByUsername("andrea")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("andrea@example.com"))
                .thenReturn(Optional.of(new User("andrea", "andrea@example.com", true)));

        // ACT & ASSERT
        assertThrows(DuplicateResourceException.class,
                () -> userService.register(request));

        verify(userRepository).findByUsername("andrea");
        verify(userRepository).findByEmailIgnoreCase("andrea@example.com");
        verify(userRepository, never()).save(any(User.class));
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }

    @Test
    void register_birthDateFuturo() {
        // ARRANGE
        RegisterUserRequest request = new RegisterUserRequest(
                "andrea", "andrea@example.com", "Andrea", "García",
                "3001234567", "Bogotá", LocalDate.now().plusDays(1));

        when(userRepository.findByUsername("andrea")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("andrea@example.com")).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> userService.register(request));

        verify(userRepository).findByUsername("andrea");
        verify(userRepository).findByEmailIgnoreCase("andrea@example.com");
        verify(userRepository, never()).save(any(User.class));
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }
}
