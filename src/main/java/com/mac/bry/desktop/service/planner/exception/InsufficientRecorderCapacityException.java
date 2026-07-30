package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

import java.time.LocalDateTime;

/**
 * W2 — w oknie czasowym nie ma dość wolnych, zakwalifikowanych rejestratorów.
 * <p>
 * Przyczyna logistyczna: pomaga przesunięcie terminu albo zwolnienie sprzętu,
 * dlatego wyjątek niesie propozycję najbliższego wolnego okna (ST-W2-01).
 */
public class InsufficientRecorderCapacityException extends RecorderAllocationException {

    private final int requiredCount;
    private final int availableCount;

    public InsufficientRecorderCapacityException(String message,
                                                 int requiredCount,
                                                 int availableCount,
                                                 LocalDateTime suggestedWindowStart) {
        super(message, TaskResourceStatus.INSUFFICIENT_CAPACITY, suggestedWindowStart);
        this.requiredCount = requiredCount;
        this.availableCount = availableCount;
    }

    public int getRequiredCount() {
        return requiredCount;
    }

    public int getAvailableCount() {
        return availableCount;
    }
}