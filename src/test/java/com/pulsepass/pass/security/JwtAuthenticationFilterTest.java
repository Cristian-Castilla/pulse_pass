package com.pulsepass.pass.security;

import com.pulsepass.pass.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final JwtService jwt = mock(JwtService.class);
    private final CustomUserDetailsService users = mock(CustomUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void validIdentity() {
        when(jwt.validateAndExtractSubject("valid")).thenReturn(42L);
        when(users.loadAuthorities("test-user")).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(users.loadPrincipalById(42L)).thenReturn(new SecurityPrincipal(42L, "test-user", true));
    }

    private void runFilter() throws Exception {
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200); // Route authorization chooses 401, not this filter.
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsDuplicateAuthorizationHeadersInEitherOrder(boolean validFirst) throws Exception {
        validIdentity();
        request.addHeader("Authorization", validFirst ? "Bearer valid" : "Bearer invalid");
        request.addHeader("Authorization", validFirst ? "Bearer invalid" : "Bearer valid");
        runFilter();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwt, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic valid", "Bearer", "Bearer ", "Bearer valid, Bearer invalid", "Bearer valid invalid", "Bearer\tvalid"})
    void rejectsInvalidSchemeOrAmbiguousToken(String value) throws Exception {
        request.addHeader("Authorization", value);
        runFilter();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwt, users);
    }

    @Test
    void ignoresQueryCookieAndSessionWithoutAuthorizationHeader() throws Exception {
        request.setParameter("access_token", "valid");
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", "valid"),
                new jakarta.servlet.http.Cookie("JSESSIONID", "valid"));
        request.getSession().setAttribute("token", "valid");
        runFilter();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwt, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer valid", "bearer valid", "BEARER valid"})
    void authenticatesSingleBearerWithPersistedIdentity(String header) throws Exception {
        validIdentity();
        request.addHeader("Authorization", header);
        runFilter();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(new SecurityPrincipal(42L, "test-user", true));
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void keepsAnExistingTrustedAuthenticationWithoutReadingBearer() throws Exception {
        var existing = new UsernamePasswordAuthenticationToken("existing", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.addHeader("Authorization", "Bearer valid");
        request.addHeader("Authorization", "Bearer invalid");
        runFilter();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verifyNoInteractions(jwt, users);
    }

    @Test
    void invalidTokenContinuesAnonymously() throws Exception {
        request.addHeader("Authorization", "Bearer invalid");
        when(jwt.validateAndExtractSubject("invalid")).thenThrow(new IllegalArgumentException("invalid"));
        runFilter();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(users);
    }

    @Test
    void inactiveOrMissingIdentityContinuesAnonymously() throws Exception {
        request.addHeader("Authorization", "Bearer valid");
        when(jwt.validateAndExtractSubject("valid")).thenReturn(42L);
        when(users.loadPrincipalById(42L)).thenThrow(new UsernameNotFoundException("Invalid credentials"));
        runFilter();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
