package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.CalibrationPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CalibrationPointRepository extends JpaRepository<CalibrationPoint, Long> {
}
