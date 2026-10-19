package com.pulsepass.pass.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales de acceso. PRD de Seguridad §10.1: "LoginRequest(identifier, password)".
 *
 * <p>El campo se llama {@code identifier} y no {@code username} porque el login acepta ambos
 * identificadores: PRD §10 FR-SEC-AUTH-002 — "Autentica usuario/correo + contraseña". Recibirlo como
 * {@code username} obligaría al cliente a saber cuál está enviando.
 */
public record LoginRequest(

        @NotBlank(message = "Identifier is required")
        String identifier,

        @NotBlank(message = "Password is required")
        String password
) {}
