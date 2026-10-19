package com.pulsepass.pass.service;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.security.SecurityPrincipal;

/**
 * Autorización por rol y por propiedad de recursos. PRD de Seguridad §7.1 y §11.1.
 *
 * <p>Responsabilidad: "Comparar principal con propietario del recurso y comprobar permisos.
 * Prohibido: Asumir que un ID enviado por cliente es confiable".
 *
 * <p><b>Por qué existe y no vive en el controlador.</b> PRD §3.2: "Los controles de propiedad deben
 * situarse en una frontera de negocio segura (preferentemente un servicio de autorización o una
 * anotación de seguridad a nivel de método), no depender solamente del Controller". Un controlador
 * que valida la propiedad sigue siendo un controlador que decide sobre el dominio.
 *
 * <p><b>La identidad viene del contexto, nunca del request.</b> PRD §8 SEC-006: "No confiar en
 * {@code role}, {@code userEmail} o {@code userId} proporcionados por el cliente como identidad".
 * Por eso este servicio recibe el {@link SecurityPrincipal} ya construido por el filtro y nunca un
 * parámetro del cliente.
 *
 * <p><b>403, no 404.</b> Decisión D1 del plan: un ticket ajeno responde 403 con mensaje genérico. Es
 * determinista y testeable; un 404 para ocultar existencia haría depender la aserción del estado
 * del recurso. El PRD §11.1 lo permite: "El sistema puede responder 403 para objeto ajeno".
 */
public interface AuthorizationService {

    /**
     * Devuelve la identidad del request actual, leyéndola del contexto de seguridad.
     *
     * PRD §10 FR-SEC-AUTH-009: "El principal se construye usando usuario persistido y authorities
     * confiables". El filtro ya validó la firma y recargó el estado; aquí solo se lee.
     *
     * @throws com.pulsepass.pass.exception.AuthenticationException si no hay principal
     */
    SecurityPrincipal currentPrincipal();

    /**
     * Autoriza una operación sobre un recurso propio.
     *
     * PRD §11.1: "GET /api/tickets/{ticketCode} debe determinar el usuario propietario desde la base
     * de datos y contrastarlo con principal.userId; jamás basta el ticketCode".
     *
     * <p>Regla: el dueño del recurso pasa; STAFF y ADMIN pasan (PRD §11: "STAFF puede consultar
     * tickets y marcar uso únicamente dentro de su función operativa"); cualquier otro usuario
     * recibe 403.
     *
     * <p>STAFF se autoriza global porque "el dominio aún no modela asignación de personal a eventos,
     * el MVP autoriza STAFF global para esos endpoints y lo documenta como limitación que debe
     * restringirse en producción" (PRD §11.1).
     *
     * @param ownerId id persistente del propietario real, cargado desde la base
     * @throws com.pulsepass.pass.exception.AccessDeniedException si la identidad no es la propietaria
     *         ni privilegiada
     */
    void requireOwnerOrPrivileged(Long ownerId);

    /**
     * Autoriza que el email de un recurso pertenezca a la identidad actual.
     *
     * PRD §11.1: "POST /api/tickets debe comprobar que el userEmail del DTO representa al principal
     * (para USER). Nunca confiar en el email del request como comprobación de autorización".
     *
     * <p>Se compara el id persistente, no el string: dos correos distintos pueden ser la misma
     * cuenta si el usuario cambia su correo, y el id no cambia (PRD §9.1).
     *
     * @throws com.pulsepass.pass.exception.AccessDeniedException si el recurso no pertenece al principal
     */
    void requireSelf(Long ownerId);

    /**
     * Indica si la identidad actual posee un rol dado, leyéndolo del contexto.
     *
     * <p>No consulta la base aquí: las authorities ya se cargaron en cada request por el filtro
     * (PRD §12.3: "revalidar roles/estado actuales"), así que una revocación de rol entra en
     * vigencia en la siguiente solicitud.
     */
    boolean hasRole(Role role);
}
