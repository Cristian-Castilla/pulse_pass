package com.pulsepass.pass.dto.response;

/**
 * Token emitido en un login correcto. PRD de Seguridad §10.1: "AuthResponse(accessToken, tokenType,
 * expiresIn)" y "Resultado 200 AuthResponse(accessToken, tokenType, expiresIn)".
 *
 * <p>{@code tokenType} es siempre {@code "Bearer"} (PRD §10.1: "La duración inicial de acceso se
 * define en 900 segundos... Authorization: Bearer").
 *
 * <p>{@code expiresIn} está en segundos, no en milisegundos: lo consume el cliente HTTP para saber
 * cuándo refrescar, y el PRD lo expresa en segundos ("900 segundos (15 minutos)").
 *
 * <p>No incluye la contraseña ni el hash, y no incluye el id del usuario: eso lo devuelve
 * {@code AuthenticatedUserResponse} vía {@code GET /api/auth/me}.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
