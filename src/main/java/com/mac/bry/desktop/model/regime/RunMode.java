package com.mac.bry.desktop.model.regime;

/**
 * Tryb runu sesji pomiarowej deklarowany przez operatora przed uruchomieniem procedury.
 * Determinuje politykę werdyktu (VerdictPolicy) i interpretację wykrytych zdarzeń.
 * <p>
 * Zgodnie z DP-001 §4.5 — werdykt zależny od trybu runu.
 */
public enum RunMode {

    /**
     * Formalna kwalifikacja (IQ/OQ/PQ / OV) — rygorystyczne kryteria WHO/GMP.
     * Ekskursja w oknie STEADY_STATE → FAIL.
     * Metryki: Cpk ≥ 1,0; std dev ≤ 0,3°C (lodówka) / ≤ 1,0°C (zamrażarka).
     */
    QUALIFICATION,

    /**
     * Charakteryzacja urządzenia — obserwacja zachowania w realnym użyciu.
     * Ekskursje i zdarzenia raportowane jako FINDING, nie FAIL.
     * Metryki kwalifikacyjne prezentowane tylko dla segmentów STEADY_STATE — informacyjnie.
     */
    CHARACTERIZATION,

    /**
     * Rutynowy nadzór operacyjny (monitoring ciągły lub cykliczny).
     * Alert przy odchyleniu od baseline poprzedniej kwalifikacji.
     * Werdykt: porównanie ze stanem bazowym, nie kwalifikacja absolutna.
     */
    MONITORING
}
