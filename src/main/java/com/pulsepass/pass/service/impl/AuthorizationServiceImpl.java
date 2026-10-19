package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.exception.AccessDeniedException;
import com.pulsepass.pass.exception.AuthenticationException;
import com.pulsepass.pass.security.SecurityPrincipal;
import com.pulsepass.pass.service.AuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Implementación de {@link AuthorizationService}.
 *
 * PRD de Seguridad §7.1: "AuthorizationService — Comparar principal con propietario del recurso y
 * comprobar permisos. Prohibido: Asumir que un ID enviado por cliente es confiable".
 *
 * <p>La identidad se lee del {@code SecurityContextHolder}, que el
 * {@code JwtAuthenticationFilter} pobló en cada request con un {@link SecurityPrincipal} construido
 * desde la base (PRD §10 FR-SEC-AUTH-009). Nunca se acepta un id o un email como parámetro.
 *
 * <p>El 403 lo lanza esta clase y lo mapea el {@code GlobalExceptionHandler}; el
 * {@code RestAccessDeniedHandler} atiende solo los errores de la cadena de filtros, que ocurren
 * antes del controlador.
 */
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    /** Mensaje uniforme de 403. PRD §8 SEC-009: sin filtrar detalles internos. */
    static final String DENIED_MESSAGE = "No tiene permisos para realizar esta operación";

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new AuthenticationException("No authenticated principal");
        }
        return principal;
    }

    /**
     * El dueño del recurso pasa; STAFF y ADMIN pasan; cualquier otro usuario recibe 403.
     *
     * <p>PRD §11.1: "STAFF puede consultar tickets y marcar uso únicamente dentro de su función
     * operativa; como el dominio aún no modela asignación de personal a eventos, el MVP autoriza
     * STAFF global para esos endpoints y lo documenta como <b>limitación que debe restringirse en
     * producción</b>". Esa limitación está declarada aquí y no es un descuido: no hay entidad
     * {@code EventStaffAssignment} en el modelo.
     *
     * <p>Un principal inactivo no pasa por ser dueño: PRD §8 SEC-011 ("un usuario desactivado pierde
     * acceso en el siguiente request") se cumple en el filtro al recargar el estado, y esta
     * comprobación es el segundo candado para el caso de un token emitido antes de la desactivación.
     */
    @Override
    public void requireOwnerOrPrivileged(Long ownerId) {
        SecurityPrincipal principal = currentPrincipal();

        if (!principal.isActive()) {
            throw new AccessDeniedException(DENIED_MESSAGE);
        }

        if (ownerId != null && ownerId.equals(principal.userId())) {
            return;
        }

        if (hasRole(Role.STAFF) || hasRole(Role.ADMIN)) {
            return;
        }

        throw new AccessDeniedException(DENIED_MESSAGE);
    }

    /**
     * Exige que el recurso pertenezca a la identidad actual.
     *
     * <p>PRD §11.1: "POST /api/tickets debe comprobar que el userEmail del DTO representa al
     * principal (para USER). Nunca confiar en el email del request como comprobación de
     * autorización". La comparación es por id persistente (PRD §9.1), no por string: si el usuario
     * cambia su correo, su id sigue siendo el mismo y su acceso no debe perderse ni ampliarse.
     *
     * <p>A diferencia de {@link #requireOwnerOrPrivileged(Long)}, aquí STAFF y ADMIN NO pasan: la
     * compra es una operación de autoservicio del propio usuario. Esa distinción es la que el PRD
     * marca en §11 entre TKT-01 ("USER; userEmail debe pertenecer al principal") y TKT-02/04/06.
     */
    @Override
    public void requireSelf(Long ownerId) {
        SecurityPrincipal principal = currentPrincipal();

        if (!principal.isActive()) {
            throw new AccessDeniedException(DENIED_MESSAGE);
        }

        if (ownerId == null || !ownerId.equals(principal.userId())) {
            throw new AccessDeniedException(DENIED_MESSAGE);
        }
    }

    /**
     * Indica si la identidad actual tiene el rol dado.
     *
     * <p>Lee las authorities del contexto, que el filtro cargó desde la base en este request. No
     * consulta persistencia: PRD §12.3 pide "revalidar roles/estado actuales" en cada solicitud, y
     * el filtro ya lo hace al construir el principal, así que una revocación de rol entra en
     * vigencia sin esperar al vencimiento del JWT.
     */
    @Override
    public boolean hasRole(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        String authority = ROLE_PREFIX + role.name();
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
