package com.pulsepass.pass.service.dto.response;

import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import java.time.LocalDate;

public record EventSummaryResponse(
        Long id,
        String eventCode,
        String name,
        EventCategory category,
        EventStatus status,
        LocalDate eventDate
) {}
