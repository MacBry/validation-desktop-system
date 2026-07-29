package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.CoolingChamberRepository;
import com.mac.bry.desktop.repository.PlannedValidationTaskRepository;
import com.mac.bry.desktop.repository.ProcedureClassConfigRepository;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.service.planner.exception.InsufficientRecorderCapacityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * ST-R1-01..04, ST-R2-01/02, ST-W3-01, ST-W6-01 oraz dopasowanie okna roboczego.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevalidationSchedulerEngineTest {

    @Mock private CoolingChamberRepository chamberRepository;
    @Mock private ProcedureClassConfigRepository procedureClassConfigRepository;
    @Mock private PlannedValidationTaskRepository taskRepository;
    @Mock private ValidationPlanNumberRepository validationPlanNumberRepository;
    @Mock private OperatorCalendarService calendarService;
    @Mock private RecorderAllocationService allocationService;

    private RevalidationSchedulerEngine engine;
    private final TestoDelayCalculatorService delayCalculator = new TestoDelayCalculatorService();

    @BeforeEach
    void setUp() {
        engine = new RevalidationSchedulerEngine(
                chamberRepository, procedureClassConfigRepository, taskRepository,
                validationPlanNumberRepository, calendarService, delayCalculator, allocationService);

        when(procedureClassConfigRepository.findFirstByProcedureTypeAndActiveTrueOrderByNameAsc(any()))
                .thenReturn(Optional.of(standardConfig()));
        when(calendarService.resolveShiftConfig(any())).thenReturn(defaultShift());
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.findByCoolingChamberAndProcedureTypeAndDueDate(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(validationPlanNumberRepository.findByCoolingDeviceAndYear(any(), anyInt()))
                .thenReturn(Optional.empty());
        when(validationPlanNumberRepository.findMaxPlanNumberByYear(anyInt())).thenReturn(4);
        when(validationPlanNumberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProcedureClassConfig standardConfig() {
        return ProcedureClassConfig.builder()
                .name("Standard").procedureType(GxPProcedureType.PERIODIC_REVALIDATION)
                .step1ProgMinutes(10).step2PlacementMinutes(20).step3StabHours(6)
                .step4IntervalMinutes(180).step4SampleCount(40).step5ReadoutBufferHours(6)
                .active(true).build();
    }

    private OperatorShiftConfig defaultShift() {
        return OperatorShiftConfig.builder()
                .shiftStart(LocalTime.of(6, 30)).shiftEnd(LocalTime.of(13, 30))
                .worksMonday(true).worksTuesday(true).worksWednesday(true)
                .worksThursday(true).worksFriday(true)
                .worksSaturday(false).worksSunday(false)
                .active(true).build();
    }

    private CoolingChamber chamber(VolumeCategory category, boolean requiresMapping) {
        Laboratory lab = new Laboratory();
        lab.setName("Pracownia HLA");
        lab.setAbbreviation("HLA");

        CoolingDevice device = CoolingDevice.builder()
                .id(1L).status(DeviceStatus.ACTIVE).laboratory(lab)
                .chambers(new ArrayList<>()).build();

        CoolingChamber chamber = CoolingChamber.builder()
                .id(1L)
                .chamberName("Komora testowa")
                .volumeCategory(category)
                .materialType(MaterialType.builder()
                        .name(requiresMapping ? "KKCZ" : "Odczynniki")
                        .minStorageTemp(2.0).maxStorageTemp(6.0)
                        .requiresMapping(requiresMapping).build())
                .build();
        device.addChamber(chamber);
        return chamber;
    }

    /** Dni robocze pon–pt bez świąt w lipcu/sierpniu 2026. */
    private void stubWorkingDays(LocalDate from, LocalDate to) {
        java.util.TreeSet<LocalDate> workingDays = new java.util.TreeSet<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                workingDays.add(d);
            }
        }
        when(calendarService.workingDaysBetween(any(), any(), any())).thenReturn(workingDays);
    }

    // ------------------------------------------------------------------
    // R1 / R2
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "ST-R1: {0} → {1} rejestratorów")
    @CsvSource({"SMALL, 2", "MEDIUM, 4", "LARGE, 8"})
    @DisplayName("ST-R1-01/03/04: macierz zapotrzebowania wg klasy kubatury")
    void st_r1_loggerMatrixByVolumeCategory(VolumeCategory category, int expected) {
        assertThat(engine.calculateRequiredLoggers(chamber(category, true),
                GxPProcedureType.PERIODIC_REVALIDATION)).isEqualTo(expected);
    }

    @Test
    @DisplayName("ST-R1-02: urządzenie dwukomorowe SMALL → 2 + 2 = 4 rejestratory")
    void st_r1_02_deviceLevelAggregation() {
        CoolingChamber upper = chamber(VolumeCategory.SMALL, true);
        CoolingDevice device = upper.getCoolingDevice();
        CoolingChamber lower = CoolingChamber.builder()
                .id(2L).chamberName("Dolna (Zamrażarka)")
                .volumeCategory(VolumeCategory.SMALL)
                .materialType(upper.getMaterialType())
                .build();
        device.addChamber(lower);

        assertThat(engine.calculateRequiredLoggers(upper, GxPProcedureType.PERIODIC_REVALIDATION)).isEqualTo(2);
        assertThat(engine.calculateRequiredLoggers(lower, GxPProcedureType.PERIODIC_REVALIDATION)).isEqualTo(2);
        assertThat(engine.calculateRequiredLoggersForDevice(device, GxPProcedureType.PERIODIC_REVALIDATION))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("ST-R2-01: materiał krytyczny → mapowanie z 8 rejestratorami")
    void st_r2_01_criticalMaterialRequiresMapping() {
        assertThat(engine.calculateRequiredLoggers(chamber(VolumeCategory.SMALL, true), GxPProcedureType.MAPPING))
                .isEqualTo(8);
    }

    @Test
    @DisplayName("ST-R2-02: odczynniki zwolnione z mapowania → 0 rejestratorów")
    void st_r2_02_reagentsExemptFromMapping() {
        assertThat(engine.calculateRequiredLoggers(chamber(VolumeCategory.LARGE, false), GxPProcedureType.MAPPING))
                .isZero();
    }

    @Test
    @DisplayName("Komora bez klasy kubatury jest błędem konfiguracji, nie cichym zerem")
    void missingVolumeCategoryFailsLoudly() {
        CoolingChamber chamber = chamber(VolumeCategory.SMALL, true);
        chamber.setVolumeCategory(null);

        assertThatThrownBy(() -> engine.calculateRequiredLoggers(chamber, GxPProcedureType.PERIODIC_REVALIDATION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("klasy kubatury");
    }

    // ------------------------------------------------------------------
    // Rozdział zegarów (BA R2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Materiał krytyczny w roku mapowania dostaje dwa zadania z osobnych zegarów")
    void bothProceduresDueInMappingYear() {
        CoolingChamber chamber = chamber(VolumeCategory.SMALL, true);
        chamber.setLastPeriodicRevalidationDate(LocalDate.of(2025, 3, 10));
        chamber.setLastMappingDate(LocalDate.of(2021, 6, 1));

        List<RevalidationSchedulerEngine.ProcedureDue> due =
                engine.determineDueProcedures(chamber, 2026, LocalDate.of(2026, 1, 1));

        assertThat(due).extracting(RevalidationSchedulerEngine.ProcedureDue::procedureType)
                .containsExactlyInAnyOrder(
                        GxPProcedureType.PERIODIC_REVALIDATION, GxPProcedureType.MAPPING);
        assertThat(due).extracting(RevalidationSchedulerEngine.ProcedureDue::dueDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("Odczynniki nigdy nie dostają mapowania, choćby minęło 10 lat")
    void reagentsNeverGetMapping() {
        CoolingChamber chamber = chamber(VolumeCategory.MEDIUM, false);
        chamber.setLastPeriodicRevalidationDate(LocalDate.of(2025, 3, 10));
        chamber.setLastMappingDate(LocalDate.of(2015, 1, 1));

        assertThat(engine.determineDueProcedures(chamber, 2026, LocalDate.of(2026, 1, 1)))
                .extracting(RevalidationSchedulerEngine.ProcedureDue::procedureType)
                .containsExactly(GxPProcedureType.PERIODIC_REVALIDATION);
    }

    @Test
    @DisplayName("ST-CAL-04: rok przestępny — 29.02.2024 + 12 miesięcy = 28.02.2025")
    void st_cal_04_leapYearDueDate() {
        CoolingChamber chamber = chamber(VolumeCategory.SMALL, false);
        chamber.setLastPeriodicRevalidationDate(LocalDate.of(2024, 2, 29));

        assertThat(chamber.getNextDueDate(GxPProcedureType.PERIODIC_REVALIDATION, LocalDate.of(2025, 1, 1)))
                .isEqualTo(LocalDate.of(2025, 2, 28));
    }

    // ------------------------------------------------------------------
    // Dopasowanie okna roboczego
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Odczyt wykonalny w oknie 06:30–13:30 dnia roboczego, przed nieprzekraczalnym terminem")
    void readoutAppointmentLandsInsideWorkingWindow() {
        stubWorkingDays(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 31));

        Optional<RevalidationSchedulerEngine.StepChain> chain =
                engine.fitTaskToShift(LocalDateTime.of(2026, 7, 1, 0, 0), standardConfig());

        assertThat(chain).isPresent();
        LocalDateTime appointment = chain.get().readoutAppointment();
        assertThat(appointment.toLocalTime()).isBetween(LocalTime.of(6, 30), LocalTime.of(13, 30));
        assertThat(appointment.getDayOfWeek().getValue()).isLessThanOrEqualTo(5);
        assertThat(appointment)
                .as("odczyt nie wcześniej niż po zatrzymaniu zapisu")
                .isAfterOrEqualTo(chain.get().measurementEnd())
                .as("i nie później niż nieprzekraczalny termin")
                .isBeforeOrEqualTo(chain.get().readoutDeadline());
    }

    @Test
    @DisplayName("Wszystkie akcje manualne mieszczą się w oknie zmiany")
    void allManualStepsFitInShift() {
        stubWorkingDays(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 31));

        RevalidationSchedulerEngine.StepChain chain =
                engine.fitTaskToShift(LocalDateTime.of(2026, 7, 1, 0, 0), standardConfig()).orElseThrow();

        assertThat(chain.step1().toLocalTime()).isBetween(LocalTime.of(6, 30), LocalTime.of(13, 30));
        assertThat(chain.step2().toLocalTime()).isBetween(LocalTime.of(6, 30), LocalTime.of(13, 30));
        assertThat(chain.readoutAppointment().toLocalTime()).isBetween(LocalTime.of(6, 30), LocalTime.of(13, 30));
        assertThat(chain.step2()).isEqualTo(chain.step1().plusMinutes(10));
    }

    @Test
    @DisplayName("Termin odczytu sam w sobie wypada poza zmianą — dlatego liczy się przedział, nie punkt")
    void readoutDeadlineItselfFallsOutsideShift() {
        stubWorkingDays(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 31));

        RevalidationSchedulerEngine.StepChain chain =
                engine.fitTaskToShift(LocalDateTime.of(2026, 7, 1, 0, 0), standardConfig()).orElseThrow();

        // Od Kroku 1 do terminu upływa 132,5 h, czyli 12,5 h ponad pełne doby,
        // więc termin zawsze ląduje wieczorem lub w nocy.
        LocalTime deadlineTime = chain.readoutDeadline().toLocalTime();
        assertThat(deadlineTime.isBefore(LocalTime.of(6, 30)) || deadlineTime.isAfter(LocalTime.of(13, 30)))
                .as("termin odczytu %s poza oknem zmiany", deadlineTime)
                .isTrue();
    }

    @Test
    @DisplayName("Brak dni roboczych w horyzoncie → brak okna zamiast złego planu")
    void noWorkingDaysYieldsEmpty() {
        when(calendarService.workingDaysBetween(any(), any(), any())).thenReturn(new java.util.TreeSet<>());

        assertThat(engine.fitTaskToShift(LocalDateTime.of(2026, 7, 1, 0, 0), standardConfig()))
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // W3
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ST-W3-01: pierwsza próbka przed końcem stabilizacji → naruszenie Zero-Junk Data")
    void st_w3_01_samplingBeforeStabilizationIsRejected() {
        ProcedureClassConfig config = standardConfig();
        LocalDateTime step2 = LocalDateTime.of(2026, 7, 6, 6, 40);

        PlannedValidationTask task = PlannedValidationTask.builder()
                .plannedStep2Time(step2)
                // stabilizacja skrócona o godzinę — zapis ruszyłby w fazie rozruchu
                .plannedStep3StabEnd(step2.plusMinutes(320))
                .build();

        assertThatThrownBy(() -> engine.validateZeroJunkData(task, config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Zero-Junk Data");
    }

    @Test
    @DisplayName("W3: poprawny łańcuch przechodzi walidację")
    void w3_correctChainPasses() {
        ProcedureClassConfig config = standardConfig();
        LocalDateTime step2 = LocalDateTime.of(2026, 7, 6, 6, 40);

        PlannedValidationTask task = PlannedValidationTask.builder()
                .plannedStep2Time(step2)
                .plannedStep3StabEnd(step2.plusMinutes(380))
                .build();

        engine.validateZeroJunkData(task, config); // brak wyjątku
    }

    // ------------------------------------------------------------------
    // W6 i braki zasobów
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ST-W6-01: komora przeterminowana planowana przed komorą z terminem w przyszłości")
    void st_w6_01_overdueChamberIsScheduledFirst() {
        stubWorkingDays(LocalDate.now().minusDays(30), LocalDate.now().plusDays(400));

        CoolingChamber overdue = chamber(VolumeCategory.SMALL, false);
        overdue.setId(10L);
        overdue.setLastPeriodicRevalidationDate(LocalDate.now().minusYears(2));

        // Termin musi jeszcze mieścić się w roku planu, inaczej silnik słusznie go pominie
        CoolingChamber future = chamber(VolumeCategory.SMALL, false);
        future.setId(20L);
        future.setLastPeriodicRevalidationDate(LocalDate.now().minusYears(1).plusDays(20));

        when(chamberRepository.findByCoolingDeviceStatusOrderByIdAsc(DeviceStatus.ACTIVE))
                .thenReturn(List.of(future, overdue));

        List<PlannedValidationTask> schedule = engine.generateYearlySchedule(LocalDate.now().getYear());

        assertThat(schedule).hasSize(2);
        assertThat(schedule.get(0).getCoolingChamber().getId())
                .as("przeterminowana komora sięga po pulę rejestratorów jako pierwsza")
                .isEqualTo(10L);
    }

    @Test
    @DisplayName("Brak obsady nie wywraca planu — zadanie zapisane z przyczyną")
    void resourceShortageIsRecordedNotThrown() {
        stubWorkingDays(LocalDate.now().minusDays(30), LocalDate.now().plusDays(400));
        doThrow(new InsufficientRecorderCapacityException(
                "Brak wolnych rejestratorów", 8, 6, LocalDateTime.of(2026, 9, 1, 8, 0)))
                .when(allocationService).allocateRecorders(any(), any());

        CoolingChamber chamber = chamber(VolumeCategory.LARGE, false);
        chamber.setLastPeriodicRevalidationDate(LocalDate.now().minusYears(1));

        PlannedValidationTask task = engine.planTask(
                chamber, GxPProcedureType.PERIODIC_REVALIDATION, LocalDate.now(), LocalDate.now().getYear());

        assertThat(task.getStatus()).isEqualTo(PlannedTaskStatus.PLANNED);
        assertThat(task.getResourceStatus()).isEqualTo(TaskResourceStatus.INSUFFICIENT_CAPACITY);
        assertThat(task.getShortageReason()).contains("Brak wolnych rejestratorów");
        assertThat(task.getSuggestedWindowStart()).isEqualTo(LocalDateTime.of(2026, 9, 1, 8, 0));
    }

    @Test
    @DisplayName("Numer zadania to RPW urządzenia w formacie planNumber/skrótPracowni/rok")
    void taskNumberFollowsRpwFormat() {
        stubWorkingDays(LocalDate.now().minusDays(30), LocalDate.now().plusDays(400));
        CoolingChamber chamber = chamber(VolumeCategory.SMALL, false);

        PlannedValidationTask task = engine.planTask(
                chamber, GxPProcedureType.PERIODIC_REVALIDATION, LocalDate.now(), 2026);

        assertThat(task.getTaskNumber())
                .as("kolejny wolny numer po 4 w pracowni HLA")
                .isEqualTo("5/HLA/2026");
    }

    @Test
    @DisplayName("Zadanie już zaplanowane nie jest duplikowane")
    void existingTaskIsNotDuplicated() {
        stubWorkingDays(LocalDate.now().minusDays(30), LocalDate.now().plusDays(400));
        CoolingChamber chamber = chamber(VolumeCategory.SMALL, false);
        chamber.setLastPeriodicRevalidationDate(LocalDate.now().minusYears(1));
        when(chamberRepository.findByCoolingDeviceStatusOrderByIdAsc(DeviceStatus.ACTIVE))
                .thenReturn(List.of(chamber));
        when(taskRepository.findByCoolingChamberAndProcedureTypeAndDueDate(any(), any(), any()))
                .thenReturn(Optional.of(new PlannedValidationTask()));

        assertThat(engine.generateYearlySchedule(LocalDate.now().getYear())).isEmpty();
    }
}