package com.pulsepass.pass.controller;

import com.pulsepass.pass.dto.request.RegisterUserRequest;
import com.pulsepass.pass.dto.response.UserResponse;
import com.pulsepass.pass.exception.DuplicateResourceException;
import com.pulsepass.pass.exception.GlobalExceptionHandler;
import com.pulsepass.pass.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void register_validRequest_returns201() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest("andrea", "andrea@example.com",
                "Andrea", "Gomez", "3001234567", "Santa Marta", LocalDate.of(1990, 1, 1));
        UserResponse response = new UserResponse(1L, "andrea", "andrea@example.com", true);
        when(userService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "andrea",
                                    "email": "andrea@example.com",
                                    "firstName": "Andrea",
                                    "lastName": "Gomez",
                                    "phone": "3001234567",
                                    "city": "Santa Marta",
                                    "birthDate": "1990-01-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("andrea"));

        verify(userService).register(any());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "andrea",
                                    "email": "invalid-email",
                                    "firstName": "Andrea",
                                    "lastName": "Gomez",
                                    "phone": "3001234567",
                                    "city": "Santa Marta",
                                    "birthDate": "1990-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.email").value("Email must be valid"));

        verify(userService, never()).register(any());
    }

    @Test
    void register_duplicateUsername_returns409() throws Exception {
        when(userService.register(any()))
                .thenThrow(new DuplicateResourceException("Username already exists"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "username": "andrea",
                                    "email": "andrea2@example.com",
                                    "firstName": "Andrea",
                                    "lastName": "Gomez",
                                    "phone": "3001234567",
                                    "city": "Santa Marta",
                                    "birthDate": "1990-01-01"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Username already exists"));

        verify(userService).register(any());
    }

    @Test
    void findByEmail_existingUser_returns200() throws Exception {
        UserResponse response = new UserResponse(1L, "andrea", "andrea@example.com", true);
        when(userService.findByEmail("andrea@example.com")).thenReturn(response);

        mockMvc.perform(get("/api/users/by-email").param("email", "andrea@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("andrea@example.com"));

        verify(userService).findByEmail("andrea@example.com");
    }

    @Test
    void findByUsername_existingUser_returns200() throws Exception {
        UserResponse response = new UserResponse(1L, "andrea", "andrea@example.com", true);
        when(userService.findByUsername("andrea")).thenReturn(response);

        mockMvc.perform(get("/api/users/by-username").param("username", "andrea"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("andrea"));

        verify(userService).findByUsername("andrea");
    }
}
