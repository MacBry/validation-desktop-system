package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoolingChamberRepository extends JpaRepository<CoolingChamber, Long> {
    List<CoolingChamber> findByCoolingDeviceId(Long coolingDeviceId);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "coolingDevice")
    List<CoolingChamber> findAll();

    /**
     * Komory urządzeń w danym statusie — planer bierze pod uwagę wyłącznie
     * urządzenia aktywne. Kolejność stała, żeby plan roczny był odtwarzalny.
     */
    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"coolingDevice", "coolingDevice.laboratory", "coolingDevice.department", "materialType"})
    List<CoolingChamber> findByCoolingDeviceStatusOrderByIdAsc(DeviceStatus status);
}
