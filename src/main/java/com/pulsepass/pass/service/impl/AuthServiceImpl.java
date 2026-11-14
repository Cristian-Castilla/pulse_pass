package com.pulsepass.pass.service.impl;

import com.pulsepass.pass.domain.Role;
import com.pulsepass.pass.domain.User;
import com.pulsepass.pass.domain.UserCredential;

import com.pulsepass.pass.domain.UserRole;
import com.pulsepass.pass.dto.request.RegisterUserRequest;
import com.pulsepass.pass.dto.response.AuthResponse;
import com.pulsepass.pass.dto.response.AuthenticatedUserResponse;
import com.pulsepass.pass.dto.response.UserResponse;
import com.pulsepass.pass.exception.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.pulsepass.pass.exception.ResourceNotFoundException;
import com.pulsepass.pass.repository.UserCredentialRepository;

import com.pulsepass.pass.repository.UserRepository;
import com.pulsepass.pass.repository.UserRoleRepository;
import com.pulsepass.pass.security.CustomUserDetailsService;
import com.pulsepass.pass.security.SecurityPrincipal;
import com.pulsepass.pass.service.AuthService;
import com.pulsepass.pass.service.JwtService;
import com.pulsepass.pass.service.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import java.util.List;

/**
 * Implementación de {@link AuthService}.
 *
 * PRD de Seguridad §7.1: "AuthService — Autenticar, registrar credenciales según diseño, emitir token.
 * Prohibido: Reemplazar todos los Services anteriores".
 *
 * <p><b>Autenticación directa, sin {@code AuthenticationManager}.</b> El PRD §5.1 sugiere
 * "AuthenticationManager / DaoAuthenticationProvider o equivalente compatible", y aquí el equivalente
 * es cargar el {@code UserDetails} con el {@code UserDetailsService} y comparar con el
 * {@code PasswordEncoder}. Se hace así porque el bean {@code AuthenticationManager} solo existe cuando
 * hay una {@code SecurityFilterChain} configurada (Etapa 6), y esta clase debe poder instanciarse antes.
 * La comparación es la misma que haría {@code DaoAuthenticationProvider}, que es exactamente lo que
 * {@code CustomUserDetailsService} alimenta.
 */
@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /**
     * Límite superior de la contraseña en bytes, no en caracteres.
     *
     * PRD §9.3: "máximo 72 bytes si se emplea BCrypt tradicional". BCrypt trunca silenciosamente lo que
     * supera 72 bytes, así que una contraseña más larga quedaría hasheada de forma distinta a la que el
     * usuario escribió: el login fallaría sin un mensaje que lo explique. Se valida aquí, además del
     * @Size del DTO, porque Bean Validation cuenta caracteres.
     */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserCredentialRepository userCredentialRepository;
    private final UserRoleRepository userRoleRepository;


    public AuthServiceImpl(JwtService jwtService,
                           PasswordEncoder passwordEncoder,
                           CustomUserDetailsService userDetailsService,
                           UserRepository userRepository,
                           UserService userService,
                           UserCredentialRepository userCredentialRepository,
                           UserRoleRepository userRoleRepository) {
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.userService = userService;
        this.userCredentialRepository = userCredentialRepository;
        this.userRoleRepository = userRoleRepository;

    }

    /**
     * Autentica por username o email y emite el token.
     *
     * PRD §10 FR-SEC-AUTH-002: "Autentica usuario/correo + contraseña; 200 con token y metadatos".
     * PRD §10 FR-SEC-AUTH-004: un usuario inactivo no obtiene token. Eso se comprueba con
     * {@code isEnabled()}, que el {@code UserDetailsService} alimenta desde {@code User.active}.
     * PRD §10 FR-SEC-AUTH-003: "Retorna 401 genérico, no informa si el usuario existe". Por eso tanto el
     * usuario inexistente como la contraseña incorrecta producen la misma excepción y el mismo mensaje:
     * distinguirlos permitiría enumerar cuentas.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(String identifier, String rawPassword) {
        try {
            if (rawPassword == null || rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
                throw new BadCredentialsException("Invalid credentials");
            }
            UserDetails userDetails = userDetailsService.loadUserByUsername(identifier);

            if (!userDetails.isEnabled()) {
                throw new BadCredentialsException("Invalid credentials");
            }

            if (!passwordEncoder.matches(rawPassword, userDetails.getPassword())) {
                throw new BadCredentialsException("Invalid credentials");
            }

            Long userId = userDetailsService.resolveUserId(userDetails.getUsername());
            AuthResponse response = jwtService.issueToken(userId);
            log.info("auth_success requestId={}", MDC.get("requestId"));
            return response;
        } catch (BadCredentialsException | UsernameNotFoundException ex) {
            log.info("auth_failure requestId={}", MDC.get("requestId"));
            throw new AuthenticationException("Invalid credentials");
        }
    }

    /**
     * Registro atómico: User + UserProfile + credencial BCrypt + ROLE_USER en una transacción.
     *
     * PRD §9.3: "AuthService debe orquestar la creación de usuario y hash en una única transacción,
     * evitando cuentas creadas sin credenciales".
     *
     * <p>Se ejecuta en una sola transacción porque las cuatro escrituras deben ser todo o nada: si la
     * credencial fallara después de crear el usuario, quedaría una cuenta sin login posible.
     *
     * <p>UserService conserva las reglas de usuario/perfil y participa en esta transacción exterior.
     */
    @Override
    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        validatePasswordBytes(request.password());

        UserResponse response = userService.register(request);
        User user = userRepository.findById(response.id())
                .orElseThrow(() -> new ResourceNotFoundException("Registered user not found"));

        userCredentialRepository.save(new UserCredential(
                user,
                passwordEncoder.encode(request.password()),
                null
        ));

        // PRD §9.2: "No promover a ADMIN durante el registro público". El rol inicial es siempre USER.
        userRoleRepository.save(new UserRole(user, Role.USER));

        return response;
    }

    /**
     * Devuelve la identidad del principal tomada del contexto de seguridad.
     *
     * PRD §10 FR-SEC-AUTH-006: "No confiar en role, userEmail o userId proporcionados por el cliente
     * como identidad". Por eso se lee del {@code SecurityContextHolder} y no de un parámetro del
     * request.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUserResponse currentUser() {
        SecurityPrincipal principal = currentPrincipal();
        User user = userRepository.findByUsername(principal.username())
                .orElseThrow(() -> new AuthenticationException("Authenticated user no longer exists"));

        List<String> roles = userRoleRepository.findByUserUsername(principal.username()).stream()
                .map(userRole -> "ROLE_" + userRole.getRole().name())
                .toList();

        return new AuthenticatedUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getActive(),
                roles
        );
    }

    /**
     * Recupera el principal del contexto actual.
     *
     * <p>Se expone como método de instancia (no estático) para que la clase pueda mockearse en los
     * tests de controller sin tocar el estado global de Spring Security.
     */
    protected SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new AuthenticationException("No authenticated principal");
        }
        return principal;
    }

    private void validatePasswordBytes(String password) {
        if (password == null || password.isBlank() || password.length() < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 12 characters");
        }
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must not exceed " + MAX_PASSWORD_BYTES + " bytes");
        }
    }
}
