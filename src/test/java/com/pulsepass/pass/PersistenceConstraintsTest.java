package com.pulsepass.pass;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
class PersistenceConstraintsTest extends PostgresContainerSupport {
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void fixtures() {
        jdbc.update("INSERT INTO users(username,email) VALUES ('constraint-user','constraint@example.test')");
        jdbc.update("INSERT INTO venues(code,name,city,address,capacity) VALUES ('CHECK-V','Venue','City','Address',1)");
        jdbc.update("INSERT INTO events(event_code,name,description,category,status,event_date,venue_id) "
                + "SELECT 'CHECK-E','Event','Description','MUSIC','PUBLISHED',TIMESTAMP '2030-01-01 19:00:00',id FROM venues WHERE code='CHECK-V'");
    }

    static Stream<Arguments> invalidRows() {
        String user = "(SELECT id FROM users WHERE username='constraint-user')";
        String event = "(SELECT id FROM events WHERE event_code='CHECK-E')";
        String ticket = "INSERT INTO tickets(ticket_code,type,price,status,purchase_date,user_id,event_id) VALUES ('CHECK-T',%s,%s,%s,CURRENT_TIMESTAMP,%s,%s)";
        return Stream.of(
                Arguments.of("INSERT INTO venues(code,name,city,address,capacity) VALUES ('BAD','Venue','City','Address',0)", "chk_venue_capacity", "23514"),
                Arguments.of("INSERT INTO venues(code,name,city,address,capacity) VALUES ('BAD','Venue','City','Address',-1)", "chk_venue_capacity", "23514"),
                Arguments.of(ticket.formatted("'GENERAL'", "-0.01", "'PAID'", user, event), "chk_ticket_price", "23514"),
                Arguments.of(ticket.formatted("'GENERAL'", "0", "'PAID'", "-1", event), "fk_ticket_user", "23503"),
                Arguments.of(ticket.formatted("'GENERAL'", "0", "'PAID'", user, "-1"), "fk_ticket_event", "23503"),
                Arguments.of(ticket.formatted("'UNKNOWN'", "0", "'PAID'", user, event), "chk_ticket_type", "23514"),
                Arguments.of(ticket.formatted("'GENERAL'", "0", "'UNKNOWN'", user, event), "chk_ticket_status", "23514"),
                Arguments.of("INSERT INTO user_roles(user_id,role) VALUES (" + user + ",'SUPERUSER')", "chk_user_role", "23514"),
                Arguments.of("INSERT INTO user_roles(user_id,role) VALUES (-1,'USER')", "fk_user_roles_user", "23503"),
                Arguments.of("INSERT INTO user_credentials(user_id,password_hash) VALUES (-1,'test-only-hash')", "fk_user_credentials_user", "23503"),
                Arguments.of("INSERT INTO user_profiles(first_name,last_name,user_id) VALUES ('First','Last',-1)", "fk_user_profile_user", "23503"),
                Arguments.of("UPDATE events SET venue_id=-1 WHERE event_code='CHECK-E'", "fk_event_venue", "23503"),
                Arguments.of("UPDATE events SET category='UNKNOWN' WHERE event_code='CHECK-E'", "chk_event_category", "23514"),
                Arguments.of("UPDATE events SET status='UNKNOWN' WHERE event_code='CHECK-E'", "chk_event_status", "23514")
        );
    }

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("invalidRows")
    void databaseRejectsInvalidNonNullValues(String sql, String constraint, String sqlState) {
        DataIntegrityViolationException error = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql));
        assertThat(error.getMostSpecificCause()).isInstanceOf(SQLException.class).hasMessageContaining(constraint);
        assertThat(((SQLException) error.getMostSpecificCause()).getSQLState()).isEqualTo(sqlState);
    }

    @Test
    void databaseAcceptsZeroPrice() {
        jdbc.update("INSERT INTO tickets(ticket_code,type,price,status,purchase_date,user_id,event_id) "
                + "SELECT 'FREE','GENERAL',0,'PAID',CURRENT_TIMESTAMP,u.id,e.id FROM users u CROSS JOIN events e "
                + "WHERE u.username='constraint-user' AND e.event_code='CHECK-E'");
        assertThat(jdbc.queryForObject("SELECT price FROM tickets WHERE ticket_code='FREE'", java.math.BigDecimal.class))
                .isEqualByComparingTo("0.00");
    }
}
