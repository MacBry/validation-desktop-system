package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.MaterialType;
import java.util.List;
import java.util.Optional;

public interface MaterialTypeService {
    List<MaterialType> findAll();
    List<MaterialType> findAllActive();
    Optional<MaterialType> findById(Long id);
    MaterialType save(MaterialType materialType);
    void deleteById(Long id);
}
