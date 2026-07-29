package com.mac.bry.desktop.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Migracja Flyway V32 — schemat Planera Rewalidacji.
 * <p>
 * Test celowo <b>nie</b> używa {@code @SpringBootTest}: profil {@code test} ma
 * {@code flyway.enabled=false} i {@code ddl-auto=create-drop}, więc schemat
 * w kontekście Springa buduje Hibernate z encji. Taki test przechodziłby nawet
 * przy całkowicie zepsutym SQL-u migracji. Tutaj Flyway jest uruchamiany jawnie
 * na czystej bazie H2, więc weryfikowany jest realny plik V32.
 * <p>
 * Sedno testu to tabele {@code _aud}: przy {@code ddl-auto=none} na produkcji
 * Envers ich nie utworzy, a ich brak ujawniłby się dopiero przy pierwszym
 * zapisie encji.
 */
class PlannerSchemaMigrationIntegrationTest {

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateCleanDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:plannerV32;DB_CLOSE_DELAY=-1;MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/common", "classpath:db/migration/h2")
                .load()
                .migrate();

        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("V32 została faktycznie zastosowana przez Flyway")
    void v32IsApplied() {
        // Flyway tworzy tabelę historii z cytowanymi identyfikatorami, więc w H2 są
        // zapisane małymi literami — bez cudzysłowów H2 podniósłby je do wielkich.
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" " +
                        "WHERE \"version\" = '32' AND \"success\" = TRUE",
                Integer.class);

        assertThat(applied).isEqualTo(1);
    }

    @ParameterizedTest(name = "tabela {0}")
    @ValueSource(strings = {
            "procedure_class_configs",
            "operator_shift_configs",
            "user_vacations",
            "planned_validation_tasks",
            "planned_task_recorder_assignments"
    })
    @DisplayName("V32: tabele planera zostały utworzone")
    void plannerTablesExist(String table) {
        assertThatCode(() -> jdbc.queryForList("SELECT id FROM " + table + " LIMIT 1"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "tabela audytowa {0}")
    @ValueSource(strings = {
            "procedure_class_configs_aud",
            "operator_shift_configs_aud",
            "user_vacations_aud",
            "planned_validation_tasks_aud",
            "planned_task_recorder_assignments_aud"
    })
    @DisplayName("V32: tabele _aud dla Envers utworzone ręcznie (ddl-auto=none ich nie zrobi)")
    void auditTablesExist(String table) {
        assertThatCode(() -> jdbc.queryForList("SELECT id, rev, revtype FROM " + table + " LIMIT 1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("V33: przeniesienie zegara nie zostawia komory z datą mapowania, a bez rewalidacji")
    void v33_backfillsPeriodicRevalidationClock() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" " +
                        "WHERE \"version\" = '33' AND \"success\" = TRUE",
                Integer.class);
        assertThat(applied).isEqualTo(1);

        Integer stragglers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cooling_chambers " +
                        "WHERE last_mapping_date IS NOT NULL AND last_periodic_revalidation_date IS NULL",
                Integer.class);

        assertThat(stragglers)
                .as("komora z datą mapowania musi mieć też datę rewalidacji, inaczej digest zgłosi ją jako nigdy nierewalidowaną")
                .isZero();
    }

    @Test
    @DisplayName("V32: rozdział zegarów — last_periodic_revalidation_date w tabeli i w _aud")
    void periodicRevalidationClockColumnExists() {
        assertThatCode(() -> jdbc.queryForList(
                "SELECT last_mapping_date, last_periodic_revalidation_date FROM cooling_chambers LIMIT 1"
        )).doesNotThrowAnyException();

        assertThatCode(() -> jdbc.queryForList(
                "SELECT last_mapping_date, last_periodic_revalidation_date FROM cooling_chambers_aud LIMIT 1"
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("V32: globalne okno pracy operatora 06:30–13:30 zasiane")
    void globalShiftConfigSeeded() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT CAST(shift_start AS VARCHAR) AS shift_start, " +
                        "CAST(shift_end AS VARCHAR) AS shift_end, " +
                        "works_monday, works_saturday " +
                        "FROM operator_shift_configs WHERE user_id IS NULL AND active = TRUE");

        assertThat(row.get("SHIFT_START")).isEqualTo("06:30:00");
        assertThat(row.get("SHIFT_END")).isEqualTo("13:30:00");
        assertThat(row.get("WORKS_MONDAY")).isEqualTo(Boolean.TRUE);
        assertThat(row.get("WORKS_SATURDAY")).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("V32: domyślne klasy procedur wg BA §3 — Krok 4 = 40 próbek co 180 min")
    void defaultProcedureClassesSeeded() {
        List<Map<String, Object>> classes = jdbc.queryForList(
                "SELECT procedure_type, step1_prog_minutes, step2_placement_minutes, step3_stab_hours, " +
                        "step4_interval_minutes, step4_sample_count, step5_readout_buffer_hours " +
                        "FROM procedure_class_configs WHERE active = TRUE ORDER BY procedure_type");

        assertThat(classes).hasSize(2);
        assertThat(classes).extracting(c -> c.get("PROCEDURE_TYPE"))
                .containsExactly("MAPPING", "PERIODIC_REVALIDATION");

        Map<String, Object> revalidation = classes.get(1);
        assertThat(revalidation.get("STEP1_PROG_MINUTES")).isEqualTo(10);
        assertThat(revalidation.get("STEP2_PLACEMENT_MINUTES")).isEqualTo(20);
        assertThat(revalidation.get("STEP3_STAB_HOURS")).isEqualTo(6);
        assertThat(revalidation.get("STEP4_INTERVAL_MINUTES")).isEqualTo(180);
        assertThat(revalidation.get("STEP4_SAMPLE_COUNT")).isEqualTo(40);
        assertThat(revalidation.get("STEP5_READOUT_BUFFER_HOURS")).isEqualTo(6);
    }

    @Test
    @DisplayName("V32: task_number dopuszcza duplikaty, unikalność pilnuje trójka komora+typ+termin")
    void taskNumberIsNotUniqueButChamberTypeDueIs() {
        Map<String, List<String>> uniqueConstraints = jdbc.queryForList(
                        "SELECT kcu.CONSTRAINT_NAME AS cname, kcu.COLUMN_NAME AS col " +
                                "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                                "JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu " +
                                "  ON tc.CONSTRAINT_NAME = kcu.CONSTRAINT_NAME " +
                                " AND tc.TABLE_NAME = kcu.TABLE_NAME " +
                                "WHERE UPPER(tc.TABLE_NAME) = 'PLANNED_VALIDATION_TASKS' " +
                                "AND tc.CONSTRAINT_TYPE = 'UNIQUE' " +
                                "ORDER BY kcu.CONSTRAINT_NAME, kcu.ORDINAL_POSITION")
                .stream()
                .collect(Collectors.groupingBy(
                        r -> String.valueOf(r.get("CNAME")),
                        Collectors.mapping(r -> String.valueOf(r.get("COL")).toUpperCase(), Collectors.toList())));

        assertThat(uniqueConstraints.values())
                .as("żadne ograniczenie UNIQUE nie może obejmować samego task_number — " +
                        "dwie komory jednego urządzenia dzielą numer RPW")
                .noneMatch(cols -> cols.equals(List.of("TASK_NUMBER")));

        assertThat(uniqueConstraints.values())
                .as("realny duplikat pilnuje trójka (komora, typ procedury, termin)")
                .anyMatch(cols -> cols.containsAll(
                        List.of("COOLING_CHAMBER_ID", "PROCEDURE_TYPE", "DUE_DATE")));
    }
}