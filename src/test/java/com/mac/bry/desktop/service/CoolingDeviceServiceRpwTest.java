package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.model.ValidationPlanNumber;
import com.mac.bry.desktop.repository.CoolingDeviceRepository;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CoolingDeviceServiceRpwTest {

    @Autowired
    private CoolingDeviceService coolingDeviceService;

    @Autowired
    private CoolingDeviceRepository coolingDeviceRepository;

    @Autowired
    private ValidationPlanNumberRepository validationPlanNumberRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    private CoolingDevice testDevice;
    private Department testDept;
    private Laboratory testLab;

    @BeforeEach
    void setUp() {
        validationPlanNumberRepository.deleteAll();
        coolingDeviceRepository.deleteAll();
        laboratoryRepository.deleteAll();
        departmentRepository.deleteAll();

        testDept = new Department();
        testDept.setName("Dział Produkcji");
        testDept.setAbbreviation("PROD");
        testDept = departmentRepository.save(testDept);

        testLab = new Laboratory();
        testLab.setName("Pracownia Kontroli Jakości");
        testLab.setAbbreviation("QC");
        testLab.setDepartment(testDept);
        testLab = laboratoryRepository.save(testLab);

        testDevice = CoolingDevice.builder()
                .inventoryNumber("DEV-RPW-123")
                .name("Chłodziarka QC-1")
                .department(testDept)
                .laboratory(testLab)
                .build();
        testDevice = coolingDeviceRepository.save(testDevice);
    }

    @Test
    @DisplayName("Powinien poprawnie dodać numer RPW do urządzenia i sformatować go z pracownią")
    void shouldAddRpwAndFormatWithLaboratory() {
        // When
        ValidationPlanNumber vpn = coolingDeviceService.addValidationPlanNumber(testDevice.getId(), 2026, 42);

        // Then
        assertThat(vpn.getId()).isNotNull();
        assertThat(vpn.getYear()).isEqualTo(2026);
        assertThat(vpn.getPlanNumber()).isEqualTo(42);
        assertThat(vpn.getFormattedRpw()).isEqualTo("42/QC/2026");

        // Verify database state
        List<ValidationPlanNumber> list = validationPlanNumberRepository.findByCoolingDeviceOrderByYearDesc(testDevice);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getFormattedRpw()).isEqualTo("42/QC/2026");
    }

    @Test
    @DisplayName("Powinien poprawnie dodać numer RPW do urządzenia bez pracowni i sformatować go z działem")
    void shouldAddRpwAndFormatWithDepartmentFallback() {
        // Given
        CoolingDevice deviceNoLab = CoolingDevice.builder()
                .inventoryNumber("DEV-RPW-456")
                .name("Chłodziarka PROD-1")
                .department(testDept)
                .laboratory(null)
                .build();
        deviceNoLab = coolingDeviceRepository.save(deviceNoLab);

        // When
        ValidationPlanNumber vpn = coolingDeviceService.addValidationPlanNumber(deviceNoLab.getId(), 2026, 99);

        // Then
        assertThat(vpn.getFormattedRpw()).isEqualTo("99/PROD/2026");
    }

    @Test
    @DisplayName("Powinien zgłosić wyjątek przy próbie dodania planu na ten sam rok dla tego samego urządzenia")
    void shouldPreventDuplicateYearRpwForSameDevice() {
        // Given
        coolingDeviceService.addValidationPlanNumber(testDevice.getId(), 2026, 42);

        // When / Then
        assertThatThrownBy(() -> {
            coolingDeviceService.addValidationPlanNumber(testDevice.getId(), 2026, 43);
        }).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("istnieje już zdefiniowany numer RPW na rok 2026");
    }

    @Test
    @DisplayName("Powinien poprawnie usunąć numer RPW")
    void shouldDeleteRpw() {
        // Given
        ValidationPlanNumber vpn = coolingDeviceService.addValidationPlanNumber(testDevice.getId(), 2026, 42);
        assertThat(validationPlanNumberRepository.existsById(vpn.getId())).isTrue();

        // When
        coolingDeviceService.removeValidationPlanNumber(testDevice.getId(), vpn.getId());

        // Then
        assertThat(validationPlanNumberRepository.existsById(vpn.getId())).isFalse();
    }
}
