package com.pulsepass.pass.dto.request;

import com.pulsepass.pass.domain.EventCategory;
import java.time.LocalDate;

public record CreateEventRequest(
        String eventCode,
        String name,
        String description,
        EventCategory category,
        LocalDate eventDate,
        int minimumAge,
        String venueCode
) {}
