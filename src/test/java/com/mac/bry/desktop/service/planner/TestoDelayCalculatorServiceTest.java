package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.GxPProcedureType;
import com.mac.bry.desktop.model.ProcedureClassConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ST-DELAY-01, ST-DELAY-02 oraz ST-CAL-03 — matematyka opóźnienia startu Testo.
 */
class TestoDelayCalculatorServiceTest {

    private final TestoDelayCalculatorService service = new TestoDelayCalculatorService();

    private ProcedureClassConfig standardConfig() {
        return ProcedureClassConfig.builder()
                .name("Rewalidacja okresowa — standard")
                .procedureType(GxPProcedureType.PERIODIC_REVALIDATION)
                .step1ProgMinutes(10)
                .step2PlacementMinutes(20)
                .step3StabHours(6)
                .step4IntervalMinutes(180)
                .step4SampleCount(40)
                .step5ReadoutBufferHours(6)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("ST-DELAY-01: Krok 2 (20 min) + Krok 3 (6 h) = 380 minut")
    void st_delay_01_startDelayIs380Minutes() {
        assertThat(service.calculateStartDelay(standardConfig())).isEqualTo(380);
    }

    @Test
    @DisplayName("ST-DELAY-02: Krok 1 nie wpływa na opóźnienie startu")
    void st_delay_02_programmingTimeDoesNotAffectDelay() {
        ProcedureClassConfig config = standardConfig();

        int withTenMinutes = service.calculateStartDelay(config);

        config.setStep1ProgMinutes(90);
        int withNinetyMinutes = service.calculateStartDelay(config);

        assertThat(withNinetyMinutes)
                .as("zegar opóźnienia rusza dopiero po zaprogramowaniu")
                .isEqualTo(withTenMinutes)
                .isEqualTo(380);
    }

    @Test
    @DisplayName("Krok 4: 40 próbek co 180 min = 120 h okresu pomiarowego")
    void measurementDurationMatchesBa() {
        assertThat(service.calculateMeasurementDurationMinutes(standardConfig()))
                .isEqualTo(7200);
    }

    @Test
    @DisplayName("Pierwsza czysta próbka GxP wypada 380 min po umieszczeniu w komorze")
    void firstSampleAfterStabilization() {
        LocalDateTime placement = LocalDateTime.of(2026, 7, 6, 7, 0);

        assertThat(service.calculateFirstSampleTime(standardConfig(), placement))
                .isEqualTo(LocalDateTime.of(2026, 7, 6, 13, 20));
    }

    @Test
    @DisplayName("Termin odczytu = umieszczenie + 380 min + 120 h + 6 h buforu")
    void readoutDeadlineChainsAllSteps() {
        LocalDateTime placement = LocalDateTime.of(2026, 7, 6, 7, 0);

        // 07:00 + 6h20m = 13:20 (6 lipca) → +120h = 13:20 (11 lipca) → +6h = 19:20
        assertThat(service.calculateReadoutDeadline(standardConfig(), placement))
                .isEqualTo(LocalDateTime.of(2026, 7, 11, 19, 20));
    }

    @Test
    @DisplayName("Ostatnia próbka zapisana jeden interwał przed końcem pomiaru")
    void lastSampleIsOneIntervalBeforeEnd() {
        LocalDateTime placement = LocalDateTime.of(2026, 7, 6, 7, 0);

        assertThat(service.calculateLastSampleTime(standardConfig(), placement))
                .isEqualTo(service.calculateMeasurementEnd(standardConfig(), placement).minusMinutes(180));
    }

    @Test
    @DisplayName("ST-CAL-03: pomiar przez zmianę na czas letni — liczy się czas rzeczywisty")
    void st_cal_03_springForwardShiftsWallClockByOneHour() {
        // Zmiana czasu: 29.03.2026 o 02:00 → 03:00. Umieszczenie 26.03 o 07:00.
        LocalDateTime placement = LocalDateTime.of(2026, 3, 26, 7, 0);

        LocalDateTime end = service.calculateMeasurementEnd(standardConfig(), placement);

        // 07:00 + 6h20m = 13:20 (26.03) → +120 h czasu rzeczywistego przez zmianę
        // czasu = 14:20 (31.03), a nie 13:20 jak przy naiwnym dodawaniu.
        assertThat(end)
                .as("rejestrator odlicza czas fizyczny, nie zegarowy")
                .isEqualTo(LocalDateTime.of(2026, 3, 31, 14, 20));
    }

    @Test
    @DisplayName("ST-CAL-03: pomiar przez zmianę na czas zimowy — godzina cofa się")
    void st_cal_03_fallBackShiftsWallClockBackByOneHour() {
        // Zmiana czasu: 25.10.2026 o 03:00 → 02:00. Umieszczenie 22.10 o 07:00.
        LocalDateTime placement = LocalDateTime.of(2026, 10, 22, 7, 0);

        LocalDateTime end = service.calculateMeasurementEnd(standardConfig(), placement);

        assertThat(end).isEqualTo(LocalDateTime.of(2026, 10, 27, 12, 20));
    }

    @Test
    @DisplayName("Niekompletna konfiguracja jest odrzucana, a nie liczona po cichu")
    void incompleteConfigIsRejected() {
        ProcedureClassConfig config = standardConfig();
        config.setStep3StabHours(null);

        assertThatThrownBy(() -> service.calculateStartDelay(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Niekompletna konfiguracja");

        assertThatThrownBy(() -> service.calculateStartDelay(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}