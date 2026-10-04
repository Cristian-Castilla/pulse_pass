package com.pulsepass.pass.service.dto.response;

import com.pulsepass.pass.domain.EventCategory;
import com.pulsepass.pass.domain.EventStatus;
import java.time.LocalDate;
import java.util.Set;

public record EventResponse(
        Long id,
        String eventCode,
        String name,
        String description,
        EventCategory category,
        EventStatus status,
        LocalDate eventDate,
        int minimumAge,
        String venueCode,
        String venueName,
        Set<ArtistResponse> artists
) {}
