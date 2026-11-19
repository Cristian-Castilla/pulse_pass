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

@WebMvcTest(TicketController.class)
class TicketControllerSliceTest extends ControllerSliceSupport {
    private final TicketResponse ticket = new TicketResponse(1L,"TCK",TicketType.GENERAL,new BigDecimal("100000"),
        TicketStatus.PAID,LocalDateTime.of(2098,1,1,19,0),"andrea@example.com","EVT","Festival","Andrea Ruiz");
    @Test void purchase() throws Exception {
        when(tickets.purchase(any())).thenReturn(ticket);
        mvc.perform(post("/api/tickets").with(actor("USER")).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"userEmail":"andrea@example.com","eventCode":"EVT","type":"GENERAL"}
                """))
            .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.price").value(100000));
        verify(tickets).purchase(argThat(r -> r.type()==TicketType.GENERAL));
    }
    @Test void invalidPurchaseNeverCallsService() throws Exception {
        mvc.perform(post("/api/tickets").with(actor("USER")).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(tickets);
    }
    @Test void lookup() throws Exception {
        when(tickets.findByCode("TCK")).thenReturn(ticket);
        mvc.perform(get("/api/tickets/TCK").with(actor("USER"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.ticketCode").value("TCK"))
            .andExpect(jsonPath("$.userFullName").value("Andrea Ruiz"))
            .andExpect(jsonPath("$.userEmail").value("andrea@example.com"))
            .andExpect(jsonPath("$.birthDate").doesNotExist())
            .andExpect(jsonPath("$.profile").doesNotExist());
        verify(tickets).findByCode("TCK");
    }
    @Test void userTickets() throws Exception {
        when(tickets.findByUserEmail("andrea@example.com")).thenReturn(List.of(ticket));
        mvc.perform(get("/api/tickets/by-user").with(actor("USER")).param("email","andrea@example.com"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].eventCode").value("EVT"));
        verify(tickets).findByUserEmail("andrea@example.com");
    }
    @Test void cancel() throws Exception {
        when(tickets.cancel("TCK")).thenReturn(ticket);
        mvc.perform(patch("/api/tickets/TCK/cancel").with(actor("USER"))).andExpect(status().isOk());
        verify(tickets).cancel("TCK");
    }
    @Test void use() throws Exception {
        when(tickets.markAsUsed("TCK")).thenReturn(ticket);
        mvc.perform(patch("/api/tickets/TCK/use").with(actor("STAFF"))).andExpect(status().isOk());
        verify(tickets).markAsUsed("TCK");
    }
}
