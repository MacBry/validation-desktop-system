package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

/**
 * W4 — reguły nie da się rozstrzygnąć, bo brakuje danych wejściowych:
 * kartoteki sprzętowej modelu, limitu temperatury komory albo odczytu stanu
 * baterii ze stacji Testo USB.
 * <p>
 * Wyjątek jest blokujący celowo. Import bez informacji o baterii zwraca
 * sentinel {@code -1}; potraktowanie go jako liczby dałoby ujemny budżet
 * energii, a potraktowanie braku danych jako „w porządku" przepuściłoby
 * rejestrator bez żadnej weryfikacji W4 — w GxP niedopuszczalne jest ani jedno,
 * ani drugie.
 */
public class HardwareDataIncompleteException extends RecorderAllocationException {

    public HardwareDataIncompleteException(String message) {
        super(message, TaskResourceStatus.HARDWARE_DATA_INCOMPLETE);
    }
}