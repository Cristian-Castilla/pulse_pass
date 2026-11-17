package com.pulsepass.pass.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({VenueController.class, EventController.class, AuthController.class,
        ArtistController.class, UserController.class, TicketController.class})
class SecurityHttpContractSliceTest extends ControllerSliceSupport {

    @ParameterizedTest
    @ValueSource(strings = {"/api/artists/by-stage-name", "/api/events/by-artist", "/api/users/by-email",
            "/api/users/by-username", "/api/tickets/by-user"})
    void allRequiredQueryFamiliesRejectOmissionsBeforeService(String path) throws Exception {
        mvc.perform(get(path).with(actor("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Required request parameter is missing"))
                .andExpect(jsonPath("$.details").isNotEmpty());
        verifyNoInteractions(artists, events, users, tickets);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"category\":\"INVALID\"}", "{\"eventDate\":\"not-a-date\"}"})
    void malformedJsonEnumAndDateAre400WithoutService(String body) throws Exception {
        mvc.perform(post("/api/events").with(actor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.details").isEmpty());
        verifyNoInteractions(events);
    }

    @Test
    void unsupportedMediaKeeps415WithoutService() throws Exception {
        mvc.perform(post("/api/events").with(actor("ADMIN"))
                        .contentType(MediaType.TEXT_PLAIN).content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
        verifyNoInteractions(events);
    }
    @Test
    void allowedOriginPreflightRunsBeforeAuthentication() throws Exception {
        mvc.perform(options("/api/events")
                        .header("Origin", "https://client.pulsepass.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://client.pulsepass.test"));
        verifyNoInteractions(events);
    }

    @Test
    void unapprovedOriginIsRejected() throws Exception {
        mvc.perform(options("/api/events")
                        .header("Origin", "https://untrusted.invalid")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(events);
    }

    @Test
    void requestIdIsGeneratedForSecurityErrorsAndMdcIsCleaned() throws Exception {
        var response = mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("X-Request-ID", "untrusted-client-value"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.details").isMap())
                .andReturn().getResponse();
        String id = response.getHeader("X-Request-ID");
        assertThat(java.util.UUID.fromString(id).toString()).isEqualTo(id);
        assertThat(id).isNotEqualTo("untrusted-client-value");
        assertThat(MDC.get("requestId")).isNull();
        verifyNoInteractions(events);
    }

    @Test
    void insufficientRoleIs403BeforeValidationOrService() throws Exception {
        mvc.perform(post("/api/events").with(actor("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.details").isEmpty());
        verifyNoInteractions(events);
    }
}
