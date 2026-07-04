package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.regime.AirflowSourcePreset;
import com.mac.bry.desktop.repository.CoolingChamberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TEST-EXC002 §6 (TC-INT-002, TC-INT-003) — migracja Flyway V30
 * i domyślna wartość presetu dla istniejących komór.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AirflowSourceMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CoolingChamberRepository coolingChamberRepository;

    @Test
    @DisplayName("TC-INT-002: Flyway V30 — nowe kolumny w cooling_chambers istnieją")
    void tc_int_002_flywayMigration() {
        assertThatCode(() -> jdbc.queryForList(
                "SELECT airflow_source_preset, custom_airflow_positions FROM cooling_chambers LIMIT 1"
        )).doesNotThrowAnyException();

        assertThatCode(() -> jdbc.queryForList(
                "SELECT airflow_source_preset, custom_airflow_positions FROM cooling_chambers_aud LIMIT 1"
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TC-INT-003: Istniejące komory → domyślny REAR_WALL")
    void tc_int_003_existingChambersDefaultPreset() {
        List<CoolingChamber> chambers = coolingChamberRepository.findAll();
        for (CoolingChamber chamber : chambers) {
            assertThat(chamber.getAirflowSourcePreset())
                    .as("Komora %s powinna mieć domyślny preset REAR_WALL", chamber.getChamberName())
                    .isEqualTo(AirflowSourcePreset.REAR_WALL);
        }
    }
}
