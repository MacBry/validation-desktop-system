package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.RevalidationSession.PositionData;
import com.mac.bry.desktop.security.model.Department;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RevalidationZipCompilerTest {

    @Mock
    private TestoRevalidationPdfService pdfService;

    @Mock
    private TestoPdfReportService pdfReportService;

    @Mock
    private JavaFxChartRenderer chartRenderer;

    private TestoRevalidationWordService wordService;
    private RevalidationZipCompiler zipCompiler;

    @BeforeEach
    void setUp() {
        wordService = new TestoRevalidationWordService(org.mockito.Mockito.mock(com.mac.bry.desktop.repository.ValidationPlanNumberRepository.class));
        zipCompiler = new RevalidationZipCompiler(pdfService, wordService, pdfReportService, chartRenderer);
    }

    @Test
    @DisplayName("Powinien skompilować paczkę ZIP zawierającą Załącznik nr 3, Załącznik nr 8 oraz inne raporty")
    void shouldCompileZipPackageWithAppendix3And8(@TempDir Path tempDir) throws Exception {
        // Given
        Department department = new Department();
        department.setName("Dział Walidacji");

        CoolingDevice device = CoolingDevice.builder()
                .inventoryNumber("DEV-999")
                .name("Liebherr Fridge")
                .department(department)
                .build();

        CoolingChamber chamber = CoolingChamber.builder()
                .chamberType(ChamberType.FRIDGE)
                .minOperatingTemp(2.0)
                .maxOperatingTemp(6.0)
                .build();

        LocalDateTime testStart = LocalDateTime.of(2026, 5, 20, 10, 0);
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .minTemperature(3.5)
                .maxTemperature(4.5)
                .avgTemperature(4.0)
                .batteryLevelPercent(100)
                .loggingIntervalMinutes(15)
                .measurementsCount(2)
                .startDelayMinutes(0)
                .firstMeasurementTimeLocal(testStart)
                .build();

        PositionData posData = PositionData.builder()
                .serialNumber("SN-123")
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

        File mainChartPng = tempDir.resolve("chart.png").toFile();
        mainChartPng.createNewFile();

        File outputZip = tempDir.resolve("revalidation_package.zip").toFile();

        // Stubs
        doNothing().when(pdfService).generateRevalidationReport(any(), any(), any());
        when(pdfService.getShortCode(GridPosition.TOP_FRONT_LEFT)).thenReturn("GPL");
        
        File indChartPng = tempDir.resolve("individual_chart.png").toFile();
        indChartPng.createNewFile();
        when(chartRenderer.renderSeriesToPng(any())).thenReturn(indChartPng);
        doNothing().when(pdfReportService).generatePdfReport(any(), any(), any());

        // When
        zipCompiler.compile(session, mainChartPng, outputZip);

        // Then
        assertThat(outputZip).exists();
        assertThat(outputZip.length()).isGreaterThan(0);

        // Weryfikacja zawartości archiwum ZIP
        try (ZipFile zipFile = new ZipFile(outputZip)) {
            Map<String, ZipEntry> entries = new HashMap<>();
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                entries.put(entry.getName(), entry);
            }

            // Sprawdzenie obecności kluczowych raportów w ZIP
            assertThat(entries.keySet()).contains(
                "Raport_Rewalidacji_GxP_DEV-999.pdf",
                "Zalacznik_nr_8_Graficzny_schemat_rozmieszczenia_rejestratorow.docx",
                "Zalacznik_nr_3_Raport_z_walidacji_procesu_przechowywania.docx",
                "wykresy/Wykres_serii_GPL_SN-123.pdf"
            );

            // Sprawdzenie, czy plik Załącznika nr 3 nie jest pusty
            ZipEntry app3Entry = entries.get("Zalacznik_nr_3_Raport_z_walidacji_procesu_przechowywania.docx");
            assertThat(app3Entry).isNotNull();
            assertThat(app3Entry.getSize()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("Sentinel -1 (brak odczytu baterii) trafia do raportu jako N/D, nie jako '-1%'")
    void shouldRenderBatterySentinelAsNotAvailable(@TempDir Path tempDir) throws Exception {
        // Given — seria z importu PDF 184, gdzie źródło nie podaje stanu baterii.
        Department department = new Department();
        department.setName("Dział Walidacji");

        CoolingDevice device = CoolingDevice.builder()
                .inventoryNumber("DEV-777").name("Liebherr Fridge").department(department).build();
        CoolingChamber chamber = CoolingChamber.builder()
                .chamberType(ChamberType.FRIDGE).minOperatingTemp(2.0).maxOperatingTemp(6.0).build();

        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .minTemperature(3.5).maxTemperature(4.5).avgTemperature(4.0)
                .batteryLevelPercent(-1)
                .loggingIntervalMinutes(15).measurementsCount(2).startDelayMinutes(0)
                .firstMeasurementTimeLocal(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();

        Map<GridPosition, PositionData> assignedPositions = new HashMap<>();
        assignedPositions.put(GridPosition.TOP_FRONT_LEFT,
                PositionData.builder().serialNumber("SN-184").series(series).build());

        RevalidationSession session = RevalidationSession.builder()
                .coolingDevice(device).coolingChamber(chamber)
                .assignedPositions(assignedPositions)
                .procedureType(GxPProcedureType.PERIODIC_REVALIDATION)
                .build();

        File mainChartPng = tempDir.resolve("chart.png").toFile();
        mainChartPng.createNewFile();
        File indChartPng = tempDir.resolve("individual_chart.png").toFile();
        indChartPng.createNewFile();

        doNothing().when(pdfService).generateRevalidationReport(any(), any(), any());
        when(pdfService.getShortCode(GridPosition.TOP_FRONT_LEFT)).thenReturn("GPL");
        when(chartRenderer.renderSeriesToPng(any())).thenReturn(indChartPng);
        doNothing().when(pdfReportService).generatePdfReport(any(), any(), any());

        // When
        zipCompiler.compile(session, mainChartPng, tempDir.resolve("pkg.zip").toFile());

        // Then
        ArgumentCaptor<TestoPdfReportService.TestoReportData> captor =
                ArgumentCaptor.forClass(TestoPdfReportService.TestoReportData.class);
        verify(pdfReportService).generatePdfReport(captor.capture(), any(), any());

        assertThat(captor.getValue().batteryLevel)
                .as("wartość niemierzona nie może trafić do dokumentu GxP jako liczba")
                .isEqualTo("N/D")
                .doesNotContain("-1");
    }

    /**
     * Buduje minimalną sesję z jedną pozycją i przechwytuje metrykę, która trafia
     * do indywidualnego wykresu PDF. Pozwala sprawdzić opis rejestratora i stanu
     * baterii bez powtarzania całego rusztowania sesji w każdym teście.
     */
    private TestoPdfReportService.TestoReportData captureReportDataFor(
            PositionData positionData, Path tempDir) throws Exception {
        Department department = new Department();
        department.setName("Dział Walidacji");

        CoolingDevice device = CoolingDevice.builder()
                .inventoryNumber("DEV-555").name("Liebherr Fridge").department(department).build();
        CoolingChamber chamber = CoolingChamber.builder()
                .chamberType(ChamberType.FRIDGE).minOperatingTemp(2.0).maxOperatingTemp(6.0).build();

        Map<GridPosition, PositionData> assignedPositions = new HashMap<>();
        assignedPositions.put(GridPosition.TOP_FRONT_LEFT, positionData);

        RevalidationSession session = RevalidationSession.builder()
                .coolingDevice(device).coolingChamber(chamber)
                .assignedPositions(assignedPositions)
                .procedureType(GxPProcedureType.PERIODIC_REVALIDATION)
                .build();

        File mainChartPng = tempDir.resolve("chart.png").toFile();
        mainChartPng.createNewFile();
        File indChartPng = tempDir.resolve("individual_chart.png").toFile();
        indChartPng.createNewFile();

        doNothing().when(pdfService).generateRevalidationReport(any(), any(), any());
        when(pdfService.getShortCode(GridPosition.TOP_FRONT_LEFT)).thenReturn("GPL");
        when(chartRenderer.renderSeriesToPng(any())).thenReturn(indChartPng);
        doNothing().when(pdfReportService).generatePdfReport(any(), any(), any());

        zipCompiler.compile(session, mainChartPng, tempDir.resolve("pkg.zip").toFile());

        ArgumentCaptor<TestoPdfReportService.TestoReportData> captor =
                ArgumentCaptor.forClass(TestoPdfReportService.TestoReportData.class);
        verify(pdfReportService).generatePdfReport(captor.capture(), any(), any());
        return captor.getValue();
    }

    private ThermoMeasurementSeries seriesWith(Integer remainingDays, Integer legacyPercent) {
        return ThermoMeasurementSeries.builder()
                .minTemperature(3.5).maxTemperature(4.5).avgTemperature(4.0)
                .batteryRemainingDays(remainingDays)
                .batteryLevelPercent(legacyPercent)
                .loggingIntervalMinutes(15).measurementsCount(2).startDelayMinutes(0)
                .firstMeasurementTimeLocal(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
    }

    @Test
    @DisplayName("Stan baterii w pakiecie to dni, ta sama wielkość co w zakładce Odczyt Testo")
    void shouldRenderBatteryAsRemainingDays(@TempDir Path tempDir) throws Exception {
        PositionData position = PositionData.builder()
                .serialNumber("SN-174").series(seriesWith(386, null)).build();

        TestoPdfReportService.TestoReportData rd = captureReportDataFor(position, tempDir);

        assertThat(rd.batteryLevel)
                .as("procent był wielkością wyprowadzoną z progu alarmowego, nie ze stanu ogniwa")
                .isEqualTo("386 dni pozostałej pracy")
                .doesNotContain("%");
    }

    @Test
    @DisplayName("Seria archiwalna z samym procentem drukuje go z adnotacją, żeby nie udawał dni")
    void shouldMarkLegacyPercentReading(@TempDir Path tempDir) throws Exception {
        PositionData position = PositionData.builder()
                .serialNumber("SN-174").series(seriesWith(null, 80)).build();

        TestoPdfReportService.TestoReportData rd = captureReportDataFor(position, tempDir);

        assertThat(rd.batteryLevel)
                .startsWith("80%")
                .as("bez adnotacji ten sam wiersz raportu znaczyłby raz dni, raz procent")
                .contains("archiwalny");
    }

    @Test
    @DisplayName("Zero dni to wyczerpana bateria, nie brak odczytu")
    void shouldTreatZeroDaysAsMeasuredValue(@TempDir Path tempDir) throws Exception {
        PositionData position = PositionData.builder()
                .serialNumber("SN-174").series(seriesWith(0, null)).build();

        TestoPdfReportService.TestoReportData rd = captureReportDataFor(position, tempDir);

        assertThat(rd.batteryLevel).isEqualTo("0 dni pozostałej pracy").isNotEqualTo("N/D");
    }

    @Test
    @DisplayName("Rejestrator wielokanałowy niesie numer kanału w metryce wykresu")
    void shouldQualifySerialNumberWithChannelForMultiChannelRecorders(@TempDir Path tempDir) throws Exception {
        PositionData position = PositionData.builder()
                .serialNumber("SN-175-T3")
                .model(ThermoRecorderModel.builder().name("Testo 175 T3").channelCount(3).build())
                .channelNumber(2)
                .series(seriesWith(400, null))
                .build();

        TestoPdfReportService.TestoReportData rd = captureReportDataFor(position, tempDir);

        assertThat(rd.serialNumber)
                .as("bez kanału pakiet ma kilka wykresów opisanych tym samym S/N")
                .isEqualTo("SN-175-T3 (Kanał 2)");
    }

    @Test
    @DisplayName("Rejestrator jednokanałowy nie dostaje zbędnego dopisku o kanale")
    void shouldLeaveSerialNumberIntactForSingleChannelRecorders(@TempDir Path tempDir) throws Exception {
        PositionData position = PositionData.builder()
                .serialNumber("SN-174-T")
                .model(ThermoRecorderModel.builder().name("Testo 174 T").channelCount(1).build())
                .channelNumber(1)
                .series(seriesWith(400, null))
                .build();

        TestoPdfReportService.TestoReportData rd = captureReportDataFor(position, tempDir);

        assertThat(rd.serialNumber).isEqualTo("SN-174-T").doesNotContain("Kanał");
    }

    @Test
    @DisplayName("Powinien skompilować paczkę ZIP zawierającą Załącznik nr 7 zamiast Załącznika nr 3 dla sesji mapowania")
    void shouldCompileZipPackageWithAppendix7ForMappingSession(@TempDir Path tempDir) throws Exception {
        // Given
        Department department = new Department();
        department.setName("Dział Walidacji");

        CoolingDevice device = CoolingDevice.builder()
                .inventoryNumber("DEV-999")
                .name("Liebherr Fridge")
                .department(department)
                .build();

        CoolingChamber chamber = CoolingChamber.builder()
                .chamberType(ChamberType.FRIDGE)
                .minOperatingTemp(2.0)
                .maxOperatingTemp(6.0)
                .materialType(new MaterialType())
                .build();

        LocalDateTime testStart = LocalDateTime.of(2026, 5, 20, 10, 0);
        Map<GridPosition, PositionData> assignedPositions = new HashMap<>();

        // We populate all 8 positions to satisfy MappingValidator
        int idx = 1;
        for (GridPosition pos : GridPosition.values()) {
            double temp = 5.0;
            if (pos == GridPosition.TOP_FRONT_LEFT) {
                temp = 7.2;
            } else if (pos == GridPosition.BOTTOM_BACK_RIGHT) {
                temp = 2.1;
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
                    .serialNumber("SN-" + idx)
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

        File mainChartPng = tempDir.resolve("chart.png").toFile();
        mainChartPng.createNewFile();

        File outputZip = tempDir.resolve("revalidation_package_mapping.zip").toFile();

        // Stubs
        doNothing().when(pdfService).generateRevalidationReport(any(), any(), any());
        when(pdfService.getShortCode(any())).thenReturn("GPL");
        
        File indChartPng = tempDir.resolve("individual_chart.png").toFile();
        indChartPng.createNewFile();
        when(chartRenderer.renderSeriesToPng(any())).thenReturn(indChartPng);
        doNothing().when(pdfReportService).generatePdfReport(any(), any(), any());

        // When
        zipCompiler.compile(session, mainChartPng, outputZip);

        // Then
        assertThat(outputZip).exists();
        assertThat(outputZip.length()).isGreaterThan(0);

        try (ZipFile zipFile = new ZipFile(outputZip)) {
            Map<String, ZipEntry> entries = new HashMap<>();
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                entries.put(entry.getName(), entry);
            }

            // Sprawdzenie obecności kluczowych raportów w ZIP
            assertThat(entries.keySet()).contains(
                "Raport_Rewalidacji_GxP_DEV-999.pdf",
                "Zalacznik_nr_8_Graficzny_schemat_rozmieszczenia_rejestratorow.docx",
                "Zalacznik_nr_7_Protokol_wykonania_mapowania_urzadzenia.docx"
            );

            // Sprawdzenie, czy nie ma Załącznika nr 3
            assertThat(entries.keySet()).doesNotContain("Zalacznik_nr_3_Raport_z_walidacji_procesu_przechowywania.docx");

            // Sprawdzenie, czy plik Załącznika nr 7 nie jest pusty
            ZipEntry app7Entry = entries.get("Zalacznik_nr_7_Protokol_wykonania_mapowania_urzadzenia.docx");
            assertThat(app7Entry).isNotNull();
            assertThat(app7Entry.getSize()).isGreaterThan(0);
        }
    }
}
