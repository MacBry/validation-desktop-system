package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.service.planner.exception.CalibrationExpiredException;
import com.mac.bry.desktop.service.planner.exception.MetrologicalRangeMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ST-W8-01..04 oraz ST-W1-01 — kwalifikacja metrologiczna kanału rejestratora.
 */
class MetrologicalQualificationServiceTest {

    private static final LocalDate MEASUREMENT_END = LocalDate.of(2026, 8, 10);

    private final MetrologicalQualificationService service = new MetrologicalQualificationService();

    private ThermoRecorder recorderWithCalibrationPoints(LocalDate validUntil, double... temperatures) {
        Calibration calibration = Calibration.builder()
                .calibrationDate(LocalDate.of(2026, 1, 15))
                .certificateNumber("PCA/2026/001")
                .validUntil(validUntil)
                .channelNumber(1)
                .build();

        for (double temperature : temperatures) {
            calibration.addPoint(CalibrationPoint.builder()
                    .temperatureValue(BigDecimal.valueOf(temperature))
                    .systematicError(BigDecimal.ZERO)
                    .uncertainty(new BigDecimal("0.1"))
                    .build());
        }

        ThermoRecorder recorder = ThermoRecorder.builder()
                .serialNumber("SN-001")
                .status(RecorderStatus.ACTIVE)
                .model(ThermoRecorderModel.builder().name("Testo 175 T1").channelCount(1).build())
                .build();
        recorder.addCalibration(calibration);
        return recorder;
    }

    private CoolingChamber chamberForMaterial(Double minTemp, Double maxTemp, String materialName) {
        return CoolingChamber.builder()
                .chamberName("Komora " + materialName)
                .volumeCategory(VolumeCategory.SMALL)
                .materialType(MaterialType.builder()
                        .name(materialName)
                        .minStorageTemp(minTemp)
                        .maxStorageTemp(maxTemp)
                        .requiresMapping(true)
                        .build())
                .build();
    }

    @ParameterizedTest(name = "materiał {0}…{1} °C wobec punktów {2} i {3} → akceptacja={4}")
    @CsvSource({
            // przykłady wprost z decyzji użytkownika
            "2,   6,   2,   8,  true",
            "2,   10,  2,   8,  false",
            // analogia dla materiałów mrożonych
            "-30, -25, -30, -20, true",
            "-30, -15, -30, -20, false",
            // ST-W8-03: dolna granica niepokryta
            "2,   6,   3,   8,  false",
            // ST-W8-02: punkty 0/10 pokrywają 2…6 z zapasem
            "2,   6,   0,   10, true"
    })
    @DisplayName("W8: zawieranie domknięte na liczbach ze znakiem")
    void w8_closedIntervalContainment(double materialMin, double materialMax,
                                      double calibrationLow, double calibrationHigh,
                                      boolean expectedAcceptance) {
        ThermoRecorder recorder = recorderWithCalibrationPoints(
                LocalDate.of(2027, 1, 15), calibrationLow, calibrationHigh);
        CoolingChamber chamber = chamberForMaterial(materialMin, materialMax, "Materiał testowy");

        assertThat(service.isQualified(recorder, 1, chamber, MEASUREMENT_END))
                .isEqualTo(expectedAcceptance);
    }

    @Test
    @DisplayName("ST-W8-01: rejestrator mrożarkowy odrzucony dla komory KKCZ")
    void st_w8_01_frozenRangeRecorderRejectedForKkcz() {
        ThermoRecorder recorder = recorderWithCalibrationPoints(LocalDate.of(2027, 1, 15), -30.0, -20.0);
        CoolingChamber kkcz = chamberForMaterial(2.0, 6.0, "KKCZ");

        assertThatThrownBy(() -> service.requireQualified(recorder, 1, kkcz, MEASUREMENT_END))
                .isInstanceOf(MetrologicalRangeMismatchException.class)
                .hasMessageContaining("nie pokrywa zakresu materiału");
    }

    @Test
    @DisplayName("ST-W8-02: świadectwo w punktach 0/+5/+10 kwalifikuje do KKCZ")
    void st_w8_02_correctRangeAccepted() {
        ThermoRecorder recorder = recorderWithCalibrationPoints(
                LocalDate.of(2027, 1, 15), 0.0, 5.0, 10.0);
        CoolingChamber kkcz = chamberForMaterial(2.0, 6.0, "KKCZ");

        assertThat(service.isQualified(recorder, 1, kkcz, MEASUREMENT_END)).isTrue();
        assertThat(recorder.getLatestCalibration().getCalibratedMinTemp()).isEqualTo(0.0);
        assertThat(recorder.getLatestCalibration().getCalibratedMaxTemp()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("ST-W8-04: FFP bez dolnej granicy — sprawdzana tylko strona górna")
    void st_w8_04_openEndedMaterialRange() {
        ThermoRecorder recorder = recorderWithCalibrationPoints(LocalDate.of(2027, 1, 15), -30.0, -20.0);
        // Materiał „≤ −25 °C" — dolna granica nieokreślona
        CoolingChamber ffp = chamberForMaterial(null, -25.0, "Osocze FFP");

        assertThat(service.isQualified(recorder, 1, ffp, MEASUREMENT_END))
                .as("−20 °C pokrywa granicę −25 °C, dolnej granicy materiał nie ma")
                .isTrue();
    }

    @Test
    @DisplayName("ST-W1-01: świadectwo wygasa w trakcie pomiaru → odrzucenie")
    void st_w1_01_calibrationExpiresDuringMeasurement() {
        // Pomiar kończy się 10.08.2026, więc wzorcowanie musi być ważne do 17.08.2026
        ThermoRecorder recorder = recorderWithCalibrationPoints(LocalDate.of(2026, 8, 13), 0.0, 10.0);
        CoolingChamber kkcz = chamberForMaterial(2.0, 6.0, "KKCZ");

        assertThatThrownBy(() -> service.requireQualified(recorder, 1, kkcz, MEASUREMENT_END))
                .isInstanceOf(CalibrationExpiredException.class)
                .hasMessageContaining("2026-08-17");
    }

    @Test
    @DisplayName("W1: zapas 7 dni liczony domknięcie — ważność dokładnie do granicy wystarcza")
    void w1_marginBoundaryIsInclusive() {
        CoolingChamber kkcz = chamberForMaterial(2.0, 6.0, "KKCZ");

        ThermoRecorder exactly = recorderWithCalibrationPoints(LocalDate.of(2026, 8, 17), 0.0, 10.0);
        assertThat(service.isQualified(exactly, 1, kkcz, MEASUREMENT_END)).isTrue();

        ThermoRecorder oneDayShort = recorderWithCalibrationPoints(LocalDate.of(2026, 8, 16), 0.0, 10.0);
        assertThat(service.isQualified(oneDayShort, 1, kkcz, MEASUREMENT_END)).isFalse();
    }

    @Test
    @DisplayName("Świadectwo bez punktów nie kwalifikuje — nie ma z czego wyprowadzić zakresu")
    void calibrationWithoutPointsIsRejected() {
        ThermoRecorder recorder = recorderWithCalibrationPoints(LocalDate.of(2027, 1, 15));
        CoolingChamber kkcz = chamberForMaterial(2.0, 6.0, "KKCZ");

        assertThatThrownBy(() -> service.requireQualified(recorder, 1, kkcz, MEASUREMENT_END))
                .isInstanceOf(MetrologicalRangeMismatchException.class);
    }

    @Test
    @DisplayName("Brak świadectwa dla kanału nie kwalifikuje")
    void missingCalibrationForChannelIsRejected() {
        ThermoRecorder recorder = recorderWithCalibrationPoints(LocalDate.of(2027, 1, 15), 0.0, 10.0);
        CoolingChamber kkcz = chamberForMaterial(2.0, 6.0, "KKCZ");

        assertThatThrownBy(() -> service.requireQualified(recorder, 2, kkcz, MEASUREMENT_END))
                .isInstanceOf(MetrologicalRangeMismatchException.class)
                .hasMessageContaining("nie ma świadectwa wzorcowania");
    }

    @Test
    @DisplayName("Do puli wchodzą wyłącznie rejestratory aktywne")
    void onlyActiveRecordersAreAvailable() {
        ThermoRecorder recorder = recorderWithCalibrationPoints(LocalDate.of(2027, 1, 15), 0.0, 10.0);

        assertThat(service.isOperationallyAvailable(recorder)).isTrue();

        recorder.setStatus(RecorderStatus.UNDER_CALIBRATION);
        assertThat(service.isOperationallyAvailable(recorder)).isFalse();

        recorder.setStatus(RecorderStatus.DECOMMISSIONED);
        assertThat(service.isOperationallyAvailable(recorder)).isFalse();
    }
}