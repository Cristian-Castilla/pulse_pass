package com.pulsepass.pass.controller;

import com.pulsepass.pass.dto.request.RegisterUserRequest;
import com.pulsepass.pass.dto.response.UserResponse;
import com.pulsepass.pass.service.AuthService;
import com.pulsepass.pass.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    public UserController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /**
     * Registro de cuenta.
     *
     * PRD de Seguridad §3.4: "POST /api/users mantiene su URL y semántica de creación de cuenta".
     * PRD §9.3: "Si se conserva la invocación directa de UserService.register() desde Controller, se debe
     * refactorizar de forma explícita para garantizar atomicidad".
     *
     * <p>Por eso el registro se delega en {@code AuthService}, que crea usuario, credencial y rol en una
     * única transacción. La URL y el contrato de salida (201 + UserResponse) no cambian.
     */
    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/by-email")
    public ResponseEntity<UserResponse> findByEmail(@RequestParam String email) {
        return ResponseEntity.ok(userService.findByEmail(email));
    }

    @GetMapping("/by-username")
    public ResponseEntity<UserResponse> findByUsername(@RequestParam String username) {
        return ResponseEntity.ok(userService.findByUsername(username));
    }
}
