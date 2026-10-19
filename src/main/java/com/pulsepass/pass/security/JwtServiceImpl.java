package com.pulsepass.pass.security;

import com.pulsepass.pass.dto.response.AuthResponse;
import com.pulsepass.pass.exception.AuthenticationException;
import com.pulsepass.pass.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emisión y validación de JWT firmados con HS256. PRD de Seguridad §12.
 *
 * <p>Patrón obligatorio del PRD §14.4:
 *
 * <pre>
 * SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
 *
 * String token = Jwts.builder()
 *         .subject(userId.toString())
 *         .issuer(expectedIssuer)
 *         .issuedAt(Date.from(now))
 *         .expiration(Date.from(now.plusSeconds(900)))
 *         .id(UUID.randomUUID().toString())
 *         .signWith(key, Jwts.SIG.HS256)
 *         .compact();
 *
 * Claims claims = Jwts.parser()
 *         .verifyWith(key)
 *         .requireIssuer(expectedIssuer)
 *         .build()
 *         .parseSignedClaims(token)
 *         .getPayload();
 * </pre>
 *
 * <p>PRD §14.4: "Nunca usar APIs obsoletas de JJWT como setSigningKey / parseClaimsJws en código
 * nuevo". Se usan las APIs de la 0.13.x ({@code signWith}, {@code parseSignedClaims}).
 *
 * <p>PRD §12.2: "Comparar algoritmo esperado y verificar criptográficamente firma antes de confiar
 * en claims". {@code verifyWith(key)} fuerza HS256 porque la clave es HMAC: con una clave HMAC no es
 * posible firmar con {@code alg=none} ni con un algoritmo asimétrico, y JJWT lo rechaza.
 */
@Service
public class JwtServiceImpl implements JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtServiceImpl.class);

    /**
     * Algoritmo declarado explícitamente. El PRD §12 lo pide ("HS256 explícito") y sirve como
     * documentación y como punto de comparación al auditar el token recibido.
     */
    private static final String EXPECTED_ALGORITHM = "HS256";

    /**
     * Longitud mínima de la clave en bytes. PRD §12.3: "al menos 32 bytes aleatorios".
     * HS256 con menos de 256 bits es débil y JJWT lo rechaza con una clave más corta; el mensaje de
     * error del framework es técnico, así que se valida antes con uno que explica la causa.
     */
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtServiceImpl(JwtProperties properties) {
        this.properties = properties;
        this.key = resolveKey(properties.getSecretBase64());
    }

    /**
     * Emite un token para el usuario dado.
     *
     * <p>PRD §12.1: los claims son {@code sub} (id persistente), {@code iss}, {@code iat},
     * {@code exp} y {@code jti}. No se incluyen roles: PRD §12.3 pide consultar los roles actuales
     * en persistencia para que una revocación de rol entre en vigencia sin esperar al vencimiento.
     *
     * <p>PRD §12.1: "Nunca incluir contraseña, documento personal, correo privado innecesario ni
     * datos sensibles en el payload". El payload lleva solo el id y los metadatos de tiempo.
     */
    @Override
    public AuthResponse issueToken(Long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        long expiresInSeconds = Duration.between(now, expiresAt).getSeconds();
        return new AuthResponse(token, "Bearer", expiresInSeconds);
    }

    /**
     * Valida firma, emisor, algoritmo y vencimiento, y devuelve el sujeto.
     *
     * <p>PRD §10 FR-SEC-AUTH-006: "Rechaza JWT expirado, manipulado, issuer erróneo, token malformado o
     * algoritmo inesperado".
     *
     * <p>PRD §14.4: "La validación debe imponer exp, sub y algoritmo esperado, no asumir su presencia
     * por haber parseado la firma". Por eso se exige el subject de forma explícita después de parsear.
     *
     * <p>El mensaje es siempre el mismo: PRD §10 FR-SEC-AUTH-003 exige no revelar detalles, y un
     * mensaje distinto para "expirado" vs "manipulado" permitiría distinguir tokens válidos de
     * inválidos y diagnosticar el sistema.
     */
    @Override
    public Long validateAndExtractSubject(String token) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException("Invalid credentials");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                throw new AuthenticationException("Invalid credentials");
            }

            return Long.valueOf(subject);
        } catch (AuthenticationException ex) {
            throw ex;
        } catch (JwtException | IllegalArgumentException ex) {
            // No se registra el token ni el detalle del error: PRD §8 SEC-009 y NFR-SEC-001
            // prohíben filtrar información interna en las respuestas.
            log.debug("JWT rejected: {}", ex.getClass().getSimpleName());
            throw new AuthenticationException("Invalid credentials");
        }
    }

    /**
     * Deriva la clave HMAC desde el Base64 de la configuración.
     *
     * <p>PRD §14.2: "la aplicación debe fallar al arrancar si la clave está ausente". Sin clave no hay
     * firma posible, y usar una clave vacía o por defecto daría una falsa sensación de seguridad.
     */
    private SecretKey resolveKey(String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalStateException(
                    "pulsepass.security.jwt.secret-base64 is required. Set PULSEPASS_JWT_SECRET_BASE64 "
                            + "to a Base64-encoded secret of at least " + MIN_SECRET_BYTES + " random bytes.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretBase64);
        } catch (JwtException ex) {
            // Decoders.BASE64.decode lanza io.jsonwebtoken.io.DecodingException cuando la cadena no es
            // Base64 válido. Su jerarquía es:
            //   DecodingException -> CodecException -> io.jsonwebtoken.io.IOException -> JwtException
            // No desciende de IllegalArgumentException, así que hay que capturar JwtException.
            // Se envuelve para que el mensaje explique la causa en términos de la configuración y no
            // en términos de la librería.
            throw new IllegalStateException(
                    "pulsepass.security.jwt.secret-base64 must be valid Base64.", ex);
        }

        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "pulsepass.security.jwt.secret-base64 must decode to at least " + MIN_SECRET_BYTES
                            + " bytes; got " + keyBytes.length + ".");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Expone el algoritmo declarado, para que los tests puedan contrastarlo con el header del token.
     */
    public String expectedAlgorithm() {
        return EXPECTED_ALGORITHM;
    }
}
