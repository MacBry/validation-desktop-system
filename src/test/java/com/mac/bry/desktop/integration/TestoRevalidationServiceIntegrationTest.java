package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.*;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import com.mac.bry.desktop.service.TestoRevalidationService;
import com.mac.bry.desktop.service.TestoRevalidationPdfService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Slf4j
public class TestoRevalidationServiceIntegrationTest {

    @Autowired
    private TestoRevalidationService revalidationService;

    @Autowired
    private ThermoRecorderRepository recorderRepository;

    @Autowired
    private CalibrationRepository calibrationRepository;

    @Autowired
    private CoolingDeviceRepository coolingDeviceRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ThermoMeasurementSeriesRepository seriesRepository;

    @Autowired
    private ThermoMeasurementPointRepository pointRepository;

    @Autowired
    private TestoRevalidationPdfService pdfService;

    @Autowired
    private com.mac.bry.desktop.security.repository.UserRepository userRepository;

    @Autowired
    private com.mac.bry.desktop.security.repository.LaboratoryRepository laboratoryRepository;

    private Department testDept;
    private CoolingDevice testDevice;
    private CoolingChamber testChamber;

    @BeforeEach
    void setUp() {
        seriesRepository.deleteAll();
        recorderRepository.deleteAll();
        coolingDeviceRepository.deleteAll();
        userRepository.deleteAll();
        laboratoryRepository.deleteAll();
        departmentRepository.deleteAll();

        // Przygotowanie testowego działu
        testDept = new Department();
        testDept.setName("Dział Walidacji Systemów");
        testDept.setAbbreviation("DWS");
        testDept.setDescription("Dział odpowiedzialny za rewalidację komór GxP");
        testDept = departmentRepository.save(testDept);

        // Przygotowanie urządzenia chłodniczego z komorą
        testDevice = CoolingDevice.builder()
                .inventoryNumber("DEV-INV-CH100")
                .name("Lodówka Szczepionkowa Liebherr")
                .department(testDept)
                .build();
        
        testChamber = CoolingChamber.builder()
                .chamberName("Komora Główna L1")
                .chamberType(ChamberType.FRIDGE)
                .volume(0.8)
                .build();
        testChamber.updateVolumeCategoryFromVolume();
        
        testDevice.addChamber(testChamber);
        testDevice = coolingDeviceRepository.save(testDevice);
        testChamber = testDevice.getChambers().get(0);
    }

    @Test
    @WithMockUser(username = "metrolog", roles = {"USER"})
    void testRevalidationSessionFlowInSimulationMode() {
        // 1. Inicjalizacja sesji rewalidacji w pamięci
        RevalidationSession session = revalidationService.initSession(testDevice, testChamber);
        assertNotNull(session);
        assertEquals("DEV-INV-CH100", session.getCoolingDevice().getInventoryNumber());
        assertEquals("Komora Główna L1", session.getCoolingChamber().getChamberName());
        assertTrue(session.getAssignedPositions().isEmpty());

        // 2. Symulacyjny odczyt 8 narożników komory
        LocalDateTime expectedStart = LocalDateTime.of(2026, 5, 18, 0, 0, 0);

        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            RevalidationSession.PositionData posData = revalidationService.readPositionData(session, pos, true);
            assertNotNull(posData);
            
            int index = pos.ordinal() + 1;
            String expectedSn = "SN-174-T00" + index + "-SIM";
            String expectedCert = "CERT-2026-T00" + index + "-SIM";

            assertEquals(expectedSn, posData.getSerialNumber());
            assertEquals("Testo 174T (Symulacja)", posData.getModel());
            assertNotNull(posData.getRecorder());
            assertNotNull(posData.getLatestCalibration());
            assertEquals(expectedCert, posData.getLatestCalibration().getCertificateNumber());
            
            // Weryfikacja ważności certyfikatu
            assertTrue(posData.getLatestCalibration().isValid());
            assertTrue(posData.getLatestCalibration().getValidUntil().isAfter(LocalDate.now()));

            // Weryfikacja serii pomiarowej
            ThermoMeasurementSeries series = posData.getSeries();
            assertNotNull(series);
            assertEquals(180, series.getLoggingIntervalMinutes()); // interwał 3h
            assertEquals(40, series.getMeasurementsCount()); // 40 próbek
            assertEquals(expectedStart, series.getFirstMeasurementTimeLocal());
            assertEquals(40, series.getMeasurements().size());

            // Weryfikacja spójności punktów
            for (int i = 0; i < 40; i++) {
                ThermoMeasurementPoint pt = series.getMeasurements().get(i);
                assertEquals(i + 1, pt.getMeasurementIndex());
                assertEquals(expectedStart.plusHours(i * 3), pt.getTimestampLocal());
                
                // Temperatura nie może odbiegać za daleko od baselinu (2.0°C - 8.0°C)
                assertTrue(pt.getRawCelsius() >= 2.0 && pt.getRawCelsius() <= 8.0, 
                        "Temperatura " + pt.getRawCelsius() + " poza bezpiecznym zakresem walidacji!");
            }

            // Dodanie do sesji
            session.getAssignedPositions().put(pos, posData);
        }

        // Upewniamy się, że wczytano dokładnie 8 pozycji
        assertEquals(8, session.getAssignedPositions().size());

        // 3. Sprawdzenie, czy mock logery zostały zapisane w bazie danych w trakcie symulacji
        for (int index = 1; index <= 8; index++) {
            String sn = "SN-174-T00" + index + "-SIM";
            Optional<ThermoRecorder> recorderOpt = recorderRepository.findBySerialNumber(sn);
            assertTrue(recorderOpt.isPresent());
            ThermoRecorder rec = recorderOpt.get();
            assertEquals("Testo 174T (Symulacja)", rec.getModel());
            assertNotNull(rec.getLatestCalibration());
        }

        // 4. Trwałe zapisanie całej sesji (8 wgranych serii) w bazie danych
        revalidationService.saveRevalidationSession(session);

        // 5. Weryfikacja, czy serie i punkty pomiarowe fizycznie trafiły do bazy danych
        assertEquals(8, seriesRepository.count());
        
        for (ThermoMeasurementSeries saved : seriesRepository.findAll()) {
            assertEquals(session.getSessionId(), saved.getRevalidationGroupId());
            assertNotNull(saved.getGridPosition());
        }
        
        // Każda seria ma 40 punktów, czyli 8 * 40 = 320 punktów
        assertEquals(320, pointRepository.count());
        
        log.info("Zintegrowany proces rewalidacji w trybie symulacji przeszedł test integracyjny w 100% poprawnie!");
    }

    @Test
    @WithMockUser(username = "metrolog", roles = {"USER"})
    void testPdfCompilationFlow() throws Exception {
        // 1. Inicjalizacja sesji rewalidacji
        RevalidationSession session = revalidationService.initSession(testDevice, testChamber);
        
        // 2. Populacja danych
        for (RevalidationSession.GridPosition pos : RevalidationSession.GridPosition.values()) {
            RevalidationSession.PositionData posData = revalidationService.readPositionData(session, pos, true);
            session.getAssignedPositions().put(pos, posData);
        }

        // 3. Tworzymy tymczasowe pliki
        java.io.File tempPdf = java.io.File.createTempFile("reval_test_report_", ".pdf");
        java.io.File tempChart = java.io.File.createTempFile("reval_test_chart_", ".png");
        
        try {
            // Zapisujemy przykładowy pusty/mock plik graficzny
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(500, 260, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            javax.imageio.ImageIO.write(img, "png", tempChart);

            // 4. Kompilujemy PDF
            pdfService.generateRevalidationReport(session, tempPdf, tempChart);

            // 5. Weryfikacja
            assertTrue(tempPdf.exists());
            assertTrue(tempPdf.length() > 0);
            log.info("Zintegrowany raport PDF z rewalidacji został pomyślnie skompilowany i zapisany (rozmiar: {} B)", tempPdf.length());

        } finally {
            if (tempPdf.exists()) tempPdf.delete();
            if (tempChart.exists()) tempChart.delete();
        }
    }
}
