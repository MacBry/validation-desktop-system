package com.mac.bry.desktop.security.repository;

import com.mac.bry.desktop.security.model.Laboratory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LaboratoryRepository extends JpaRepository<Laboratory, Long> {
    List<Laboratory> findByDepartmentId(Long departmentId);
}
