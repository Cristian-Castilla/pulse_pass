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

@WebMvcTest(VenueController.class)
class VenueControllerSliceTest extends ControllerSliceSupport {
    private final VenueResponse venue = new VenueResponse(1L,"VEN","Venue","Santa Marta","Street",3L,true);
    @Test void existingVenue() throws Exception {
        when(venues.findByCode("VEN")).thenReturn(venue);
        mvc.perform(get("/api/venues/VEN")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("VEN"));
        verify(venues).findByCode("VEN");
    }
    @Test void activeVenues() throws Exception {
        when(venues.findActiveVenues()).thenReturn(List.of(venue));
        mvc.perform(get("/api/venues/active")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].capacity").value(3));
        verify(venues).findActiveVenues();
    }
    @Test void missingVenueIs404() throws Exception {
        when(venues.findByCode("none")).thenThrow(new ResourceNotFoundException("Venue not found: none"));
        mvc.perform(get("/api/venues/none")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.details").isEmpty());
        verify(venues).findByCode("none");
    }
    @Test void unexpectedServiceFailureIsReally500() throws Exception {
        when(venues.findByCode("bad")).thenThrow(new IllegalStateException("private"));
        mvc.perform(get("/api/venues/bad")).andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
        verify(venues).findByCode("bad");
    }
}
