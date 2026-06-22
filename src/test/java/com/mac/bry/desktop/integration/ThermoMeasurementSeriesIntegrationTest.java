package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.*;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import com.mac.bry.desktop.service.ThermoMeasurementSeriesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ThermoMeasurementSeriesIntegrationTest {

    @Autowired
    private ThermoMeasurementSeriesService seriesService;

    @Autowired
    private ThermoRecorderRepository recorderRepository;

    @Autowired
    private CoolingDeviceRepository coolingDeviceRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private ThermoMeasurementSeriesRepository seriesRepository;

    @Autowired
    private ThermoRecorderModelRepository modelRepository;

    private Department testDept;
    private CoolingDevice testDevice;
    private CoolingChamber testChamber;
    private ThermoRecorder testRecorder;

    @BeforeEach
    void setUp() {
        seriesRepository.deleteAll();
        recorderRepository.deleteAll();
        coolingDeviceRepository.deleteAll();
        laboratoryRepository.deleteAll();
        departmentRepository.deleteAll();

        // Przygotowanie testowego działu
        testDept = new Department();
        testDept.setName("Dział Metrologii");
        testDept.setAbbreviation("MET");
        testDept.setDescription("Opis działu");
        testDept = departmentRepository.save(testDept);

        // Przygotowanie modelu rejestratora
        ThermoRecorderModel model = modelRepository.findByName("Testo 174T")
                .orElseGet(() -> modelRepository.save(ThermoRecorderModel.builder()
                        .name("Testo 174T")
                        .channelCount(1)
                        .defaultResolution(new BigDecimal("0.100"))
                        .active(true)
                        .build()));

        // Przygotowanie urządzenia chłodniczego z komorą
        testDevice = CoolingDevice.builder()
                .inventoryNumber("DEV-999")
                .name("Chłodziarka Testowa")
                .department(testDept)
                .build();
        
        testChamber = CoolingChamber.builder()
                .chamberName("Komora Główna Testowa")
                .chamberType(ChamberType.FRIDGE)
                .volume(0.5)
                .build();
        testChamber.updateVolumeCategoryFromVolume();
        
        testDevice.addChamber(testChamber);
        testDevice = coolingDeviceRepository.save(testDevice);
        testChamber = testDevice.getChambers().get(0);

        // Przygotowanie rejestratora
        testRecorder = ThermoRecorder.builder()
                .serialNumber("SN-174-TEST")
                .model(model)
                .status(RecorderStatus.ACTIVE)
                .resolution(new BigDecimal("0.100"))
                .department(testDept)
                .build();
        testRecorder = recorderRepository.save(testRecorder);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testSaveAndRetrieveSeriesAndPoints() {
        // 1. Przygotowanie serii pomiarowej
        LocalDateTime now = LocalDateTime.now();
        ThermoMeasurementSeries series = ThermoMeasurementSeries.builder()
                .thermoRecorder(testRecorder)
                .coolingChamber(testChamber)
                .batteryLevelPercent(99)
                .loggingIntervalMinutes(15)
                .measurementsCount(3)
                .programmingTimeUtc(now.minusHours(2))
                .startDelayMinutes(60)
                .firstMeasurementTimeUtc(now.minusHours(1))
                .firstMeasurementTimeLocal(now.minusHours(1))
                .importedAt(now)
                .importedBy("admin")
                .rawHexDump("AB3000020B37AB3100421B66AB32020020002D0036")
                .build();

        // Dodanie 3 punktów pomiarowych
        series.addMeasurement(ThermoMeasurementPoint.builder().measurementIndex(1).timestampLocal(now.minusMinutes(30)).rawCelsius(4.5).build());
        series.addMeasurement(ThermoMeasurementPoint.builder().measurementIndex(2).timestampLocal(now.minusMinutes(15)).rawCelsius(5.2).build());
        series.addMeasurement(ThermoMeasurementPoint.builder().measurementIndex(3).timestampLocal(now).rawCelsius(4.8).build());

        // 2. Zapis w bazie danych
        ThermoMeasurementSeries savedSeries = seriesService.saveSeries(series);
        assertNotNull(savedSeries.getId());
        assertEquals(3, savedSeries.getMeasurements().size());

        // 3. Wyszukiwanie serii i weryfikacja danych
        Optional<ThermoMeasurementSeries> retrievedOpt = seriesService.getSeriesById(savedSeries.getId());
        assertTrue(retrievedOpt.isPresent());
        
        ThermoMeasurementSeries retrieved = retrievedOpt.get();
        assertEquals("SN-174-TEST", retrieved.getThermoRecorder().getSerialNumber());
        assertEquals("Komora Główna Testowa", retrieved.getCoolingChamber().getChamberName());
        assertEquals(99, retrieved.getBatteryLevelPercent());
        assertEquals(3, retrieved.getMeasurements().size());
        assertEquals(4.5, retrieved.getMeasurements().get(0).getRawCelsius());
        assertEquals(1, retrieved.getMeasurements().get(0).getMeasurementIndex());

        // 4. Testy wyszukiwania po kluczu obcym
        assertFalse(seriesService.getSeriesForRecorder(testRecorder.getId()).isEmpty());
        assertFalse(seriesService.getSeriesForChamber(testChamber.getId()).isEmpty());

        // 5. Test kaskadowego usuwania
        seriesService.deleteSeries(retrieved.getId());
        assertTrue(seriesService.getSeriesById(retrieved.getId()).isEmpty());
    }
}
