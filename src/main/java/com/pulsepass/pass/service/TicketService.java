package com.pulsepass.pass.service;

import com.pulsepass.pass.dto.request.PurchaseTicketRequest;
import com.pulsepass.pass.dto.response.TicketResponse;

import java.util.List;

public interface TicketService {

    TicketResponse purchase(PurchaseTicketRequest request);

    TicketResponse findByCode(String ticketCode);

    List<TicketResponse> findByUserEmail(String email);

    List<TicketResponse> findPaidTicketByEvent(String eventCode);

    TicketResponse cancel (String ticketCode);

    TicketResponse markAsUsed(String ticketCode);
}
