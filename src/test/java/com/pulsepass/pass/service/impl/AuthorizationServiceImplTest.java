package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserProfile;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.exception.AccessDeniedException;
import com.pulsepass.pass.exception.AuthenticationException;
import com.pulsepass.pass.security.SecurityPrincipal;
import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests de {@link AuthorizationServiceImpl}. PRD §17.1: "AuthorizationServiceTest: dueño vs
 * tercero; STAFF en uso; ADMIN en publicación; rechazo de correos ajenos".
 *
 * <p>Sin Spring ni PostgreSQL: el contexto de seguridad se arma a mano con
 * {@code SecurityContextHolder}, que es exactamente lo que el filtro pobla en producción.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;

    @InjectMocks
    private AuthorizationServiceImpl authorizationService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(SecurityPrincipal principal, Role... roles) {
        List<org.springframework.security.core.GrantedAuthority> authorities = java.util.Arrays
                .stream(roles)
                .<org.springframework.security.core.GrantedAuthority>map(
                        r -> new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_" + r.name()))
                .toList();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private SecurityPrincipal principal(Long userId, boolean active) {
        return new SecurityPrincipal(userId, "user" + userId, active);
    }

    // ------------------------------------------------------------------
    // currentPrincipal
    // ------------------------------------------------------------------

    @Test
    void currentPrincipal_withoutAuthentication_throwsUnauthorized() {
        assertThrows(AuthenticationException.class, () -> authorizationService.currentPrincipal());
    }

    @Test
    void currentPrincipal_returnsIdentityFromContext() {
        authenticate(principal(7L, true), Role.USER);

        SecurityPrincipal result = authorizationService.currentPrincipal();

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.username()).isEqualTo("user7");
    }

    // ------------------------------------------------------------------
    // requireOwnerOrPrivileged — dueño vs tercero (anti-IDOR)
    // ------------------------------------------------------------------

    @Test
    void requireOwnerOrPrivileged_ownerIsAllowed() {
        // PRD §11.1: "contrastarlo con principal.userId; jamás basta el ticketCode"
        authenticate(principal(7L, true), Role.USER);

        authorizationService.requireOwnerOrPrivileged(7L);
    }

    @Test
    void requireOwnerOrPrivileged_thirdPartyIsDenied() {
        // T-SEC-009: "USER consulta ticket ajeno → 403"
        authenticate(principal(7L, true), Role.USER);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireOwnerOrPrivileged(99L));

        assertThat(ex.getMessage()).isEqualTo("No tiene permisos para realizar esta operación");
    }

    @Test
    void requireOwnerOrPrivileged_staffCanAccessForeignResource() {
        // PRD §11.1: "STAFF puede consultar tickets ... dentro de su función operativa"
        authenticate(principal(1L, true), Role.STAFF);

        authorizationService.requireOwnerOrPrivileged(99L);
    }

    @Test
    void requireOwnerOrPrivileged_adminCanAccessForeignResource() {
        authenticate(principal(1L, true), Role.ADMIN);

        authorizationService.requireOwnerOrPrivileged(99L);
    }

    @Test
    void requireOwnerOrPrivileged_inactiveOwnerIsDenied() {
        // PRD §8 SEC-011: "Un usuario desactivado pierde acceso en el siguiente request"
        authenticate(principal(7L, false), Role.USER);

        assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireOwnerOrPrivileged(7L));
    }

    @Test
    void requireOwnerOrPrivileged_nullOwnerIdIsDeniedForRegularUser() {
        authenticate(principal(7L, true), Role.USER);

        assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireOwnerOrPrivileged(null));
    }

    // ------------------------------------------------------------------
    // requireSelf — compra para uno mismo
    // ------------------------------------------------------------------

    @Test
    void requireSelf_ownerIsAllowed() {
        authenticate(principal(7L, true), Role.USER);

        authorizationService.requireSelf(7L);
    }

    @Test
    void requireSelf_otherUserIsDenied() {
        // T-SEC-010: "USER compra con email ajeno → 403; 0 tickets creados"
        authenticate(principal(7L, true), Role.USER);

        assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireSelf(99L));
    }

    @Test
    void requireSelf_adminCannotPurchaseForSomeoneElse() {
        // A diferencia de requireOwnerOrPrivileged, la compra es de autoservicio: ni ADMIN pasa.
        // PRD §11 TKT-01: "USER; userEmail debe pertenecer al principal".
        authenticate(principal(1L, true), Role.ADMIN);

        assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireSelf(99L));
    }

    @Test
    void requireSelf_staffCannotPurchaseForSomeoneElse() {
        authenticate(principal(1L, true), Role.STAFF);

        assertThrows(AccessDeniedException.class,
                () -> authorizationService.requireSelf(99L));
    }

    // ------------------------------------------------------------------
    // hasRole
    // ------------------------------------------------------------------

    @Test
    void hasRole_returnsTrueForOwnedRole() {
        authenticate(principal(7L, true), Role.USER);

        assertThat(authorizationService.hasRole(Role.USER)).isTrue();
        assertThat(authorizationService.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void hasRole_withoutAuthentication_returnsFalse() {
        assertThat(authorizationService.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    void hasRole_withMultipleRoles_matchesAny() {
        authenticate(principal(7L, true), Role.USER, Role.STAFF);

        assertThat(authorizationService.hasRole(Role.STAFF)).isTrue();
    }
}
