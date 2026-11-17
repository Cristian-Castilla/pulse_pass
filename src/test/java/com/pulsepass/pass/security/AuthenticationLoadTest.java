package com.pulsepass.pass.security;

import com.pulsepass.pass.PostgresContainerSupport;
import com.pulsepass.pass.domain.*;
import com.pulsepass.pass.repository.*;
import com.pulsepass.pass.service.JwtService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class AuthenticationLoadTest extends PostgresContainerSupport {
    @Autowired private UserRepository users;
    @Autowired private UserCredentialRepository credentials;
    @Autowired private UserRoleRepository roles;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwt;
    @Autowired private JwtAuthenticationFilter filter;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;

    private Long account(String suffix, Role... authorities) {
        User user = users.saveAndFlush(new User("load-"+suffix, "load-"+suffix+"@example.com", true));
        credentials.saveAndFlush(new UserCredential(user, encoder.encode("Fictitious-load-test!"), null));
        for (Role role : authorities) roles.saveAndFlush(new UserRole(user, role));
        return user.getId();
    }

    private long measure(Long id, int expectedRoles) throws Exception {
        em.flush();
        em.clear();
        SecurityContextHolder.clearContext();
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer "+jwt.issueToken(id).accessToken());
        long start = System.nanoTime();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        long elapsed = System.nanoTime()-start;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).hasSize(expectedRoles);
        long queries = statistics.getPrepareStatementCount();
        System.out.printf("AUTH_LOAD roles=%d statements=%d elapsedMicros=%d%n", expectedRoles, queries, elapsed/1000);
        return queries;
    }

    @Test void currentIdentityLoadingHasBoundedSqlCostIndependentOfRoleCount() throws Exception {
        Long single = account("single", Role.USER);
        Long multiple = account("multiple", Role.USER, Role.STAFF, Role.ADMIN);
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            long one = measure(single, 1);
            long three = measure(multiple, 3);
            assertThat(one).isBetween(1L, 4L);
            assertThat(three).isEqualTo(one);
        } finally {
            statistics.setStatisticsEnabled(previous);
            SecurityContextHolder.clearContext();
        }
    }
}
