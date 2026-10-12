package com.pulsepass.pass.controller;

import com.pulsepass.pass.dto.response.ArtistResponse;
import com.pulsepass.pass.exception.GlobalExceptionHandler;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.service.ArtistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArtistController.class)
@Import(GlobalExceptionHandler.class)
class ArtistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtistService artistService;

    @Test
    void findById_existingArtist_returns200() throws Exception {
        ArtistResponse response = new ArtistResponse(1L, "Solar Beat", "Colombia", "Electronic", true);
        when(artistService.findById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/artists/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stageName").value("Solar Beat"));

        verify(artistService).findById(1L);
    }

    @Test
    void findById_nonExistingId_returns404() throws Exception {
        when(artistService.findById(999L))
                .thenThrow(new ResourceNotFoundException("Artist not found: 999"));

        mockMvc.perform(get("/api/artists/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Artist not found: 999"));

        verify(artistService).findById(999L);
    }

    @Test
    void findByStageName_existingArtist_returns200() throws Exception {
        ArtistResponse response = new ArtistResponse(1L, "Solar Beat", "Colombia", "Electronic", true);
        when(artistService.findByStageName("Solar Beat")).thenReturn(response);

        mockMvc.perform(get("/api/artists/by-stage-name").param("stageName", "Solar Beat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stageName").value("Solar Beat"));

        verify(artistService).findByStageName("Solar Beat");
    }

    @Test
    void findActiveArtists_returns200() throws Exception {
        List<ArtistResponse> artists = List.of(
                new ArtistResponse(1L, "Solar Beat", "Colombia", "Electronic", true),
                new ArtistResponse(2L, "Neon Waves", "Colombia", "Pop", true)
        );
        when(artistService.findActiveArtists()).thenReturn(artists);

        mockMvc.perform(get("/api/artists/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].stageName").value("Solar Beat"))
                .andExpect(jsonPath("$[1].stageName").value("Neon Waves"));

        verify(artistService).findActiveArtists();
    }
}
