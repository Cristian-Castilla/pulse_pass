package com.pulsepass.pass.controller;

import com.pulsepass.pass.dto.response.*;
import com.pulsepass.pass.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerSliceTest extends ControllerSliceSupport {
    private final UserResponse user = new UserResponse(7L,"andrea","andrea@example.com",true);
    private final String body = """
        {"username":"andrea","email":"andrea@example.com","firstName":"Andrea","lastName":"Gomez","birthDate":"1995-01-01","password":"OnlyForTests-2026!"}
        """;
    @Test void registerUsesAuthOrchestrator() throws Exception {
        when(auth.register(any())).thenReturn(user);
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.username").value("andrea")).andExpect(jsonPath("$.password").doesNotExist());
        verify(auth).register(argThat(r -> r.username().equals("andrea")));
        verifyNoInteractions(users);
    }
    @Test void invalidRegistrationNeverCallsServices() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("andrea@example.com","not-an-email")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.email").exists());
        verifyNoInteractions(auth,users);
    }
    @Test void emailLookup() throws Exception {
        when(users.findByEmail("andrea@example.com")).thenReturn(user);
        mvc.perform(get("/api/users/by-email").with(actor("USER")).param("email","andrea@example.com"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7));
        verify(users).findByEmail("andrea@example.com");
    }
    @Test void usernameLookup() throws Exception {
        when(users.findByUsername("andrea")).thenReturn(user);
        mvc.perform(get("/api/users/by-username").with(actor("USER")).param("username","andrea"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("andrea@example.com"));
        verify(users).findByUsername("andrea");
    }
}
