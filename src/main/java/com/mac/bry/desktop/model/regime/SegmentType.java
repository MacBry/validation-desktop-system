package com.mac.bry.desktop.model.regime;

/**
 * Typ segmentu reżimu pracy urządzenia chłodniczego.
 * Klasyfikacja wykonywana algorytmicznie (OLS + CUSUM + detekcja ekskursji)
 * lub deklarowana przez operatora (human-in-the-loop).
 * <p>
 * Zgodnie z DP-001 §4.4 i BA-DP001 §2.2.
 */
public enum SegmentType {

    /**
     * Dochodzenie do stanu ustalonego (rampa mono., trend).
     * Metryki kwalifikacyjne NIE są liczone na tym segmencie.
     * Typowo: 1–8h po uruchomieniu lub zmianie nastawy.
     */
    EQUILIBRATION,

    /**
     * Stan ustalony — jedyna faza prawidłowa do oceny kwalifikacyjnej.
     * Kryterium: nachylenie OLS &lt; EPS i szerokość pasma &lt; BAND przez ≥ N minut.
     * Metryki kwalifikacyjne (Cpk, std dev, MKT) są liczone WYŁĄCZNIE tutaj.
     */
    STEADY_STATE,

    /**
     * Cykl odszraniania (defrostu) — periodyczny, sygnatura od ewaporatora.
     * Detekcja: szybki gradient + powrót, regularny interwał (FFT).
     * Raportowany jako FINDING, nie FAIL.
     */
    DEFROST,

    /**
     * Zdarzenie otwarcia drzwi komory.
     * Detekcja: szybki gradient, czujniki przednie/górne reagują pierwsze, nieperiodyczny.
     * Raportowany jako FINDING.
     */
    DOOR_EVENT,

    /**
     * Trwała zmiana poziomu średniej (zmiana nastawy termostat lub tryb fastcooling).
     * Detekcja: algorytm CUSUM — sustained shift bez powrotu.
     * Powoduje nowy segment EQUILIBRATION → STEADY_STATE po ustabilizowaniu.
     */
    SETPOINT_CHANGE,

    /**
     * Anomalia nieidentyfikowalna jako znane zdarzenie.
     * Wymaga weryfikacji przez operatora (human-in-the-loop).
     * W trybie QUALIFICATION: FAIL jeśli w oknie STEADY_STATE.
     */
    EXCURSION,

    /**
     * Eksploatacja domowa / wielokrotne drobne zdarzenia nakładające się.
     * Stosowany w trybie CHARACTERIZATION dla urządzeń domowych.
     * Nie nakładane kryteria kwalifikacyjne.
     */
    NORMAL_USE
}
