package com.mac.bry.desktop.service.planner.dto;

/**
 * Pojedyncze naruszenie reguły W4 wraz z gotowym komunikatem dla audit trailu.
 * <p>
 * Pola {@code required} i {@code available} niosą liczby stojące za odrzuceniem
 * w jednostce właściwej dla kryterium — próbki dla {@link Rule#MEMORY}, dni dla
 * {@link Rule#BATTERY} — żeby UI i testy nie musiały parsować komunikatu.
 * Dla kryteriów bez wymiaru liczbowego są {@code NaN}.
 *
 * @param rule      które kryterium W4 zostało naruszone
 * @param message   komunikat z konkretnymi liczbami — trafia do {@code shortageReason}
 * @param required  ile badanie wymaga
 * @param available ile sprzęt oferuje
 */
public record HardwareViolation(Rule rule, String message, double required, double available) {

    public HardwareViolation(Rule rule, String message) {
        this(rule, message, Double.NaN, Double.NaN);
    }

    public enum Rule {

        /** W4a — temperatura komory poza zakresem pracy modelu. */
        OPERATING_RANGE,

        /** W4b — za mało pamięci na wymaganą liczbę próbek. */
        MEMORY,

        /** W4c — budżet energii nie pokrywa czasu misji. */
        BATTERY,

        /** Brak danych, żeby regułę w ogóle rozstrzygnąć. */
        DATA_INCOMPLETE
    }
}