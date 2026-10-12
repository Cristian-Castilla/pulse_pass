package com.pulsepass.pass.controller;

import com.pulsepass.pass.domain.TicketStatus;
import com.pulsepass.pass.domain.TicketType;
import com.pulsepass.pass.dto.request.PurchaseTicketRequest;
import com.pulsepass.pass.dto.response.TicketResponse;
import com.pulsepass.pass.exception.BusinessRuleException;
import com.pulsepass.pass.exception.DuplicateResourceException;
import com.pulsepass.pass.exception.GlobalExceptionHandler;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import(GlobalExceptionHandler.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @Test
    void purchase_validRequest_returns201() throws Exception {
        PurchaseTicketRequest request = new PurchaseTicketRequest("andrea@example.com", "CMF-2026", TicketType.VIP);
        TicketResponse response = new TicketResponse(1L, "TCK-0001", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.PAID,
                LocalDateTime.now(), "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026");
        when(ticketService.purchase(any())).thenReturn(response);

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userEmail": "andrea@example.com",
                                    "eventCode": "CMF-2026",
                                    "type": "VIP"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketCode").value("TCK-0001"))
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(ticketService).purchase(any());
    }

    @Test
    void purchase_invalidRequest_returns400() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userEmail": "",
                                    "eventCode": "",
                                    "type": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.userEmail").value("User email is required"))
                .andExpect(jsonPath("$.details.eventCode").value("Event code is required"))
                .andExpect(jsonPath("$.details.type").value("Ticket type is required"));

        verify(ticketService, never()).purchase(any());
    }

    @Test
    void purchase_nonExistingUser_returns404() throws Exception {
        when(ticketService.purchase(any()))
                .thenThrow(new ResourceNotFoundException("User not found: unknown@example.com"));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userEmail": "unknown@example.com",
                                    "eventCode": "CMF-2026",
                                    "type": "VIP"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found: unknown@example.com"));

        verify(ticketService).purchase(any());
    }

    @Test
    void purchase_businessRuleViolation_returns409() throws Exception {
        when(ticketService.purchase(any()))
                .thenThrow(new BusinessRuleException("User is under minimum age"));

        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "userEmail": "laura@example.com",
                                    "eventCode": "CMF-2026",
                                    "type": "VIP"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("User is under minimum age"));

        verify(ticketService).purchase(any());
    }

    @Test
    void findByCode_existingTicket_returns200() throws Exception {
        TicketResponse response = new TicketResponse(1L, "TCK-0001", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.PAID,
                LocalDateTime.now(), "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026");
        when(ticketService.findByCode("TCK-0001")).thenReturn(response);

        mockMvc.perform(get("/api/tickets/{ticketCode}", "TCK-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value("TCK-0001"));

        verify(ticketService).findByCode("TCK-0001");
    }

    @Test
    void findByUserEmail_returns200() throws Exception {
        List<TicketResponse> tickets = List.of(
                new TicketResponse(1L, "TCK-0001", TicketType.VIP,
                        new BigDecimal("250000.00"), TicketStatus.PAID,
                        LocalDateTime.now(), "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026"),
                new TicketResponse(2L, "TCK-0002", TicketType.GENERAL,
                        new BigDecimal("120000.00"), TicketStatus.PAID,
                        LocalDateTime.now(), "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026")
        );
        when(ticketService.findByUserEmail("andrea@example.com")).thenReturn(tickets);

        mockMvc.perform(get("/api/tickets/by-user").param("email", "andrea@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticketCode").value("TCK-0001"))
                .andExpect(jsonPath("$[1].ticketCode").value("TCK-0002"));

        verify(ticketService).findByUserEmail("andrea@example.com");
    }

    @Test
    void cancel_paidTicket_returns200() throws Exception {
        TicketResponse response = new TicketResponse(1L, "TCK-0001", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.CANCELLED,
                LocalDateTime.now(), "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026");
        when(ticketService.cancel("TCK-0001")).thenReturn(response);

        mockMvc.perform(patch("/api/tickets/{ticketCode}/cancel", "TCK-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(ticketService).cancel("TCK-0001");
    }

    @Test
    void cancel_usedTicket_returns409() throws Exception {
        when(ticketService.cancel("TCK-0002"))
                .thenThrow(new BusinessRuleException("Ticket is already USED and cannot be cancelled"));

        mockMvc.perform(patch("/api/tickets/{ticketCode}/cancel", "TCK-0002"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Ticket is already USED and cannot be cancelled"));

        verify(ticketService).cancel("TCK-0002");
    }

    @Test
    void markAsUsed_paidTicket_returns200() throws Exception {
        TicketResponse response = new TicketResponse(1L, "TCK-0001", TicketType.VIP,
                new BigDecimal("250000.00"), TicketStatus.USED,
                LocalDateTime.now(), "andrea@example.com", "CMF-2026", "Caribbean Music Fest 2026");
        when(ticketService.markAsUsed("TCK-0001")).thenReturn(response);

        mockMvc.perform(patch("/api/tickets/{ticketCode}/use", "TCK-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"));

        verify(ticketService).markAsUsed("TCK-0001");
    }

    @Test
    void markAsUsed_cancelledTicket_returns409() throws Exception {
        when(ticketService.markAsUsed("TCK-0003"))
                .thenThrow(new BusinessRuleException("Ticket is CANCELLED and cannot be marked as used"));

        mockMvc.perform(patch("/api/tickets/{ticketCode}/use", "TCK-0003"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Ticket is CANCELLED and cannot be marked as used"));

        verify(ticketService).markAsUsed("TCK-0003");
    }
}
