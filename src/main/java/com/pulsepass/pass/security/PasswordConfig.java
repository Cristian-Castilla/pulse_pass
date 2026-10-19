package com.pulsepass.pass.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Beans de cifrado de la Fase 4.
 *
 * PRD de Seguridad §5.1: "UserDetailsService, PasswordEncoder BCrypt y validación de estado activo".
 * PRD §8 SEC-003: "Contraseñas BCrypt, nunca en texto plano ni logs".
 *
 * <p>El encoder es único y compartido por todo el sistema: quien hashea (registro, semilla de tests)
 * y quien compara (login) deben usar el mismo algoritmo y la misma fuerza.
 *
 * <p>BCrypt incluye un salt aleatorio en cada hash, por eso el mismo texto plano produce hashes
 * distintos: la comparación debe hacerse siempre con {@code matches}, nunca comparando cadenas.
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt con la fuerza por defecto del framework (10 rondas, 2^10 iteraciones).
     * El PRD no exige una fuerza específica; la del framework es la recomendación por defecto.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
