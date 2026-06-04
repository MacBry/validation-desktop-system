package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.MaterialType;
import com.mac.bry.desktop.repository.MaterialTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MaterialTypeServiceImpl implements MaterialTypeService {

    private final MaterialTypeRepository materialTypeRepository;

    @Override
    public List<MaterialType> findAll() {
        log.debug("Pobieranie wszystkich typów materiałów");
        return materialTypeRepository.findAll();
    }

    @Override
    public List<MaterialType> findAllActive() {
        log.debug("Pobieranie aktywnych typów materiałów");
        return materialTypeRepository.findByActiveTrueOrderByNameAsc();
    }

    @Override
    public Optional<MaterialType> findById(Long id) {
        log.debug("Pobieranie typu materiału o id: {}", id);
        return materialTypeRepository.findById(id);
    }

    @Override
    @Transactional
    public MaterialType save(MaterialType materialType) {
        log.debug("Zapisywanie typu materiału: {}", materialType);
        return materialTypeRepository.save(materialType);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.debug("Usuwanie typu materiału o id: {}", id);
        materialTypeRepository.deleteById(id);
    }
}
