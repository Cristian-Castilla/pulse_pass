package com.pulsepass.pass.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class CorsPropertiesTest {
    @Test void noAllowedOriginIsTheRestrictiveDefault() {
        assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
    }
    @Test void acceptsExplicitOriginsAndCopiesTheList() {
        var origins = new java.util.ArrayList<>(List.of("https://pulsepass.example", "http://localhost:3000"));
        var properties = new CorsProperties(origins);
        origins.clear();
        assertThat(properties.allowedOrigins()).hasSize(2);
    }
    @ParameterizedTest
    @ValueSource(strings={"*","https://*.example.com","https://example.com/","https://example.com/api",
            "https://user:password@example.com","https://example.com?all=true","https://example.com#fragment",
            "ftp://example.com","https://example.com:65536","not-an-origin"})
    void rejectsWildcardsPathsCredentialsAndInvalidOrigins(String origin) {
        assertThatThrownBy(() -> new CorsProperties(List.of(origin))).isInstanceOf(IllegalArgumentException.class);
    }
}
