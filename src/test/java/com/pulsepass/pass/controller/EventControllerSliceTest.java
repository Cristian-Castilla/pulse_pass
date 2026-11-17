package com.pulsepass.pass.controller;

import com.pulsepass.pass.dto.response.*;
import com.pulsepass.pass.domain.*;
import com.pulsepass.pass.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerSliceTest extends ControllerSliceSupport {
    private final EventResponse event = new EventResponse(1L,"EVT","Festival","Description",EventCategory.MUSIC,
        EventStatus.DRAFT,LocalDateTime.of(2099,1,1,19,0),18,"VEN","Venue",Set.of());
    private final String body = """
        {"eventCode":"EVT","name":"Festival","category":"MUSIC","eventDate":"2099-01-01T19:00:00","minimumAge":18,"venueCode":"VEN"}
        """;
    @Test void createDelegatesValidatedRequest() throws Exception {
        when(events.create(any())).thenReturn(event);
        mvc.perform(post("/api/events").with(actor("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("DRAFT"));
        verify(events).create(argThat(r -> r.eventCode().equals("EVT") && r.minimumAge()==18));
    }
    @Test void invalidRequestNeverReachesService() throws Exception {
        mvc.perform(post("/api/events").with(actor("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(events);
    }
    @Test void findByCode() throws Exception {
        when(events.findByCode("EVT")).thenReturn(event);
        mvc.perform(get("/api/events/EVT")).andExpect(status().isOk()).andExpect(jsonPath("$.eventCode").value("EVT"));
        verify(events).findByCode("EVT");
    }
    @Test void publishedEvents() throws Exception {
        when(events.findPublishedEvents()).thenReturn(List.of());
        mvc.perform(get("/api/events/published")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        verify(events).findPublishedEvents();
    }
    @Test void publish() throws Exception {
        when(events.publish("EVT")).thenReturn(event);
        mvc.perform(patch("/api/events/EVT/publish").with(actor("ADMIN"))).andExpect(status().isOk());
        verify(events).publish("EVT");
    }
    @Test void addArtist() throws Exception {
        when(events.addArtist("EVT",1L)).thenReturn(event);
        mvc.perform(post("/api/events/EVT/artists/1").with(actor("ADMIN"))).andExpect(status().isOk());
        verify(events).addArtist("EVT",1L);
    }
    @Test void searchByArtist() throws Exception {
        when(events.findByArtist("Solar Beat")).thenReturn(List.of());
        mvc.perform(get("/api/events/by-artist").param("stageName","Solar Beat")).andExpect(status().isOk());
        verify(events).findByArtist("Solar Beat");
    }
    @Test void paidTicketsSubresource() throws Exception {
        when(tickets.findPaidTicketsByEvent("EVT")).thenReturn(List.of());
        mvc.perform(get("/api/events/EVT/tickets/paid").with(actor("STAFF"))).andExpect(status().isOk());
        verify(tickets).findPaidTicketsByEvent("EVT");
    }
    @Test void businessConflictIs409() throws Exception {
        when(events.publish("EVT")).thenThrow(new BusinessRuleException("Only DRAFT"));
        mvc.perform(patch("/api/events/EVT/publish").with(actor("ADMIN"))).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Only DRAFT"));
        verify(events).publish("EVT");
    }
}
