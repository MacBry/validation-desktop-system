package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.MaterialType;
import com.mac.bry.desktop.model.ProcedureClassConfig;
import com.mac.bry.desktop.model.RecorderStatus;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.model.ThermoRecorderModel;
import com.mac.bry.desktop.service.planner.dto.HardwareBudget;
import com.mac.bry.desktop.service.planner.dto.HardwareViolation;
import com.mac.bry.desktop.service.planner.exception.HardwareDataIncompleteException;
import com.mac.bry.desktop.service.planner.exception.InsufficientBatteryLevelException;
import com.mac.bry.desktop.service.planner.exception.InsufficientRecorderMemoryException;
import com.mac.bry.desktop.service.planner.exception.RecorderOutOfOperatingRangeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ST-W4a/b/c — limity sprzętowe rejestratora.
 * <p>
 * Dane modeli odpowiadają kartom katalogowym Testo (patrz
 * {@code REVALIDATION_PLANNER_W4_SUPPLEMENT.md} §2), bo od nich zależy, czy
 * asercje mają jakikolwiek sens walidacyjny.
 */
class HardwareCapacityServiceTest {

    private static final LocalDate MISSION_START = LocalDate.of(2026, 8, 3);
    private static final double SAFETY_FACTOR = 1.5;

    private final HardwareCapacityService service = new HardwareCapacityService(SAFETY_FACTOR);

    // --- budowniczowie danych katalogowych ---------------------------------

    /** testo 174 T: 16 000 odczytów, 1 kanał, -30…+70 °C, CR2032, 500 dni @ 15 min / +25 °C. */
    private ThermoRecorderModel testo174T() {
        return ThermoRecorderModel.builder()
                .name("testo 174 T").channelCount(1).sampleCapacity(16000)
                .minOperatingTempC(-30.0).maxOperatingTempC(70.0)
                .batteryType("CR2032").batteryReplaceable(true)
                .batteryLifeDays(500).batteryLifeRefCycleMin(15).batteryLifeRefTempC(25.0)
                .build();
    }

    /** testo 184 T3: 40 000 odczytów, CR2450, 500 dni @ 15 min / +25 °C. */
    private ThermoRecorderModel testo184T3() {
        return ThermoRecorderModel.builder()
                .name("testo 184 T3").channelCount(1).sampleCapacity(40000)
                .minOperatingTempC(-35.0).maxOperatingTempC(70.0)
                .batteryType("CR2450").batteryReplaceable(true)
                .batteryLifeDays(500).batteryLifeRefCycleMin(15).batteryLifeRefTempC(25.0)
                .build();
    }

    /** testo 184 T4: jedyny model do -80 °C; ER2450T, 100 dni @ 15 min / -80 °C. */
    private ThermoRecorderModel testo184T4() {
        return ThermoRecorderModel.builder()
                .name("testo 184 T4").channelCount(1).sampleCapacity(40000)
                .minOperatingTempC(-80.0).maxOperatingTempC(70.0)
                .batteryType("ER2450T").batteryReplaceable(true)
                .batteryLifeDays(100).batteryLifeRefCycleMin(15).batteryLifeRefTempC(-80.0)
                .build();
    }

    /** testo 184 T1: bateria niewymienna, sztywny limit pracy 90 dni. */
    private ThermoRecorderModel testo184T1() {
        return ThermoRecorderModel.builder()
                .name("testo 184 T1").channelCount(1).sampleCapacity(16000)
                .minOperatingTempC(-35.0).maxOperatingTempC(70.0)
                .batteryReplaceable(false).operatingDurationDays(90)
                .build();
    }

    /** testo 175 T3: 1 000 000 odczytów dzielone na 3 kanały. */
    private ThermoRecorderModel testo175T3() {
        return ThermoRecorderModel.builder()
                .name("testo 175 T3").channelCount(3).sampleCapacity(1000000)
                .minOperatingTempC(-35.0).maxOperatingTempC(70.0)
                .batteryType("3xAAA AlMn").batteryReplaceable(true)
                .batteryLifeDays(1095).batteryLifeRefCycleMin(15).batteryLifeRefTempC(25.0)
                .build();
    }

    private ThermoRecorder recorder(ThermoRecorderModel model, Integer batteryPercent) {
        return ThermoRecorder.builder()
                .id(1L).serialNumber("SN-001").status(RecorderStatus.ACTIVE)
                .model(model)
                .lastBatteryLevelPercent(batteryPercent)
                .build();
    }

    private CoolingChamber chamber(String name, double minTemp, double maxTemp) {
        return CoolingChamber.builder()
                .id(1L).chamberName(name)
                .materialType(MaterialType.builder()
                        .name("Materiał testowy")
                        .minStorageTemp(minTemp).maxStorageTemp(maxTemp)
                        .requiresMapping(true)
                        .build())
                .build();
    }

    /**
     * Konfiguracja procedury. Czas misji to {@code 20 min + 6 h + interval*count + 6 h},
     * a nie sam Krok 4 — stabilizacja i bufor odczytu też zużywają energię.
     */
    private ProcedureClassConfig config(int intervalMinutes, int sampleCount) {
        return ProcedureClassConfig.builder()
                .step1ProgMinutes(10)
                .step2PlacementMinutes(20)
                .step3StabHours(6)
                .step4IntervalMinutes(intervalMinutes)
                .step4SampleCount(sampleCount)
                .step5ReadoutBufferHours(6)
                .build();
    }

    // --- W4a: zakres pracy urządzenia --------------------------------------

    @Nested
    @DisplayName("W4a — zakres pracy urządzenia")
    class OperatingRange {

        @Test
        @DisplayName("ST-W4a-01: testo 174 T w -80 °C odpada na zakresie pracy, nie na baterii")
        void st_w4a_01_recorderOutsideOperatingRangeIsRejectedRegardlessOfBattery() {
            HardwareBudget budget = service.evaluate(
                    recorder(testo174T(), 100), config(10, 100), chamber("Zamrażarka -80", -80.0, -60.0),
                    MISSION_START);

            assertThat(budget.isAcceptable()).isFalse();
            assertThat(budget.violations())
                    .extracting(HardwareViolation::rule)
                    .as("pełna bateria nie ratuje urządzenia użytego poza specyfikacją")
                    .contains(HardwareViolation.Rule.OPERATING_RANGE);
            // Separator dziesiętny zależy od locale JVM (pl: przecinek, en: kropka),
            // więc oczekiwany fragment formatujemy tak samo jak komunikat.
            assertThat(budget.firstViolation().message())
                    .contains("testo 174 T")
                    .contains(String.format("%.1f…%.1f°C", -30.0, 70.0))
                    .contains("Zamrażarka -80");
        }

        @Test
        @DisplayName("ST-W4a-02: testo 184 T4 w -80 °C mieści się w zakresie")
        void st_w4a_02_t4CoversUltraLowTemperature() {
            HardwareBudget budget = service.evaluate(
                    recorder(testo184T4(), 100), config(15, 100), chamber("Zamrażarka -80", -80.0, -60.0),
                    MISSION_START);

            assertThat(budget.violations())
                    .extracting(HardwareViolation::rule)
                    .doesNotContain(HardwareViolation.Rule.OPERATING_RANGE);
        }

        @Test
        @DisplayName("Model bez danych katalogowych blokuje, zamiast przechodzić po cichu")
        void modelWithoutSpecificationBlocks() {
            ThermoRecorderModel unknown = ThermoRecorderModel.builder()
                    .name("Nieznany model").channelCount(1).build();

            HardwareBudget budget = service.evaluate(
                    recorder(unknown, 100), config(15, 100), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);

            assertThat(budget.violations())
                    .extracting(HardwareViolation::rule)
                    .contains(HardwareViolation.Rule.DATA_INCOMPLETE);
        }

        @Test
        @DisplayName("Komora bez limitów temperatury blokuje ocenę W4")
        void chamberWithoutLimitsBlocks() {
            CoolingChamber noLimits = CoolingChamber.builder().id(2L).chamberName("Komora bez limitów").build();

            HardwareBudget budget = service.evaluate(
                    recorder(testo174T(), 100), config(15, 100), noLimits, MISSION_START);

            assertThat(budget.violations())
                    .extracting(HardwareViolation::rule)
                    .contains(HardwareViolation.Rule.DATA_INCOMPLETE);
        }
    }

    // --- W4b: budżet pamięci ------------------------------------------------

    @Nested
    @DisplayName("W4b — budżet pamięci")
    class MemoryBudget {

        @Test
        @DisplayName("ST-W4b-01: 14 dni co 1 min = 20 160 próbek > 16 000 w testo 174 T")
        void st_w4b_01_memoryOverflowIsRejected() {
            HardwareBudget budget = service.evaluate(
                    recorder(testo174T(), 100), config(1, 20160), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);

            HardwareViolation memory = budget.violations().stream()
                    .filter(v -> v.rule() == HardwareViolation.Rule.MEMORY)
                    .findFirst().orElseThrow();

            assertThat(memory.required()).isEqualTo(20160);
            assertThat(memory.available()).isEqualTo(16000);
            assertThat(budget.memoryLimitDays()).isCloseTo(11.1, org.assertj.core.data.Offset.offset(0.1));
        }

        @Test
        @DisplayName("ST-W4b-02: te same 20 160 próbek mieści się w testo 184 T3 (40 000)")
        void st_w4b_02_largerMemoryAccepts() {
            HardwareBudget budget = service.evaluate(
                    recorder(testo184T3(), 100), config(1, 20160), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);

            assertThat(budget.violations())
                    .extracting(HardwareViolation::rule)
                    .doesNotContain(HardwareViolation.Rule.MEMORY);
        }

        @Test
        @DisplayName("ST-W4b-03: testo 175 T3 dzieli 1 mln odczytów na 3 kanały → 333 333 na kanał")
        void st_w4b_03_memoryIsSharedBetweenChannels() {
            HardwareBudget budget = service.evaluate(
                    recorder(testo175T3(), 100), config(1, 400000), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);

            HardwareViolation memory = budget.violations().stream()
                    .filter(v -> v.rule() == HardwareViolation.Rule.MEMORY)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "pojemność katalogowa 1 mln nie jest liczbą próbek dostępną dla kanału"));

            assertThat(memory.available())
                    .as("1 000 000 / 3 kanały")
                    .isEqualTo(333333);
        }
    }

    // --- W4c: budżet energii ------------------------------------------------

    @Nested
    @DisplayName("W4c — budżet energii")
    class BatteryBudget {

        /** 20 min + 6 h + 10 min × 2950 + 6 h = 30 240 min = dokładnie 21 dni. */
        private ProcedureClassConfig mission21Days() {
            return config(10, 2950);
        }

        @Test
        @DisplayName("ST-W4c-01: 184 T4 w -80 °C, 60 % baterii, misja 21 dni → dopuszczony")
        void st_w4c_01_sufficientBudgetPasses() {
            HardwareBudget budget = service.evaluate(
                    recorder(testo184T4(), 60), mission21Days(), chamber("Zamrażarka -80", -80.0, -60.0),
                    MISSION_START);

            // 100 dni × min(1; 10/15) × 0,60 = 40,0 dnia; dopuszczalne 40/1,5 = 26,7 > 21
            assertThat(budget.batteryLimitDays()).isCloseTo(40.0, org.assertj.core.data.Offset.offset(0.1));
            assertThat(budget.missionDays()).isCloseTo(21.0, org.assertj.core.data.Offset.offset(0.01));
            assertThat(budget.isAcceptable()).isTrue();
        }

        @Test
        @DisplayName("ST-W4c-02: ten sam scenariusz przy 25 % baterii → odrzucony")
        void st_w4c_02_insufficientBudgetIsRejected() {
            HardwareBudget budget = service.evaluate(
                    recorder(testo184T4(), 25), mission21Days(), chamber("Zamrażarka -80", -80.0, -60.0),
                    MISSION_START);

            HardwareViolation battery = budget.violations().stream()
                    .filter(v -> v.rule() == HardwareViolation.Rule.BATTERY)
                    .findFirst().orElseThrow();

            assertThat(battery.available()).isCloseTo(16.7, org.assertj.core.data.Offset.offset(0.1));
            assertThat(battery.required()).isCloseTo(21.0, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("ST-W4c-03: przy gęstym próbkowaniu wiąże bateria, nie pamięć")
        void st_w4c_03_bindingConstraintIsReported() {
            // 184 T3, +4 °C, Δt = 1 min, 75 % baterii:
            //   pamięć  = 40 000 × 1 min       = 27,8 dnia
            //   bateria = 500 × (1/15) × 0,75  = 25,0 dnia → dopuszczalne 16,7
            HardwareBudget budget = service.evaluate(
                    recorder(testo184T3(), 75), config(1, 20000), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);

            assertThat(budget.memoryLimitDays()).isCloseTo(27.8, org.assertj.core.data.Offset.offset(0.1));
            assertThat(budget.batteryLimitDays()).isCloseTo(25.0, org.assertj.core.data.Offset.offset(0.1));
            assertThat(budget.binding()).isEqualTo(HardwareBudget.BindingConstraint.BATTERY);
        }

        @Test
        @DisplayName("ST-W4c-04: sentinel -1 (brak odczytu) nie wchodzi do arytmetyki")
        void st_w4c_04_unknownBatteryBlocksInsteadOfComputing() {
            HardwareBudget fromPdf = service.evaluate(
                    recorder(testo184T3(), -1), config(15, 100), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);
            HardwareBudget neverRead = service.evaluate(
                    recorder(testo184T3(), null), config(15, 100), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);

            for (HardwareBudget budget : List.of(fromPdf, neverRead)) {
                assertThat(budget.violations())
                        .extracting(HardwareViolation::rule)
                        .containsExactly(HardwareViolation.Rule.DATA_INCOMPLETE);
                assertThat(budget.batteryLimitDays()).isNaN();
                assertThat(budget.binding()).isEqualTo(HardwareBudget.BindingConstraint.UNKNOWN);
            }
        }

        @Test
        @DisplayName("ST-W4c-05: logger jednorazowy 184 T1 rozlicza się z limitu pracy, nie z procentu")
        void st_w4c_05_disposableLoggerUsesOperatingDurationBudget() {
            ThermoRecorder used = recorder(testo184T1(), null);
            used.setFirstActivationDate(MISSION_START.minusDays(80));

            HardwareBudget budget = service.evaluate(
                    used, mission21Days(), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);

            // 90 dni limitu - 80 zużytych = 10 dni; dopuszczalne 6,7 < 21 dni misji
            assertThat(budget.batteryLimitDays()).isEqualTo(10.0);
            assertThat(budget.violations())
                    .extracting(HardwareViolation::rule)
                    .as("brak odczytu % nie jest tu brakiem danych — model nie ma wymiennej baterii")
                    .containsExactly(HardwareViolation.Rule.BATTERY);
        }

        @Test
        @DisplayName("ST-W4c-06: budżet liczy pełną misję, nie sam Krok 4")
        void st_w4c_06_missionIncludesStabilisationAndReadoutBuffer() {
            // Model dobrany tak, by budżet leżał między czasem samego Kroku 4
            // (1,0 dnia) a pełnym czasem misji (1,51 dnia): gdyby reguła liczyła
            // tylko pomiar, rejestrator zostałby dopuszczony.
            ThermoRecorderModel shortLived = ThermoRecorderModel.builder()
                    .name("TEST-SHORT").channelCount(1).sampleCapacity(40000)
                    .minOperatingTempC(-35.0).maxOperatingTempC(70.0)
                    .batteryReplaceable(true)
                    .batteryLifeDays(3).batteryLifeRefCycleMin(15).batteryLifeRefTempC(25.0)
                    .build();

            HardwareBudget budget = service.evaluate(
                    recorder(shortLived, 50), config(15, 96), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);

            assertThat(budget.missionDays())
                    .as("20 min + 6 h stabilizacji + 24 h pomiaru + 6 h na odczyt")
                    .isCloseTo(1.514, org.assertj.core.data.Offset.offset(0.01));
            assertThat(budget.batteryLimitDays()).isEqualTo(1.5); // 3 dni × 1,0 × 0,50
            assertThat(budget.violations())
                    .extracting(HardwareViolation::rule)
                    .containsExactly(HardwareViolation.Rule.BATTERY);
        }

        @Test
        @DisplayName("Przeterminowana bateria blokuje mimo wysokiego stanu naładowania")
        void expiredBatteryIsRejected() {
            ThermoRecorderModel model = testo184T3();
            model.setBatteryShelfLifeMonths(24);
            ThermoRecorder old = recorder(model, 95);
            old.setBatteryReplacementDate(MISSION_START.minusMonths(30));

            HardwareBudget budget = service.evaluate(
                    old, config(15, 100), chamber("Chłodziarka", 2.0, 8.0), MISSION_START);

            assertThat(budget.violations())
                    .extracting(HardwareViolation::rule)
                    .contains(HardwareViolation.Rule.BATTERY);
            assertThat(budget.firstViolation().message()).contains("wymagana wymiana");
        }

        @Test
        @DisplayName("Praca poniżej temperatury referencyjnej to zastrzeżenie, nie blokada")
        void workBelowReferenceTemperatureOnlyWarns() {
            // 174 T ma żywotność podaną dla +25 °C; w zamrażarce -20 °C mieści się
            // w zakresie pracy (-30 °C), ale budżet energii jest oszacowaniem.
            HardwareBudget budget = service.evaluate(
                    recorder(testo174T(), 90), config(15, 100), chamber("Zamrażarka -20", -20.0, -15.0),
                    MISSION_START);

            assertThat(budget.isAcceptable()).isTrue();
            assertThat(budget.warnings()).hasSize(1);
            assertThat(budget.warnings().get(0)).contains("Kierownika Walidacji");
        }
    }

    // --- wariant rzucający (ręczna podmiana sprzętu) ------------------------

    @Nested
    @DisplayName("require(...) — ręczna podmiana rejestratora")
    class RequireVariant {

        @Test
        @DisplayName("Każde kryterium ma własny typ wyjątku")
        void violationsMapToDedicatedExceptions() {
            assertThatThrownBy(() -> service.require(
                    recorder(testo174T(), 100), config(15, 100), chamber("Zamrażarka -80", -80.0, -60.0),
                    MISSION_START))
                    .isInstanceOf(RecorderOutOfOperatingRangeException.class);

            assertThatThrownBy(() -> service.require(
                    recorder(testo174T(), 100), config(1, 20160), chamber("Chłodziarka", 2.0, 8.0), MISSION_START))
                    .isInstanceOf(InsufficientRecorderMemoryException.class)
                    .satisfies(e -> assertThat(((InsufficientRecorderMemoryException) e).getAvailableSamples())
                            .isEqualTo(16000));

            assertThatThrownBy(() -> service.require(
                    recorder(testo184T4(), 25), config(10, 2950), chamber("Zamrażarka -80", -80.0, -60.0),
                    MISSION_START))
                    .isInstanceOf(InsufficientBatteryLevelException.class);

            assertThatThrownBy(() -> service.require(
                    recorder(testo184T3(), -1), config(15, 100), chamber("Chłodziarka", 2.0, 8.0), MISSION_START))
                    .isInstanceOf(HardwareDataIncompleteException.class);
        }

        @Test
        @DisplayName("Sprzęt spełniający wszystkie kryteria przechodzi bez wyjątku")
        void acceptableHardwarePasses() {
            service.require(recorder(testo184T3(), 90), config(15, 100),
                    chamber("Chłodziarka", 2.0, 8.0), MISSION_START);
        }
    }
}