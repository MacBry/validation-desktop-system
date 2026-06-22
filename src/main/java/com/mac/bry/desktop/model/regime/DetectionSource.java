package com.mac.bry.desktop.model.regime;

/**
 * Źródło detekcji segmentu — algorytm automatyczny lub adnotacja operatora.
 * Kluczowe dla audytowalności GxP (kto/co zdecydowało o segmentacji).
 */
public enum DetectionSource {

    /**
     * Segment wykryty automatycznie przez algorytm (OLS, CUSUM, detekcja ekskursji).
     * Pole {@code confidence} zawiera wartość [0.0–1.0].
     */
    ALGORITHM,

    /**
     * Segment dodany lub zmodyfikowany ręcznie przez operatora (human-in-the-loop).
     * Pole {@code confirmedBy} i {@code confirmedAt} wskazują kto i kiedy dokonał adnotacji.
     */
    OPERATOR
}
