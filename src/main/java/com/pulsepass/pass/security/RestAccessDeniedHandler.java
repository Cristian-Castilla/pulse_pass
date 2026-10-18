package com.pulsepass.pass.security;

import tools.jackson.databind.ObjectMapper;
import com.pulsepass.pass.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Responde 403 cuando una identidad válida intenta una operación que su rol no permite.
 *
 * PRD de Seguridad §13: "Usuario válido sin rol requerido → 403 Forbidden → AccessDeniedHandler".
 * PRD §13 (tabla): "Usuario intenta ticket ajeno → 403 (o 404, decisión consistente) → Servicio de
 * autorización + handler".
 *
 * <p>PRD §13 (ejemplo de 403): el mensaje de ejemplo es "No tiene permisos para realizar esta
 * operación". Se usa ese texto para el caso de rol insuficiente.
 *
 * <p>Escribe el {@code ErrorResponse} con {@code ObjectMapper} por la misma razón que el
 * {@link RestAuthenticationEntryPoint}: el handler se ejecuta antes del {@code DispatcherServlet},
 * así que el {@code @RestControllerAdvice} no lo cubre.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    /** Mensaje del PRD §13 para 403. */
    static final String MESSAGE = "No tiene permisos para realizar esta operación";

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                MESSAGE,
                Map.of());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
