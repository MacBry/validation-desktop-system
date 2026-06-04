package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Encja reprezentująca pojedynczy punkt pomiarowy (odczyt temperatury) przypisany do serii.
 */
@Entity
@Table(name = "thermo_measurement_points")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThermoMeasurementPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relacja zwrotna do nagłówka serii
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    @NotNull(message = "Seria pomiarowa jest wymagana")
    private ThermoMeasurementSeries series;

    @Column(name = "measurement_index", nullable = false)
    private Integer measurementIndex; // Numer kolejny pomiaru (1, 2, 3...)

    @Column(name = "timestamp_local", nullable = false)
    private LocalDateTime timestampLocal; // Dokładna data i godzina pomiaru (Czas lokalny hosta)

    @Column(name = "raw_celsius", nullable = false)
    private Double rawCelsius; // Surowa temperatura odczytana z rejestratora (np. 4.5 °C)

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThermoMeasurementPoint that = (ThermoMeasurementPoint) o;
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31;
    }
}
