package com.mac.bry.desktop.service.planner.event;

/**
 * Zgłoszono nieplanowaną nieobecność operatora (L4).
 * <p>
 * Wymaga rekalkulacji zadań, których akcje manualne wypadają w okresie
 * absencji (W10). Zadania będące w trakcie pomiaru nie są przerywane —
 * rejestratory dopisują dane do zapełnienia pamięci, a przesuwany jest
 * wyłącznie odczyt.
 *
 * @param vacationId  zgłoszona nieobecność
 * @param reportedBy  użytkownik zgłaszający — trafia do audit trailu
 */
public record UnplannedAbsenceReportedEvent(Long vacationId, String reportedBy) {
}