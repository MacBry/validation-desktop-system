package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.RevalidationSession.PositionData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestoRevalidationPdfServiceTest {

    private final TestoRevalidationPdfService pdfService = new TestoRevalidationPdfService(null);

    @Test
    @DisplayName("Powinien wygenerować indywidualny wykres serii PDF o rozmiarze większym niż zero")
    void shouldGenerateIndividualSeriesChartPdf(@TempDir File tempDir) throws Exception {
        // Given
        File outputFile = new File(tempDir, "wykres_test.pdf");

        List<ThermoMeasurementPoint> measurements = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 18, 0, 0);
        for (int i = 0; i < 40; i++) {
            measurements.add(ThermoMeasurementPoint.builder()
                    .timestampLocal(startTime.plusMinutes(i * 15))
                    .rawCelsius(4.5 + Math.sin(i * 0.5))
                    .build());
        }

        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .minTemperature(3.5)
                .maxTemperature(5.5)
                .avgTemperature(4.5)
                .mktTemperature(4.6)
                .expandedUncertainty(0.08)
                .driftClassification("STABLE")
                .measurements(measurements)
                .build();

        PositionData positionData = PositionData.builder()
                .serialNumber("SN-999-PDF")
                .model("Testo 174T")
                .series(series)
                .build();

        // When
        pdfService.generateIndividualSeriesChartPdf(GridPosition.TOP_FRONT_LEFT, positionData, outputFile);

        // Then
        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Powinien wygenerować makietę certyfikatu PDF o rozmiarze większym niż zero")
    void shouldGenerateMockCertificatePdf(@TempDir File tempDir) throws Exception {
        // Given
        File outputFile = new File(tempDir, "cert_test.pdf");

        ThermoRecorder recorder = ThermoRecorder.builder()
                .serialNumber("SN-CERT-777")
                .model("Testo 174T")
                .build();

        Calibration calibration = Calibration.builder()
                .certificateNumber("CERT-1234/2026")
                .calibrationDate(LocalDate.now().minusMonths(1))
                .validUntil(LocalDate.now().plusMonths(11))
                .thermoRecorder(recorder)
                .points(new ArrayList<>())
                .build();

        calibration.addPoint(CalibrationPoint.builder().temperatureValue(new BigDecimal("0.0")).systematicError(new BigDecimal("0.05")).uncertainty(new BigDecimal("0.02")).build());
        calibration.addPoint(CalibrationPoint.builder().temperatureValue(new BigDecimal("5.0")).systematicError(new BigDecimal("-0.02")).uncertainty(new BigDecimal("0.02")).build());

        // When
        pdfService.generateMockCertificatePdf(calibration, outputFile);

        // Then
        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(0);
    }
}
