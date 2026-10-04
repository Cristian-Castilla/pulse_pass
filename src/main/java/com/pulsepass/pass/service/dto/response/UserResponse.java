package com.pulsepass.pass.service.dto.response;

public record UserResponse(
        Long id,
        String username,
        String email,
        Boolean active
) {}
