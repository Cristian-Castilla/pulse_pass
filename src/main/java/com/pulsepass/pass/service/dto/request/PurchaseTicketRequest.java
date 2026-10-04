package com.pulsepass.pass.service.dto.request;

import com.pulsepass.pass.domain.TicketType;

public record PurchaseTicketRequest(
        String userEmail,
        String eventCode,
        TicketType type
) {}
