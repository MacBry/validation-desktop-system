package com.mac.bry.desktop.model.regime;

/**
 * Predefiniowana konfiguracja lokalizacji źródła nawiewu/ewaporatora
 * w komorze chłodniczej. Determinuje oczekiwany wektor propagacji
 * ciepła podczas cyklu defrostu (BA-EXC002 §2.3, §4.3).
 * <p>
 * Układ współrzędnych: x=[0,1] lewo→prawo, y=[0,1] przód→tył, z=[0,1] dół→góra.
 */
public enum AirflowSourcePreset {

    REAR_WALL("Tylna ściana", new double[]{0, -1, 0}),
    CEILING("Sufit (nawiew górny)", new double[]{0, 0, -1}),
    FLOOR("Podłoga (nawiew dolny)", new double[]{0, 0, 1}),
    LEFT_WALL("Lewa ściana", new double[]{1, 0, 0}),
    RIGHT_WALL("Prawa ściana", new double[]{-1, 0, 0}),
    REAR_AND_LEFT("Tył + lewa ściana", new double[]{0.707, -0.707, 0}),
    REAR_AND_CEILING("Tył + sufit", new double[]{0, -0.707, -0.707}),
    CUSTOM("Konfiguracja ręczna", null);

    private final String label;
    private final double[] expectedDefrostVector;

    AirflowSourcePreset(String label, double[] expectedDefrostVector) {
        this.label = label;
        this.expectedDefrostVector = expectedDefrostVector;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Oczekiwany wektor propagacji defrostu (od źródła w głąb komory).
     * {@code null} dla CUSTOM — wektor wyliczany z zadeklarowanych pozycji.
     */
    public double[] getExpectedDefrostVector() {
        return expectedDefrostVector != null ? expectedDefrostVector.clone() : null;
    }

    /** Wektor drzwi — zawsze od przodu do tyłu (stała dla wszystkich konfiguracji). */
    public static double[] getDoorVector() {
        return new double[]{0, 1, 0};
    }
}
