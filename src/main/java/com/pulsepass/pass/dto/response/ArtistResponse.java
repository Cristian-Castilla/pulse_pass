package com.pulsepass.pass.dto.response;

public record ArtistResponse(
        Long id,
        String stageName,
        String country,
        String genre,
        Boolean active
) {}
