package com.mac.bry.desktop.service.planner;

import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.PlannedTaskStatus;
import com.mac.bry.desktop.model.PlannedValidationTask;
import com.mac.bry.desktop.repository.PlannedValidationTaskRepository;
import com.mac.bry.desktop.security.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ST-W7-01 — alert przy przekroczonym terminie odczytu USB.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadoutDeadlineMonitorTest {

    @Mock private PlannedValidationTaskRepository taskRepository;
    @Mock private AuditService auditService;

    private ReadoutDeadlineMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new ReadoutDeadlineMonitor(taskRepository, auditService);
    }

    @Test
    @DisplayName("ST-W7-01: przekroczony termin odczytu podnosi alert, status bez zmiany")
    void st_w7_01_overdueReadoutRaisesAlert() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 10, 0);
        PlannedValidationTask task = PlannedValidationTask.builder()
                .id(1L).taskNumber("5/HLA/2026")
                .status(PlannedTaskStatus.READOUT_PENDING)
                .plannedStep5ReadoutDeadline(now.minusHours(30))
                .coolingChamber(CoolingChamber.builder().chamberName("Komora KKCZ").build())
                .build();
        when(taskRepository.findReadoutOverdue(now)).thenReturn(List.of(task));

        List<PlannedValidationTask> alerted = monitor.raiseAlerts(now);

        assertThat(alerted).containsExactly(task);
        assertThat(task.getStatus())
                .as("alert uwidacznia zadanie, nie zamyka go")
                .isEqualTo(PlannedTaskStatus.READOUT_PENDING);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).logAccessEvent(eq("SYSTEM"), eq("PLANNER_READOUT_OVERDUE"), details.capture());
        assertThat(details.getValue())
                .contains("5/HLA/2026")
                .contains("Komora KKCZ")
                .contains("30 h temu");
    }

    @Test
    @DisplayName("Brak zaległych odczytów — cisza w audit trailu")
    void noOverdueReadoutsProducesNoAlerts() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 10, 0);
        when(taskRepository.findReadoutOverdue(now)).thenReturn(List.of());

        assertThat(monitor.raiseAlerts(now)).isEmpty();
        verify(auditService, never()).logAccessEvent(any(), any(), any());
    }

    @Test
    @DisplayName("Encja sama rozpoznaje przekroczony termin odczytu")
    void entityDetectsOverdueReadout() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 29, 10, 0);
        PlannedValidationTask task = PlannedValidationTask.builder()
                .status(PlannedTaskStatus.READOUT_PENDING)
                .plannedStep5ReadoutDeadline(now.minusHours(1))
                .build();

        assertThat(task.isReadoutOverdue(now)).isTrue();

        task.setStatus(PlannedTaskStatus.COMPLETED);
        assertThat(task.isReadoutOverdue(now))
                .as("zadanie zakończone nie generuje alertu")
                .isFalse();
    }
}