package com.mac.bry.desktop.model;

/**
 * Cykl życia zaplanowanego zadania walidacyjnego.
 * <p>
 * Oś wyłącznie procesowa — mówi, na którym etapie procedury jest zadanie.
 * Informacja o możliwości obsadzenia zadania rejestratorami leży na osobnej
 * osi ({@link TaskResourceStatus}) i nie miesza się z tym enumem.
 */
public enum PlannedTaskStatus {
    PLANNED("Zaplanowane"),
    IN_PROGRESS("W trakcie pomiaru"),
    READOUT_PENDING("Oczekuje na odczyt"),
    COMPLETED("Zakończone");

    private final String displayName;

    PlannedTaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}