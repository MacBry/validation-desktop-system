package com.mac.bry.desktop.model;

import com.mac.bry.desktop.security.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Okno pracy operatora, w którym wolno planować akcje manualne
 * (Krok 1, 2 i 5) — reguła W9.
 * <p>
 * {@code user == null} oznacza konfigurację globalną/domyślną, używaną
 * gdy operator nie ma własnego wpisu.
 */
@Entity
@Table(name = "operator_shift_configs")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperatorShiftConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL = konfiguracja globalna (domyślna dla wszystkich operatorów). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "shift_start", nullable = false)
    @NotNull(message = "Początek zmiany jest wymagany")
    @Builder.Default
    private LocalTime shiftStart = LocalTime.of(6, 30);

    @Column(name = "shift_end", nullable = false)
    @NotNull(message = "Koniec zmiany jest wymagany")
    @Builder.Default
    private LocalTime shiftEnd = LocalTime.of(13, 30);

    @Column(name = "works_monday", nullable = false)
    @Builder.Default
    private Boolean worksMonday = true;

    @Column(name = "works_tuesday", nullable = false)
    @Builder.Default
    private Boolean worksTuesday = true;

    @Column(name = "works_wednesday", nullable = false)
    @Builder.Default
    private Boolean worksWednesday = true;

    @Column(name = "works_thursday", nullable = false)
    @Builder.Default
    private Boolean worksThursday = true;

    @Column(name = "works_friday", nullable = false)
    @Builder.Default
    private Boolean worksFriday = true;

    @Column(name = "works_saturday", nullable = false)
    @Builder.Default
    private Boolean worksSaturday = false;

    @Column(name = "works_sunday", nullable = false)
    @Builder.Default
    private Boolean worksSunday = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Czy operator pracuje w danym dniu tygodnia.
     */
    public boolean worksOn(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> Boolean.TRUE.equals(worksMonday);
            case TUESDAY -> Boolean.TRUE.equals(worksTuesday);
            case WEDNESDAY -> Boolean.TRUE.equals(worksWednesday);
            case THURSDAY -> Boolean.TRUE.equals(worksThursday);
            case FRIDAY -> Boolean.TRUE.equals(worksFriday);
            case SATURDAY -> Boolean.TRUE.equals(worksSaturday);
            case SUNDAY -> Boolean.TRUE.equals(worksSunday);
        };
    }

    /**
     * Czy godzina mieści się w oknie zmiany (granice domknięte).
     */
    public boolean isWithinShift(LocalTime time) {
        return !time.isBefore(shiftStart) && !time.isAfter(shiftEnd);
    }

    public boolean isGlobal() {
        return user == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperatorShiftConfig that = (OperatorShiftConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "OperatorShiftConfig{" +
                "id=" + id +
                ", shift=" + shiftStart + "-" + shiftEnd +
                ", global=" + isGlobal() +
                '}';
    }
}