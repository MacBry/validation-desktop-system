package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

import java.time.LocalDateTime;

/**
 * W5 — próba zarezerwowania kanału rejestratora na okno kolidujące
 * z istniejącą rezerwacją.
 */
public class RecorderDoubleBookingException extends RecorderAllocationException {

    public RecorderDoubleBookingException(String message, LocalDateTime suggestedWindowStart) {
        super(message, TaskResourceStatus.INSUFFICIENT_CAPACITY, suggestedWindowStart);
    }
}