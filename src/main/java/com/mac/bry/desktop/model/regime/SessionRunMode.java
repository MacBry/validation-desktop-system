package com.mac.bry.desktop.model.regime;

import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tryb runu (RunMode) powiązany z serią pomiarową w relacji 1:1.
 * Deklarowany przez operatora przed wygenerowaniem raportu GxP.
 * Wpływa na politykę werdyktu i interpretację wykrytych zdarzeń.
 * <p>
 * Audytowany przez Hibernate Envers — pełny ślad kto i kiedy zadeklarował tryb.
 * Zgodnie z DP-001 §4.5.
 */
@Entity
@Table(name = "session_run_modes")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionRunMode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Powiązanie 1:1 z serią pomiarową.
     * UNIQUE na poziomie bazy danych (V29 constraint).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false, unique = true)
    private ThermoMeasurementSeries series;

    /**
     * Zadeklarowany tryb runu.
     * Domyślnie CHARACTERIZATION — bezpieczna wartość (nie nakłada kryteriów kwalifikacyjnych).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "run_mode", nullable = false, length = 20)
    @Builder.Default
    private RunMode runMode = RunMode.CHARACTERIZATION;

    /**
     * Login operatora, który zadeklarował tryb runu.
     */
    @Column(name = "declared_by", length = 100)
    private String declaredBy;

    /**
     * Timestamp deklaracji trybu runu.
     */
    @Column(name = "declared_at", nullable = false)
    @Builder.Default
    private LocalDateTime declaredAt = LocalDateTime.now();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionRunMode that = (SessionRunMode) o;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return false;
    }

    @Override
    public int hashCode() {
        return 31;
    }
}
