package com.pulsepass.pass.dto.response;

import java.time.LocalDate;

public record UserResponse(
        Long id,
        String username,
        String email,
        Boolean active,
        String firstName,
        String lastName,
        LocalDate birthDate
) {}
