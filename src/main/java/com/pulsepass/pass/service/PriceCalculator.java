package com.pulsepass.pass.service;

import com.pulsepass.pass.domain.TicketType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Estrategia de precios para tickets.
 *
 * Reglas:
 * - GENERAL → precio base (1.0x)
 * - STUDENT → 20% de descuento (0.8x)
 * - VIP → 2.5x el precio base
 * - BACKSTAGE → 4.0x el precio base
 *
 * El precio base se define como una constante académica.
 * En producción podría venir de una tabla de precios o un servicio externo.
 */
@Component
public class PriceCalculator {

    private static final BigDecimal BASE_PRICE = new BigDecimal("100000");

    private static final BigDecimal STUDENT_DISCOUNT = new BigDecimal("0.80");
    private static final BigDecimal VIP_MULTIPLIER = new BigDecimal("2.50");
    private static final BigDecimal BACKSTAGE_MULTIPLIER = new BigDecimal("4.00");

    public BigDecimal calculatePrice(TicketType type) {
        BigDecimal multiplier = switch (type) {
            case GENERAL -> BigDecimal.ONE;
            case STUDENT -> STUDENT_DISCOUNT;
            case VIP -> VIP_MULTIPLIER;
            case BACKSTAGE -> BACKSTAGE_MULTIPLIER;
        };
        return BASE_PRICE.multiply(multiplier);
    }
}
