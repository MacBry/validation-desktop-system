package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.Calibration;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.RecorderStatus;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.service.planner.exception.CalibrationExpiredException;
import com.mac.bry.desktop.service.planner.exception.MetrologicalRangeMismatchException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Kwalifikacja metrologiczna pojedynczego kanału rejestratora do badania
 * konkretnej komory — reguły W1 (ważność świadectwa) i W8 (pokrycie zakresu).
 * <p>
 * Rozdzielona od alokacji, bo decyduje o czymś innym: alokacja odpowiada na
 * pytanie „czy sprzęt jest wolny”, ta klasa na „czy w ogóle wolno go użyć”.
 */
@Service
public class MetrologicalQualificationService {

    /** Zapas ważności świadectwa poza koniec pomiaru (W1). */
    public static final int CALIBRATION_MARGIN_DAYS = 7;

    /**
     * Czy kanał rejestratora wolno użyć do badania komory.
     *
     * @param measurementEnd koniec okresu pomiarowego (Krok 4)
     * @throws CalibrationExpiredException         gdy świadectwo wygasa za wcześnie (W1)
     * @throws MetrologicalRangeMismatchException  gdy zakres PCA nie pokrywa materiału (W8)
     */
    public void requireQualified(ThermoRecorder recorder,
                                 int channelNumber,
                                 CoolingChamber chamber,
                                 LocalDate measurementEnd) {

        Calibration calibration = recorder.getLatestCalibrationForChannel(channelNumber);
        if (calibration == null) {
            throw new MetrologicalRangeMismatchException(String.format(
                    "Rejestrator %s kanał %d nie ma świadectwa wzorcowania",
                    recorder.getSerialNumber(), channelNumber));
        }

        LocalDate requiredValidUntil = measurementEnd.plusDays(CALIBRATION_MARGIN_DAYS);
        if (!calibration.isValidUntilAtLeast(requiredValidUntil)) {
            throw new CalibrationExpiredException(String.format(
                    "Rejestrator %s kanał %d: świadectwo %s ważne do %s, a pomiar wymaga ważności do %s (koniec pomiaru %s + %d dni)",
                    recorder.getSerialNumber(), channelNumber, calibration.getCertificateNumber(),
                    calibration.getValidUntil(), requiredValidUntil, measurementEnd, CALIBRATION_MARGIN_DAYS));
        }

        Double materialMin = chamber.getEffectiveMinTempLimit();
        Double materialMax = chamber.getEffectiveMaxTempLimit();
        if (!calibration.coversMaterialRange(materialMin, materialMax)) {
            throw new MetrologicalRangeMismatchException(String.format(
                    "Rejestrator %s kanał %d: zakres wzorcowania %s nie pokrywa zakresu materiału %s w komorze %s",
                    recorder.getSerialNumber(), channelNumber, calibration.getCalibratedRange(),
                    formatRange(materialMin, materialMax), chamber.getChamberName()));
        }
    }

    /**
     * Wariant bezwyjątkowy — do filtrowania puli, gdzie odrzucenie kandydata
     * jest normalnym przebiegiem, a nie błędem.
     */
    public boolean isQualified(ThermoRecorder recorder,
                               int channelNumber,
                               CoolingChamber chamber,
                               LocalDate measurementEnd) {
        try {
            requireQualified(recorder, channelNumber, chamber, measurementEnd);
            return true;
        } catch (CalibrationExpiredException | MetrologicalRangeMismatchException e) {
            return false;
        }
    }

    /**
     * Czy rejestrator jest w ogóle dostępny operacyjnie — wyłączone z użytku,
     * nieaktywne i wysłane do wzorcowania nie wchodzą do puli (W2).
     */
    public boolean isOperationallyAvailable(ThermoRecorder recorder) {
        return recorder.getStatus() == RecorderStatus.ACTIVE;
    }

    private String formatRange(Double min, Double max) {
        if (min == null && max == null) {
            return "nieokreślonego";
        }
        if (min == null) {
            return String.format("do %.1f°C", max);
        }
        if (max == null) {
            return String.format("od %.1f°C", min);
        }
        return String.format("%.1f°C do %.1f°C", min, max);
    }
}