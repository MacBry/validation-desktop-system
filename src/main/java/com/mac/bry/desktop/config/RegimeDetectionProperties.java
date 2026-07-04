package com.mac.bry.desktop.config;

import com.mac.bry.desktop.model.regime.AirflowSourcePreset;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Konfiguracja parametrów algorytmów detekcji reżimów pracy.
 * Wszystkie progi są konfiguracyjne — mogą być dostrojone bez zmiany kodu.
 * <p>
 * Pełne parametry do strojenia per DP-001 §4 i Załącznik A.
 */
@Component
@ConfigurationProperties(prefix = "regime.detection")
@Data
public class RegimeDetectionProperties {

    /**
     * Feature flag — domyślnie wyłączone do czasu walidacji CSV detektora (DP-001 §5).
     * ZMIEŃ NA true DOPIERO PO PRZEJŚCIU WSZYSTKICH CSV-TC-001..005.
     */
    private boolean enabled = false;

    // ── OLS Segmentor ─────────────────────────────────────────────────────────

    /**
     * Okno regresji liniowej OLS [minuty].
     * Mniejsze okno → szybsza detekcja granic, ale więcej szumu.
     */
    private int olsWindowMinutes = 30;

    /**
     * Maksymalne nachylenie OLS do klasyfikacji jako STEADY_STATE [°C/min].
     * Nachylenie &gt; EPS → EQUILIBRATION.
     */
    private double olsEpsSlope = 0.01;

    /**
     * Maksymalna szerokość pasma (max-min w oknie) do STEADY_STATE [°C].
     * Pasmo &gt; BAND → EQUILIBRATION lub ekskursja.
     */
    private double olsBandWidth = 1.5;

    /**
     * Minimalna długość segmentu STEADY_STATE [minuty].
     * Segmenty krótsze są reklasyfikowane jako EQUILIBRATION.
     */
    private int olsMinSteadyMinutes = 30;

    // ── CUSUM Detector ────────────────────────────────────────────────────────

    /**
     * Parametr allowance CUSUM (mnożnik sigma) — czułość na mały shift.
     * k = K * sigma; typowo 0.5 (detekcja shiftu 1-sigma).
     */
    private double cusumK = 0.5;

    /**
     * Próg decyzji CUSUM (mnożnik sigma) — kiedy ogłaszamy zmianę.
     * h = H * sigma; typowo 5.0 (ARL₀ ≈ 465 dla normalnych danych).
     */
    private double cusumH = 5.0;

    /**
     * Liczba punktów bazowych do wyznaczenia sigma referencyjnej na starcie CUSUM.
     * Używana przed pierwszym STEADY_STATE.
     */
    private int cusumBaselinePoints = 30;

    // ── Minimalna ilość danych ─────────────────────────────────────────────────

    /**
     * Minimalna liczba punktów STEADY_STATE wymagana do obliczenia statystyk warunkowych.
     * Poniżej tej wartości zwracamy hasSteadyStateData=false.
     */
    private int minSteadyPointsForStats = 30;

    // ── Excursion Detector ────────────────────────────────────────────────────
    
    /**
     * Minimalny gradient temperatury kwalifikujący skok jako początek ekskursji [°C/min].
     */
    private double excursionGradientThreshold = 0.5;

    /**
     * Maksymalny czas powrotu do baseline dla krótkotrwałego zdarzenia [minuty].
     */
    private int excursionReturnWindowMinutes = 60;

    // ── Propagation-Aware Excursion Classifier (IMPL-EXC002) ──────────────────

    /**
     * Feature flag — klasyfikacja przestrzenna na podstawie wektora propagacji.
     * Gdy false → dotychczasowa logika isFrontPosition() (backward compat).
     */
    private boolean propagationAware = false;

    /** Minimalny cosine similarity do uznania kierunku za zgodny. */
    private double propagationCosineSimilarityThreshold = 0.7;

    /** Margines niejednoznaczności — jeśli |cos_defrost - cos_door| &lt; margin → EXCURSION. */
    private double propagationAmbiguityMargin = 0.1;

    /** Minimalna liczba czujników z niezerowym lagiem do obliczenia wektora. */
    private int propagationMinSensorsForVector = 3;

    /** Domyślny preset dla komór bez konfiguracji. */
    private AirflowSourcePreset propagationDefaultPreset = AirflowSourcePreset.REAR_WALL;
}
