package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.model.ValidationPlanNumber;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;

import java.util.List;
import java.util.Optional;

public interface CoolingDeviceService {
    List<CoolingDevice> findAll();
    Optional<CoolingDevice> findById(Long id);
    Optional<CoolingDevice> findByIdWithRelations(Long id);
    Optional<CoolingDevice> findByInventoryNumber(String inventoryNumber);
    List<CoolingDevice> findByDepartment(Department department);
    List<CoolingDevice> findByLaboratory(Laboratory laboratory);
    CoolingDevice save(CoolingDevice coolingDevice);
    void deleteById(Long id);
    boolean existsByInventoryNumber(String inventoryNumber);
    List<CoolingDevice> searchDevices(String query);

    ValidationPlanNumber addValidationPlanNumber(Long deviceId, Integer year, Integer planNumber);
    void removeValidationPlanNumber(Long deviceId, Long planNumberId);
}
