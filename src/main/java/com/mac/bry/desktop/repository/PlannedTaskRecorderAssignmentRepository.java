package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.PlannedTaskRecorderAssignment;
import com.mac.bry.desktop.model.PlannedValidationTask;
import com.mac.bry.desktop.model.ThermoRecorder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PlannedTaskRecorderAssignmentRepository
        extends JpaRepository<PlannedTaskRecorderAssignment, Long> {

    List<PlannedTaskRecorderAssignment> findByPlannedTask(PlannedValidationTask plannedTask);

    /**
     * Rezerwacje kolidujące z podanym oknem (W5). Granice domknięte — bufor
     * logistyczny musi być już wliczony w przekazane okno.
     */
    @Query("select a from PlannedTaskRecorderAssignment a " +
           "where a.thermoRecorder = :recorder " +
           "and a.channelNumber = :channelNumber " +
           "and a.reservedFrom <= :until and a.reservedUntil >= :from")
    List<PlannedTaskRecorderAssignment> findCollisions(@Param("recorder") ThermoRecorder recorder,
                                                       @Param("channelNumber") Integer channelNumber,
                                                       @Param("from") LocalDateTime from,
                                                       @Param("until") LocalDateTime until);

    /**
     * Identyfikatory rejestratorów zajętych w oknie — pozwala odfiltrować pulę
     * jednym zapytaniem zamiast sprawdzać kolizje sztuka po sztuce (W2).
     */
    @Query("select distinct a.thermoRecorder.id from PlannedTaskRecorderAssignment a " +
           "where a.reservedFrom <= :until and a.reservedUntil >= :from")
    List<Long> findBusyRecorderIds(@Param("from") LocalDateTime from,
                                   @Param("until") LocalDateTime until);

    void deleteByPlannedTask(PlannedValidationTask plannedTask);
}