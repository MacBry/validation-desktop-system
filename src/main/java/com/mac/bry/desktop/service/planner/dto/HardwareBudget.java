package com.mac.bry.desktop.service.planner.dto;

import java.util.List;

/**
 * Wynik oceny limitów sprzętowych rejestratora (reguła W4) dla konkretnego
 * zadania i konkretnej komory.
 * <p>
 * Serwis zwraca komplet liczb, a nie samo „tak/nie", bo planer musi umieć
 * pokazać operatorowi <b>które</b> ograniczenie wiąże: przy gęstym próbkowaniu
 * zwykle pamięć, a w komorach -80 °C — energia. Podpowiedź „rozrzedź interwał"
 * ma sens tylko w pierwszym przypadku.
 *
 * @param violations      naruszenia blokujące alokację; pusta lista = sprzęt dopuszczony
 * @param memoryLimitDays maksymalny czas rejestracji z pojemności pamięci [dni]
 * @param batteryLimitDays dostępny budżet energii [dni]; {@code NaN} gdy nieznany
 * @param missionDays     czas od umieszczenia w komorze do terminu odczytu [dni]
 * @param binding         ograniczenie, które wiąże jako pierwsze
 */
public record HardwareBudget(List<HardwareViolation> violations,
                             double memoryLimitDays,
                             double batteryLimitDays,
                             double missionDays,
                             BindingConstraint binding) {

    public enum BindingConstraint {
        MEMORY,
        BATTERY,

        /** Budżetu nie dało się wyznaczyć — brak danych sprzętowych. */
        UNKNOWN
    }

    public boolean isAcceptable() {
        return violations.isEmpty();
    }

    /** Pierwsze naruszenie w kolejności sprawdzania (W4a → W4b → W4c). */
    public HardwareViolation firstViolation() {
        return violations.isEmpty() ? null : violations.get(0);
    }
}