package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.Objects;

/**
 * Szablon klasy procedury walidacyjnej — pięć kroków czasowych definiujących
 * przebieg badania (BA §3).
 * <p>
 * Krok 1 (programowanie) i Krok 5 (odczyt) to akcje manualne technika,
 * kroki 2–4 wyznaczają zachowanie rejestratora w komorze.
 */
@Entity
@Table(name = "procedure_class_configs")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcedureClassConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    @NotBlank(message = "Nazwa klasy procedury jest wymagana")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "procedure_type", nullable = false, length = 50)
    @NotNull(message = "Typ procedury jest wymagany")
    private GxPProcedureType procedureType;

    /** Krok 1 — programowanie rejestratorów w stacji USB. Nie wchodzi do opóźnienia startu. */
    @Column(name = "step1_prog_minutes", nullable = false)
    @NotNull
    @Min(value = 0, message = "Czas programowania nie może być ujemny")
    @Builder.Default
    private Integer step1ProgMinutes = 10;

    /** Krok 2 — transport i montaż na siatce pomiarowej. */
    @Column(name = "step2_placement_minutes", nullable = false)
    @NotNull
    @Min(value = 0, message = "Czas umieszczenia nie może być ujemny")
    @Builder.Default
    private Integer step2PlacementMinutes = 20;

    /** Krok 3 — stabilizacja termiczna po zamknięciu drzwi. BRAK ZAPISU POMIARÓW (W3). */
    @Column(name = "step3_stab_hours", nullable = false)
    @NotNull
    @Min(value = 0, message = "Czas stabilizacji nie może być ujemny")
    @Builder.Default
    private Integer step3StabHours = 6;

    /** Krok 4 — interwał próbkowania właściwego okresu pomiarowego GxP. */
    @Column(name = "step4_interval_minutes", nullable = false)
    @NotNull
    @Min(value = 1, message = "Interwał próbkowania musi być dodatni")
    @Builder.Default
    private Integer step4IntervalMinutes = 180;

    /** Krok 4 — liczba próbek GxP. */
    @Column(name = "step4_sample_count", nullable = false)
    @NotNull
    @Min(value = 1, message = "Liczba próbek musi być dodatnia")
    @Builder.Default
    private Integer step4SampleCount = 40;

    /** Krok 5 — dopuszczalny bufor na wyjęcie i zczytanie danych z Testo USB (W7). */
    @Column(name = "step5_readout_buffer_hours", nullable = false)
    @NotNull
    @Min(value = 0, message = "Bufor odczytu nie może być ujemny")
    @Builder.Default
    private Integer step5ReadoutBufferHours = 6;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Czas trwania właściwego pomiaru GxP (Krok 4) w minutach.
     */
    public int getMeasurementDurationMinutes() {
        return step4IntervalMinutes * step4SampleCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcedureClassConfig that = (ProcedureClassConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProcedureClassConfig{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", procedureType=" + procedureType +
                '}';
    }
}