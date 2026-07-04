package com.mac.bry.desktop.model.regime;

/**
 * Status werdyktu warunkowego dla pozycji pomiarowej (DP-001 §4.5).
 * Kolejność deklaracji odzwierciedla rosnącą wagę — {@link #isWorseThan}
 * pozwala agregować werdykt sesji jako najgorszy z pozycji.
 */
public enum VerdictStatus {

    /** Wszystkie kryteria spełnione. */
    PASS("PASS"),

    /** Obserwacja do odnotowania — nie blokuje kwalifikacji (tryb charakteryzacyjny). */
    FINDING("FINDING"),

    /** Przekroczenie kryterium drugorzędnego — wymaga oceny operatora. */
    WARNING("WARNING"),

    /** Kryteria kwalifikacyjne nie mają zastosowania (np. przebieg dynamiczny). */
    INCONCLUSIVE("INCONCLUSIVE"),

    /** Naruszenie kryterium kwalifikacyjnego. */
    FAIL("FAIL");

    private final String label;

    VerdictStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isWorseThan(VerdictStatus other) {
        return this.ordinal() > other.ordinal();
    }
}
