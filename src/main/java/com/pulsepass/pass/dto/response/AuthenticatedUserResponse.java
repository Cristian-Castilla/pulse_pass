package com.pulsepass.pass.dto.response;

import java.util.List;

/**
 * Identidad del usuario autenticado. PRD de Seguridad §10 FR-SEC-AUTH-010: "GET /api/auth/me retorna
 * información mínima del principal, no secretos".
 *
 * <p>Se duplica {@link UserResponse} a propósito: {@code UserResponse} es el contrato de negocio que
 * existe desde la Fase 3 y puede ganar o perder campos con el tiempo. Este DTO es el contrato del
 * contexto de seguridad, y acopla el endpoint {@code /auth/me} a cambios del DTO de negocio.
 *
 * <p>Los roles van como strings con el prefijo {@code ROLE_}, que es la convención que Spring Security
 * usa en las authorities y la que espera {@code hasRole(...)} del lado del cliente.
 */
public record AuthenticatedUserResponse(
        Long id,
        String username,
        String email,
        Boolean active,
        List<String> roles
) {}
