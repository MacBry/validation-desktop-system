package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TestoCsvExportServiceTest {

    private final TestoCsvExportService exportService = new TestoCsvExportService();

    @Test
    @DisplayName("should export correct CSV structure and contents")
    void shouldExportCorrectCsv(@TempDir java.nio.file.Path tempDir) throws IOException {
        File targetFile = tempDir.resolve("test_export.csv").toFile();
        String model = "Testo 174T";
        String serialNumber = "12345678";
        String battery = "Good";
        String interval = "5 min";
        String comments = "Test, comment with comma\nand newline";
        
        List<ThermoMeasurementPoint> points = List.of(
                ThermoMeasurementPoint.builder()
                        .measurementIndex(1)
                        .timestampLocal(LocalDateTime.of(2026, 5, 21, 12, 0, 0))
                        .rawCelsius(4.5)
                        .build(),
                ThermoMeasurementPoint.builder()
                        .measurementIndex(2)
                        .timestampLocal(LocalDateTime.of(2026, 5, 21, 12, 5, 0))
                        .rawCelsius(4.7)
                        .build()
        );

        exportService.exportToCsv(targetFile, model, serialNumber, battery, interval, points.size(), comments, points);

        assertThat(targetFile).exists();
        List<String> lines = Files.readAllLines(targetFile.toPath());

        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).isEqualTo("Validation System - Standalone Testo Report");
        assertThat(lines).anyMatch(line -> line.startsWith("Wygenerowano,"));
        assertThat(lines).contains("Model rejestratora," + model);
        assertThat(lines).contains("Numer seryjny," + serialNumber);
        assertThat(lines).contains("Stan baterii," + battery);
        assertThat(lines).contains("Interwal probkowania," + interval);
        assertThat(lines).contains("Liczba punktow," + points.size());
        assertThat(lines).contains("Uwagi,Test; comment with comma and newline");
        
        // Check data rows
        assertThat(lines).contains("Lp.,Czas Lokalny Pomiaru,Temperatura [C]");
        assertThat(lines).contains("1,2026-05-21 12:00:00,4.5");
        assertThat(lines).contains("2,2026-05-21 12:05:00,4.7");
    }
}
