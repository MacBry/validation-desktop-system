package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.ProcedureClassConfig;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Matematyka opóźnienia startu rejestratora Testo (BA §3).
 * <p>
 * Opóźnienie obejmuje Krok 2 (transport i montaż) oraz Krok 3 (stabilizacja
 * termiczna), ale <b>nie</b> Krok 1 — zegar opóźnienia rusza dopiero po
 * zaprogramowaniu urządzenia, czyli na starcie transportu. Dzięki temu pierwsza
 * zapisana próbka jest już czystą próbką GxP, bez danych z fazy rozruchu
 * (reguła W3, Zero-Junk Data).
 */
@Service
public class TestoDelayCalculatorService {

    private static final int MINUTES_PER_HOUR = 60;

    /**
     * Opóźnienie startu rejestracji w minutach: Krok 2 + Krok 3.
     *
     * @throws IllegalArgumentException gdy konfiguracja jest niekompletna
     */
    public int calculateStartDelay(ProcedureClassConfig config) {
        requireConfig(config);
        return config.getStep2PlacementMinutes() + config.getStep3StabHours() * MINUTES_PER_HOUR;
    }

    /**
     * Moment pierwszej czystej próbki GxP — koniec stabilizacji, liczony od
     * chwili umieszczenia rejestratorów w komorze (Krok 2).
     */
    public LocalDateTime calculateFirstSampleTime(ProcedureClassConfig config, LocalDateTime placementTime) {
        requireConfig(config);
        return plusElapsedMinutes(placementTime, calculateStartDelay(config));
    }

    /**
     * Czas trwania właściwego pomiaru GxP (Krok 4) w minutach.
     */
    public int calculateMeasurementDurationMinutes(ProcedureClassConfig config) {
        requireConfig(config);
        return config.getStep4IntervalMinutes() * config.getStep4SampleCount();
    }

    /**
     * Koniec okresu pomiarowego GxP, liczony od chwili umieszczenia w komorze.
     * <p>
     * Zgodnie z BA §3 okres pomiarowy to {@code interwał × liczba próbek}
     * (40 próbek co 3 h = 120 h) — moment, w którym pamięć się zapełnia
     * i rejestrator zatrzymuje zapis („Stop when full”). Ostatnia próbka jest
     * fizycznie zapisywana jeden interwał wcześniej; do planowania liczy się
     * zatrzymanie zapisu, bo dopiero od niego biegnie bufor odczytu z Kroku 5.
     */
    public LocalDateTime calculateMeasurementEnd(ProcedureClassConfig config, LocalDateTime placementTime) {
        requireConfig(config);
        return plusElapsedMinutes(
                calculateFirstSampleTime(config, placementTime),
                calculateMeasurementDurationMinutes(config));
    }

    /**
     * Moment fizycznego zapisu ostatniej próbki — o jeden interwał wcześniej niż
     * {@link #calculateMeasurementEnd}. Przydatny przy weryfikacji danych z importu.
     */
    public LocalDateTime calculateLastSampleTime(ProcedureClassConfig config, LocalDateTime placementTime) {
        requireConfig(config);
        return plusElapsedMinutes(
                calculateMeasurementEnd(config, placementTime),
                -config.getStep4IntervalMinutes());
    }

    /**
     * Nieprzekraczalny termin odczytu USB — koniec pomiaru powiększony o bufor
     * z Kroku 5. Po jego minięciu bez importu danych planer zgłasza alert W7.
     */
    public LocalDateTime calculateReadoutDeadline(ProcedureClassConfig config, LocalDateTime placementTime) {
        requireConfig(config);
        return plusElapsedMinutes(
                calculateMeasurementEnd(config, placementTime),
                (long) config.getStep5ReadoutBufferHours() * MINUTES_PER_HOUR);
    }

    /**
     * Dodaje <b>rzeczywisty</b> upływ czasu, a nie czas zegarowy.
     * <p>
     * Rejestrator odlicza sekundy własnym zegarem, więc pomiar trwający ok. 120 h
     * i przechodzący przez zmianę czasu kończy się o godzinę później (wiosną)
     * lub wcześniej (jesienią) niż wynikałoby z naiwnego dodawania na
     * {@link LocalDateTime}. Przeliczenie przez strefę {@code Europe/Warsaw}
     * odwzorowuje zachowanie urządzenia (ST-CAL-03).
     */
    private LocalDateTime plusElapsedMinutes(LocalDateTime start, long minutes) {
        return start.atZone(OperatorCalendarService.ZONE)
                .plus(Duration.ofMinutes(minutes))
                .toLocalDateTime();
    }

    private void requireConfig(ProcedureClassConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Konfiguracja klasy procedury jest wymagana");
        }
        if (config.getStep2PlacementMinutes() == null || config.getStep3StabHours() == null
                || config.getStep4IntervalMinutes() == null || config.getStep4SampleCount() == null
                || config.getStep5ReadoutBufferHours() == null) {
            throw new IllegalArgumentException(
                    "Niekompletna konfiguracja klasy procedury: " + config.getName());
        }
    }
}