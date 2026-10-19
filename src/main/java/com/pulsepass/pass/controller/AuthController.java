package com.pulsepass.pass.controller;

import com.pulsepass.pass.dto.request.LoginRequest;
import com.pulsepass.pass.dto.response.AuthResponse;
import com.pulsepass.pass.dto.response.AuthenticatedUserResponse;
import com.pulsepass.pass.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticación de la Fase 4.
 *
 * PRD de Seguridad §10.1: "POST /api/auth/login" y "GET /api/auth/me".
 * PRD §3.3: "Se conservan las 20 operaciones originales... AuthenticationEntryPoint y
 * AccessDeniedHandler transforman en JSON los errores originados en el filtro de seguridad".
 *
 * <p>El registro NO vive aquí: PRD §3.4 mantiene {@code POST /api/users} en {@code UserController}.
 * Este controlador solo expone login y la identidad del principal.
 *
 * <p>El controlador es deliberadamente delgado (PRD §7.1: "Controllers — Recibir requests y delegar.
 * Prohibido: Interpretar JWT manualmente"): no toca el token, no consulta la base, no decide permisos.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * PRD §10 FR-SEC-AUTH-002: "Autentica usuario/correo + contraseña; 200 con token y metadatos".
     * PRD §10.1: el acceso es "Público" — no requiere token.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.identifier(), request.password()));
    }

    /**
     * PRD §10 FR-SEC-AUTH-010: "GET /api/auth/me retorna información mínima del principal".
     * PRD §10.1: el acceso es "Autenticado".
     */
    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> currentUser() {
        return ResponseEntity.ok(authService.currentUser());
    }
}
