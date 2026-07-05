package com.mac.bry.desktop.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Weryfikacja trybu standalone (embedded H2): pełny łańcuch migracji Flyway
 * V1→V30 musi przechodzić na H2 w trybie kompatybilności MySQL — łącznie
 * z seedem danych (konto admina, role), bez którego świeża instalacja
 * standalone nie pozwoliłaby się zalogować.
 * <p>
 * Test celowo NIE używa kontekstu Springa — odpala Flyway wprost na tym samym
 * URL-u, którego używa profil standalone (pamięciowa odmiana).
 */
class FlywayH2CompatibilityTest {

    private static final String H2_URL =
            "jdbc:h2:mem:standalone_migration_test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";

    @Test
    @DisplayName("TC-H2-001: Pełny łańcuch migracji V1→V30 przechodzi na H2 MODE=MySQL")
    void tc_h2_001_allMigrationsRunOnH2() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(H2_URL, "sa", "")
                .locations("classpath:db/migration/common", "classpath:db/migration/h2")
                .load();

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(30);

        // Seed: konto administratora musi istnieć (V1/V3) — warunek logowania
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "")) {
            try (ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM users WHERE username = 'admin'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("konto admina z seeda migracji").isEqualTo(1);
            }
            try (ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM roles")) {
                rs.next();
                assertThat(rs.getInt(1)).as("role z seeda migracji").isGreaterThan(0);
            }
            // Kolumny z najnowszych migracji (V30) — sanity check końca łańcucha
            try (ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT airflow_source_preset FROM cooling_chambers WHERE 1=0")) {
                assertThat(rs).isNotNull();
            }
        }
    }
}
