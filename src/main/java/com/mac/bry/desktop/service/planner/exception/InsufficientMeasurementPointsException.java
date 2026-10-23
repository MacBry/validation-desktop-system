package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

import java.time.LocalDateTime;

/**
 * Suma kanałów przydzielonych rejestratorów nie pokrywa minimalnej liczby
 * punktów pomiarowych wymaganej dla klasy kubatury komory
 * (PDA TR-64: SMALL 9, MEDIUM 15, LARGE 27 — uwaga metrologiczna BA R1).
 * <p>
 * Warunek niezależny od liczby fizycznych rejestratorów: pula może spełniać
 * macierz R1, a mimo to nie dawać dość kanałów.
 */
public class InsufficientMeasurementPointsException extends RecorderAllocationException {

    private final int requiredPoints;
    private final int availableChannels;

    public InsufficientMeasurementPointsException(String message,
                                                  int requiredPoints,
                                                  int availableChannels,
                                                  LocalDateTime suggestedWindowStart) {
        super(message, TaskResourceStatus.INSUFFICIENT_CHANNELS, suggestedWindowStart);
        this.requiredPoints = requiredPoints;
        this.availableChannels = availableChannels;
    }

    public int getRequiredPoints() {
        return requiredPoints;
    }

    public int getAvailableChannels() {
        return availableChannels;
    }
}