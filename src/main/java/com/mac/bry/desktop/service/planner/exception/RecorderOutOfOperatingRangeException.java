package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

/**
 * W4a — temperatura komory wykracza poza zakres pracy modelu rejestratora.
 * <p>
 * Bramka twarda, sprawdzana przed jakąkolwiek oceną baterii: testo 174 T
 * (zakres -30…+70 °C) w zamrażarce -80 °C nie jest rejestratorem „ze słabą
 * baterią", tylko urządzeniem użytym niezgodnie ze specyfikacją producenta.
 * Żaden stan naładowania tego nie zmienia.
 */
public class RecorderOutOfOperatingRangeException extends RecorderAllocationException {

    public RecorderOutOfOperatingRangeException(String message) {
        super(message, TaskResourceStatus.HARDWARE_LIMITS_EXCEEDED);
    }
}