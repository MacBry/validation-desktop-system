package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.model.GxPProcedureType;
import com.mac.bry.desktop.model.OperatorShiftConfig;
import com.mac.bry.desktop.model.ProcedureClassConfig;
import com.mac.bry.desktop.model.UserVacation;
import com.mac.bry.desktop.repository.OperatorShiftConfigRepository;
import com.mac.bry.desktop.repository.ProcedureClassConfigRepository;
import com.mac.bry.desktop.repository.UserVacationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Envers na konfiguracji produkcyjnej: MySQL, Flyway włączony,
 * {@code ddl-auto=none}.
 * <p>
 * Profil {@code test} używa H2 z {@code ddl-auto=create-drop} i wyłączonym
 * Flywayem, więc tabele {@code _aud} tworzy tam Hibernate z adnotacji — test
 * pod tym profilem przeszedłby nawet, gdyby ręcznie napisane DDL z V32 było
 * niezgodne z oczekiwaniami Enversa. Tutaj schemat pochodzi wyłącznie
 * z migracji, więc niezgodność wyszłaby jako błąd zapisu rewizji.
 * <p>
 * Zapis rewizji jest wymogiem 21 CFR Part 11 — bez niego zmiana terminu
 * badania GxP byłaby nieodtwarzalna.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PlannerEnversMySqlIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("validation_desktop_db")
            .withUrlParam("allowPublicKeyRetrieval", "true")
            .withUrlParam("useSSL", "false");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // Jawnie utrwalamy warunki produkcyjne, także gdyby application.yml się zmienił.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("app.notifications.enabled", () -> "false");
        registry.add("app.planner.readout-alerts-enabled", () -> "false");
    }

    @Autowired private ProcedureClassConfigRepository procedureClassConfigRepository;
    @Autowired private OperatorShiftConfigRepository shiftConfigRepository;
    @Autowired private UserVacationRepository vacationRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("Envers zapisuje rewizje klasy procedury do ręcznie utworzonej tabeli _aud")
    void enversRecordsProcedureClassConfigRevisions() {
        ProcedureClassConfig config = procedureClassConfigRepository.save(ProcedureClassConfig.builder()
                .name("Rewalidacja — test Envers")
                .procedureType(GxPProcedureType.PERIODIC_REVALIDATION)
                .step1ProgMinutes(10).step2PlacementMinutes(20).step3StabHours(6)
                .step4IntervalMinutes(180).step4SampleCount(40).step5ReadoutBufferHours(6)
                .active(true)
                .build());

        config.setStep3StabHours(8);
        procedureClassConfigRepository.save(config);

        List<Number> revisions = revisionsOf(ProcedureClassConfig.class, config.getId());
        assertThat(revisions).as("wstawienie + modyfikacja").hasSize(2);

        ProcedureClassConfig firstRevision =
                findAtRevision(ProcedureClassConfig.class, config.getId(), revisions.get(0));
        assertThat(firstRevision.getStep3StabHours())
                .as("pierwsza rewizja zachowuje wartość sprzed zmiany")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("Envers zapisuje rewizje okna pracy operatora")
    void enversRecordsOperatorShiftConfigRevisions() {
        OperatorShiftConfig shift = shiftConfigRepository.save(OperatorShiftConfig.builder()
                .shiftStart(LocalTime.of(7, 0))
                .shiftEnd(LocalTime.of(14, 0))
                .worksMonday(true).worksTuesday(true).worksWednesday(true)
                .worksThursday(true).worksFriday(true)
                .worksSaturday(false).worksSunday(false)
                .active(true)
                .build());

        assertThat(revisionsOf(OperatorShiftConfig.class, shift.getId())).isNotEmpty();
    }

    @Test
    @DisplayName("Envers zapisuje rewizje nieobecności operatora")
    void enversRecordsUserVacationRevisions() {
        UserVacation vacation = vacationRepository.save(UserVacation.builder()
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 7))
                .reason("Urlop wypoczynkowy")
                .unplannedL4(false)
                .build());

        assertThat(revisionsOf(UserVacation.class, vacation.getId())).isNotEmpty();
    }

    @Test
    @DisplayName("Dane startowe z V32 są dostępne przez repozytoria na MySQL")
    void seededDataIsReadableThroughRepositories() {
        assertThat(shiftConfigRepository.findFirstByUserIsNullAndActiveTrue())
                .as("globalne okno pracy operatora zasiane w V32")
                .isPresent();

        assertThat(procedureClassConfigRepository
                .findFirstByProcedureTypeAndActiveTrueOrderByNameAsc(GxPProcedureType.MAPPING))
                .as("domyślna klasa procedury mapowania")
                .isPresent();
    }

    private List<Number> revisionsOf(Class<?> entityType, Object id) {
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            AuditReader reader = AuditReaderFactory.get(em);
            return reader.getRevisions(entityType, id);
        }
    }

    private <T> T findAtRevision(Class<T> entityType, Object id, Number revision) {
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            AuditReader reader = AuditReaderFactory.get(em);
            return reader.find(entityType, id, revision);
        }
    }
}