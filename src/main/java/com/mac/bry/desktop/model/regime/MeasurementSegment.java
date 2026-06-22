package com.mac.bry.desktop.model.regime;

import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Encja reprezentująca pojedynczy segment reżimu pracy w serii pomiarowej.
 * Segmenty są wykrywane algorytmicznie (OLS + CUSUM) lub adnotowane przez operatora.
 * <p>
 * Każda zmiana jest audytowana przez Hibernate Envers (kto, kiedy, co zmienił).
 * Zgodnie z DP-001 §4.3 i wymaganiem NFR-03 (audytowalność).
 */
@Entity
@Table(name = "measurement_segments")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeasurementSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Seria pomiarowa, do której należy ten segment.
     * Cascade DELETE: usunięcie serii usuwa wszystkie jej segmenty.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private ThermoMeasurementSeries series;

    /**
     * Czas lokalny hosta — początek segmentu.
     * Używamy LocalDateTime spójnie z {@code ThermoMeasurementPoint#timestampLocal}.
     */
    @Column(name = "from_timestamp", nullable = false)
    private LocalDateTime fromTimestamp;

    /**
     * Czas lokalny hosta — koniec segmentu.
     */
    @Column(name = "to_timestamp", nullable = false)
    private LocalDateTime toTimestamp;

    /**
     * Klasyfikacja reżimu tego segmentu.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SegmentType type;

    /**
     * Pewność detekcji algorytmicznej [0.0–1.0].
     * {@code null} gdy {@code source == OPERATOR} (człowiek nie ma "confidence score").
     */
    @Column
    private Double confidence;

    /**
     * Kto wykrył/zadeklarował segment — algorytm lub operator.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DetectionSource source = DetectionSource.ALGORITHM;

    /**
     * Opcjonalna notatka — hipoteza przyczynowa wygenerowana przez system
     * lub komentarz ręczny operatora.
     */
    @Column(length = 500)
    private String note;

    /**
     * Login operatora, który zatwierdził lub odrzucił segment (human-in-the-loop).
     * {@code null} jeśli segment nie przeszedł przez weryfikację ludzką.
     */
    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    /**
     * Timestamp zatwierdzenia przez operatora.
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * Flaga akceptacji — {@code false} oznacza odrzucenie przez operatora.
     * Odrzucone segmenty są wykluczone z obliczeń statystyk warunkowych.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean accepted = true;

    @Column(name = "created_date", nullable = false)
    @Builder.Default
    private LocalDateTime createdDate = LocalDateTime.now();

    // ──────────────────────────────────────────────────────────
    // Domain helpers
    // ──────────────────────────────────────────────────────────

    /**
     * Sprawdza czy dany punkt czasowy mieści się w tym segmencie (włącznie z granicami).
     */
    public boolean contains(LocalDateTime timestamp) {
        return !timestamp.isBefore(fromTimestamp) && !timestamp.isAfter(toTimestamp);
    }

    /**
     * Czas trwania segmentu w minutach.
     */
    public long durationMinutes() {
        return java.time.Duration.between(fromTimestamp, toTimestamp).toMinutes();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeasurementSegment that = (MeasurementSegment) o;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return false;
    }

    @Override
    public int hashCode() {
        return 31;
    }

    @Override
    public String toString() {
        return "MeasurementSegment{id=" + id + ", type=" + type
                + ", from=" + fromTimestamp + ", to=" + toTimestamp
                + ", accepted=" + accepted + "}";
    }
}
