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

@WebMvcTest(ArtistController.class)
class ArtistControllerSliceTest extends ControllerSliceSupport {
    private final ArtistResponse artist = new ArtistResponse(1L,"Solar Beat","Colombia","Electronic",true);
    @Test void lookupById() throws Exception {
        when(artists.findById(1L)).thenReturn(artist);
        mvc.perform(get("/api/artists/1")).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.stageName").value("Solar Beat"));
        verify(artists).findById(1L);
    }
    @Test void lookupByStageName() throws Exception {
        when(artists.findByStageName("Solar Beat")).thenReturn(artist);
        mvc.perform(get("/api/artists/by-stage-name").param("stageName","Solar Beat"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        verify(artists).findByStageName("Solar Beat");
    }
    @Test void activeArtists() throws Exception {
        when(artists.findActiveArtists()).thenReturn(List.of(artist));
        mvc.perform(get("/api/artists/active")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].active").value(true));
        verify(artists).findActiveArtists();
    }
    @Test void invalidIdentifierNeverInvokesService() throws Exception {
        mvc.perform(get("/api/artists/not-a-number")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.id").exists());
        verifyNoInteractions(artists);
    }
    @Test void missingQueryNeverInvokesService() throws Exception {
        mvc.perform(get("/api/artists/by-stage-name")).andExpect(status().isBadRequest());
        verifyNoInteractions(artists);
    }
}
