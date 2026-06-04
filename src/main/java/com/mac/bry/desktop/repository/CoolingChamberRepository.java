package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.CoolingChamber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoolingChamberRepository extends JpaRepository<CoolingChamber, Long> {
    List<CoolingChamber> findByCoolingDeviceId(Long coolingDeviceId);

    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "coolingDevice")
    List<CoolingChamber> findAll();
}
