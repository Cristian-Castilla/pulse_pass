package com.pulsepass.pass.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cadena de filtros de seguridad. PRD de Seguridad §5.1, §14.3.
 *
 * <p>El orden de los matchers es funcional, no estético: en {@code authorizeHttpRequests} el
 * <b>primer patrón que matchea gana</b> (a diferencia de Spring MVC, donde el literal siempre gana
 * por ranking). Sin el orden correcto, {@code GET /api/events/published} se matchearía como
 * {@code eventCode = "published"} y caería en la regla condicional de EVT-02. Es la advertencia 4
 * del PRD §14.3. Verificado en {@code MatrizAutorizacion.md} §2.
 *
 * <p><b>Piso mínimo, no la matriz completa.</b> PRD §14.3, advertencia 1: "Este fragmento no es la
 * matriz completa: las comprobaciones de propiedad y la visibilidad de eventos no publicados deben
 * implementarse en Services/seguridad a nivel de método". La matriz real es la del PRD §11 y es la
 * que manda (PRD §20: "El spec es la fuente de verdad").
 *
 * <p>Las rutas de propiedad (USR-02, USR-03, TKT-01, TKT-02, TKT-03, TKT-05) caen en
 * {@code anyRequest().authenticated()} como piso y su verificación de propiedad se resuelve en
 * {@code AuthorizationService}, no en el filtro.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // PRD §14.3: "Solo API Bearer sin cookies de autenticación". Sin cookies, CSRF no
            // aplica; y PRD §8 SEC-008 exige que no se use JSESSIONID como autenticación.
            .csrf(csrf -> csrf.disable())

            // PRD §5.1: "configuración sin sesión". STATELESS: el servidor no guarda nada entre
            // requests y el contexto se reconstruye desde el JWT en cada uno.
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // PRD §14.3: sin formLogin ni httpBasic. La autenticación es por Bearer y la API no
            // tiene login por navegador.
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())

            // PRD §13: los errores originados en el filtro (antes de DispatcherServlet) los
            // escriben estos handlers, porque el @RestControllerAdvice no los cubre.
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))

            .authorizeHttpRequests(auth -> auth
                // ------------------------------------------------------------------
                // Rutas públicas (PRD §11: VEN-01, VEN-02, EVT-03, ART-01..03, USR-01)
                // ------------------------------------------------------------------
                // PRD §10.1: "POST /api/users — Público registro → login". El registro no requiere
                // token; el login tampoco (es el único punto de entrada de credenciales).
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/users").permitAll()

                // PRD §14.3: "La ruta GET /api/venues/active es pública y queda incluida en
                // /api/venues/**; verificarla con una prueba HTTP explícita".
                .requestMatchers(HttpMethod.GET, "/api/venues/**").permitAll()

                // PRD §14.3 adv. 4: "rutas específicas antes de patrones generales;
                // GET /api/events/published antes de {eventCode}". Los literales van primero para
                // que no los trague el patrón con variable.
                .requestMatchers(HttpMethod.GET, "/api/events/published").permitAll()

                // PRD §11 ART-01..03: los tres endpoints de artistas son públicos. Se agrupan en
                // /api/artists/** porque no hay ninguno protegido.
                //
                // PRD §14.3 adv. 4: "rutas específicas antes de patrones generales". El literal
                // /api/events/published va ANTES que /api/events/{eventCode}, y /api/artists/...
                // son todos literales o parámetros, así que el orden no los afecta.
                .requestMatchers(HttpMethod.GET, "/api/artists/**").permitAll()

                // PRD §11 EVT-02: "Público solo si publicado; para otros estados STAFF/ADMIN,
                // evitando revelar borradores". La ruta es alcanzable por anónimo y el filtrado de
                // borradores se resuelve en EventService (decisión D3), en la capa de servicio.
                // El literal /api/events/published ya se declaró arriba; /api/events/by-artist
                // también. Este patrón con variable cubre el resto de los GET de evento.
                .requestMatchers(HttpMethod.GET, "/api/events/{eventCode}").permitAll()

                // PRD §11 EVT-06: "Público únicamente para eventos publicados; otros estados
                // requieren STAFF/ADMIN". El filtrado de borradores se resuelve en EventService (D3).
                .requestMatchers(HttpMethod.GET, "/api/events/by-artist").permitAll()

                // ------------------------------------------------------------------
                // Rutas de administración (PRD §11: EVT-01, EVT-04, EVT-05)
                // ------------------------------------------------------------------
                .requestMatchers(HttpMethod.POST, "/api/events").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/events/*/publish").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/events/*/artists/*").hasRole("ADMIN")

                // ------------------------------------------------------------------
                // Operaciones de staff (PRD §11: TKT-04, TKT-06)
                // ------------------------------------------------------------------
                .requestMatchers(HttpMethod.GET, "/api/events/*/tickets/paid")
                    .hasAnyRole("STAFF", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/tickets/*/use")
                    .hasAnyRole("STAFF", "ADMIN")

                // ------------------------------------------------------------------
                // Piso mínimo (PRD §14.3 adv. 1: "no es la matriz completa")
                // ------------------------------------------------------------------
                // Cualquier otra ruta exige identidad válida. La distinción fina por propiedad
                // (quién es dueño del ticket o del usuario consultado) la decide AuthorizationService.
                //
                // El orden importa aquí también: los literales de tickets deben ir ANTES del patrón
                // con variable para no tragarse /by-user. Como caen en anyRequest(), se cubren por
                // autenticación y la decisión de propiedad es del servicio.
                .anyRequest().authenticated());

        // PRD §14.3: "addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)".
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
