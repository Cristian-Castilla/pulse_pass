package com.pulsepass.pass.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración del JWT. PRD de Seguridad §14.2:
 *
 * <pre>
 * pulsepass:
 *   security:
 *     jwt:
 *       issuer: ${PULSEPASS_JWT_ISSUER:pulsepass-api}
 *       secret-base64: ${PULSEPASS_JWT_SECRET_BASE64}
 *       access-token-ttl: PT15M
 * </pre>
 *
 * <p>PRD §8 SEC-004: "Clave JJWT desde entorno; prohibida en código fuente". El valor llega por
 * variable de entorno y no hay default para él: la ausencia debe fallar el arranque, no resolverse
 * con una clave inventada.
 *
 * <p>PRD §14.2: "No subir un secreto real a .env versionado ni a application.yml. Documentar una
 * plantilla .env.example sin secreto".
 *
 * <p>La duración por defecto es 15 minutos (PRD §10.1: "900 segundos (15 minutos)"). Es un valor de
 * diseño parametrizable, no una constante del código.
 */
@ConfigurationProperties(prefix = "pulsepass.security.jwt")
public class JwtProperties {

    /**
     * Emisor esperado. PRD §12.1: "iss: pulsepass-api o valor configurado. Debe coincidir con issuer
     * esperado". El token se firma con este emisor y se rechaza cualquier token con otro.
     */
    private String issuer = "pulsepass-api";

    /**
     * Clave secreta en Base64. PRD §12.3: "al menos 32 bytes aleatorios".
     *
     * <p>No tiene valor por defecto: la ausencia debe detener el arranque (ver
     * {@code JwtServiceImpl}), porque una clave vacía o inventada rompe la garantía de firma.
     */
    private String secretBase64;

    /**
     * Vida del token de acceso. PRD §10.1: 900 segundos (15 minutos). PRD §12.3: "La expiración
     * limita la validez de tokens robados".
     */
    private Duration accessTokenTtl = Duration.ofMinutes(15);

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSecretBase64() {
        return secretBase64;
    }

    public void setSecretBase64(String secretBase64) {
        this.secretBase64 = secretBase64;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }
}
