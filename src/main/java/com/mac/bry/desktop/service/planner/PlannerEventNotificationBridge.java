package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.CoolingChamberRepository;
import com.mac.bry.desktop.repository.PlannedValidationTaskRepository;
import com.mac.bry.desktop.repository.UserVacationRepository;
import com.mac.bry.desktop.security.service.AuditService;
import com.mac.bry.desktop.service.planner.event.RevalidationReportGeneratedEvent;
import com.mac.bry.desktop.service.planner.event.UnplannedAbsenceReportedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reakcja planera na zdarzenia aplikacji — zatwierdzenie raportu i zgłoszenie
 * nieplanowanej nieobecności (reguła W10).
 * <p>
 * Każda zmiana wywołana zdarzeniem odkłada się w audit trailu: przesunięcie
 * terminu badania GxP musi dać się odtworzyć wraz z przyczyną (21 CFR Part 11).
 */
@Component
public class PlannerEventNotificationBridge {

    private static final Logger log = LoggerFactory.getLogger(PlannerEventNotificationBridge.class);

    private final CoolingChamberRepository chamberRepository;
    private final PlannedValidationTaskRepository taskRepository;
    private final UserVacationRepository vacationRepository;
    private final RecorderAllocationService allocationService;
    private final OperatorCalendarService calendarService;
    private final AuditService auditService;

    public PlannerEventNotificationBridge(CoolingChamberRepository chamberRepository,
                                          PlannedValidationTaskRepository taskRepository,
                                          UserVacationRepository vacationRepository,
                                          RecorderAllocationService allocationService,
                                          OperatorCalendarService calendarService,
                                          AuditService auditService) {
        this.chamberRepository = chamberRepository;
        this.taskRepository = taskRepository;
        this.vacationRepository = vacationRepository;
        this.allocationService = allocationService;
        this.calendarService = calendarService;
        this.auditService = auditService;
    }

    /**
     * Zatwierdzenie raportu: aktualizuje <b>wyłącznie</b> zegar odpowiadający
     * wykonanej procedurze, zamyka zadanie i zwalnia rejestratory
     * (ST-EVENT-01, ST-EVENT-02).
     */
    @EventListener
    @Transactional
    public void onRevalidationReportGenerated(RevalidationReportGeneratedEvent event) {
        CoolingChamber chamber = chamberRepository.findById(event.chamberId())
                .orElseThrow(() -> new IllegalStateException(
                        "Raport dotyczy nieistniejącej komory id=" + event.chamberId()));

        String clockName;
        if (event.procedureType() == GxPProcedureType.MAPPING) {
            chamber.setLastMappingDate(event.completedOn());
            clockName = "lastMappingDate";
        } else {
            chamber.setLastPeriodicRevalidationDate(event.completedOn());
            clockName = "lastPeriodicRevalidationDate";
        }
        chamberRepository.save(chamber);

        auditService.logAccessEvent(event.performedBy(), "PLANNER_REPORT_APPROVED", String.format(
                "Komora %s (id=%d): procedura %s zakończona %s — zaktualizowano wyłącznie %s",
                chamber.getChamberName(), chamber.getId(),
                event.procedureType(), event.completedOn(), clockName));

        closeTask(event);
    }

    /**
     * Nieplanowane L4: zadania jeszcze nierozpoczęte czekają na powrót
     * operatora, a pomiary w toku biegną dalej — rejestratory dopisują dane do
     * zapełnienia pamięci, przesuwany jest wyłącznie odczyt (ST-L4-01).
     */
    @EventListener
    @Transactional
    public void onUnplannedAbsence(UnplannedAbsenceReportedEvent event) {
        UserVacation absence = vacationRepository.findById(event.vacationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Zgłoszenie dotyczy nieistniejącej nieobecności id=" + event.vacationId()));

        LocalDateTime firstShiftBack =
                calendarService.findFirstShiftStartAfter(absence, absence.getUser());

        List<PlannedValidationTask> affected = taskRepository.findWithManualActionsBetween(
                absence.getStartDate(), absence.getEndDate());

        int rescheduledReadouts = 0;
        int releasedTasks = 0;

        for (PlannedValidationTask task : affected) {
            if (task.getStatus() == PlannedTaskStatus.PLANNED) {
                // Badanie jeszcze nie ruszyło — zwalniamy sprzęt, żeby nie blokował
                // innych zadań, i oddajemy zadanie do ponownego zaplanowania.
                allocationService.releaseRecorders(task);
                task.setSuggestedWindowStart(firstShiftBack);
                task.setShortageReason("Przesunięte po zgłoszeniu nieplanowanej nieobecności ("
                        + absence.getStartDate() + " – " + absence.getEndDate() + ")");
                releasedTasks++;
            } else if (task.getStatus() == PlannedTaskStatus.IN_PROGRESS
                    || task.getStatus() == PlannedTaskStatus.READOUT_PENDING) {
                // Pomiar biegnie dalej („Stop when full”), przesuwamy sam odczyt.
                if (task.getPlannedStep5ReadoutDeadline().isBefore(firstShiftBack)) {
                    task.setPlannedStep5ReadoutDeadline(firstShiftBack);
                    rescheduledReadouts++;
                }
            }
            taskRepository.save(task);
        }

        auditService.logAccessEvent(event.reportedBy(), "PLANNER_UNPLANNED_ABSENCE", String.format(
                "Nieobecność %s – %s: przesunięto %d odczytów na %s, zwolniono sprzęt w %d zadaniach",
                absence.getStartDate(), absence.getEndDate(),
                rescheduledReadouts, firstShiftBack, releasedTasks));

        log.info("Rekalkulacja po L4 {} – {}: {} zadań dotkniętych",
                absence.getStartDate(), absence.getEndDate(), affected.size());
    }

    private void closeTask(RevalidationReportGeneratedEvent event) {
        if (event.plannedTaskId() == null) {
            return;
        }
        taskRepository.findById(event.plannedTaskId()).ifPresent(task -> {
            allocationService.releaseRecorders(task);
            task.setStatus(PlannedTaskStatus.COMPLETED);
            taskRepository.save(task);
        });
    }
}