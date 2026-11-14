package com.pulsepass.pass.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();
    @AfterEach void cleanup() { MDC.clear(); }

    @Test void responseAndLogsShareGeneratedIdAndNoThreadLocalLeaks() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("X-Request-ID", "client-controlled");
        filter.doFilter(request, response, (req, res) -> {
            String id = response.getHeader("X-Request-ID");
            assertThat(java.util.UUID.fromString(id).toString()).isEqualTo(id);
            assertThat(MDC.get("requestId")).isEqualTo(id);
            assertThat(id).isNotEqualTo("client-controlled");
        });
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void restoresPreviousMdcEvenWhenDownstreamFails() {
        MDC.put("requestId", "outer");
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (req, res) -> {
                    assertThat(MDC.get("requestId")).isNotEqualTo("outer");
                    throw new jakarta.servlet.ServletException("test failure");
                })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(MDC.get("requestId")).isEqualTo("outer");
    }
}
