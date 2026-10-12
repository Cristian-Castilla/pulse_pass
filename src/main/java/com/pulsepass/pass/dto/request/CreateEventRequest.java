package com.pulsepass.pass.dto.request;

import com.pulsepass.pass.domain.EventCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateEventRequest(
        @NotBlank(message = "Event code is required")
        String eventCode,

        @NotBlank(message = "Name is required")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @NotNull(message = "Category is required")
        EventCategory category,

        @NotNull(message = "Event date is required")
        LocalDate eventDate,

        @Min(value = 0, message = "Minimum age must be zero or greater")
        int minimumAge,

        @NotBlank(message = "Venue code is required")
        String venueCode
) {}
