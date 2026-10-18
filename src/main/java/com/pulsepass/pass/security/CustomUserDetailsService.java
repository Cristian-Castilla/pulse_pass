package com.pulsepass.pass.security;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;
import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.repository.UserCredentialRepository;
import com.pulsepass.pass.repository.UserRoleRepository;
import com.pulsepass.pass.security.SecurityPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Carga la identidad de un usuario a partir de sus credenciales y roles.
 *
 * PRD de Seguridad §5.1: "UserDetailsService, PasswordEncoder BCrypt y validación de estado activo".
 * PRD §7.1: "CustomUserDetailsService — Cargar identidad, password hash, roles y estado de usuario.
 * Prohibido: validar reglas comerciales."
 * PRD §10 FR-SEC-AUTH-009: "El principal se construye usando usuario persistido y authorities confiables".
 * PRD §12.3: "Preferencia: guardar únicamente identificador (sub) y metadatos; consultar roles actuales
 * en persistencia. De este modo, un token no conserva privilegios después de una revocación de rol".
 *
 * <p>Por eso los roles se leen <b>en cada request</b> desde {@link UserRoleRepository} y no se toman
 * del token: si a un usuario se le retira un rol, el cambio entra en vigencia en la siguiente
 * solicitud sin esperar a que venza el JWT.
 *
 * <p>El login acepta username <b>o</b> email (PRD §10 FR-SEC-AUTH-002: "Autentica usuario/correo +
 * contraseña"). El {@code username} que recibe Spring Security es entonces un identificador ambiguo,
 * y por eso la búsqueda cubre ambas columnas.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserCredentialRepository userCredentialRepository;
    private final UserRoleRepository userRoleRepository;

    public CustomUserDetailsService(UserCredentialRepository userCredentialRepository,
                                    UserRoleRepository userRoleRepository) {
        this.userCredentialRepository = userCredentialRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * Busca por username o, si no aparece, por email. El primer intento es por username porque es el
     * identificador técnico declarado por el PRD §9.1 ("La identidad de login se vincula con su ID
     * persistente, no con un correo editable como identificador técnico").
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        UserCredential credential = findByUsernameOrEmail(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        User user = credential.getUser();
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (UserRole userRole : userRoleRepository.findByUserUsername(user.getUsername())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + userRole.getRole().name()));
        }

        // PRD §7.1 SEC-011: un usuario desactivado pierde acceso en el siguiente request.
        // isEnabled() alimenta la comprobación de estado de Spring Security.
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                credential.getPasswordHash(),
                user.getActive(),
                true,
                true,
                true,
                authorities);
    }

    /**
     * Expone el id persistente del usuario autenticado.
     *
     * PRD §12.1: el claim {@code sub} debe ser el "ID interno inmutable del usuario".
     * El username es el identificador de login; el id es el que no cambia si el usuario quiere
     * modificar su correo. Este método lo deja disponible para quien construya el principal.
     */
    @Transactional(readOnly = true)
    public Long resolveUserId(String identifier) {
        return findByUsernameOrEmail(identifier)
                .map(c -> c.getUser().getId())
                .orElse(null);
    }

    /**
     * Comprueba si el usuario posee un rol dado, leyendo el rol vigente desde la base de datos.
     * Se usa para las comprobaciones de autorización que no dependen del token.
     */
    @Transactional(readOnly = true)
    public boolean hasRole(String username, Role role) {
        return userRoleRepository.findByUserUsername(username).stream()
                .anyMatch(userRole -> userRole.getRole() == role);
    }

    /**
     * Carga la identidad a partir del id persistente que trae el claim {@code sub} del JWT.
     *
     * PRD §12.3: "Preferencia: guardar únicamente identificador (sub) y metadatos; consultar roles
     * actuales en persistencia". El filtro recibe ese id y por eso se resuelve la identidad desde
     * el id, no desde el username: el token no especifica si el usuario se logueó con username o
     * con email.
     *
     * PRD §10 FR-SEC-AUTH-009: "El principal se construye usando usuario persistido y authorities
     * confiables". Se vuelve a leer la fila en cada request, así que un usuario desactivado pierde
     * acceso en el siguiente request (PRD §8 SEC-011) aunque su token siga vigente.
     *
     * @throws UsernameNotFoundException si el id no existe o el usuario está desactivado
     */
    @Transactional(readOnly = true)
    public SecurityPrincipal loadPrincipalById(Long userId) {
        UserCredential credential = userCredentialRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        User user = credential.getUser();
        if (!user.getActive()) {
            throw new UsernameNotFoundException("Invalid credentials");
        }

        return new SecurityPrincipal(user.getId(), user.getUsername(), user.getActive());
    }

    /**
     * Devuelve las authorities vigentes de un usuario, leídas de persistencia.
     *
     * PRD §12.3: "De este modo, un token no conserva privilegios después de una revocación de rol
     * si revalida el estado y roles actuales en cada request". Los roles viajan en el token solo
     * como referencia; la fuente de verdad es la base.
     */
    @Transactional(readOnly = true)
    public List<GrantedAuthority> loadAuthorities(Long userId) {
        return userRoleRepository.findByUserUsername(resolveUsername(userId)).stream()
                .<GrantedAuthority>map(userRole ->
                        new SimpleGrantedAuthority("ROLE_" + userRole.getRole().name()))
                .toList();
    }

    private String resolveUsername(Long userId) {
        return userCredentialRepository.findById(userId)
                .map(c -> c.getUser().getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    private Optional<UserCredential> findByUsernameOrEmail(String identifier) {
        return userCredentialRepository.findByUserUsername(identifier)
                .or(() -> userCredentialRepository.findByUserEmailIgnoreCase(identifier));
    }
}
