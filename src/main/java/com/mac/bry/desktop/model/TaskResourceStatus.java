package com.mac.bry.desktop.model;

/**
 * Możliwość obsadzenia zadania rejestratorami — oś niezależna od
 * {@link PlannedTaskStatus}.
 * <p>
 * Rozróżnienie przyczyny jest istotne operacyjnie: braku puli (W2) zaradzi
 * przesunięcie terminu lub dołożenie sprzętu, natomiast niedopasowania
 * zakresu wzorcowania (W8) nie naprawi żadna liczba rejestratorów — sprzęt
 * musi trafić na wzorcowanie w innym zakresie.
 */
public enum TaskResourceStatus {

    /** Komplet rejestratorów przydzielony, wszystkie reguły spełnione. */
    OK("Obsadzone"),

    /** W2 — za mało wolnych rejestratorów w oknie czasowym. */
    INSUFFICIENT_CAPACITY("Brak wolnych rejestratorów"),

    /** W8 — żaden dostępny rejestrator nie pokrywa zakresu materiału. */
    NO_METROLOGICAL_MATCH("Brak rejestratora o wymaganym zakresie wzorcowania"),

    /** W1 — świadectwo wzorcowania wygasa przed końcem pomiaru (+7 dni). */
    CALIBRATION_EXPIRED("Wzorcowanie wygasa w trakcie pomiaru"),

    /** Suma kanałów przydzielonych rejestratorów nie pokrywa minimalnej liczby punktów pomiarowych. */
    INSUFFICIENT_CHANNELS("Za mało kanałów na wymagane punkty pomiarowe");

    private final String displayName;

    TaskResourceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isBlocking() {
        return this != OK;
    }
}