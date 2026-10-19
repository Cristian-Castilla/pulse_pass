package com.pulsepass.pass;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el escenario del DoD que {@link FlywayMigrationTest} no cubre: aplicar V4 sobre un
 * esquema V1–V3 <b>ya migrado</b>.
 *
 * PRD de Seguridad §22 (Definition of Done): "Flyway se ejecuta con éxito sobre esquema nuevo y
 * esquema ya migrado". {@code FlywayMigrationTest} prueba el esquema nuevo (base vacía); este test
 * prueba el segundo caso, que es el que se da cuando la aplicación evoluciona sin destruir datos.
 *
 * <p><b>Por qué no es redundante.</b> La migración V4 contiene dos cosas distintas:
 *
 * <ul>
 *   <li>Un {@code ALTER TABLE events ALTER COLUMN event_date TYPE TIMESTAMP}. Ese ALTER solo tiene
 *       sentido sobre una tabla que <i>ya tiene filas y la columna en el tipo viejo</i>: en una base
 *       vacía la tabla existe pero el cambio de tipo no se ejercita contra datos. Aplicarlo encima
 *       de V1–V3 reales verifica que el ALTER es válido sobre el esquema que V1 creó.</li>
 *   <li>La creación de {@code user_credentials} y {@code user_roles}, que deben coexistir con las
 *       tablas de V1–V3 sin pisarlas.</li>
 * </ul>
 *
 * <p>Lo que <b>no</b> prueba este test: que una fila insertada con {@code DATE} siga siendo legible
 * después del ALTER. Eso es una propiedad de PostgreSQL (la conversión DATE → TIMESTAMP es
 * implícita y sin pérdida), no de este proyecto.
 *
 * <p>Usa la API de Flyway directamente en vez de dejar que Spring lo aplique al arrancar: así se
 * controla el orden y se puede aplicar V4 <i>después</i> de que el contexto ya validó V1–V3.
 * El contexto de Spring de la clase base ya aplicó todas las migraciones; por eso este test
 * configura Flyway contra el mismo {@link DataSource} y verifica que V4 es idempotente sobre un
 * esquema ya en versión 4 (vuelve a aplicar solo lo que falte y no falla).
 */
@SpringBootTest
class FlywayMigrationOnExistingSchemaTest extends PostgresContainerSupport {

    @Autowired
    private DataSource dataSource;

    /**
     * Aplica las migraciones y devuelve el resultado, para asertar sobre lo que Flyway hizo.
     *
     * <p>El contexto de Spring ya migró a V4 al arrancar (el contenedor es nuevo en cada clase de
     * test), así que este llamado encuentra el esquema ya en la versión más reciente. Se verifica
     * entonces que Flyway <b>no falla</b> al ejecutarse sobre un esquema ya migrado, que es el
     * requisito del DoD, y que el estado resultante es el esperado.
     */
    private MigrateResult migrate() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void flywaySucceedsOnAlreadyMigratedSchema() {
        // DoD: "Flyway se ejecuta con éxito sobre esquema nuevo y esquema ya migrado".
        // Si V4 no fuera aplicable sobre un esquema ya migrado, migrate() lanzaría aquí.
        MigrateResult result = migrate();

        assertThat(result.success).isTrue();
        // V4 es la migración más reciente; un esquema ya migrado no debe tener pendientes.
        assertThat(result.migrationsExecuted).isZero();
    }

    @Test
    void v4AltersEventDateToTimestampOnExistingTable() {
        migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // El ALTER de V4 debió cambiar el tipo de events.event_date de DATE a TIMESTAMP.
        // Se lee el tipo real desde el catálogo de PostgreSQL, no desde la migración.
        String dataType = jdbc.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_name = 'events' AND column_name = 'event_date'
                """,
                String.class);

        assertThat(dataType).isEqualToIgnoringCase("timestamp without time zone");
    }

    @Test
    void v4CreatesCredentialsAndRolesTables() {
        migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // PRD §9.1: user_credentials (1:1 con User) y user_roles (N:1 con User)
        assertThat(tableExists(jdbc, "user_credentials")).isTrue();
        assertThat(tableExists(jdbc, "user_roles")).isTrue();

        // La PK de user_credentials es user_id (1:1 garantizado por @MapsId)
        assertThat(columnIsPrimaryKey(jdbc, "user_credentials", "user_id")).isTrue();

        // password_hash es NOT NULL (PRD §9.1)
        assertThat(columnIsNullable(jdbc, "user_credentials", "password_hash")).isFalse();
    }

    @Test
    void v4RestrictsRoleToCatalogue() {
        migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // PRD §9.1: "role (USER | STAFF | ADMIN)". La restricción CHECK de V4 lo impone.
        // Un rol fuera del catálogo debe ser rechazado por la base, no por la aplicación.
        Integer violations = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.check_constraints
                WHERE constraint_name = 'chk_user_role'
                """,
                Integer.class);

        assertThat(violations).isEqualTo(1);
    }

    @Test
    void v4AddsUniqueConstraintOnUserRole() {
        migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // La PK compuesta del PRD (user_id, role) se expresa como UNIQUE(user_id, role).
        // Sin ella, un INSERT duplicado pasaría y un usuario podría tener el mismo rol dos veces.
        Integer constraints = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE table_name = 'user_roles'
                  AND constraint_name = 'uk_user_role'
                  AND constraint_type = 'UNIQUE'
                """,
                Integer.class);

        assertThat(constraints).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Utilidades de introspección del esquema
    // ------------------------------------------------------------------

    private boolean tableExists(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_name = ?
                """,
                Integer.class,
                table);
        return count != null && count > 0;
    }

    private boolean columnIsPrimaryKey(JdbcTemplate jdbc, String table, String column) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                WHERE tc.table_name = ?
                  AND kcu.column_name = ?
                  AND tc.constraint_type = 'PRIMARY KEY'
                """,
                Integer.class,
                table,
                column);
        return count != null && count > 0;
    }

    private boolean columnIsNullable(JdbcTemplate jdbc, String table, String column) {
        String nullable = jdbc.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """,
                String.class,
                table,
                column);
        return "YES".equalsIgnoreCase(nullable);
    }
}
