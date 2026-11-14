package com.pulsepass.pass.security;

import com.pulsepass.pass.PostgresContainerSupport;
import com.pulsepass.pass.domain.*;
import com.pulsepass.pass.dto.request.RegisterUserRequest;
import com.pulsepass.pass.repository.*;
import com.pulsepass.pass.service.AuthService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** No outer test transaction: service commits/rollbacks are observed from a fresh transaction. */
class AuthRegistrationIntegrationTest extends PostgresContainerSupport {
    private static final String PASSWORD = "FictitiousAtomicPassword2026!";
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired UserCredentialRepository credentials;
    @MockitoSpyBean UserRoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate sql;

    private TransactionTemplate freshTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
    private RegisterUserRequest request(String username) {
        return new RegisterUserRequest(username, username + "@example.com", "First", "Last",
                "3001234567", "City", LocalDate.now().minusYears(25), PASSWORD);
    }

    @AfterEach
    void removeCommittedFixtures() {
        reset(roles);
        freshTransaction().executeWithoutResult(status -> {
            for (String table : new String[]{"user_roles", "user_profiles", "user_credentials"}) {
                sql.update("DELETE FROM " + table + " WHERE user_id IN (SELECT id FROM users WHERE username IN ('atomicok','atomicfail'))");
            }
            sql.update("DELETE FROM users WHERE username IN ('atomicok','atomicfail')");
        });
    }

    @Test
    void registrationCommitsProfileVerifiableBcryptAndOnlyUserRole() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var response = auth.register(request("atomicok"));
        freshTransaction().executeWithoutResult(status -> {
            User user = users.findById(response.id()).orElseThrow();
            assertThat(user.getActive()).isTrue();
            assertThat(user.getProfile().getFirstName()).isEqualTo("First");
            assertThat(user.getProfile().getBirthDate()).isEqualTo(request("atomicok").birthDate());
            String hash = credentials.findById(user.getId()).orElseThrow().getPasswordHash();
            assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2");
            assertThat(encoder.matches(PASSWORD, hash)).isTrue();
            assertThat(roles.findByUserUsername("atomicok")).extracting(UserRole::getRole).containsExactly(Role.USER);
        });
    }

    @Test
    void lastRoleWriteFailureRollsBackAlreadyFlushedUserProfileAndCredential() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        java.util.concurrent.atomic.AtomicLong userId = new java.util.concurrent.atomic.AtomicLong();
        doAnswer(invocation -> {
            em.flush();
            User user = users.findByUsername("atomicfail").orElseThrow();
            userId.set(user.getId());
            assertThat(sql.queryForObject("SELECT count(*) FROM user_profiles WHERE user_id=?", Long.class, user.getId())).isEqualTo(1);
            assertThat(credentials.findById(user.getId())).isPresent();
            throw new DataIntegrityViolationException("Fictitious last-write failure");
        }).when(roles).save(any(UserRole.class));
        assertThrows(DataIntegrityViolationException.class, () -> auth.register(request("atomicfail")));
        verify(roles).save(any(UserRole.class));
        freshTransaction().executeWithoutResult(status -> {
            assertThat(users.findByUsername("atomicfail")).isEmpty();
            assertThat(userId.get()).isPositive();
            for (String table : new String[]{"user_profiles", "user_credentials", "user_roles"}) {
                assertThat(sql.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?", Long.class, userId.get())).isZero();
            }
            assertThat(roles.findByUserUsername("atomicfail")).isEmpty();
        });
    }
}
