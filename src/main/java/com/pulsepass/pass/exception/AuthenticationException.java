package com.pulsepass.pass.exception;

/**
 * Fallo de autenticación: credenciales inválidas o ausencia de un principal autenticado.
 *
 * PRD de Seguridad §13: "Login incorrecto → 401 Unauthorized".
 * PRD §10 FR-SEC-AUTH-003: "Retorna 401 genérico, no informa si el usuario existe".
 *
 * <p>Se modela como tipo propio y no como {@code BadCredentialsException} de Spring Security para que
 * el {@code GlobalExceptionHandler} pueda mapearla a 401 con el contrato {@code ErrorResponse} sin
 * depender de excepciones internas del framework.
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
}
