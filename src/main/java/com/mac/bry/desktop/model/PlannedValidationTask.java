package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Zaplanowane zadanie walidacyjne dla pojedynczej komory.
 * <p>
 * Jednostką planowania jest komora, nie urządzenie (BA R1) — urządzenie
 * dwukomorowe generuje dwa zadania dzielące ten sam numer RPW.
 * Dlatego {@code taskNumber} nie jest unikalny; realny duplikat pilnuje
 * ograniczenie {@code (cooling_chamber_id, procedure_type, due_date)}.
 */
@Entity
@Table(name = "planned_validation_tasks")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedValidationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numer RPW urządzenia w formacie {@code planNumber/skrótPracowni/rok}. */
    @Column(name = "task_number", nullable = false, length = 50)
    @NotBlank(message = "Numer zadania jest wymagany")
    private String taskNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cooling_chamber_id", nullable = false)
    @NotNull(message = "Komora jest wymagana")
    private CoolingChamber coolingChamber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "procedure_class_config_id", nullable = false)
    @NotNull(message = "Klasa procedury jest wymagana")
    private ProcedureClassConfig procedureClassConfig;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedure_type", nullable = false, length = 50)
    @NotNull(message = "Typ procedury jest wymagany")
    private GxPProcedureType procedureType;

    /** Termin wynikający z właściwego zegara: rewalidacja +12 mies., mapowanie +5 lat (BA R2). */
    @Column(name = "due_date", nullable = false)
    @NotNull(message = "Termin badania jest wymagany")
    private LocalDate dueDate;

    /** Krok 1 — programowanie (akcja manualna, okno zmiany). */
    @Column(name = "planned_step1_time", nullable = false)
    @NotNull
    private LocalDateTime plannedStep1Time;

    /** Krok 2 — umieszczenie w komorze (akcja manualna, okno zmiany). */
    @Column(name = "planned_step2_time", nullable = false)
    @NotNull
    private LocalDateTime plannedStep2Time;

    /** Koniec Kroku 3 — moment pierwszej czystej próbki GxP. */
    @Column(name = "planned_step3_stab_end", nullable = false)
    @NotNull
    private LocalDateTime plannedStep3StabEnd;

    /** Koniec Kroku 4 — ostatnia próbka pomiaru. */
    @Column(name = "planned_step4_map_end", nullable = false)
    @NotNull
    private LocalDateTime plannedStep4MapEnd;

    /** Krok 5 — nieprzekraczalny termin odczytu USB; po nim alert W7. */
    @Column(name = "planned_step5_readout_deadline", nullable = false)
    @NotNull
    private LocalDateTime plannedStep5ReadoutDeadline;

    /** Krok 2 + Krok 3, bez Kroku 1 (BA §3). */
    @Column(name = "calculated_testo_delay_minutes", nullable = false)
    @NotNull
    private Integer calculatedTestoDelayMinutes;

    @Column(name = "required_recorder_count", nullable = false)
    @NotNull
    private Integer requiredRecorderCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @NotNull
    @Builder.Default
    private PlannedTaskStatus status = PlannedTaskStatus.PLANNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_status", nullable = false, length = 50)
    @NotNull
    @Builder.Default
    private TaskResourceStatus resourceStatus = TaskResourceStatus.OK;

    /** Uzasadnienie braku obsady — wymagane przez audit trail (21 CFR Part 11). */
    @Column(name = "shortage_reason", length = 500)
    private String shortageReason;

    /** Najbliższe okno, w którym obsada byłaby możliwa (ST-W2-01). */
    @Column(name = "suggested_window_start")
    private LocalDateTime suggestedWindowStart;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotAudited
    @OneToMany(mappedBy = "plannedTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PlannedTaskRecorderAssignment> recorderAssignments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void addRecorderAssignment(PlannedTaskRecorderAssignment assignment) {
        recorderAssignments.add(assignment);
        assignment.setPlannedTask(this);
    }

    public void removeRecorderAssignment(PlannedTaskRecorderAssignment assignment) {
        recorderAssignments.remove(assignment);
        assignment.setPlannedTask(null);
    }

    /**
     * Zwalnia całą obsadę rejestratorów — po zatwierdzeniu raportu albo przy
     * rekalkulacji planu.
     */
    public void releaseRecorders() {
        recorderAssignments.forEach(a -> a.setPlannedTask(null));
        recorderAssignments.clear();
    }

    /**
     * Suma kanałów przydzielonych rejestratorów — musi pokryć
     * {@code VolumeCategory.getMinMeasurementPoints()} (uwaga metrologiczna BA R1).
     */
    public int getAssignedChannelCount() {
        return recorderAssignments.size();
    }

    public boolean isBlockedByResources() {
        return resourceStatus != null && resourceStatus.isBlocking();
    }

    /**
     * Czy termin badania został przekroczony (W6).
     */
    public boolean isOverdue() {
        return dueDate != null
                && status != PlannedTaskStatus.COMPLETED
                && dueDate.isBefore(LocalDate.now());
    }

    /**
     * Czy minął termin odczytu bez importu danych — podstawa alertu W7.
     */
    public boolean isReadoutOverdue(LocalDateTime now) {
        return status == PlannedTaskStatus.READOUT_PENDING
                && plannedStep5ReadoutDeadline != null
                && now.isAfter(plannedStep5ReadoutDeadline);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlannedValidationTask that = (PlannedValidationTask) o;
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31;
    }

    @Override
    public String toString() {
        return "PlannedValidationTask{" +
                "id=" + id +
                ", taskNumber='" + taskNumber + '\'' +
                ", procedureType=" + procedureType +
                ", dueDate=" + dueDate +
                ", status=" + status +
                ", resourceStatus=" + resourceStatus +
                '}';
    }
}