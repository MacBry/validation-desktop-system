package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoolingDeviceRepository extends JpaRepository<CoolingDevice, Long> {

    Optional<CoolingDevice> findByInventoryNumber(String inventoryNumber);

    List<CoolingDevice> findByDepartment(Department department);

    List<CoolingDevice> findByLaboratory(Laboratory laboratory);

    boolean existsByInventoryNumber(String inventoryNumber);

    @Query("SELECT cd FROM CoolingDevice cd JOIN FETCH cd.department d LEFT JOIN FETCH cd.laboratory l LEFT JOIN FETCH cd.chambers ch WHERE cd.id = :id")
    Optional<CoolingDevice> findByIdWithRelations(@Param("id") Long id);

    @Query("SELECT cd FROM CoolingDevice cd WHERE LOWER(cd.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(cd.inventoryNumber) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<CoolingDevice> searchDevices(@Param("query") String query);
}
