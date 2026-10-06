package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.dto.response.VenueResponse;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.VenueMapper;
import com.pulsepass.pass.repository.VenueRepository;
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
class VenueServiceImplTest {

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private VenueMapper venueMapper;

    @InjectMocks
    private VenueServiceImpl venueService;

    @Test
    void findByCode_retornaDTO() {
        // ARRANGE
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1 # 1-01", 5000L, true);
        VenueResponse expectedResponse = new VenueResponse(
                1L, "VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1 # 1-01", 5000L, true);

        when(venueRepository.findByCode("VEN-SMR-01")).thenReturn(Optional.of(venue));
        when(venueMapper.toResponse(venue)).thenReturn(expectedResponse);

        // ACT
        VenueResponse result = venueService.findByCode("VEN-SMR-01");

        // ASSERT
        assertThat(result).isEqualTo(expectedResponse);
        verify(venueRepository).findByCode("VEN-SMR-01");
        verify(venueMapper).toResponse(venue);
    }

    @Test
    void findByCode_lanzaException() {
        // ARRANGE
        when(venueRepository.findByCode("NO-EXISTE")).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(ResourceNotFoundException.class,
                () -> venueService.findByCode("NO-EXISTE"));

        verify(venueRepository).findByCode("NO-EXISTE");
        verify(venueMapper, never()).toResponse(any());
    }

    @Test
    void findActiveVenues_retornaSoloActivos() {
        // ARRANGE
        Venue venue1 = new Venue("VEN-01", "Venue 1", "Ciudad", "Dir 1", 100L, true);
        Venue venue2 = new Venue("VEN-02", "Venue 2", "Ciudad", "Dir 2", 200L, true);
        VenueResponse response1 = new VenueResponse(1L, "VEN-01", "Venue 1", "Ciudad", "Dir 1", 100L, true);
        VenueResponse response2 = new VenueResponse(2L, "VEN-02", "Venue 2", "Ciudad", "Dir 2", 200L, true);

        when(venueRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(venue1, venue2));
        when(venueMapper.toResponse(venue1)).thenReturn(response1);
        when(venueMapper.toResponse(venue2)).thenReturn(response2);

        // ACT
        List<VenueResponse> result = venueService.findActiveVenues();

        // ASSERT
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(response1, response2);
        verify(venueRepository).findByActiveTrueOrderByNameAsc();
    }
}
