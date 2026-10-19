package com.pulsepass.pass.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Credenciales de acceso de un usuario. Relación 1:1 con {@link User}.
 *
 * PRD de Seguridad §9.1: "UserCredential (NUEVA, 1:1 con User) ├── user_id (PK y FK unique)
 * ├── password_hash (NOT NULL) └── password_changed_at (opcional)".
 *
 * <p>Se mantiene separada de {@link User} a propósito (PRD §9.1): "Esta separación evita modificar
 * indebidamente DTOs de negocio y elimina cualquier posibilidad de serializar passwordHash desde
 * MapStruct por accidente."
 *
 * <p>{@code @MapsId} reutiliza el id de {@code User} como PK de esta tabla, garantizando el 1:1
 * sin una columna adicional.
 *
 * <p>{@code cascade = CascadeType.ALL} es necesario porque la PK es compartida: sin cascada,
 * borrar un {@code User} deja una credencial apuntando a una instancia transient y Hibernate
 * falla con {@code TransientPropertyValueException} antes de evaluar la FK.
 */
@Entity
@Table(name = "user_credentials")
public class UserCredential {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    protected UserCredential() {
    }

    public UserCredential(User user, String passwordHash, LocalDateTime passwordChangedAt) {
        this.user = user;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = passwordChangedAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserCredential that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
