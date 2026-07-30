package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.GxPProcedureType;
import com.mac.bry.desktop.model.ProcedureClassConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProcedureClassConfigRepository extends JpaRepository<ProcedureClassConfig, Long> {

    List<ProcedureClassConfig> findByActiveTrue();

    List<ProcedureClassConfig> findByProcedureTypeAndActiveTrue(GxPProcedureType procedureType);

    Optional<ProcedureClassConfig> findByName(String name);

    /**
     * Domyślny szablon dla danego typu procedury — pierwszy aktywny wg nazwy.
     */
    Optional<ProcedureClassConfig> findFirstByProcedureTypeAndActiveTrueOrderByNameAsc(GxPProcedureType procedureType);
}