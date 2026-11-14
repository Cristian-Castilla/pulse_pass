package com.pulsepass.pass.service;

import com.pulsepass.pass.domain.TicketType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class PriceCalculatorTest {
    @ParameterizedTest
    @CsvSource({"GENERAL,100000", "STUDENT,80000", "VIP,250000", "BACKSTAGE,400000"})
    void realPolicyCalculatesEveryTicketType(TicketType type, BigDecimal expected) {
        BigDecimal price = new PriceCalculator().calculatePrice(type);
        assertThat(price).isEqualByComparingTo(expected).isNotNegative();
    }
}
