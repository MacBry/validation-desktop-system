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
}
