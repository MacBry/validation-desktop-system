package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.dto.UserAuditDto;
import com.mac.bry.desktop.model.ChamberType;
import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.model.MaterialType;
import com.mac.bry.desktop.repository.CoolingDeviceRepository;
import com.mac.bry.desktop.repository.MaterialTypeRepository;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import com.mac.bry.desktop.security.service.AuditService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class CoolingDeviceAuditIntegrationTest {

    @Autowired
    private CoolingDeviceRepository coolingDeviceRepository;

    @Autowired
    private MaterialTypeRepository materialTypeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EntityManager entityManager;

    private Department testDept;
    private Laboratory testLab;

    @BeforeEach
    void setUp() {
        coolingDeviceRepository.deleteAll();
        materialTypeRepository.deleteAll();
        laboratoryRepository.deleteAll();
        departmentRepository.deleteAll();

        // Przygotowanie testowego działu i pracowni
        testDept = new Department();
        testDept.setName("Metrologia i Walidacja");
        testDept.setAbbreviation("MET-VAL");
        testDept = departmentRepository.save(testDept);

        testLab = new Laboratory();
        testLab.setName("Laboratorium Pomiarowe");
        testLab.setAbbreviation("LAB-POM");
        testLab.setDepartment(testDept);
        testLab = laboratoryRepository.save(testLab);
    }

    @Test
    @WithMockUser(username = "admin_audit_tester")
    void shouldAuditMaterialTypeCreationAndModifications() {
        // 1. UTWORZENIE materiału
        MaterialType material = MaterialType.builder()
                .name("Krew pełna testowa (2-6°C)")
                .description("Opis testowy")
                .minStorageTemp(2.0)
                .maxStorageTemp(6.0)
                .activationEnergy(new BigDecimal("75.4300"))
                .standardSource("ISO 9001 / GMP")
                .active(true)
                .build();

        material = materialTypeRepository.saveAndFlush(material);
        Long materialId = material.getId();
        entityManager.clear();

        // Sprawdzenie historii po utworzeniu
        List<UserAuditDto> historyAfterCreate = auditService.getEntityHistory(MaterialType.class, materialId, "Typ Materiału");
        assertFalse(historyAfterCreate.isEmpty());
        
        UserAuditDto creationEntry = historyAfterCreate.stream()
                .filter(h -> "UTWORZENIE".equals(h.getOperationType()))
                .findFirst()
                .orElseThrow();
        assertEquals("Krew pełna testowa (2-6°C)", creationEntry.getNewValue());
        assertEquals("admin_audit_tester", creationEntry.getModifiedBy());

        // 2. EDYCJA materiału
        MaterialType loadedMaterial = materialTypeRepository.findById(materialId).orElseThrow();
        loadedMaterial.setName("Krew pełna testowa modyfikowana");
        loadedMaterial.setMinStorageTemp(3.0);
        loadedMaterial.setActivationEnergy(new BigDecimal("80.1200"));
        loadedMaterial.setActive(false);

        materialTypeRepository.saveAndFlush(loadedMaterial);
        entityManager.clear();

        // Sprawdzenie historii po modyfikacji
        List<UserAuditDto> historyAfterMod = auditService.getEntityHistory(MaterialType.class, materialId, "Typ Materiału");
        
        UserAuditDto nameChange = historyAfterMod.stream()
                .filter(h -> "Nazwa".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("Krew pełna testowa (2-6°C)", nameChange.getOldValue());
        assertEquals("Krew pełna testowa modyfikowana", nameChange.getNewValue());

        UserAuditDto tempMinChange = historyAfterMod.stream()
                .filter(h -> "Temp Min".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("2.0", tempMinChange.getOldValue());
        assertEquals("3.0", tempMinChange.getNewValue());

        UserAuditDto eaChange = historyAfterMod.stream()
                .filter(h -> "Energia aktywacji Ea".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("75.4300", eaChange.getOldValue());
        assertEquals("80.1200", eaChange.getNewValue());

        UserAuditDto activeChange = historyAfterMod.stream()
                .filter(h -> "Status aktywności".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("true", activeChange.getOldValue());
        assertEquals("false", activeChange.getNewValue());
    }

    @Test
    @WithMockUser(username = "device_audit_tester")
    void shouldAuditCoolingDeviceCreationAndModifications() {
        // Przygotowanie typu materiału dla powiązania
        MaterialType material = MaterialType.builder()
                .name("Szczepionki testowe (2-8°C)")
                .minStorageTemp(2.0)
                .maxStorageTemp(8.0)
                .active(true)
                .build();
        material = materialTypeRepository.saveAndFlush(material);

        MaterialType otherMaterial = MaterialType.builder()
                .name("Surowce farmaceutyczne (15-25°C)")
                .minStorageTemp(15.0)
                .maxStorageTemp(25.0)
                .active(true)
                .build();
        otherMaterial = materialTypeRepository.saveAndFlush(otherMaterial);

        // 1. UTWORZENIE urządzenia wraz z komorą podrzędną
        CoolingDevice device = CoolingDevice.builder()
                .inventoryNumber("CD-VAL-2026-001")
                .name("Komora chłodnicza K-01")
                .department(testDept)
                .laboratory(testLab)
                .build();

        com.mac.bry.desktop.model.CoolingChamber chamber = com.mac.bry.desktop.model.CoolingChamber.builder()
                .chamberName("Komora Główna")
                .chamberType(ChamberType.FRIDGE)
                .materialType(material)
                .minOperatingTemp(2.0)
                .maxOperatingTemp(8.0)
                .volume(1.8)
                .build();
        chamber.updateVolumeCategoryFromVolume();

        device.addChamber(chamber);

        device = coolingDeviceRepository.saveAndFlush(device);
        Long deviceId = device.getId();
        entityManager.clear();

        // Weryfikacja historii po utworzeniu
        List<UserAuditDto> historyAfterCreate = auditService.getEntityHistory(CoolingDevice.class, deviceId, "Urządzenie Chłodnicze");
        assertFalse(historyAfterCreate.isEmpty());
        
        UserAuditDto creationEntry = historyAfterCreate.stream()
                .filter(h -> "UTWORZENIE".equals(h.getOperationType()))
                .findFirst()
                .orElseThrow();
        assertEquals("Komora chłodnicza K-01", creationEntry.getNewValue());
        assertEquals("device_audit_tester", creationEntry.getModifiedBy());

        // 2. MODYFIKACJA urządzenia i komory
        CoolingDevice loadedDevice = coolingDeviceRepository.findById(deviceId).orElseThrow();
        loadedDevice.setName("Komora chłodnicza K-01 Modyfikowana");
        
        com.mac.bry.desktop.model.CoolingChamber loadedChamber = loadedDevice.getChambers().get(0);
        loadedChamber.setChamberType(ChamberType.LOW_TEMP_FREEZER);
        loadedChamber.setMaterialType(otherMaterial);
        loadedChamber.setMinOperatingTemp(-80.0);
        loadedChamber.setMaxOperatingTemp(-60.0);
        loadedChamber.setVolume(2.5); // Klasa M (2-20 m3)
        loadedChamber.updateVolumeCategoryFromVolume();

        coolingDeviceRepository.saveAndFlush(loadedDevice);
        entityManager.clear();

        // Weryfikacja historii po modyfikacjach
        List<UserAuditDto> historyAfterMod = auditService.getEntityHistory(CoolingDevice.class, deviceId, "Urządzenie Chłodnicze");

        UserAuditDto nameChange = historyAfterMod.stream()
                .filter(h -> "Nazwa urządzenia".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("Komora chłodnicza K-01", nameChange.getOldValue());
        assertEquals("Komora chłodnicza K-01 Modyfikowana", nameChange.getNewValue());

        UserAuditDto typeChange = historyAfterMod.stream()
                .filter(h -> "Komora (Komora Główna) - Typ".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals(ChamberType.FRIDGE.getDisplayName(), typeChange.getOldValue());
        assertEquals(ChamberType.LOW_TEMP_FREEZER.getDisplayName(), typeChange.getNewValue());

        UserAuditDto matChange = historyAfterMod.stream()
                .filter(h -> "Komora (Komora Główna) - Typ materiału".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("Szczepionki testowe (2-8°C)", matChange.getOldValue());
        assertEquals("Surowce farmaceutyczne (15-25°C)", matChange.getNewValue());

        UserAuditDto tempMinChange = historyAfterMod.stream()
                .filter(h -> "Komora (Komora Główna) - Temp Min".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("2.0", tempMinChange.getOldValue());
        assertEquals("-80.0", tempMinChange.getNewValue());

        UserAuditDto tempMaxChange = historyAfterMod.stream()
                .filter(h -> "Komora (Komora Główna) - Temp Max".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("8.0", tempMaxChange.getOldValue());
        assertEquals("-60.0", tempMaxChange.getNewValue());

        UserAuditDto volChange = historyAfterMod.stream()
                .filter(h -> "Komora (Komora Główna) - Pojemność (m³)".equals(h.getFieldName()))
                .findFirst()
                .orElseThrow();
        assertEquals("1.8", volChange.getOldValue());
        assertEquals("2.5", volChange.getNewValue());
    }
}
