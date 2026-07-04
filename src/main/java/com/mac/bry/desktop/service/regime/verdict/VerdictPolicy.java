package com.mac.bry.desktop.service.regime.verdict;

import com.mac.bry.desktop.model.regime.RunMode;

/**
 * Polityka werdyktu zależna od trybu runu — wzorzec Strategy (DP-001 §4.5).
 * <p>
 * Ta sama obserwacja (np. ekskursja w oknie ustalonym) prowadzi do różnych
 * werdyktów w zależności od zadeklarowanego {@link RunMode}:
 * QUALIFICATION → FAIL, CHARACTERIZATION → FINDING + rekomendacja.
 */
public interface VerdictPolicy {

    /** Tryb runu obsługiwany przez tę politykę. */
    RunMode appliesTo();

    /** Ocenia kontekst i zwraca werdykt z uzasadnieniami. */
    VerdictResult evaluate(VerdictContext context);
}
