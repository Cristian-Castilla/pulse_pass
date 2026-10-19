package com.pulsepass.pass.security;

import com.pulsepass.pass.dto.response.AuthResponse;
import com.pulsepass.pass.exception.AuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de emisión y validación del JWT. Sin contexto de Spring: se construye el servicio a mano.
 *
 * PRD de Seguridad §17.1: "JwtServiceTest: firma correcta; claims correctos; expiración; issuer
 * incorrecto; manipulación; token vacío; clave débil".
 * PRD §12.2: "Comparar algoritmo esperado y verificar criptográficamente firma antes de confiar en
 * claims".
 */
class JwtServiceTest {

    private static final String ISSUER = "pulsepass-api";
    private static final Duration TTL = Duration.ofMinutes(15);

    /**
     * Clave de 48 bytes (384 bits) en Base64: supera el mínimo de 32 bytes que exige el PRD §12.3.
     */
    private static final String VALID_SECRET_BASE64 =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());

    private JwtServiceImpl jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer(ISSUER);
        properties.setSecretBase64(VALID_SECRET_BASE64);
        properties.setAccessTokenTtl(TTL);
        jwtService = new JwtServiceImpl(properties);
    }

    private SecretKey key() {
        return io.jsonwebtoken.security.Keys.hmacShaKeyFor(Decoders.BASE64.decode(VALID_SECRET_BASE64));
    }

    private String tokenSignedWithOtherKey() {
        SecretKey otherKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(Base64.getEncoder().encodeToString(
                        "ffffffffffffffffffffffffffffffffffffffffffffffff".getBytes())));
        return Jwts.builder()
                .subject("42")
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();
    }

    // ------------------------------------------------------------- emisión

    @Test
    void issueToken_producesSignedTokenWithAllClaims() {
        // PRD §12.1: claims sub, iss, iat, exp, jti
        AuthResponse response = jwtService.issueToken(42L);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();

        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(response.accessToken())
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getId()).isNotBlank();
        assertThat(jwtService.expectedAlgorithm()).isEqualTo("HS256");
    }

    @Test
    void issueToken_exposesTtlInSeconds() {
        // PRD §10.1: "expiresIn" en segundos (900 para 15 minutos)
        AuthResponse response = jwtService.issueToken(1L);

        assertThat(response.expiresIn()).isEqualTo(TTL.getSeconds());
    }

    @Test
    void issueToken_setsExpirationFromConfiguredTtl() {
        JwtProperties shortTtl = new JwtProperties();
        shortTtl.setIssuer(ISSUER);
        shortTtl.setSecretBase64(VALID_SECRET_BASE64);
        shortTtl.setAccessTokenTtl(Duration.ofMinutes(5));
        JwtServiceImpl service = new JwtServiceImpl(shortTtl);

        assertThat(service.issueToken(1L).expiresIn()).isEqualTo(300L);
    }

    @Test
    void issueToken_doesNotLeakSensitiveDataInPayload() {
        // PRD §12.1: "Nunca incluir contraseña, documento personal, correo privado innecesario ni
        // datos sensibles en el payload"
        AuthResponse response = jwtService.issueToken(42L);

        String payload = response.accessToken().split("\\.")[1];
        String decoded = new String(Decoders.BASE64URL.decode(payload));

        assertThat(decoded).doesNotContain("password");
        assertThat(decoded).doesNotContain("hash");
        assertThat(decoded).doesNotContain("@");
        assertThat(decoded).contains("\"sub\":\"42\"");
    }

    @Test
    void issueToken_generatesUniqueJtiPerToken() {
        // jti aleatorio: dos tokens para el mismo usuario no deben ser idénticos
        String first = jwtService.issueToken(42L).accessToken();
        String second = jwtService.issueToken(42L).accessToken();

        assertThat(first).isNotEqualTo(second);
        assertThat(Jwts.parser().verifyWith(key()).build().parseSignedClaims(first).getPayload().getId())
                .isNotEqualTo(Jwts.parser().verifyWith(key()).build().parseSignedClaims(second).getPayload().getId());
    }

    // ------------------------------------------------------------ validación

    @Test
    void validate_extractsSubjectFromValidToken() {
        String token = jwtService.issueToken(42L).accessToken();

        assertThat(jwtService.validateAndExtractSubject(token)).isEqualTo(42L);
    }

    @Test
    void validate_rejectsTokenWithWrongIssuer() {
        // PRD §12.1: "iss ... Debe coincidir con issuer esperado"
        String token = Jwts.builder()
                .subject("42")
                .issuer("some-other-api")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject(token));

        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void validate_rejectsTokenSignedWithDifferentKey() {
        // PRD §12.2: "verificar criptográficamente firma antes de confiar en claims"
        String token = tokenSignedWithOtherKey();

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject(token));

        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void validate_rejectsTamperedPayload() {
        // Se altera el payload pero se conserva la firma original: la verificación debe fallar
        String token = jwtService.issueToken(42L).accessToken();
        String[] parts = token.split("\\.");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"iss\":\"pulsepass-api\"}".getBytes());
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject(tampered));
    }

    @Test
    void validate_rejectsExpiredToken() {
        // PRD §10 FR-SEC-AUTH-006: "Rechaza JWT expirado"
        JwtProperties expired = new JwtProperties();
        expired.setIssuer(ISSUER);
        expired.setSecretBase64(VALID_SECRET_BASE64);
        expired.setAccessTokenTtl(Duration.ofSeconds(1));
        JwtServiceImpl service = new JwtServiceImpl(expired);

        String token = service.issueToken(42L).accessToken();

        // Se espera a que venza de verdad: el reloj del sistema es la fuente de verdad de la expiración
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        assertThrows(AuthenticationException.class, () -> service.validateAndExtractSubject(token));
    }

    @Test
    void validate_rejectsEmptyAndBlankTokens() {
        assertThrows(AuthenticationException.class, () -> jwtService.validateAndExtractSubject(null));
        assertThrows(AuthenticationException.class, () -> jwtService.validateAndExtractSubject(""));
        assertThrows(AuthenticationException.class, () -> jwtService.validateAndExtractSubject("   "));
    }

    @Test
    void validate_rejectsMalformedToken() {
        // PRD §10 FR-SEC-AUTH-006: "token malformado"
        assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject("not-a-jwt"));
        assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject("a.b.c"));
    }

    @Test
    void validate_rejectsUnsignedToken() {
        // PRD §12: "No permitir alg=none ni token unsigned"
        String unsigned = Jwts.builder()
                .subject("42")
                .issuer(ISSUER)
                .compact();

        assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject(unsigned));
    }

    @Test
    void validate_rejectsTokenWithoutSubject() {
        // PRD §14.4: "La validación debe imponer exp, sub y algoritmo esperado"
        String token = Jwts.builder()
                .issuer(ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();

        assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject(token));
    }

    @Test
    void validate_neverLeaksFailureReason() {
        // PRD §8 SEC-009 y §10 FR-SEC-AUTH-003: el mensaje no debe permitir distinguir la causa
        String expired = Jwts.builder()
                .subject("42")
                .issuer(ISSUER)
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key(), Jwts.SIG.HS256)
                .compact();

        AuthenticationException expiredEx = assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject(expired));
        AuthenticationException tamperedEx = assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject(tokenSignedWithOtherKey()));
        AuthenticationException malformedEx = assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject("not-a-jwt"));

        assertThat(expiredEx.getMessage())
                .isEqualTo(tamperedEx.getMessage())
                .isEqualTo(malformedEx.getMessage())
                .isEqualTo("Invalid credentials");
    }

    // ---------------------------------------------------------- clave débil

    @Test
    void constructor_failsWhenSecretIsMissing() {
        // PRD §14.2: "la aplicación debe fallar al arrancar si la clave está ausente"
        JwtProperties properties = new JwtProperties();
        properties.setIssuer(ISSUER);
        properties.setSecretBase64(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtServiceImpl(properties));

        assertThat(ex.getMessage()).contains("secret-base64 is required");
    }

    @Test
    void constructor_failsWhenSecretIsBlank() {
        JwtProperties properties = new JwtProperties();
        properties.setSecretBase64("   ");

        assertThrows(IllegalStateException.class, () -> new JwtServiceImpl(properties));
    }

    @Test
    void constructor_failsWhenSecretIsShorterThan32Bytes() {
        // PRD §12.3: "al menos 32 bytes aleatorios"
        JwtProperties properties = new JwtProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString("short".getBytes()));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtServiceImpl(properties));

        assertThat(ex.getMessage()).contains("at least 32 bytes");
    }

    @Test
    void constructor_failsWhenSecretIsNotBase64() {
        JwtProperties properties = new JwtProperties();
        properties.setSecretBase64("this-is-not-valid-base64!!!");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtServiceImpl(properties));

        assertThat(ex.getMessage()).contains("valid Base64");
    }

    @Test
    void constructor_acceptsExactly32Bytes() {
        // El límite es inclusivo: 32 bytes es válido
        JwtProperties properties = new JwtProperties();
        properties.setSecretBase64(Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes()));

        JwtServiceImpl service = new JwtServiceImpl(properties);
        assertThat(service.issueToken(1L).accessToken()).isNotBlank();
    }

    // ------------------------------------------------- integración emisión → validación

    @Test
    void issuedToken_roundTripsThroughValidation() {
        // El token emitido debe ser aceptado por la misma clave
        String token = jwtService.issueToken(7L).accessToken();

        assertThat(jwtService.validateAndExtractSubject(token)).isEqualTo(7L);
    }

    @Test
    void validation_throwsAuthenticationExceptionForRejection() {
        // El contrato de la interfaz es JwtException, pero la implementación lanza
        // AuthenticationException (un RuntimeException) para que el GlobalExceptionHandler la
        // mapee a 401 con el contrato ErrorResponse. Lo importante es que NUNCA se propague una
        // excepción del framework hacia la capa web.
        String token = tokenSignedWithOtherKey();

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> jwtService.validateAndExtractSubject(token));

        assertThat(ex).isNotNull();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
