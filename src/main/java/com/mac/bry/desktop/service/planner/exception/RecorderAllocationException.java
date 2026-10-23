package com.mac.bry.desktop.service.planner.exception;

import com.mac.bry.desktop.model.TaskResourceStatus;

import java.time.LocalDateTime;

/**
 * Wspólna nadklasa niepowodzeń alokacji rejestratorów.
 * <p>
 * Wyjątek niesie {@link TaskResourceStatus}, dzięki czemu silnik planera nie
 * musi rozpoznawać typu wyjątku, żeby zapisać przyczynę w zadaniu — przekłada
 * ją wprost na pola {@code resourceStatus} i {@code shortageReason}
 * (wymóg audit trail 21 CFR Part 11).
 */
public abstract class RecorderAllocationException extends RuntimeException {

    private final transient TaskResourceStatus resourceStatus;
    private final transient LocalDateTime suggestedWindowStart;

    protected RecorderAllocationException(String message,
                                          TaskResourceStatus resourceStatus,
                                          LocalDateTime suggestedWindowStart) {
        super(message);
        this.resourceStatus = resourceStatus;
        this.suggestedWindowStart = suggestedWindowStart;
    }

    protected RecorderAllocationException(String message, TaskResourceStatus resourceStatus) {
        this(message, resourceStatus, null);
    }

    public TaskResourceStatus getResourceStatus() {
        return resourceStatus;
    }

    /**
     * Najbliższy moment, w którym alokacja mogłaby się powieść — {@code null},
     * gdy nie da się go wskazać.
     */
    public LocalDateTime getSuggestedWindowStart() {
        return suggestedWindowStart;
    }
}