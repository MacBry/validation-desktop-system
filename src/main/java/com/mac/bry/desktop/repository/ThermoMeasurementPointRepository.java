package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThermoMeasurementPointRepository extends JpaRepository<ThermoMeasurementPoint, Long> {

    List<ThermoMeasurementPoint> findBySeriesIdOrderByMeasurementIndexAsc(Long seriesId);
}
