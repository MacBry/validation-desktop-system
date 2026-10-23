package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

/**
 * W1 — świadectwo wzorcowania wygasa przed końcem pomiaru powiększonym
 * o 7 dni zapasu.
 */
public class CalibrationExpiredException extends RecorderAllocationException {

    public CalibrationExpiredException(String message) {
        super(message, TaskResourceStatus.CALIBRATION_EXPIRED);
    }
}