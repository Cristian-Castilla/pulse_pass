package com.pulsepass.pass.controller;

import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import com.pulsepass.pass.domain.TicketStatus;
import com.pulsepass.pass.domain.TicketType;
import com.pulsepass.pass.dto.request.CreateEventRequest;
import com.pulsepass.pass.dto.response.EventResponse;
import com.pulsepass.pass.dto.response.EventSummaryResponse;
import com.pulsepass.pass.dto.response.TicketResponse;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.DuplicateResourceException;
import com.pulsepass.pass.exception.GlobalExceptionHandler;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.service.EventService;
import com.pulsepass.pass.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import(GlobalExceptionHandler.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private TicketService ticketService;

    @Test
    void create_validRequest_returns201() throws Exception {
        CreateEventRequest request = new CreateEventRequest("CMF-2026", "Caribbean Music Fest 2026",
                "Test description", EventCategory.MUSIC, LocalDate.now().plusDays(30), 0, "VEN-SMR-01");
        EventResponse response = new EventResponse(1L, "CMF-2026", "Caribbean Music Fest 2026",
                "Test description", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusDays(30), 0, "VEN-SMR-01", "Marina Convention Center", java.util.Set.of());

        when(eventService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventCode": "CMF-2026",
                                    "name": "Caribbean Music Fest 2026",
                                    "description": "Test description",
                                    "category": "MUSIC",
                                    "eventDate": "2026-12-31",
                                    "minimumAge": 0,
                                    "venueCode": "VEN-SMR-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"))
                .andExpect(jsonPath("$.name").value("Caribbean Music Fest 2026"));

        verify(eventService).create(any());
    }

    @Test
    void create_invalidRequest_returns400() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventCode": "",
                                    "name": "",
                                    "description": "Test description",
                                    "category": "MUSIC",
                                    "eventDate": "2026-12-31",
                                    "minimumAge": -5,
                                    "venueCode": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.eventCode").value("Event code is required"))
                .andExpect(jsonPath("$.details.name").value("Name is required"))
                .andExpect(jsonPath("$.details.venueCode").value("Venue code is required"))
                .andExpect(jsonPath("$.details.minimumAge").value("Minimum age must be zero or greater"));

        verify(eventService, never()).create(any());
    }

    @Test
    void create_descriptionExceeds500Chars_returns400() throws Exception {
        String longDescription = "a".repeat(501);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "eventCode": "CMF-2026",
                                    "name": "Caribbean Music Fest 2026",
                                    "description": "%s",
                                    "category": "MUSIC",
                                    "eventDate": "2026-12-31",
                                    "minimumAge": 0,
                                    "venueCode": "VEN-SMR-01"
                                }
                                """.formatted(longDescription)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.description").value("Description must not exceed 500 characters"));

        verify(eventService, never()).create(any());
    }

    @Test
    void findByCode_existingEvent_returns200() throws Exception {
        EventResponse response = new EventResponse(1L, "CMF-2026", "Caribbean Music Fest 2026",
                "Test description", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusDays(30), 0, "VEN-SMR-01", "Marina Convention Center", java.util.Set.of());
        when(eventService.findByCode("CMF-2026")).thenReturn(response);

        mockMvc.perform(get("/api/events/{eventCode}", "CMF-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"));

        verify(eventService).findByCode("CMF-2026");
    }

    @Test
    void findByCode_nonExisting_returns404() throws Exception {
        when(eventService.findByCode("XXX-999"))
                .thenThrow(new ResourceNotFoundException("Event not found: XXX-999"));

        mockMvc.perform(get("/api/events/{eventCode}", "XXX-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Event not found: XXX-999"));

        verify(eventService).findByCode("XXX-999");
    }

    @Test
    void findPublishedEvents_returns200() throws Exception {
        List<EventSummaryResponse> events = List.of(
                new EventSummaryResponse(1L, "CMF-2026", "Caribbean Music Fest 2026",
                        EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(30)),
                new EventSummaryResponse(2L, "SPT-2026", "Sports Tournament 2026",
                        EventCategory.SPORTS, EventStatus.PUBLISHED, LocalDate.now().plusDays(60))
        );
        when(eventService.findPublishedEvents()).thenReturn(events);

        mockMvc.perform(get("/api/events/published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventCode").value("CMF-2026"))
                .andExpect(jsonPath("$[1].eventCode").value("SPT-2026"));

        verify(eventService).findPublishedEvents();
    }

    @Test
    void publish_validEvent_returns200() throws Exception {
        EventResponse response = new EventResponse(1L, "CMF-2026", "Caribbean Music Fest 2026",
                "Test description", EventCategory.MUSIC, EventStatus.PUBLISHED,
                LocalDate.now().plusDays(30), 0, "VEN-SMR-01", "Marina Convention Center", java.util.Set.of());
        when(eventService.publish("CMF-2026")).thenReturn(response);

        mockMvc.perform(patch("/api/events/{eventCode}/publish", "CMF-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(eventService).publish("CMF-2026");
    }

    @Test
    void publish_invalidEvent_returns409() throws Exception {
        when(eventService.publish("INVALID-001"))
                .thenThrow(new BusinessRuleException("Event is not in DRAFT state"));

        mockMvc.perform(patch("/api/events/{eventCode}/publish", "INVALID-001"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Event is not in DRAFT state"));

        verify(eventService).publish("INVALID-001");
    }

    @Test
    void addArtist_validRequest_returns200() throws Exception {
        EventResponse response = new EventResponse(1L, "CMF-2026", "Caribbean Music Fest 2026",
                "Test description", EventCategory.MUSIC, EventStatus.DRAFT,
                LocalDate.now().plusDays(30), 0, "VEN-SMR-01", "Marina Convention Center", java.util.Set.of());
        when(eventService.addArtist("CMF-2026", 1L)).thenReturn(response);

        mockMvc.perform(post("/api/events/{eventCode}/artists/{artistId}", "CMF-2026", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value("CMF-2026"));

        verify(eventService).addArtist("CMF-2026", 1L);
    }

    @Test
    void findByArtist_returns200() throws Exception {
        List<EventSummaryResponse> events = List.of(
                new EventSummaryResponse(1L, "CMF-2026", "Caribbean Music Fest 2026",
                        EventCategory.MUSIC, EventStatus.PUBLISHED, LocalDate.now().plusDays(30))
        );
        when(eventService.findByArtist("Solar Beat")).thenReturn(events);

        mockMvc.perform(get("/api/events/by-artist").param("stageName", "Solar Beat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventCode").value("CMF-2026"));

        verify(eventService).findByArtist("Solar Beat");
    }

    @Test
    void findPaidTicketsByEvent_returns200() throws Exception {
        List<TicketResponse> tickets = List.of(
                new TicketResponse(1L, "TCK-0001", TicketType.VIP,
                        new BigDecimal("250000.00"), TicketStatus.PAID,
                        LocalDateTime.now(), "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026"),
                new TicketResponse(2L, "TCK-0002", TicketType.GENERAL,
                        new BigDecimal("120000.00"), TicketStatus.PAID,
                        LocalDateTime.now(), "carlos@example.com", "CMF-2026", "Caribbean Music Fest 2026")
        );
        when(ticketService.findPaidTicketByEvent("CMF-2026")).thenReturn(tickets);

        mockMvc.perform(get("/api/events/{eventCode}/tickets/paid", "CMF-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticketCode").value("TCK-0001"))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[1].ticketCode").value("TCK-0002"));

        verify(ticketService).findPaidTicketByEvent("CMF-2026");
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        String malformedJson = "{\"eventCode\": \"CMF-2026\",}";

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"));

        verify(eventService, never()).create(any());
    }
}
