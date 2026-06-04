package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.MaterialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialTypeRepository extends JpaRepository<MaterialType, Long> {
    List<MaterialType> findByActiveTrueOrderByNameAsc();
}
