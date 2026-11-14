package com.pulsepass.pass.security;

import com.pulsepass.pass.domain.*;
import com.pulsepass.pass.repository.*;
import com.pulsepass.pass.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthenticationLookupTest {
    private final UserCredentialRepository credentials = mock(UserCredentialRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final CustomUserDetailsService users = new CustomUserDetailsService(credentials, roles);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private User user(boolean active) {
        User user = new User("lookup-user", "lookup@example.com", active);
        ReflectionTestUtils.setField(user, "id", 42L);
        when(jwt.validateAndExtractSubject("valid")).thenReturn(42L);
        when(credentials.findById(42L)).thenReturn(Optional.of(new UserCredential(user, "test-hash", null)));
        return user;
    }

    private void run() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    }

    @Test
    void bearerLoadsIdentityOnceAndCurrentRolesOnce() throws Exception {
        User user = user(true);
        when(roles.findByUserUsername("lookup-user"))
                .thenReturn(List.of(new UserRole(user, Role.USER), new UserRole(user, Role.STAFF)));
        run();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_STAFF");
        verify(credentials, times(1)).findById(42L);
        verify(roles, times(1)).findByUserUsername("lookup-user");
        verifyNoMoreInteractions(credentials, roles);
    }

    @Test
    void inactiveIdentityDoesNotLoadAnyRoles() throws Exception {
        user(false);
        run();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(credentials).findById(42L);
        verifyNoInteractions(roles);
    }
}
