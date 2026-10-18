package com.pulsepass.pass.service;

import com.pulsepass.pass.dto.response.AuthResponse;

/**
 * Emite y valida los tokens de acceso firmados.
 *
 * PRD de Seguridad §12: "Algoritmo: HS256 explícito. No permitir alg=none ni token unsigned".
 * PRD §12.1: claims mínimos {@code sub}, {@code iss}, {@code iat}, {@code exp}, {@code jti}.
 * PRD §12.2: "Comparar algoritmo esperado y verificar criptográficamente firma antes de confiar en claims".
 *
 * <p>El rol de este servicio es solo criptográfico. PRD §7.1: "JwtService — Emitir y validar firma,
 * issuer, tipo, fechas y sujeto. Prohibido: Consultar tickets o ejecutar negocio".
 *
 * <p><b>Esta interfaz se implementa en la Etapa 5.</b> Se declara ahora para que {@code AuthService}
 * pueda escribirse contra un contrato estable y no contra una clase concreta que aún no existe.
 */
public interface JwtService {

    /**
     * Emite un token de acceso para el usuario dado.
     *
     * @param userId id persistente del usuario; se guarda como claim {@code sub} (PRD §12.1:
     *               "ID interno inmutable del usuario")
     * @return respuesta con el token, el tipo y la duración en segundos
     */
    AuthResponse issueToken(Long userId);

    /**
     * Valida firma, emisor, algoritmo y vencimiento, y devuelve el sujeto.
     *
     * @throws io.jsonwebtoken.JwtException si el token está vencido, manipulado, mal formado o
     *                                      firmado con un algoritmo distinto al esperado
     */
    Long validateAndExtractSubject(String token);
}
