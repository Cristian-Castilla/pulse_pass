package com.pulsepass.pass.service;

import com.pulsepass.pass.dto.request.RegisterUserRequest;
import com.pulsepass.pass.dto.response.AuthResponse;
import com.pulsepass.pass.dto.response.AuthenticatedUserResponse;
import com.pulsepass.pass.dto.response.UserResponse;

/**
 * Orquesta autenticación y registro de la Fase 4.
 *
 * PRD de Seguridad §7.1: "AuthService — Autenticar, registrar credenciales según diseño, emitir token.
 * Prohibido: Reemplazar todos los Services anteriores".
 *
 * <p>Por eso este servicio no reimplementa las reglas de negocio de {@code UserService}: las invoca.
 * Su responsabilidad propia es la que el PRD §9.3 le asigna: "AuthService debe orquestar la creación
 * de usuario y hash en una única transacción, evitando cuentas creadas sin credenciales".
 *
 * <p>El registro pasa por aquí y no por el {@code UserController} directamente por esa misma razón:
 * un usuario creado sin su credencial dejaría una cuenta que no puede iniciar sesión.
 */
public interface AuthService {

    /**
     * Autentica por username o email y emite un token de acceso.
     *
     * PRD §10 FR-SEC-AUTH-002: "Autentica usuario/correo + contraseña; 200 con token y metadatos".
     * PRD §10 FR-SEC-AUTH-003: "Retorna 401 genérico, no informa si el usuario existe".
     */
    AuthResponse login(String identifier, String rawPassword);

    /**
     * Registra una cuenta con su credencial y rol inicial USER.
     *
     * PRD §10 FR-SEC-AUTH-001: "Crea User + credencial BCrypt + ROLE_USER de forma atómica; 201".
     * PRD §9.2: "No promover a ADMIN durante el registro público".
     */
    UserResponse register(RegisterUserRequest request);

    /**
     * Devuelve la identidad del principal autenticado.
     *
     * PRD §10 FR-SEC-AUTH-010: "GET /api/auth/me retorna información mínima del principal, no secretos".
     */
    AuthenticatedUserResponse currentUser();
}
