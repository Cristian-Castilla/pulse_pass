package com.pulsepass.pass.exception;

/**
 * La identidad es válida pero no tiene permiso para la operación solicitada.
 *
 * PRD de Seguridad §13: "Usuario válido sin rol requerido → 403 Forbidden".
 * PRD §13 (tabla): "Usuario intenta ticket ajeno → 403 → Servicio de autorización + handler".
 *
 * <p>Se modela como tipo propio y no como {@code AccessDeniedException} de Spring Security por dos
 * razones: (1) el {@code GlobalExceptionHandler} la mapea a 403 con el contrato
 * {@code ErrorResponse} sin depender de excepciones internas del framework, y (2) la decisión de
 * propiedad ocurre en la capa de servicio, antes de que la cadena de filtros termine, así que un
 * tipo propio mantiene el error donde se produce.
 *
 * <p>El mensaje es genérico a propósito: PRD §8 SEC-009 exige respuestas uniformes "sin filtración de
 * detalles internos". No se revela si el recurso existe ni quién es su dueño.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
