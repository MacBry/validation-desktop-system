package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.ValidationPlanNumber;
import com.mac.bry.desktop.repository.CoolingDeviceRepository;
import com.mac.bry.desktop.repository.ValidationPlanNumberRepository;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CoolingDeviceServiceImpl implements CoolingDeviceService {

    private final CoolingDeviceRepository coolingDeviceRepository;
    private final ValidationPlanNumberRepository validationPlanNumberRepository;

    @Override
    public List<CoolingDevice> findAll() {
        log.debug("Pobieranie wszystkich urządzeń chłodniczych");
        return coolingDeviceRepository.findAll();
    }

    @Override
    public Optional<CoolingDevice> findById(Long id) {
        log.debug("Pobieranie urządzenia chłodniczego o id: {}", id);
        return coolingDeviceRepository.findById(id);
    }

    @Override
    public Optional<CoolingDevice> findByIdWithRelations(Long id) {
        log.debug("Pobieranie urządzenia chłodniczego z relacjami o id: {}", id);
        return coolingDeviceRepository.findByIdWithRelations(id);
    }

    @Override
    public Optional<CoolingDevice> findByInventoryNumber(String inventoryNumber) {
        log.debug("Pobieranie urządzenia chłodniczego o numerze inwentarzowym: {}", inventoryNumber);
        return coolingDeviceRepository.findByInventoryNumber(inventoryNumber);
    }

    @Override
    public List<CoolingDevice> findByDepartment(Department department) {
        log.debug("Pobieranie urządzeń chłodniczych dla działu: {}", department.getName());
        return coolingDeviceRepository.findByDepartment(department);
    }

    @Override
    public List<CoolingDevice> findByLaboratory(Laboratory laboratory) {
        log.debug("Pobieranie urządzeń chłodniczych dla pracowni: {}", laboratory.getName());
        return coolingDeviceRepository.findByLaboratory(laboratory);
    }

    @Override
    @Transactional
    public CoolingDevice save(CoolingDevice coolingDevice) {
        log.debug("Zapisywanie urządzenia chłodniczego: {}", coolingDevice);
        
        // Auto-klasyfikacja kubatury według PDA TR-64 na podstawie objętości dla każdej komory
        if (coolingDevice.getChambers() != null) {
            for (CoolingChamber chamber : coolingDevice.getChambers()) {
                if (chamber.getVolume() != null && chamber.getVolumeCategory() == null) {
                    chamber.updateVolumeCategoryFromVolume();
                    log.debug("Automatycznie przypisano kategorię kubatury: {} dla komory: {} (objętość: {} m³)",
                            chamber.getVolumeCategory(), chamber.getChamberName(), chamber.getVolume());
                }
                // Zapewnienie relacji dwukierunkowej przed zapisem
                chamber.setCoolingDevice(coolingDevice);
            }
        }
        
        return coolingDeviceRepository.save(coolingDevice);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.debug("Usuwanie urządzenia chłodniczego o id: {}", id);
        coolingDeviceRepository.deleteById(id);
    }

    @Override
    public boolean existsByInventoryNumber(String inventoryNumber) {
        return coolingDeviceRepository.existsByInventoryNumber(inventoryNumber);
    }

    @Override
    public List<CoolingDevice> searchDevices(String query) {
        log.debug("Wyszukiwanie urządzeń chłodniczych po frazie: {}", query);
        return coolingDeviceRepository.searchDevices(query);
    }

    @Override
    @Transactional
    public ValidationPlanNumber addValidationPlanNumber(Long deviceId, Integer year, Integer planNumber) {
        log.debug("Dodawanie numeru RPW do urządzenia o id: {}, rok: {}, numer: {}", deviceId, year, planNumber);

        CoolingDevice device = coolingDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono urządzenia chłodniczego o id: " + deviceId));

        boolean exists = validationPlanNumberRepository.existsByCoolingDeviceAndYear(device, year);
        if (exists) {
            throw new IllegalArgumentException("Dla urządzenia o numerze " + device.getInventoryNumber() +
                    " istnieje już zdefiniowany numer RPW na rok " + year);
        }

        ValidationPlanNumber vpn = ValidationPlanNumber.builder()
                .year(year)
                .planNumber(planNumber)
                .coolingDevice(device)
                .build();

        device.addValidationPlanNumber(vpn);
        return validationPlanNumberRepository.save(vpn);
    }

    @Override
    @Transactional
    public void removeValidationPlanNumber(Long deviceId, Long planNumberId) {
        log.debug("Usuwanie numeru RPW o id: {} z urządzenia o id: {}", planNumberId, deviceId);

        CoolingDevice device = coolingDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono urządzenia chłodniczego o id: " + deviceId));

        ValidationPlanNumber vpn = validationPlanNumberRepository.findById(planNumberId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono numeru RPW o id: " + planNumberId));

        if (!vpn.getCoolingDevice().getId().equals(deviceId)) {
            throw new IllegalArgumentException("Numer RPW nie należy do wskazanego urządzenia.");
        }

        device.removeValidationPlanNumber(vpn);
        validationPlanNumberRepository.delete(vpn);
    }
}
