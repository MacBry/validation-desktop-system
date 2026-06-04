package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.RevalidationSession.PositionData;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TestoRevalidationWordServiceTest {

    private final TestoRevalidationWordService wordService = new TestoRevalidationWordService();

    @Test
    @DisplayName("Powinien wygenerować Załącznik nr 8 podmieniając aktywne znaczniki i czyszcząc nieaktywne")
    void shouldGenerateAppendix8WithCorrectReplacements() throws Exception {
        // Given
        Department department = new Department();
        department.setName("Dzial Testow Metrologicznych");
        department.setAbbreviation("DTM");

        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorium Referencyjne");
        laboratory.setAbbreviation("LR");

        CoolingDevice device = CoolingDevice.builder()
                .name("Zamrazarka Liebherr LGT 3725")
                .inventoryNumber("DEV-LIEB-LGT3725")
                .department(department)
                .laboratory(laboratory)
                .build();

        MaterialType materialType = new MaterialType();
        materialType.setName("Osocze świeżo mrożone");

        CoolingChamber chamber = CoolingChamber.builder()
                .chamberName("Komora Dolna")
                .chamberType(ChamberType.FREEZER)
                .materialType(materialType)
                .build();

        // Tworzenie serii pomiarowej dla aktywnego narożnika TOP_FRONT_LEFT (Pozycja 1)
        LocalDateTime testStart = LocalDateTime.of(2026, 5, 18, 8, 0);
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .minTemperature(-32.4)
                .maxTemperature(-28.1)
                .batteryLevelPercent(99)
                .loggingIntervalMinutes(15)
                .measurementsCount(100)
                .firstMeasurementTimeLocal(testStart)
                .build();

        PositionData activePositionData = PositionData.builder()
                .serialNumber("SN-TEST-12345")
                .model("Testo 174T")
                .series(series)
                .build();

        Map<GridPosition, PositionData> assignedPositions = new HashMap<>();
        assignedPositions.put(GridPosition.TOP_FRONT_LEFT, activePositionData);

        RevalidationSession session = RevalidationSession.builder()
                .coolingDevice(device)
                .coolingChamber(chamber)
                .assignedPositions(assignedPositions)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        wordService.generateAppendix8(session, baos);

        // Then
        byte[] docxBytes = baos.toByteArray();
        assertThat(docxBytes).isNotEmpty();

        // Odczyt wyjściowego dokumentu w celu weryfikacji zawartości tekstowej
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String fullText = extractTextFromDoc(doc);

            // 1. Sprawdzenie metadanych nagłówkowych
            assertThat(fullText).contains("Dzial Testow Metrologicznych");
            assertThat(fullText).contains("Laboratorium Referencyjne");
            assertThat(fullText).contains("Zamrazarka Liebherr LGT 3725");
            assertThat(fullText).contains("DEV-LIEB-LGT3725");
            assertThat(fullText).contains("Osocze świeżo mrożone");
            assertThat(fullText).contains("2026-05-18");

            // 2. Sprawdzenie danych aktywnej pozycji 1 (TOP_FRONT_LEFT)
            assertThat(fullText).contains("SN-TEST-12345");
            assertThat(fullText).contains("-32.4");
            assertThat(fullText).contains("-28.1");

            // 3. Sprawdzenie, czy znacznik cyfrowy $1$ (obecność rejestratora) został zamieniony na X
            // W pełnym tekście akapitu lub tabeli nie powinien już występować surowy znacznik $1$, lecz X
            assertThat(fullText).doesNotContain("$1$");
            
            // 4. Sprawdzenie, czy nieaktywne znaczniki (np. dla pozycji 8) zostały zamienione na puste napisy ""
            assertThat(fullText).doesNotContain("$nrSerREJ8$");
            assertThat(fullText).doesNotContain("$tmax8$");
            assertThat(fullText).doesNotContain("$tmin8$");
            assertThat(fullText).doesNotContain("$8$");
        }
    }

    private String extractTextFromDoc(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        
        // Akapity główne
        for (XWPFParagraph p : doc.getParagraphs()) {
            sb.append(p.getText()).append("\n");
        }
        
        // Tabele
        for (XWPFTable table : doc.getTables()) {
            extractTextFromTable(table, sb);
        }

        // Nagłówki i stopki
        for (XWPFHeader header : doc.getHeaderList()) {
            for (XWPFParagraph p : header.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            for (XWPFTable table : header.getTables()) {
                extractTextFromTable(table, sb);
            }
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            for (XWPFParagraph p : footer.getParagraphs()) {
                sb.append(p.getText()).append("\n");
            }
            for (XWPFTable table : footer.getTables()) {
                extractTextFromTable(table, sb);
            }
        }

        return sb.toString();
    }

    private void extractTextFromTable(XWPFTable table, StringBuilder sb) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    sb.append(p.getText()).append(" ");
                }
                sb.append("\n");
                for (XWPFTable nestedTable : cell.getTables()) {
                    extractTextFromTable(nestedTable, sb);
                }
            }
        }
    }

    @Test
    @DisplayName("Powinien wygenerować Załącznik nr 3 podmieniając aktywne znaczniki i wykonując wnioskowanie GxP")
    void shouldGenerateAppendix3WithCorrectReplacements() throws Exception {
        // Given
        Department department = new Department();
        department.setName("Dzial Testow Metrologicznych");
        department.setAbbreviation("DTM");

        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorium Referencyjne");
        laboratory.setAbbreviation("LR");

        CoolingDevice device = CoolingDevice.builder()
                .name("Lodowka Liebherr LKv 3913")
                .inventoryNumber("DEV-LIEB-LKV3913")
                .department(department)
                .laboratory(laboratory)
                .build();

        MaterialType materialType = new MaterialType();
        materialType.setName("KKCz");

        CoolingChamber chamber = CoolingChamber.builder()
                .chamberName("Komora Glowna")
                .chamberType(ChamberType.FRIDGE)
                .materialType(materialType)
                .minOperatingTemp(2.0)
                .maxOperatingTemp(6.0)
                .build();

        LocalDateTime testStart = LocalDateTime.of(2026, 5, 18, 8, 0);
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .minTemperature(2.5)
                .maxTemperature(5.8)
                .avgTemperature(4.1)
                .batteryLevelPercent(99)
                .loggingIntervalMinutes(15)
                .measurementsCount(3)
                .firstMeasurementTimeLocal(testStart)
                .build();
                
        series.addMeasurement(ThermoMeasurementPoint.builder().measurementIndex(1).timestampLocal(testStart).rawCelsius(3.0).build());
        series.addMeasurement(ThermoMeasurementPoint.builder().measurementIndex(2).timestampLocal(testStart.plusMinutes(15)).rawCelsius(4.0).build());
        series.addMeasurement(ThermoMeasurementPoint.builder().measurementIndex(3).timestampLocal(testStart.plusMinutes(30)).rawCelsius(5.0).build());

        Calibration calibration = Calibration.builder()
                .calibrationDate(java.time.LocalDate.of(2025, 12, 10))
                .certificateNumber("CERT-12345-2025")
                .validUntil(java.time.LocalDate.of(2026, 12, 10))
                .build();

        PositionData posData = PositionData.builder()
                .serialNumber("SN-184-TEST")
                .model("Testo 184T3")
                .series(series)
                .latestCalibration(calibration)
                .build();

        Map<GridPosition, PositionData> assignedPositions = new HashMap<>();
        assignedPositions.put(GridPosition.TOP_FRONT_LEFT, posData);

        RevalidationSession session = RevalidationSession.builder()
                .coolingDevice(device)
                .coolingChamber(chamber)
                .assignedPositions(assignedPositions)
                .procedureType(GxPProcedureType.PERIODIC_REVALIDATION)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        wordService.generateAppendix3(session, baos);

        // Then
        byte[] docxBytes = baos.toByteArray();
        assertThat(docxBytes).isNotEmpty();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String fullText = extractTextFromDoc(doc);

            // Weryfikacja podstawowych metadanych
            assertThat(fullText).contains("Dzial Testow Metrologicznych");
            assertThat(fullText).contains("Laboratorium Referencyjne");
            assertThat(fullText).contains("Lodowka Liebherr LKv 3913");
            assertThat(fullText).contains("DEV-LIEB-LKV3913");
            assertThat(fullText).contains("KKCz");
            assertThat(fullText).contains("2026-05-18");

            // Weryfikacja nowych pól RPW (brak repozytorium -> wartości domyślne)
            assertThat(fullText).contains("LR"); // skrót pracowni pobrany z urządzenia
            assertThat(fullText).doesNotContain("$NrRPW$");
            assertThat(fullText).doesNotContain("$skrotPracowni$");
            assertThat(fullText).doesNotContain("$rokRPW$");

            // Weryfikacja kryteriów akceptacji (Lodówka KKCz -> $o4$)
            assertThat(fullText).contains("[X]"); // o4 lub tak
            assertThat(fullText).doesNotContain("$o4$");
            assertThat(fullText).doesNotContain("$o1$");

            // Weryfikacja wyników sensorów
            assertThat(fullText).contains("SN-184-TEST");
            assertThat(fullText).contains("CERT-12345-2025");
            assertThat(fullText).contains("2025-12-10");
            assertThat(fullText).contains("Góra - Przód-Lewy");
            assertThat(fullText).contains("2.5");
            assertThat(fullText).contains("5.8");
            assertThat(fullText).contains("4.1");

            // Weryfikacja czyszczenia nieaktywnych rejestratorów
            assertThat(fullText).doesNotContain("$nrSerRej8$");
            assertThat(fullText).doesNotContain("$TminRej8$");

            // Weryfikacja wnioskowania GxP
            assertThat(fullText).contains("Na podstawie analizy przestrzennej rozkładu temperatur");
            assertThat(fullText).contains("spełnia kryteria akceptacji");
            assertThat(fullText).contains("Brak uwag. Walidacja zakończona wynikiem pozytywnym");
            assertThat(fullText).contains("2027-05-18"); // data kolejnej walidacji (rok po ostatnim odczycie: 2026-05-18 + 1 rok)
        }
    }

    @Test
    @DisplayName("Powinien wygenerować Załącznik nr 3 pobierając dane RPW z repozytorium")
    void shouldGenerateAppendix3WithCorrectRpwFromRepository() throws Exception {
        // Given
        ValidationPlanNumberRepository mockRepo = org.mockito.Mockito.mock(ValidationPlanNumberRepository.class);
        wordService.setValidationPlanNumberRepository(mockRepo);
        try {
            Department department = new Department();
            department.setName("Dzial Testow Metrologicznych");
            department.setAbbreviation("DTM");

            Laboratory laboratory = new Laboratory();
            laboratory.setName("Laboratorium Referencyjne");
            laboratory.setAbbreviation("LR");

            CoolingDevice device = CoolingDevice.builder()
                    .name("Lodowka Liebherr LKv 3913")
                    .inventoryNumber("DEV-LIEB-LKV3913")
                    .department(department)
                    .laboratory(laboratory)
                    .build();

            ValidationPlanNumber plan = ValidationPlanNumber.builder()
                    .planNumber(42)
                    .year(2026)
                    .coolingDevice(device)
                    .build();

            org.mockito.Mockito.when(mockRepo.findByCoolingDeviceOrderByYearDesc(device))
                    .thenReturn(java.util.List.of(plan));

            MaterialType materialType = new MaterialType();
            materialType.setName("KKP");

            CoolingChamber chamber = CoolingChamber.builder()
                    .chamberType(ChamberType.FRIDGE)
                    .minOperatingTemp(2.0)
                    .maxOperatingTemp(6.0)
                    .materialType(materialType)
                    .build();

            LocalDateTime testStart = LocalDateTime.of(2026, 5, 18, 8, 0);
            ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                    .minTemperature(2.5)
                    .maxTemperature(5.8)
                    .avgTemperature(4.1)
                    .batteryLevelPercent(99)
                    .loggingIntervalMinutes(15)
                    .measurementsCount(1)
                    .firstMeasurementTimeLocal(testStart)
                    .build();
            series.addMeasurement(ThermoMeasurementPoint.builder().measurementIndex(1).timestampLocal(testStart).rawCelsius(3.0).build());

            PositionData posData = PositionData.builder()
                    .serialNumber("SN-184-TEST")
                    .series(series)
                    .build();

            Map<GridPosition, PositionData> assignedPositions = new HashMap<>();
            assignedPositions.put(GridPosition.TOP_FRONT_LEFT, posData);

            RevalidationSession session = RevalidationSession.builder()
                    .coolingDevice(device)
                    .coolingChamber(chamber)
                    .assignedPositions(assignedPositions)
                    .procedureType(GxPProcedureType.PERIODIC_REVALIDATION)
                    .build();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // When
            wordService.generateAppendix3(session, baos);

            // Then
            byte[] docxBytes = baos.toByteArray();
            assertThat(docxBytes).isNotEmpty();

            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
                String fullText = extractTextFromDoc(doc);
                // Note: since mock test runs on the template, we assert placeholders are replaced
                assertThat(fullText).doesNotContain("$NrRPW$");
                assertThat(fullText).doesNotContain("$skrotPracowni$");
                assertThat(fullText).doesNotContain("$rokRPW$");
            }
        } finally {
            wordService.setValidationPlanNumberRepository(null);
        }
    }

    @Test
    @DisplayName("Powinien wygenerować Załącznik nr 7 podmieniając dane mapowania i kwalifikacji")
    void shouldGenerateAppendix7WithCorrectReplacements() throws Exception {
        // Given
        Department department = new Department();
        department.setName("Dzial Testow Metrologicznych");
        department.setAbbreviation("DTM");

        Laboratory laboratory = new Laboratory();
        laboratory.setName("Laboratorium Referencyjne");
        laboratory.setAbbreviation("LR");

        CoolingDevice device = CoolingDevice.builder()
                .name("Lodowka Liebherr LKv 3913")
                .inventoryNumber("DEV-LIEB-LKV3913")
                .department(department)
                .laboratory(laboratory)
                .build();

        MaterialType materialType = new MaterialType();
        materialType.setName("KKP");

        CoolingChamber chamber = CoolingChamber.builder()
                .chamberName("Komora Glowna")
                .chamberType(ChamberType.FRIDGE)
                .materialType(materialType)
                .build();

        Map<GridPosition, PositionData> assignedPositions = new HashMap<>();
        LocalDateTime testStart = LocalDateTime.of(2026, 5, 18, 8, 0);

        int idx = 1;
        for (GridPosition pos : GridPosition.values()) {
            double temp = 5.0;
            if (pos == GridPosition.TOP_FRONT_LEFT) {
                temp = 7.2; // Hotspot
            } else if (pos == GridPosition.BOTTOM_BACK_RIGHT) {
                temp = 2.1; // Coldspot
            }
            ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                    .minTemperature(temp)
                    .maxTemperature(temp)
                    .loggingIntervalMinutes(15)
                    .measurementsCount(1)
                    .firstMeasurementTimeLocal(testStart)
                    .build();
            series.addMeasurement(ThermoMeasurementPoint.builder().measurementIndex(1).timestampLocal(testStart).rawCelsius(temp).build());

            PositionData pd = PositionData.builder()
                    .serialNumber("SN-REC-" + idx)
                    .series(series)
                    .build();
            assignedPositions.put(pos, pd);
            idx++;
        }

        RevalidationSession session = RevalidationSession.builder()
                .coolingDevice(device)
                .coolingChamber(chamber)
                .assignedPositions(assignedPositions)
                .procedureType(GxPProcedureType.MAPPING)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        wordService.generateAppendix7(session, baos);

        // Then
        byte[] docxBytes = baos.toByteArray();
        assertThat(docxBytes).isNotEmpty();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String fullText = extractTextFromDoc(doc);
            assertThat(fullText).contains("Dzial Testow Metrologicznych");
            assertThat(fullText).contains("Laboratorium Referencyjne");
            assertThat(fullText).contains("Lodowka Liebherr LKv 3913");
            assertThat(fullText).contains("DEV-LIEB-LKV3913");
            assertThat(fullText).contains("KKP");

            // Weryfikacja Hotspotu (TOP_FRONT_LEFT)
            assertThat(fullText).contains("SN-REC-1");
            assertThat(fullText).contains("Góra - Przód-Lewy");

            // Weryfikacja Coldspotu (BOTTOM_BACK_RIGHT)
            assertThat(fullText).contains("SN-REC-8");
            assertThat(fullText).contains("Dół - Tył-Prawy");

            assertThat(fullText).doesNotContain("$OT1$");
            assertThat(fullText).doesNotContain("$ON1$");
            assertThat(fullText).doesNotContain("$OT8$");
            assertThat(fullText).doesNotContain("$ON8$");
        }
    }
}
