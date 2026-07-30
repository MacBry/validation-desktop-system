package com.mac.bry.desktop.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Faza 3 CI/CD: realna weryfikacja migracji Flyway na prawdziwym MySQL (Testcontainers).
 * Aplikacja produkcyjnie działa na MySQL, a testy jednostkowe/integracyjne na H2 (MODE=MySQL),
 * który nie wychwytuje wszystkich różnic składniowych. Ten test uruchamia komplet migracji
 * (common + mysql) na kontenerze mysql:8.0 i sprawdza, że schemat powstaje poprawnie.
 *
 * disabledWithoutDocker = true -> bez działającego Dockera test jest POMIJANY (lokalnie),
 * a wykonuje się tam, gdzie Docker jest dostępny (CI ubuntu, lokalny Docker Desktop).
 */
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationIntegrationTest {

    /** Tabele planera z V32 — każda ma mieć odpowiednik {@code _aud}. */
    private static final List<String> PLANNER_TABLES = List.of(
            "procedure_class_configs",
            "operator_shift_configs",
            "user_vacations",
            "planned_validation_tasks",
            "planned_task_recorder_assignments");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("validation_desktop_db")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false");

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                // Ten sam zestaw co produkcyjny {vendor}=mysql: wspólne + wariant MySQL.
                .locations("classpath:db/migration/common", "classpath:db/migration/mysql")
                .load();
    }

    @Test
    void flywayMigrationsApplyCleanlyOnRealMysql() throws Exception {
        Flyway flyway = flyway();

        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);

        // validate() rzuci wyjątek, jeśli checksumy/kolejność migracji są niespójne.
        flyway.validate();

        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement st = conn.createStatement()) {

            // Tabela bazowa (V2) musi istnieć i być zapytywalna.
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                assertThat(rs.next()).isTrue();
            }

            // Najnowsza migracja MySQL (V31) musi dodać kolumny tokenu resetu hasła.
            try (ResultSet rs = st.executeQuery(
                    "SELECT password_reset_token_hash, password_reset_token_expires_at FROM users LIMIT 1")) {
                assertThat(rs).isNotNull();
            }
        }
    }

    @Test
    void plannerSchemaIsUsableOnRealMysql() throws Exception {
        flyway().migrate();

        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement st = conn.createStatement()) {

            for (String table : PLANNER_TABLES) {
                try (ResultSet rs = st.executeQuery("SELECT id FROM " + table + " LIMIT 1")) {
                    assertThat(rs).as("tabela %s", table).isNotNull();
                }
                try (ResultSet rs = st.executeQuery("SELECT id, rev, revtype FROM " + table + "_aud LIMIT 1")) {
                    assertThat(rs).as("tabela audytowa %s_aud", table).isNotNull();
                }
            }

            // Rozdział zegarów (BA R2) wraz z kolumną w tabeli audytowej.
            try (ResultSet rs = st.executeQuery(
                    "SELECT last_mapping_date, last_periodic_revalidation_date FROM cooling_chambers LIMIT 1")) {
                assertThat(rs).isNotNull();
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT last_periodic_revalidation_date FROM cooling_chambers_aud LIMIT 1")) {
                assertThat(rs).isNotNull();
            }

            // Dane startowe muszą przetrwać migrację na MySQL, nie tylko na H2.
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM operator_shift_configs WHERE user_id IS NULL AND active = TRUE")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM procedure_class_configs WHERE active = TRUE")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }

    /**
     * Każda kolumna tabeli bazowej musi mieć odpowiednik w tabeli {@code _aud}.
     * <p>
     * Przy {@code ddl-auto=none} Envers nie utworzy ani nie uzupełni tabel
     * audytowych — pominięta kolumna ujawniłaby się dopiero przy pierwszym
     * zapisie encji na produkcji, jako błąd SQL w trakcie zapisu audytu.
     */
    @Test
    void auditTablesMirrorEveryColumnOfTheirBaseTable() throws Exception {
        flyway().migrate();

        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {

            for (String table : PLANNER_TABLES) {
                Set<String> base = columnsOf(conn, table);
                Set<String> audit = columnsOf(conn, table + "_aud");

                assertThat(audit)
                        .as("tabela %s_aud musi zawierać kolumny rewizji", table)
                        .contains("rev", "revtype");

                assertThat(audit)
                        .as("kolumny brakujące w %s_aud", table)
                        .containsAll(base);
            }
        }
    }

    private Set<String> columnsOf(Connection conn, String table) throws Exception {
        Set<String> columns = new HashSet<>();
        try (var ps = conn.prepareStatement(
                "SELECT LOWER(column_name) FROM information_schema.columns " +
                        "WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, MYSQL.getDatabaseName());
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        assertThat(columns).as("tabela %s nie istnieje albo nie ma kolumn", table).isNotEmpty();
        return columns;
    }
}
