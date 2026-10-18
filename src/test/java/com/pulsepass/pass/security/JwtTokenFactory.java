package com.pulsepass.pass.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Fábrica de tokens de prueba para los tests de seguridad.
 *
 * PRD §17.3: "En tests usar contraseñas y secretos exclusivamente ficticios y no reciclar
 * credenciales de producción".
 *
 * <p>Genera tokens con la misma clave que usa el perfil de tests, para poder construir escenarios
 * que el flujo normal no produce (por ejemplo, un token ya vencido).
 */
final class JwtTokenFactory {

    /**
     * La misma clave ficticia de 48 bytes que declara {@code src/test/resources/application.yml}.
     * Debe coincidir o la firma no será válida para el filtro.
     */
    private static final String TEST_SECRET_BASE64 =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVm";

    private static final String ISSUER = "pulsepass-api";

    private JwtTokenFactory() {
    }

    static SecretKey key() {
        return Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(TEST_SECRET_BASE64));
    }

    /** Token firmado con la clave de tests pero con fecha de vencimiento en el pasado. */
    static String expiredToken() {
        SecretKey key = key();
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject("1")
                .issuer(ISSUER)
                .issuedAt(Date.from(past.minus(1, ChronoUnit.DAYS)))
                .expiration(Date.from(past))
                .id(java.util.UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Token válido y vigente para el id dado, con la clave ficticia del perfil de tests. */
    static String tokenFor(Long userId) {
        SecretKey key = key();
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .id(java.util.UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
