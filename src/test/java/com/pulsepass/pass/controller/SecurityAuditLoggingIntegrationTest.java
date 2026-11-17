package com.pulsepass.pass.controller;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class SecurityAuditLoggingIntegrationTest extends ControllerHttpSupport {
    @Autowired private JwtService jwt;

    @Test void successfulAndFailedLoginHaveRequestIdsWithoutCredentials(CapturedOutput output) throws Exception {
        persistUser("audit-user", "audit@example.com", Role.USER);
        var success = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"audit-user\",\"password\":\""+RAW_PASSWORD+"\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var failure = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"audit-unknown\",\"password\":\"Wrong-login-test!\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(output.getAll()).contains("auth_success", "auth_failure",
                "requestId="+success.getHeader("X-Request-ID"),
                "requestId="+failure.getHeader("X-Request-ID"));
        assertThat(output.getAll()).doesNotContain("audit-user", "audit-unknown", "audit@example.com",
                RAW_PASSWORD, "Wrong-login-test!", "accessToken", "passwordHash");
    }

    @Test void denialsFromFilterChainAndOwnershipAdviceShareSafeCorrelation(CapturedOutput output) throws Exception {
        var caller = persistUser("audit-caller", "caller-audit@example.com", Role.USER);
        persistUser("audit-owner", "owner-audit@example.com", Role.USER);
        String token = jwt.issueToken(caller.getId()).accessToken();
        var chain = mockMvc.perform(post("/api/events").header("Authorization", "Bearer "+token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andReturn().getResponse();
        var advice = mockMvc.perform(get("/api/users/by-email").header("Authorization", "Bearer "+token)
                        .param("email", "owner-audit@example.com"))
                .andExpect(status().isForbidden()).andReturn().getResponse();
        assertThat(output.getAll()).contains("security_denied status=403",
                "requestId="+chain.getHeader("X-Request-ID"), "requestId="+advice.getHeader("X-Request-ID"));
        assertThat(output.getAll()).doesNotContain(token, "owner-audit@example.com", "caller-audit@example.com", RAW_PASSWORD);
    }
}
