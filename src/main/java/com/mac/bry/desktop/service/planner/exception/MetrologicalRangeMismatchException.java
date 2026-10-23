package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

/**
 * W8 — zakres wzorcowania PCA nie pokrywa zakresu dopuszczalnego materiału.
 * <p>
 * Przyczyna metrologiczna, nie logistyczna: dołożenie rejestratorów jej nie
 * usunie, sprzęt musi trafić na wzorcowanie w innym zakresie.
 */
public class MetrologicalRangeMismatchException extends RecorderAllocationException {

    public MetrologicalRangeMismatchException(String message) {
        super(message, TaskResourceStatus.NO_METROLOGICAL_MATCH);
    }
}