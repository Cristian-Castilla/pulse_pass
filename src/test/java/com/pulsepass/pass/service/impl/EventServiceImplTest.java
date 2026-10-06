package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Event;
import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.Venue;
import com.pulsepass.pass.dto.request.CreateEventRequest;
import com.pulsepass.pass.dto.response.EventResponse;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.DuplicateResourceException;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.mapper.EventMapper;
import com.pulsepass.pass.repository.ArtistRepository;
import com.pulsepass.pass.repository.EventRepository;
import com.pulsepass.pass.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private EventServiceImpl eventService;

    @Test
    void findByCode_retornaDTO() {
        // ARRANGE
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026",
                "Festival", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), null, null, venue);
        EventResponse expectedResponse = new EventResponse(
                1L, "CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), 0, "VEN-SMR-01",
                "Marina Convention Center", null);

        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.of(event));
        when(eventMapper.toResponse(event)).thenReturn(expectedResponse);

        // ACT
        EventResponse result = eventService.findByCode("CMF-2026");

        // ASSERT
        assertThat(result).isEqualTo(expectedResponse);
        verify(eventRepository).findByEventCode("CMF-2026");
        verify(eventMapper).toResponse(event);
    }

    @Test
    void findByCode_lanzaException() {
        // ARRANGE
        when(eventRepository.findByEventCode("NO-EXISTE")).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(ResourceNotFoundException.class,
                () -> eventService.findByCode("NO-EXISTE"));

        verify(eventRepository).findByEventCode("NO-EXISTE");
        verify(eventMapper, never()).toResponse(any());
    }

    @Test
    void create_eventoValido() {
        // ARRANGE
        CreateEventRequest request = new CreateEventRequest(
                "CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, LocalDate.now().plusMonths(3), 0, "VEN-SMR-01");

        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusMonths(3), 0, null, venue);
        EventResponse expectedResponse = new EventResponse(
                1L, "CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusMonths(3), 0, "VEN-SMR-01",
                "Marina Convention Center", null);

        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.empty());
        when(venueRepository.findByCode("VEN-SMR-01")).thenReturn(Optional.of(venue));
        when(eventRepository.save(any(Event.class))).thenReturn(event);
        when(eventMapper.toResponse(event)).thenReturn(expectedResponse);

        // ACT
        EventResponse result = eventService.create(request);

        // ASSERT
        assertThat(result).isEqualTo(expectedResponse);
        verify(eventRepository).findByEventCode("CMF-2026");
        verify(venueRepository).findByCode("VEN-SMR-01");
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void create_venueInexistente() {
        // ARRANGE
        CreateEventRequest request = new CreateEventRequest(
                "CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, LocalDate.now().plusMonths(3), 0, "NO-EXISTE");

        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.empty());
        when(venueRepository.findByCode("NO-EXISTE")).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(ResourceNotFoundException.class,
                () -> eventService.create(request));

        verify(eventRepository).findByEventCode("CMF-2026");
        verify(venueRepository).findByCode("NO-EXISTE");
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void create_venueInactivo() {
        // ARRANGE
        CreateEventRequest request = new CreateEventRequest(
                "CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, LocalDate.now().plusMonths(3), 0, "VEN-INACTIVO");

        Venue venue = new Venue("VEN-INACTIVO", "Venue Inactivo",
                "Bogotá", "Dir 1", 100L, false);

        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.empty());
        when(venueRepository.findByCode("VEN-INACTIVO")).thenReturn(Optional.of(venue));

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> eventService.create(request));

        verify(eventRepository).findByEventCode("CMF-2026");
        verify(venueRepository).findByCode("VEN-INACTIVO");
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void create_fechaPasada() {
        // ARRANGE
        CreateEventRequest request = new CreateEventRequest(
                "CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, LocalDate.now().minusDays(1), 0, "VEN-SMR-01");

        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);

        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.empty());
        when(venueRepository.findByCode("VEN-SMR-01")).thenReturn(Optional.of(venue));

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> eventService.create(request));

        verify(eventRepository).findByEventCode("CMF-2026");
        verify(venueRepository).findByCode("VEN-SMR-01");
        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    void publish_desdeDRAFT() {
        // ARRANGE
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusMonths(3), null, null, venue);
        EventResponse expectedResponse = new EventResponse(
                1L, "CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusMonths(3), 0, "VEN-SMR-01",
                "Marina Convention Center", null);

        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.of(event));
        when(eventMapper.toResponse(event)).thenReturn(expectedResponse);

        // ACT
        EventResponse result = eventService.publish("CMF-2026");

        // ASSERT
        assertThat(result.status()).isEqualTo(EventStatus.PUBLISHED);
        verify(eventRepository).findByEventCode("CMF-2026");
    }

    @Test
    void publish_desdeCancelled() {
        // ARRANGE
        Venue venue = new Venue("VEN-SMR-01", "Marina Convention Center",
                "Santa Marta", "Carrera 1", 5000L, true);
        Event event = new Event("CMF-2026", "Caribbean Music Fest 2026", "Festival",
                EventCategory.MUSIC, EventStatus.CANCELLED,
                LocalDate.now().plusMonths(3), null, null, venue);

        when(eventRepository.findByEventCode("CMF-2026")).thenReturn(Optional.of(event));

        // ACT & ASSERT
        assertThrows(BusinessRuleException.class,
                () -> eventService.publish("CMF-2026"));

        verify(eventRepository).findByEventCode("CMF-2026");
        verify(eventRepository, never()).save(any(Event.class));
    }
}
