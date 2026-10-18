package com.pulsepass.pass.domain;

/**
 * Roles de la Fase 4 (seguridad).
 *
 * PRD de Seguridad §9.1: "UserRole (NUEVA, N:1 con User) ├── user_id (FK) └── role (USER | STAFF | ADMIN)".
 * PRD §9.2: "No promover a ADMIN durante el registro público. Los roles privilegiados se provisionan
 * por proceso administrativo o semilla local controlada, no por parámetros HTTP del cliente."
 *
 * El prefijo ROLE_ que exige Spring Security se agrega al construir la authority
 * (ver CustomUserDetailsService), no aquí: el enum guarda el valor de negocio.
 */
public enum Role {

    USER,
    STAFF,
    ADMIN
}
