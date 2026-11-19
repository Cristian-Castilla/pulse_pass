package com.pulsepass.pass;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationOnExistingSchemaTest extends PostgresContainerSupport {
    @Autowired private DataSource dataSource;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {3, 4})
    void shouldUpgradePopulatedLegacySchemaWithoutChangingData(int startingVersion) {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(dataSource);
        String url = postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=" + schema;
        DataSource isolated = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(isolated);
        try {
            Flyway.configure().dataSource(isolated).schemas(schema).defaultSchema(schema)
                    .target("3").load().migrate();
            var column = jdbc.queryForMap("SELECT data_type,character_maximum_length,is_nullable FROM information_schema.columns "
                    + "WHERE table_schema=? AND table_name='events' AND column_name='streaming_url'", schema);
            assertThat(column).containsEntry("data_type", "character varying")
                    .containsEntry("character_maximum_length", 500).containsEntry("is_nullable", "YES");
            assertThat(jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=? "
                    + "AND table_name='events' AND column_name='event_date'", String.class, schema)).isEqualTo("date");
            long venue = jdbc.queryForObject("INSERT INTO venues(code,name,city,address,capacity) "
                    + "VALUES ('LEG-V','Legacy venue','Bogotá','Address',5000) RETURNING id", Long.class);
            long user = jdbc.queryForObject("INSERT INTO users(username,email) "
                    + "VALUES ('legacy','legacy@example.test') RETURNING id", Long.class);
            long profile = jdbc.queryForObject("INSERT INTO user_profiles(first_name,last_name,phone,city,birth_date,user_id) "
                    + "VALUES ('Legacy','User','123','Bogotá',DATE '1990-02-03',?) RETURNING id", Long.class, user);
            long event = jdbc.queryForObject("INSERT INTO events(event_code,name,description,category,status,event_date,venue_id,streaming_url) "
                    + "VALUES ('LEG-E','Legacy event','Description','MUSIC','PUBLISHED',DATE '2027-06-15',?,?) RETURNING id",
                    Long.class, venue, "https://legacy.example.test/live");
            long ticket = jdbc.queryForObject("INSERT INTO tickets(ticket_code,type,price,status,purchase_date,user_id,event_id) "
                    + "VALUES ('LEG-T','VIP',1234.56,'PAID',TIMESTAMP '2026-01-02 03:04:05',?,?) RETURNING id",
                    Long.class, user, event);
            long artist = jdbc.queryForObject("SELECT id FROM artists WHERE stage_name='Solar Beat'", Long.class);
            jdbc.update("INSERT INTO event_artists(event_id,artist_id) VALUES (?,?)", event, artist);

            long activated = 0;
            String hash = new BCryptPasswordEncoder().encode("upgrade-test-only");
            if (startingVersion == 4) {
                Flyway.configure().dataSource(isolated).schemas(schema).defaultSchema(schema)
                        .target("4").load().migrate();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM user_credentials", Long.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles", Long.class)).isZero();
                activated = jdbc.queryForObject("INSERT INTO users(username,email) "
                        + "VALUES ('activated','activated@example.test') RETURNING id", Long.class);
                jdbc.update("INSERT INTO user_credentials(user_id,password_hash) VALUES (?,?)", activated, hash);
                jdbc.update("INSERT INTO user_roles(user_id,role) VALUES (?,'USER'),(?,'STAFF')", activated, activated);
            }

            Flyway latest = Flyway.configure().dataSource(isolated).schemas(schema).defaultSchema(schema).load();
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(6 - startingVersion);
            assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("6");
            assertThat(jdbc.queryForObject("SELECT venue_id FROM events WHERE id=?", Long.class, event)).isEqualTo(venue);
            assertThat(jdbc.queryForObject("SELECT event_date FROM events WHERE id=?", LocalDateTime.class, event))
                    .isEqualTo(LocalDate.of(2027, 6, 15).atStartOfDay());
            assertThat(jdbc.queryForObject("SELECT streaming_url FROM events WHERE id=?", String.class, event))
                    .isEqualTo("https://legacy.example.test/live");
            assertThat(jdbc.queryForObject("SELECT capacity FROM venues WHERE id=?", Long.class, venue)).isEqualTo(5000L);
            assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id=?", String.class, user)).isEqualTo("legacy");
            assertThat(jdbc.queryForMap("SELECT email,active FROM users WHERE id=?", user))
                    .containsEntry("email", "legacy@example.test").containsEntry("active", true);
            assertThat(jdbc.queryForMap("SELECT first_name,last_name,phone,city FROM user_profiles WHERE id=?", profile))
                    .containsEntry("first_name", "Legacy").containsEntry("last_name", "User")
                    .containsEntry("phone", "123").containsEntry("city", "Bogotá");
            assertThat(jdbc.queryForMap("SELECT name,description,category,status FROM events WHERE id=?", event))
                    .containsEntry("name", "Legacy event").containsEntry("description", "Description")
                    .containsEntry("category", "MUSIC").containsEntry("status", "PUBLISHED");
            assertThat(jdbc.queryForMap("SELECT type,status FROM tickets WHERE id=?", ticket))
                    .containsEntry("type", "VIP").containsEntry("status", "PAID");
            assertThat(jdbc.queryForObject("SELECT user_id FROM user_profiles WHERE id=?", Long.class, profile)).isEqualTo(user);
            assertThat(jdbc.queryForObject("SELECT birth_date FROM user_profiles WHERE id=?", LocalDate.class, profile))
                    .isEqualTo(LocalDate.of(1990, 2, 3));
            assertThat(jdbc.queryForObject("SELECT user_id FROM tickets WHERE id=?", Long.class, ticket)).isEqualTo(user);
            assertThat(jdbc.queryForObject("SELECT event_id FROM tickets WHERE id=?", Long.class, ticket)).isEqualTo(event);
            assertThat(jdbc.queryForObject("SELECT price FROM tickets WHERE id=?", BigDecimal.class, ticket))
                    .isEqualByComparingTo("1234.56");
            assertThat(jdbc.queryForObject("SELECT purchase_date FROM tickets WHERE id=?", LocalDateTime.class, ticket))
                    .isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
            assertThat(jdbc.queryForObject("SELECT artist_id FROM event_artists WHERE event_id=?", Long.class, event)).isEqualTo(artist);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_credentials WHERE user_id=?", Long.class, user)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_credentials", Long.class))
                    .isEqualTo(startingVersion == 4 ? 1L : 0L);
            if (startingVersion == 4) {
                assertThat(jdbc.queryForObject("SELECT password_hash FROM user_credentials WHERE user_id=?", String.class, activated))
                        .isEqualTo(hash);
                assertThat(jdbc.queryForList("SELECT role FROM user_roles WHERE user_id=?", String.class, activated))
                        .containsExactlyInAnyOrder("USER", "STAFF");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles", Long.class)).isEqualTo(2L);
            }
            assertThat(jdbc.queryForList("SELECT k.column_name FROM information_schema.table_constraints t "
                    + "JOIN information_schema.key_column_usage k USING (constraint_catalog,constraint_schema,constraint_name) "
                    + "WHERE t.table_schema=? AND t.table_name='user_roles' AND t.constraint_type='PRIMARY KEY' "
                    + "ORDER BY k.ordinal_position", String.class, schema)).containsExactly("user_id", "role");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints "
                    + "WHERE table_schema=? AND table_name='user_roles' AND constraint_name='fk_user_roles_user' "
                    + "AND constraint_type='FOREIGN KEY'", Long.class, schema)).isEqualTo(1L);
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();
            assertThat(latest.migrate().migrationsExecuted).isZero();
            assertThat(latest.info().pending()).isEmpty();
        } finally {
            admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
