package com.pulsepass.pass.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterUserRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        String phone,

        String city,

        @NotNull(message = "Birth date is required")
        LocalDate birthDate,

        /**
         * Contraseña del registro. PRD de Seguridad §3.4: "RegisterUserRequest — Se extiende con
         * password obligatoria para un registro nuevo; cambio de contrato de entrada documentado".
         *
         * <p>PRD §9.3: "se validará longitud razonable (mínimo académico: 12 caracteres, máximo 72
         * bytes si se emplea BCrypt tradicional)".
         *
         * <p>El máximo del PRD es 72 bytes, no 72 caracteres: BCrypt procesa como máximo 72 bytes de
         * entrada y un carácter multibyte (ñ, acentos, caracteres fuera de BMP) ocupa más de un byte.
         * Bean Validation mide caracteres, así que el límite de bytes se aplica además en el servicio
         * (ver {@code AuthService}). El límite de 72 caracteres de la anotación es el que el framework
         * puede evaluar; el de bytes es el que respeta el límite real de BCrypt.
         *
         * <p>Nunca se devuelve: {@code UserResponse} no incluye la contraseña ni el hash.
         */
        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 72, message = "Password must contain between 12 and 72 characters")
        String password
) {}
