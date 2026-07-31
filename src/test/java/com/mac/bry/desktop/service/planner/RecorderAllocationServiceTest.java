package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.PlannedTaskRecorderAssignmentRepository;
import com.mac.bry.desktop.repository.ThermoRecorderRepository;
import com.mac.bry.desktop.service.planner.exception.HardwareDataIncompleteException;
import com.mac.bry.desktop.service.planner.exception.InsufficientMeasurementPointsException;
import com.mac.bry.desktop.service.planner.exception.InsufficientRecorderCapacityException;
import com.mac.bry.desktop.service.planner.exception.InsufficientRecorderMemoryException;
import com.mac.bry.desktop.service.planner.exception.MetrologicalRangeMismatchException;
import com.mac.bry.desktop.service.planner.exception.RecorderAllocationException;
import com.mac.bry.desktop.service.planner.exception.RecorderDoubleBookingException;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ST-W2-01, ST-W5-01/02, ST-R1-05 — dobór rejestratorów do zadania.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecorderAllocationServiceTest {

    private static final LocalDateTime STEP1 = LocalDateTime.of(2026, 8, 3, 6, 30);
    private static final LocalDateTime STEP4_END = LocalDateTime.of(2026, 8, 8, 13, 20);
    private static final LocalDateTime READOUT_DEADLINE = LocalDateTime.of(2026, 8, 8, 19, 20);

    @Mock
    private ThermoRecorderRepository recorderRepository;

    @Mock
    private PlannedTaskRecorderAssignmentRepository assignmentRepository;

    private RecorderAllocationService service;

    @BeforeEach
    void setUp() {
        service = new RecorderAllocationService(
                recorderRepository, assignmentRepository, new MetrologicalQualificationService(),
                new HardwareCapacityService(1.5), 24);

        when(assignmentRepository.findBusyRecorderIds(any(), any())).thenReturn(List.of());
        when(assignmentRepository.findEarliestRelease(any(), any())).thenReturn(null);
        when(assignmentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ThermoRecorder recorder(long id, String serial, int channelCount, double calLow, double calHigh) {
        return recorder(id, serial, model(channelCount, 16000), calLow, calHigh);
    }

    /** Kartoteka sprzętowa wzorowana na testo 174 T — bez niej reguła W4 odrzuca cały sprzęt. */
    private ThermoRecorderModel model(int channelCount, int sampleCapacity) {
        return ThermoRecorderModel.builder()
                .name("testo 174 T").channelCount(channelCount).sampleCapacity(sampleCapacity)
                .minOperatingTempC(-30.0).maxOperatingTempC(70.0)
                .batteryType("CR2032").batteryReplaceable(true)
                .batteryLifeDays(500).batteryLifeRefCycleMin(15).batteryLifeRefTempC(25.0)
                .build();
    }

    private ThermoRecorder recorder(long id, String serial, ThermoRecorderModel model,
                                    double calLow, double calHigh) {
        int channelCount = model.getChannelCount();
        ThermoRecorder recorder = ThermoRecorder.builder()
                .id(id)
                .serialNumber(serial)
                .status(RecorderStatus.ACTIVE)
                .model(model)
                .lastBatteryLevelPercent(100)
                .calibrations(new ArrayList<>())
                .build();

        for (int channel = 1; channel <= channelCount; channel++) {
            Calibration calibration = Calibration.builder()
                    .calibrationDate(LocalDate.of(2026, 1, 15))
                    .certificateNumber("PCA/2026/" + serial + "/" + channel)
                    .validUntil(LocalDate.of(2027, 1, 15))
                    .channelNumber(channel)
                    .points(new ArrayList<>())
                    .build();
            calibration.addPoint(point(calLow));
            calibration.addPoint(point(calHigh));
            recorder.addCalibration(calibration);
        }
        return recorder;
    }

    private CalibrationPoint point(double temperature) {
        return CalibrationPoint.builder()
                .temperatureValue(BigDecimal.valueOf(temperature))
                .systematicError(BigDecimal.ZERO)
                .uncertainty(new BigDecimal("0.1"))
                .build();
    }

    private CoolingChamber chamber(VolumeCategory category) {
        return CoolingChamber.builder()
                .id(1L)
                .chamberName("Komora KKCZ")
                .volumeCategory(category)
                .materialType(MaterialType.builder()
                        .name("KKCZ")
                        .minStorageTemp(2.0)
                        .maxStorageTemp(6.0)
                        .requiresMapping(true)
                        .build())
                .build();
    }

    /** Domyślna klasa procedury: pomiar co 180 min × 40 próbek — mieści się w każdym modelu. */
    private ProcedureClassConfig procedureConfig() {
        return ProcedureClassConfig.builder()
                .step1ProgMinutes(10)
                .step2PlacementMinutes(20)
                .step3StabHours(6)
                .step4IntervalMinutes(180)
                .step4SampleCount(40)
                .step5ReadoutBufferHours(6)
                .build();
    }

    private PlannedValidationTask task(int requiredRecorders) {
        return task(requiredRecorders, procedureConfig());
    }

    private PlannedValidationTask task(int requiredRecorders, ProcedureClassConfig config) {
        return PlannedValidationTask.builder()
                .taskNumber("5/HLA/2026")
                .procedureClassConfig(config)
                .procedureType(GxPProcedureType.PERIODIC_REVALIDATION)
                .dueDate(LocalDate.of(2026, 8, 10))
                .plannedStep1Time(STEP1)
                .plannedStep2Time(STEP1.plusMinutes(10))
                .plannedStep3StabEnd(STEP1.plusMinutes(390))
                .plannedStep4MapEnd(STEP4_END)
                .plannedStep5ReadoutDeadline(READOUT_DEADLINE)
                .calculatedTestoDelayMinutes(380)
                .requiredRecorderCount(requiredRecorders)
                .recorderAssignments(new ArrayList<>())
                .build();
    }

    private List<ThermoRecorder> pool(int count, int channelsEach) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> recorder(i, String.format("SN-%03d", i), channelsEach, 0.0, 10.0))
                .toList();
    }

    @Test
    @DisplayName("Komora SMALL: 2 rejestratory po 9 kanałów pokrywają 9 punktów")
    void allocatesRequestedRecorders() {
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(4, 9));
        PlannedValidationTask task = task(2);

        List<PlannedTaskRecorderAssignment> assignments = service.allocateRecorders(task, chamber(VolumeCategory.SMALL));

        assertThat(assignments).hasSize(18); // 2 rejestratory × 9 kanałów
        assertThat(task.getResourceStatus()).isEqualTo(TaskResourceStatus.OK);
        assertThat(task.getShortageReason()).isNull();
        assertThat(assignments).allSatisfy(a -> {
            assertThat(a.getReservedFrom()).isEqualTo(STEP1);
            // okno blokady = do terminu odczytu + 24 h buforu logistycznego
            assertThat(a.getReservedUntil()).isEqualTo(READOUT_DEADLINE.plusHours(24));
        });
    }

    @Test
    @DisplayName("ST-W2-01: komora LARGE wymaga 8, dostępnych 6 → brak alokacji częściowej")
    void st_w2_01_insufficientCapacityLeavesNoPartialAllocation() {
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(6, 27));
        when(assignmentRepository.findEarliestRelease(any(), any()))
                .thenReturn(LocalDateTime.of(2026, 8, 12, 8, 0));
        PlannedValidationTask task = task(8);

        assertThatThrownBy(() -> service.allocateRecorders(task, chamber(VolumeCategory.LARGE)))
                .isInstanceOf(InsufficientRecorderCapacityException.class)
                .satisfies(e -> {
                    InsufficientRecorderCapacityException ex = (InsufficientRecorderCapacityException) e;
                    assertThat(ex.getRequiredCount()).isEqualTo(8);
                    assertThat(ex.getAvailableCount()).isEqualTo(6);
                    assertThat(ex.getResourceStatus()).isEqualTo(TaskResourceStatus.INSUFFICIENT_CAPACITY);
                    assertThat(ex.getSuggestedWindowStart()).isEqualTo(LocalDateTime.of(2026, 8, 12, 8, 0));
                });

        assertThat(task.getRecorderAssignments()).isEmpty();
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("ST-W5-01: rejestrator zajęty w oknie wypada z puli")
    void st_w5_01_busyRecorderIsExcluded() {
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(3, 9));
        // SN-001 i SN-002 zarezerwowane przez inne zadanie
        when(assignmentRepository.findBusyRecorderIds(any(), any())).thenReturn(List.of(1L, 2L));
        PlannedValidationTask task = task(2);

        assertThatThrownBy(() -> service.allocateRecorders(task, chamber(VolumeCategory.SMALL)))
                .isInstanceOf(InsufficientRecorderCapacityException.class)
                .hasMessageContaining("zajętych, lecz zakwalifikowanych: 2");
    }

    @Test
    @DisplayName("ST-W5-02: okna rozłączne z buforem — rejestrator wraca do puli")
    void st_w5_02_disjointWindowsAllowReuse() {
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(2, 9));
        when(assignmentRepository.findBusyRecorderIds(any(), any())).thenReturn(List.of());
        PlannedValidationTask task = task(2);

        List<PlannedTaskRecorderAssignment> assignments =
                service.allocateRecorders(task, chamber(VolumeCategory.SMALL));

        assertThat(assignments).hasSize(18);
    }

    @Test
    @DisplayName("ST-R1-05: za mało kanałów na wymagane punkty pomiarowe")
    void st_r1_05_insufficientChannelsForMeasurementPoints() {
        // Komora SMALL wymaga 9 punktów, a dostępne są tylko rejestratory 1-kanałowe
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(4, 1));
        PlannedValidationTask task = task(2);

        assertThatThrownBy(() -> service.allocateRecorders(task, chamber(VolumeCategory.SMALL)))
                .isInstanceOf(InsufficientMeasurementPointsException.class)
                .satisfies(e -> {
                    InsufficientMeasurementPointsException ex = (InsufficientMeasurementPointsException) e;
                    assertThat(ex.getRequiredPoints()).isEqualTo(9);
                    assertThat(ex.getAvailableChannels()).isEqualTo(4);
                    assertThat(ex.getResourceStatus()).isEqualTo(TaskResourceStatus.INSUFFICIENT_CHANNELS);
                });
    }

    @Test
    @DisplayName("Planer dobiera rejestratory ponad macierz R1, gdy brakuje kanałów")
    void allocatesBeyondR1MatrixToCoverMeasurementPoints() {
        // 2 rejestratory (R1 dla SMALL) × 3 kanały = 6 < 9 punktów → dobiera trzeci
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(5, 3));
        PlannedValidationTask task = task(2);

        List<PlannedTaskRecorderAssignment> assignments =
                service.allocateRecorders(task, chamber(VolumeCategory.SMALL));

        assertThat(assignments)
                .as("3 rejestratory × 3 kanały = 9 punktów")
                .hasSize(9);
    }

    @Test
    @DisplayName("Żaden rejestrator nie pokrywa zakresu → przyczyna metrologiczna, nie logistyczna")
    void noMetrologicalMatchIsReportedDistinctly() {
        // Cała pula skalibrowana mrożarkowo, komora KKCZ +2…+6 °C
        List<ThermoRecorder> frozenPool = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> recorder(i, String.format("SN-%03d", i), 9, -30.0, -20.0))
                .toList();
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(frozenPool);
        PlannedValidationTask task = task(2);

        assertThatThrownBy(() -> service.allocateRecorders(task, chamber(VolumeCategory.SMALL)))
                .isInstanceOf(MetrologicalRangeMismatchException.class)
                .satisfies(e -> assertThat(((MetrologicalRangeMismatchException) e).getResourceStatus())
                        .isEqualTo(TaskResourceStatus.NO_METROLOGICAL_MATCH));
    }

    @Test
    @DisplayName("Rejestratory nieaktywne nie wchodzą do puli")
    void inactiveRecordersAreNotQueried() {
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(2, 9));
        service.allocateRecorders(task(2), chamber(VolumeCategory.SMALL));

        verify(recorderRepository).findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE);
        verify(recorderRepository, never()).findAll();
    }

    @Test
    @DisplayName("W5: jawna kontrola podwójnej rezerwacji przy ręcznej podmianie sprzętu")
    void requireNoDoubleBookingDetectsCollision() {
        ThermoRecorder recorder = recorder(1L, "SN-001", 1, 0.0, 10.0);
        PlannedTaskRecorderAssignment existing = PlannedTaskRecorderAssignment.builder()
                .thermoRecorder(recorder)
                .channelNumber(1)
                .reservedFrom(STEP1)
                .reservedUntil(READOUT_DEADLINE)
                .build();
        when(assignmentRepository.findCollisions(any(), any(), any(), any())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.requireNoDoubleBooking(recorder, 1, STEP1, READOUT_DEADLINE))
                .isInstanceOf(RecorderDoubleBookingException.class)
                .hasMessageContaining("już zarezerwowany");
    }

    @Test
    @DisplayName("ST-W4-01: cała pula za mała pamięciowo → odrzucenie sprzętowe, nie logistyczne")
    void st_w4_01_memoryLimitRejectsWholePool() {
        // 20 160 próbek co 1 min (14 dni) wobec 16 000 odczytów pojemności
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(4, 9));
        ProcedureClassConfig denseSampling = ProcedureClassConfig.builder()
                .step1ProgMinutes(10).step2PlacementMinutes(20).step3StabHours(6)
                .step4IntervalMinutes(1).step4SampleCount(20160)
                .step5ReadoutBufferHours(6)
                .build();
        PlannedValidationTask task = task(2, denseSampling);

        assertThatThrownBy(() -> service.allocateRecorders(task, chamber(VolumeCategory.SMALL)))
                .isInstanceOf(InsufficientRecorderMemoryException.class)
                .satisfies(e -> {
                    InsufficientRecorderMemoryException ex = (InsufficientRecorderMemoryException) e;
                    assertThat(ex.getRequiredSamples()).isEqualTo(20160);
                    // 16 000 na 9 kanałów — pamięć jest dzielona, nie zwielokrotniana
                    assertThat(ex.getAvailableSamples()).isEqualTo(1777);
                    assertThat(ex.getResourceStatus()).isEqualTo(TaskResourceStatus.HARDWARE_LIMITS_EXCEEDED);
                });

        assertThat(task.getRecorderAssignments()).isEmpty();
        verify(assignmentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("ST-W4-02: rejestrator bez odczytanego stanu baterii nie trafia do puli")
    void st_w4_02_unknownBatteryBlocksAllocation() {
        List<ThermoRecorder> neverRead = pool(4, 9);
        neverRead.forEach(r -> r.setLastBatteryLevelPercent(null));
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(neverRead);

        assertThatThrownBy(() -> service.allocateRecorders(task(2), chamber(VolumeCategory.SMALL)))
                .isInstanceOf(HardwareDataIncompleteException.class)
                .satisfies(e -> assertThat(((HardwareDataIncompleteException) e).getResourceStatus())
                        .isEqualTo(TaskResourceStatus.HARDWARE_DATA_INCOMPLETE))
                .hasMessageContaining("stacji Testo USB");
    }

    @Test
    @DisplayName("W4 nie blokuje sprzętu, który mieści się w limitach")
    void st_w4_03_hardwareWithinLimitsIsAllocated() {
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(4, 9));

        List<PlannedTaskRecorderAssignment> assignments =
                service.allocateRecorders(task(2), chamber(VolumeCategory.SMALL));

        assertThat(assignments).hasSize(18);
    }

    @Test
    @DisplayName("Brak obsady nie zatruwa transakcji wołającego — inaczej ginie cały plan roczny")
    void allocationFailureDoesNotMarkTransactionRollbackOnly() throws NoSuchMethodException {
        // RevalidationSchedulerEngine.generateYearlySchedule jest @Transactional
        // i woła alokację w pętli, przechwytując wyjątek, żeby zapisać przyczynę
        // w zadaniu. Bez noRollbackFor interceptor transakcji oznaczyłby wspólną
        // transakcję jako rollback-only przy pierwszej nieobsadzonej komorze,
        // a commit poleciałby na UnexpectedRollbackException — jedno zadanie bez
        // rejestratora kasowałoby plan na cały rok.
        Transactional tx = RecorderAllocationService.class
                .getMethod("allocateRecorders", PlannedValidationTask.class, CoolingChamber.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).contains(RecorderAllocationException.class);
    }

    @Test
    @DisplayName("Zwolnienie obsady czyści rezerwacje zadania")
    void releaseRecordersClearsAssignments() {
        when(recorderRepository.findByStatusOrderBySerialNumberAsc(RecorderStatus.ACTIVE))
                .thenReturn(pool(2, 9));
        PlannedValidationTask task = task(2);
        service.allocateRecorders(task, chamber(VolumeCategory.SMALL));
        assertThat(task.getRecorderAssignments()).isNotEmpty();

        service.releaseRecorders(task);

        assertThat(task.getRecorderAssignments()).isEmpty();
        verify(assignmentRepository).deleteByPlannedTask(task);
    }
}