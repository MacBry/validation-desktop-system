package com.mac.bry.desktop.service.regime.verdict;

import com.mac.bry.desktop.model.regime.VerdictStatus;

import java.util.List;

/**
 * Wynik oceny polityki werdyktu (DP-001 §4.5).
 *
 * @param status  status werdyktu
 * @param reasons uzasadnienia (puste dla PASS)
 */
public record VerdictResult(VerdictStatus status, List<String> reasons) {

    public static VerdictResult pass() {
        return new VerdictResult(VerdictStatus.PASS, List.of());
    }

    public static VerdictResult of(VerdictStatus status, String reason) {
        return new VerdictResult(status, List.of(reason));
    }

    /**
     * Notatka w konwencji rendererów PDF: "STATUS: powód; powód".
     * {@code null} dla PASS — brak notatki oznacza spełnienie kryteriów.
     */
    public String formattedNote() {
        if (status == VerdictStatus.PASS || reasons.isEmpty()) {
            return null;
        }
        return status.getLabel() + ": " + String.join("; ", reasons);
    }
}
