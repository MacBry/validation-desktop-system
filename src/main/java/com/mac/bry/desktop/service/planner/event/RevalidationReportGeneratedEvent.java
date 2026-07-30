package com.mac.bry.desktop.service.planner.event;

import com.mac.bry.desktop.model.GxPProcedureType;

import java.time.LocalDate;

/**
 * Zatwierdzono raport z badania walidacyjnego.
 * <p>
 * {@code procedureType} decyduje, który zegar komory zostanie zaktualizowany —
 * i tylko on. Nadpisanie {@code lastMappingDate} po rocznej rewalidacji
 * zresetowałoby 5-letni cykl mapowania (BA R2).
 *
 * @param chamberId     komora, której dotyczy raport
 * @param plannedTaskId zaplanowane zadanie, jeśli raport powstał z planu
 * @param procedureType typ wykonanej procedury
 * @param completedOn   data zakończenia badania
 * @param performedBy   użytkownik zatwierdzający — trafia do audit trailu
 */
public record RevalidationReportGeneratedEvent(Long chamberId,
                                               Long plannedTaskId,
                                               GxPProcedureType procedureType,
                                               LocalDate completedOn,
                                               String performedBy) {
}