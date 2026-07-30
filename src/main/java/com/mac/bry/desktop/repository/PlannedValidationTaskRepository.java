package com.mac.bry.desktop.repository;

import com.mac.bry.desktop.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlannedValidationTaskRepository extends JpaRepository<PlannedValidationTask, Long> {

    /** Wszystkie zadania jednego urządzenia — dzielą numer RPW. */
    List<PlannedValidationTask> findByTaskNumberOrderByDueDateAsc(String taskNumber);

    List<PlannedValidationTask> findByCoolingChamberOrderByDueDateDesc(CoolingChamber coolingChamber);

    Optional<PlannedValidationTask> findByCoolingChamberAndProcedureTypeAndDueDate(
            CoolingChamber coolingChamber, GxPProcedureType procedureType, LocalDate dueDate);

    List<PlannedValidationTask> findByStatusOrderByDueDateAsc(PlannedTaskStatus status);

    /** Zadania bez pełnej obsady rejestratorów — raport „co blokuje plan roczny". */
    List<PlannedValidationTask> findByResourceStatusNotOrderByDueDateAsc(TaskResourceStatus resourceStatus);

    /** Przeterminowane badania — priorytet przy generowaniu planu (W6). */
    @Query("select t from PlannedValidationTask t " +
           "where t.dueDate < :today and t.status <> com.mac.bry.desktop.model.PlannedTaskStatus.COMPLETED " +
           "order by t.dueDate asc")
    List<PlannedValidationTask> findOverdue(@Param("today") LocalDate today);

    /** Zadania z przekroczonym terminem odczytu USB — podstawa alertu W7. */
    @Query("select t from PlannedValidationTask t " +
           "where t.status = com.mac.bry.desktop.model.PlannedTaskStatus.READOUT_PENDING " +
           "and t.plannedStep5ReadoutDeadline < :now " +
           "order by t.plannedStep5ReadoutDeadline asc")
    List<PlannedValidationTask> findReadoutOverdue(@Param("now") LocalDateTime now);

    /**
     * Zadania, których akcje manualne wypadają w podanym zakresie — używane
     * przy rekalkulacji po zgłoszeniu nieplanowanego L4 (W10).
     */
    @Query("select t from PlannedValidationTask t " +
           "where t.status <> com.mac.bry.desktop.model.PlannedTaskStatus.COMPLETED " +
           "and (cast(t.plannedStep1Time as date) between :from and :to " +
           "  or cast(t.plannedStep2Time as date) between :from and :to " +
           "  or cast(t.plannedStep5ReadoutDeadline as date) between :from and :to) " +
           "order by t.plannedStep1Time asc")
    List<PlannedValidationTask> findWithManualActionsBetween(@Param("from") LocalDate from,
                                                             @Param("to") LocalDate to);

    @Query("select t from PlannedValidationTask t " +
           "where t.dueDate between :from and :to " +
           "order by t.dueDate asc")
    List<PlannedValidationTask> findByDueDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Wariant dla widoku kalendarza — pobiera komorę, urządzenie i pracownię
     * jednym zapytaniem. Bez tego tabela JavaFX, renderowana poza transakcją,
     * wywróciłaby się na leniwym ładowaniu.
     */
    @Query("select distinct t from PlannedValidationTask t " +
           "join fetch t.coolingChamber c " +
           "join fetch c.coolingDevice d " +
           "left join fetch d.laboratory " +
           "left join fetch c.materialType " +
           "join fetch t.procedureClassConfig " +
           "where t.dueDate between :from and :to " +
           "order by t.dueDate asc")
    List<PlannedValidationTask> findByDueDateRangeWithDetails(@Param("from") LocalDate from,
                                                              @Param("to") LocalDate to);
}