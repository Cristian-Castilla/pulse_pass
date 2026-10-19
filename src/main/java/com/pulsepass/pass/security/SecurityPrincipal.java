package com.pulsepass.pass.security;

/**
 * Principal de la Fase 4: identidad comprobada de quien realiza la solicitud.
 *
 * PRD de Seguridad §7.1: "JwtAuthenticationFilter — Leer Bearer, validar JWT, construir contexto seguro".
 * PRD §10 FR-SEC-AUTH-009: "El principal se construye usando usuario persistido y authorities confiables".
 *
 * <p>Es un tipo propio y no el {@code UserDetails} de Spring Security para que las capas de negocio no
 * dependan de una clase del framework. El id persistente es el dato que usan las reglas de propiedad:
 * PRD §11.1 — "contrastarlo con principal.userId; jamás basta el ticketCode".
 *
 * <p>El id es el identificador de autorización. El username es el identificador de login, y puede
 * cambiar; el id no (PRD §9.1: "La identidad de login se vincula con su ID persistente, no con un correo
 * editable como identificador técnico").
 */
public record SecurityPrincipal(
        Long userId,
        String username,
        boolean active
) {

    public boolean isActive() {
        return active;
    }
}
