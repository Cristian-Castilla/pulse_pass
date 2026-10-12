package com.pulsepass.pass.controller;

import com.pulsepass.pass.dto.response.VenueResponse;
import com.pulsepass.pass.exception.GlobalExceptionHandler;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.service.VenueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VenueController.class)
@Import(GlobalExceptionHandler.class)
class VenueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueService venueService;

    @Test
    void findByCode_existingVenue_returns200() throws Exception {
        VenueResponse response = new VenueResponse(1L, "VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Cra 1 #10-20", 5000L, true);
        when(venueService.findByCode("VEN-SMR-01")).thenReturn(response);

        mockMvc.perform(get("/api/venues/{code}", "VEN-SMR-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VEN-SMR-01"))
                .andExpect(jsonPath("$.name").value("Marina Convention Center"));

        verify(venueService).findByCode("VEN-SMR-01");
    }

    @Test
    void findByCode_nonExisting_returns404() throws Exception {
        when(venueService.findByCode("XXX-999"))
                .thenThrow(new ResourceNotFoundException("Venue not found: XXX-999"));

        mockMvc.perform(get("/api/venues/{code}", "XXX-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Venue not found: XXX-999"))
                .andExpect(jsonPath("$.details").isEmpty());

        verify(venueService).findByCode("XXX-999");
    }

    @Test
    void findActiveVenues_returns200() throws Exception {
        List<VenueResponse> venues = List.of(
                new VenueResponse(1L, "VEN-SMR-01", "Marina Convention Center",
                        "Santa Marta", "Cra 1 #10-20", 5000L, true),
                new VenueResponse(2L, "VEN-SMR-02", "Convention Center",
                        "Santa Marta", "Cra 2 #20-30", 3000L, true)
        );
        when(venueService.findActiveVenues()).thenReturn(venues);

        mockMvc.perform(get("/api/venues/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("VEN-SMR-01"))
                .andExpect(jsonPath("$[1].code").value("VEN-SMR-02"));

        verify(venueService).findActiveVenues();
    }

    @Test
    void findByCode_unexpectedException_returns500() throws Exception {
        when(venueService.findByCode("ERR-500"))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get("/api/venues/{code}", "ERR-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

        verify(venueService).findByCode("ERR-500");
    }
}
