package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "calibration_points")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalibrationPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "temperature_value", nullable = false, precision = 10, scale = 4)
    @NotNull(message = "Temperatura wzorcowania jest wymagana")
    private BigDecimal temperatureValue;

    @Column(name = "systematic_error", nullable = false, precision = 10, scale = 4)
    @NotNull(message = "Błąd systematyczny jest wymagany")
    private BigDecimal systematicError;

    @Column(name = "uncertainty", nullable = false, precision = 10, scale = 4)
    @NotNull(message = "Niepewność pomiarowa jest wymagana")
    private BigDecimal uncertainty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calibration_id", nullable = false)
    private Calibration calibration;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalibrationPoint that = (CalibrationPoint) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CalibrationPoint{" +
                "id=" + id +
                ", temperatureValue=" + temperatureValue +
                ", systematicError=" + systematicError +
                ", uncertainty=" + uncertainty +
                '}';
    }
}
