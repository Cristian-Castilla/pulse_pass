package com.pulsepass.pass.security;

import tools.jackson.databind.ObjectMapper;
import com.pulsepass.pass.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Responde 401 cuando una ruta protegida es consultada sin identidad válida.
 *
 * PRD de Seguridad §13: "Sin Bearer en ruta protegida → 401 Unauthorized → AuthenticationEntryPoint".
 * PRD §13 (tabla): "JWT expirado o firmado incorrectamente → 401 → Filtro + AuthenticationEntryPoint".
 *
 * <p>PRD §13: "las excepciones antes de DispatcherServlet no quedan cubiertas automáticamente por
 * @RestControllerAdvice; handlers de seguridad deben escribir la misma estructura JSON con
 * ObjectMapper o delegar al HandlerExceptionResolver adecuado, cuidando que la respuesta se emita
 * una sola vez". Este componente escribe el {@code ErrorResponse} directamente con
 * {@code ObjectMapper}, porque ocurre antes del {@code DispatcherServlet}.
 *
 * <p>El mensaje es siempre el mismo (PRD §8 SEC-009: "Respuestas 401/403 uniformes y sin
 * filtración de detalles internos"): no distingue "sin token" de "token inválido" de "token
 * vencido", porque esa distinción permitiría diagnosticar el sistema.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Mensaje uniforme de 401. Igual al de {@code AuthServiceImpl} y al de {@code JwtServiceImpl}. */
    static final String MESSAGE = "Invalid credentials";

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                MESSAGE,
                java.util.Map.of());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
