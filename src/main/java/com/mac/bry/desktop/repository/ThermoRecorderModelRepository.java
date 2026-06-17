package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.ThermoRecorderModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThermoRecorderModelRepository extends JpaRepository<ThermoRecorderModel, Long> {
    Optional<ThermoRecorderModel> findByName(String name);
    List<ThermoRecorderModel> findByActiveTrueOrderByNameAsc();
}
