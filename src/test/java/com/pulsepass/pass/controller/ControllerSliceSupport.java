package com.pulsepass.pass.controller;

import com.pulsepass.pass.exception.GlobalExceptionHandler;
import com.pulsepass.pass.security.*;
import com.pulsepass.pass.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

@ActiveProfiles("test")
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
abstract class ControllerSliceSupport {
    @Autowired protected MockMvc mvc;
    @MockitoBean protected JwtService jwtService;
    @MockitoBean protected CustomUserDetailsService userDetailsService;
    @MockitoBean protected VenueService venues;
    @MockitoBean protected ArtistService artists;
    @MockitoBean protected EventService events;
    @MockitoBean protected UserService users;
    @MockitoBean protected TicketService tickets;
    @MockitoBean protected AuthService auth;

    protected RequestPostProcessor actor(String... roles) {
        var authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        return authentication(new UsernamePasswordAuthenticationToken(
                new SecurityPrincipal(7L, "slice-user", true), null, authorities));
    }
}
