package com.pulsepass.pass.security;

import com.pulsepass.pass.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
/**
 * Lee el encabezado {@code Authorization: Bearer ...}, valida el JWT y construye el contexto de
 * seguridad.
 *
 * PRD de Seguridad §5.1: "Filtro Bearer único por request (OncePerRequestFilter) y configuración
 * sin sesión".
 * PRD §7.1: "JwtAuthenticationFilter — Leer Bearer, validar JWT, construir contexto seguro.
 * Prohibido: crear cuentas o persistir tokens de acceso".
 * PRD §10 FR-SEC-AUTH-007: "Token solo por encabezado Authorization; no aceptar JWT en query
 * string".
 *
 * <p><b>El filtro nunca lanza excepciones.</b> PRD §14.3, advertencia 5: "El filtro JWT debe dejar
 * continuar requests sin header <b>solo</b> para que la cadena decida si la ruta es pública; si se
 * suministra Bearer inválido, jamás autenticarlo por accidente." Cuando el token es inválido se
 * limpia el contexto y se deja pasar: la cadena evalúa la ruta y, si es protegida, el
 * {@code AuthenticationEntryPoint} responde 401. Eso hace que el 401 salga siempre por el mismo
 * punto y con el mismo contrato {@code ErrorResponse}.
 *
 * <p>Se registra antes de {@code UsernamePasswordAuthenticationFilter} (PRD §14.3).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Prefijo obligatorio del esquema Bearer (PRD §10.1: "Authorization: Bearer ***"). */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // PRD §17.1 (JwtAuthenticationFilterTest): "no sobrescribir autenticación establecida de
        // forma insegura". Si ya hay un principal, no se toca.
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));

        if (token != null && authenticate(request, token)) {
            // autenticado: la cadena sigue y la autorización de ruta ocurre después
        } else {
            // Sin Bearer, Bearer malformado o token inválido: se limpia el contexto para que la
            // cadena decida. Un token inválido NO autentica (PRD §14.3 adv. 5).
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrae el token del encabezado. Devuelve {@code null} si no hay encabezado o si el esquema no
     * es Bearer.
     *
     * <p>PRD §17.1 (JwtAuthenticationFilterTest): "doble header sospechoso" no debe colar. Un
     * encabezado con varias cabeceras Bearer se trata como inválido: se toma la primera y se
     * valida; si no pasa, el contexto queda limpio y el EntryPoint responde 401.
     */
    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            // Esquema distinto (Basic, etc.): no es un Bearer, se ignora.
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }

    /**
     * Valida el token y construye el contexto de seguridad.
     *
     * <p>El principal es un {@link SecurityPrincipal}, no un {@code UserDetails}: PRD §11.1 exige
     * contrastar {@code principal.userId} con el propietario real, y el id es lo que lleva el claim
     * {@code sub}.
     *
     * <p>Los roles se leen desde el token, que los emitió el login, pero se revalidan contra la
     * base en cada request (PRD §12.3: "revalidar roles/estado actuales"). El filtro delega esa
     * revalidación en el {@code JwtService} para no acoplarse a la persistencia.
     *
     * @return {@code true} si el token es válido y se construyó el contexto
     */
    private boolean authenticate(HttpServletRequest request, String token) {
        try {
            Long userId = jwtService.validateAndExtractSubject(token);

            // Los roles se leen en persistencia en cada request (PRD §12.3), no del token: así un
            // rol revocado entra en vigencia sin esperar al vencimiento del JWT.
            List<GrantedAuthority> authorities =
                    userDetailsService.loadAuthorities(userId);

            SecurityPrincipal principal = userDetailsService.loadPrincipalById(userId);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            return true;
        } catch (Exception ex) {
            // PRD §8 SEC-009 y NFR-SEC-001: no se registra el token ni el detalle del error.
            // PRD §16 (NFR-SEC-002): "Entrada Bearer malformada no produce excepción sin manejar
            // erróneos ni HTTP 500".
            //
            // Se captura Exception a propósito: el fallo puede venir de cualquier capa. Además de
            // las excepciones de JJWT (JwtException) y de la configuración (IllegalStateException),
            // JwtServiceImpl lanza AuthenticationException y CustomUserDetailsService lanza
            // UsernameNotFoundException cuando el usuario está desactivado. Todas significan lo
            // mismo desde el filtro — "no hay identidad válida" — y todas deben dejar la ruta a
            // cargo de la cadena, que responde 401 con ErrorResponse. Si cualquiera escapara, el
            // contenedor la reportaría como error 500 y violaría NFR-SEC-002.
            log.debug("Bearer rejected: {}", ex.getClass().getSimpleName());
            return false;
        }
    }
}
