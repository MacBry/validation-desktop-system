package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Rezerwacja pojedynczego kanału rejestratora na okno czasowe zadania.
 * <p>
 * Fundament reguł W1/W2/W5/W8 — bez powiązania rejestrator↔zadanie↔okno nie
 * da się egzekwować ani pojemności puli, ani braku podwójnej rezerwacji.
 * Jeden wiersz = jeden kanał, więc liczba wierszy zadania jest wprost liczbą
 * punktów pomiarowych.
 * <p>
 * Okno {@code [reservedFrom, reservedUntil]} obejmuje całość blokady zasobu:
 * od Kroku 1 (programowanie) do terminu odczytu z Kroku 5 powiększonego
 * o bufor logistyczny.
 */
@Entity
@Table(name = "planned_task_recorder_assignments")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedTaskRecorderAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planned_task_id", nullable = false)
    private PlannedValidationTask plannedTask;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "thermo_recorder_id", nullable = false)
    @NotNull(message = "Rejestrator jest wymagany")
    private ThermoRecorder thermoRecorder;

    @Column(name = "channel_number", nullable = false)
    @NotNull(message = "Numer kanału jest wymagany")
    @Builder.Default
    private Integer channelNumber = 1;

    @Column(name = "reserved_from", nullable = false)
    @NotNull(message = "Początek rezerwacji jest wymagany")
    private LocalDateTime reservedFrom;

    @Column(name = "reserved_until", nullable = false)
    @NotNull(message = "Koniec rezerwacji jest wymagany")
    private LocalDateTime reservedUntil;

    /**
     * Czy rezerwacja koliduje z podanym oknem (W5).
     * Granice traktowane jako domknięte — styk okien liczy się jako kolizja,
     * bo bufor logistyczny powinien być już wliczony w {@code reservedUntil}.
     */
    public boolean overlaps(LocalDateTime from, LocalDateTime until) {
        return !reservedFrom.isAfter(until) && !reservedUntil.isBefore(from);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlannedTaskRecorderAssignment that = (PlannedTaskRecorderAssignment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PlannedTaskRecorderAssignment{" +
                "id=" + id +
                ", recorder=" + (thermoRecorder != null ? thermoRecorder.getSerialNumber() : "–") +
                ", channel=" + channelNumber +
                ", " + reservedFrom + " – " + reservedUntil +
                '}';
    }
}