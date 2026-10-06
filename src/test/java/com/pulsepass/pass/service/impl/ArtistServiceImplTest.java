package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Artist;
import com.pulsepass.pass.dto.response.ArtistResponse;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.ArtistMapper;
import com.pulsepass.pass.repository.ArtistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistServiceImplTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private ArtistMapper artistMapper;

    @InjectMocks
    private ArtistServiceImpl artistService;

    @Test
    void findById_retornaDTO() {
        // ARRANGE
        Artist artist = new Artist("Solar Beat", "Colombia", "Electrónica", true);
        ArtistResponse expectedResponse = new ArtistResponse(
                1L, "Solar Beat", "Colombia", "Electrónica", true);

        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(artistMapper.toResponse(artist)).thenReturn(expectedResponse);

        // ACT
        ArtistResponse result = artistService.findById(1L);

        // ASSERT
        assertThat(result).isEqualTo(expectedResponse);
        verify(artistRepository).findById(1L);
        verify(artistMapper).toResponse(artist);
    }

    @Test
    void findById_lanzaException() {
        // ARRANGE
        when(artistRepository.findById(999L)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(ResourceNotFoundException.class,
                () -> artistService.findById(999L));

        verify(artistRepository).findById(999L);
        verify(artistMapper, never()).toResponse(any());
    }

    @Test
    void findByStageName_retornaDTO() {
        // ARRANGE
        Artist artist = new Artist("Neon Waves", "México", "Synthpop", true);
        ArtistResponse expectedResponse = new ArtistResponse(
                2L, "Neon Waves", "México", "Synthpop", true);

        when(artistRepository.findByStageName("Neon Waves")).thenReturn(Optional.of(artist));
        when(artistMapper.toResponse(artist)).thenReturn(expectedResponse);

        // ACT
        ArtistResponse result = artistService.findByStageName("Neon Waves");

        // ASSERT
        assertThat(result).isEqualTo(expectedResponse);
        verify(artistRepository).findByStageName("Neon Waves");
    }

    @Test
    void findActiveArtists_retornaSoloActivos() {
        // ARRANGE
        Artist artist1 = new Artist("Solar Beat", "Colombia", "Electrónica", true);
        Artist artist2 = new Artist("Ocean Drive", "Colombia", "Indie", true);
        ArtistResponse response1 = new ArtistResponse(1L, "Solar Beat", "Colombia", "Electrónica", true);
        ArtistResponse response2 = new ArtistResponse(2L, "Ocean Drive", "Colombia", "Indie", true);

        when(artistRepository.findByActiveTrueOrderByStageNameAsc())
                .thenReturn(List.of(artist1, artist2));
        when(artistMapper.toResponse(artist1)).thenReturn(response1);
        when(artistMapper.toResponse(artist2)).thenReturn(response2);

        // ACT
        List<ArtistResponse> result = artistService.findActiveArtists();

        // ASSERT
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(response1, response2);
        verify(artistRepository).findByActiveTrueOrderByStageNameAsc();
    }
}
