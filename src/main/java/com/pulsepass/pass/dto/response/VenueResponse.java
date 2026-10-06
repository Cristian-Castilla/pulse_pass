package com.pulsepass.pass.dto.response;

public record VenueResponse(
        Long id,
        String code,
        String name,
        String city,
        String address,
        Long capacity,
        Boolean active
) {}
