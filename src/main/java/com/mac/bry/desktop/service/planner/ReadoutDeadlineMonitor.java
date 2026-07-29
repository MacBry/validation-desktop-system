package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.PlannedValidationTask;
import com.mac.bry.desktop.repository.PlannedValidationTaskRepository;
import com.mac.bry.desktop.security.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reguła W7 — alert, gdy minął termin odczytu USB, a dane z rejestratorów
 * wciąż nie zostały zaimportowane (ST-W7-01).
 * <p>
 * Status zadania pozostaje {@link com.mac.bry.desktop.model.PlannedTaskStatus#READOUT_PENDING}:
 * badanie nie jest ani zakończone, ani nieudane — czeka na czynność technika.
 * Alert ma je uwidocznić, a nie zamknąć.
 */
@Service
public class ReadoutDeadlineMonitor {

    private static final Logger log = LoggerFactory.getLogger(ReadoutDeadlineMonitor.class);

    private final PlannedValidationTaskRepository taskRepository;
    private final AuditService auditService;

    @Value("${app.planner.readout-alerts-enabled:true}")
    private boolean enabled;

    public ReadoutDeadlineMonitor(PlannedValidationTaskRepository taskRepository,
                                  AuditService auditService) {
        this.taskRepository = taskRepository;
        this.auditService = auditService;
    }

    /** Domyślnie co godzinę — zwłoka w odczycie liczy się w godzinach, nie minutach. */
    @Scheduled(cron = "${app.planner.readout-alert-cron:0 0 * * * *}")
    public void checkOverdueReadouts() {
        if (!enabled) {
            log.debug("Alerty odczytu wyłączone (app.planner.readout-alerts-enabled=false)");
            return;
        }
        try {
            raiseAlerts(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Alerty odczytu: błąd podczas sprawdzania terminów", e);
        }
    }

    /**
     * Podnosi alert dla każdego zadania z przekroczonym terminem odczytu.
     *
     * @return zadania, dla których podniesiono alert
     */
    @Transactional(readOnly = true)
    public List<PlannedValidationTask> raiseAlerts(LocalDateTime now) {
        List<PlannedValidationTask> overdue = taskRepository.findReadoutOverdue(now);

        for (PlannedValidationTask task : overdue) {
            long hoursLate = Duration.between(task.getPlannedStep5ReadoutDeadline(), now).toHours();

            auditService.logAccessEvent("SYSTEM", "PLANNER_READOUT_OVERDUE", String.format(
                    "Zadanie %s (komora %s): termin odczytu %s minął %d h temu, brak importu danych z Testo",
                    task.getTaskNumber(),
                    task.getCoolingChamber() != null ? task.getCoolingChamber().getChamberName() : "?",
                    task.getPlannedStep5ReadoutDeadline(), hoursLate));

            log.warn("W7: zadanie {} przekroczyło termin odczytu o {} h", task.getTaskNumber(), hoursLate);
        }
        return overdue;
    }
}