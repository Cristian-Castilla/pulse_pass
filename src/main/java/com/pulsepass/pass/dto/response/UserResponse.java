package com.pulsepass.pass.dto.response;

public record UserResponse(
        Long id,
        String username,
        String email,
        Boolean active
) {}
