package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.repository.CoolingChamberRepository;
import com.mac.bry.desktop.repository.PlannedValidationTaskRepository;
import com.mac.bry.desktop.repository.UserVacationRepository;
import com.mac.bry.desktop.security.service.AuditService;
import com.mac.bry.desktop.service.planner.event.RevalidationReportGeneratedEvent;
import com.mac.bry.desktop.service.planner.event.UnplannedAbsenceReportedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ST-EVENT-01, ST-EVENT-02, ST-L4-01 — reakcja planera na zdarzenia (W10).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlannerEventNotificationBridgeTest {

    @Mock private CoolingChamberRepository chamberRepository;
    @Mock private PlannedValidationTaskRepository taskRepository;
    @Mock private UserVacationRepository vacationRepository;
    @Mock private RecorderAllocationService allocationService;
    @Mock private OperatorCalendarService calendarService;
    @Mock private AuditService auditService;

    private PlannerEventNotificationBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new PlannerEventNotificationBridge(chamberRepository, taskRepository,
                vacationRepository, allocationService, calendarService, auditService);
        when(chamberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CoolingChamber chamberWithBothClocks() {
        return CoolingChamber.builder()
                .id(1L)
                .chamberName("Komora KKCZ")
                .volumeCategory(VolumeCategory.SMALL)
                .lastMappingDate(LocalDate.of(2024, 1, 10))
                .lastPeriodicRevalidationDate(LocalDate.of(2025, 6, 1))
                .build();
    }

    @Test
    @DisplayName("ST-EVENT-01: raport rewalidacji nie rusza zegara mapowania")
    void st_event_01_periodicRevalidationLeavesMappingClockUntouched() {
        CoolingChamber chamber = chamberWithBothClocks();
        when(chamberRepository.findById(1L)).thenReturn(Optional.of(chamber));

        bridge.onRevalidationReportGenerated(new RevalidationReportGeneratedEvent(
                1L, null, GxPProcedureType.PERIODIC_REVALIDATION, LocalDate.of(2026, 7, 29), "operator1"));

        assertThat(chamber.getLastPeriodicRevalidationDate()).isEqualTo(LocalDate.of(2026, 7, 29));
        assertThat(chamber.getLastMappingDate())
                .as("5-letni cykl mapowania nie może zostać zresetowany")
                .isEqualTo(LocalDate.of(2024, 1, 10));
    }

    @Test
    @DisplayName("ST-EVENT-02: raport mapowania nie rusza zegara rewalidacji")
    void st_event_02_mappingLeavesRevalidationClockUntouched() {
        CoolingChamber chamber = chamberWithBothClocks();
        when(chamberRepository.findById(1L)).thenReturn(Optional.of(chamber));

        bridge.onRevalidationReportGenerated(new RevalidationReportGeneratedEvent(
                1L, null, GxPProcedureType.MAPPING, LocalDate.of(2026, 7, 29), "operator1"));

        assertThat(chamber.getLastMappingDate()).isEqualTo(LocalDate.of(2026, 7, 29));
        assertThat(chamber.getLastPeriodicRevalidationDate()).isEqualTo(LocalDate.of(2025, 6, 1));
    }

    @Test
    @DisplayName("Zatwierdzenie raportu zamyka zadanie i zwalnia rejestratory")
    void reportClosesTaskAndReleasesRecorders() {
        CoolingChamber chamber = chamberWithBothClocks();
        when(chamberRepository.findById(1L)).thenReturn(Optional.of(chamber));
        PlannedValidationTask task = PlannedValidationTask.builder()
                .id(99L).taskNumber("5/HLA/2026").status(PlannedTaskStatus.READOUT_PENDING).build();
        when(taskRepository.findById(99L)).thenReturn(Optional.of(task));

        bridge.onRevalidationReportGenerated(new RevalidationReportGeneratedEvent(
                1L, 99L, GxPProcedureType.PERIODIC_REVALIDATION, LocalDate.of(2026, 7, 29), "operator1"));

        assertThat(task.getStatus()).isEqualTo(PlannedTaskStatus.COMPLETED);
        verify(allocationService).releaseRecorders(task);
    }

    @Test
    @DisplayName("Aktualizacja zegara trafia do audit trailu z nazwą pola")
    void clockUpdateIsAudited() {
        when(chamberRepository.findById(1L)).thenReturn(Optional.of(chamberWithBothClocks()));

        bridge.onRevalidationReportGenerated(new RevalidationReportGeneratedEvent(
                1L, null, GxPProcedureType.PERIODIC_REVALIDATION, LocalDate.of(2026, 7, 29), "operator1"));

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).logAccessEvent(eq("operator1"), eq("PLANNER_REPORT_APPROVED"), details.capture());
        assertThat(details.getValue())
                .contains("lastPeriodicRevalidationDate")
                .doesNotContain("lastMappingDate");
    }

    @Test
    @DisplayName("ST-L4-01: pomiar w toku biegnie dalej, przesuwany jest tylko odczyt")
    void st_l4_01_measurementContinuesOnlyReadoutMoves() {
        UserVacation l4 = UserVacation.builder()
                .id(7L)
                .startDate(LocalDate.of(2026, 7, 8))
                .endDate(LocalDate.of(2026, 7, 10))
                .unplannedL4(true)
                .build();
        when(vacationRepository.findById(7L)).thenReturn(Optional.of(l4));
        when(calendarService.findFirstShiftStartAfter(eq(l4), any()))
                .thenReturn(LocalDateTime.of(2026, 7, 13, 6, 30));

        PlannedValidationTask inProgress = PlannedValidationTask.builder()
                .id(1L).taskNumber("5/HLA/2026")
                .status(PlannedTaskStatus.IN_PROGRESS)
                .plannedStep5ReadoutDeadline(LocalDateTime.of(2026, 7, 9, 19, 0))
                .build();
        when(taskRepository.findWithManualActionsBetween(any(), any())).thenReturn(List.of(inProgress));

        bridge.onUnplannedAbsence(new UnplannedAbsenceReportedEvent(7L, "operator1"));

        assertThat(inProgress.getStatus())
                .as("pomiar nie zostaje przerwany")
                .isEqualTo(PlannedTaskStatus.IN_PROGRESS);
        assertThat(inProgress.getPlannedStep5ReadoutDeadline())
                .isEqualTo(LocalDateTime.of(2026, 7, 13, 6, 30));
        verify(allocationService, never()).releaseRecorders(inProgress);
    }

    @Test
    @DisplayName("ST-L4-01: zadanie jeszcze nierozpoczęte zwalnia sprzęt i czeka na powrót")
    void st_l4_01_notStartedTaskReleasesRecorders() {
        UserVacation l4 = UserVacation.builder()
                .id(7L)
                .startDate(LocalDate.of(2026, 7, 8))
                .endDate(LocalDate.of(2026, 7, 10))
                .unplannedL4(true)
                .build();
        when(vacationRepository.findById(7L)).thenReturn(Optional.of(l4));
        when(calendarService.findFirstShiftStartAfter(eq(l4), any()))
                .thenReturn(LocalDateTime.of(2026, 7, 13, 6, 30));

        PlannedValidationTask planned = PlannedValidationTask.builder()
                .id(2L).taskNumber("6/HLA/2026")
                .status(PlannedTaskStatus.PLANNED)
                .plannedStep5ReadoutDeadline(LocalDateTime.of(2026, 7, 15, 19, 0))
                .build();
        when(taskRepository.findWithManualActionsBetween(any(), any())).thenReturn(List.of(planned));

        bridge.onUnplannedAbsence(new UnplannedAbsenceReportedEvent(7L, "operator1"));

        verify(allocationService).releaseRecorders(planned);
        assertThat(planned.getSuggestedWindowStart()).isEqualTo(LocalDateTime.of(2026, 7, 13, 6, 30));
        assertThat(planned.getShortageReason()).contains("nieplanowanej nieobecności");
    }

    @Test
    @DisplayName("Odczyt zaplanowany już po powrocie nie jest przesuwany wstecz")
    void readoutAfterReturnIsNotMovedBackwards() {
        UserVacation l4 = UserVacation.builder()
                .id(7L).startDate(LocalDate.of(2026, 7, 8)).endDate(LocalDate.of(2026, 7, 10))
                .unplannedL4(true).build();
        when(vacationRepository.findById(7L)).thenReturn(Optional.of(l4));
        when(calendarService.findFirstShiftStartAfter(eq(l4), any()))
                .thenReturn(LocalDateTime.of(2026, 7, 13, 6, 30));

        LocalDateTime laterDeadline = LocalDateTime.of(2026, 7, 20, 12, 0);
        PlannedValidationTask task = PlannedValidationTask.builder()
                .id(3L).taskNumber("7/HLA/2026")
                .status(PlannedTaskStatus.READOUT_PENDING)
                .plannedStep5ReadoutDeadline(laterDeadline)
                .build();
        when(taskRepository.findWithManualActionsBetween(any(), any())).thenReturn(List.of(task));

        bridge.onUnplannedAbsence(new UnplannedAbsenceReportedEvent(7L, "operator1"));

        assertThat(task.getPlannedStep5ReadoutDeadline()).isEqualTo(laterDeadline);
    }

    @Test
    @DisplayName("Rekalkulacja po L4 trafia do audit trailu")
    void absenceRecalculationIsAudited() {
        UserVacation l4 = UserVacation.builder()
                .id(7L).startDate(LocalDate.of(2026, 7, 8)).endDate(LocalDate.of(2026, 7, 10))
                .unplannedL4(true).build();
        when(vacationRepository.findById(7L)).thenReturn(Optional.of(l4));
        when(calendarService.findFirstShiftStartAfter(eq(l4), any()))
                .thenReturn(LocalDateTime.of(2026, 7, 13, 6, 30));
        when(taskRepository.findWithManualActionsBetween(any(), any())).thenReturn(List.of());

        bridge.onUnplannedAbsence(new UnplannedAbsenceReportedEvent(7L, "operator1"));

        verify(auditService).logAccessEvent(eq("operator1"), eq("PLANNER_UNPLANNED_ABSENCE"), any());
    }
}