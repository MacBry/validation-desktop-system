package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.model.ValidationPlanNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

@Repository
public interface ValidationPlanNumberRepository extends JpaRepository<ValidationPlanNumber, Long> {

    @Query("select vpn from ValidationPlanNumber vpn " +
           "join fetch vpn.coolingDevice cd " +
           "left join fetch cd.laboratory " +
           "left join fetch cd.department " +
           "where vpn.coolingDevice = :coolingDevice " +
           "order by vpn.year desc")
    List<ValidationPlanNumber> findByCoolingDeviceOrderByYearDesc(@Param("coolingDevice") CoolingDevice coolingDevice);

    Optional<ValidationPlanNumber> findByCoolingDeviceAndYear(CoolingDevice coolingDevice, Integer year);

    boolean existsByCoolingDeviceAndYear(CoolingDevice coolingDevice, Integer year);
}
