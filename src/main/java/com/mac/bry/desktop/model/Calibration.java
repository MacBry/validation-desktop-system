package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "calibrations")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Calibration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calibration_date", nullable = false)
    @NotNull(message = "Data wzorcowania jest wymagana")
    private LocalDate calibrationDate;

    @Column(name = "certificate_number", nullable = false, length = 100)
    @NotBlank(message = "Numer świadectwa wzorcowania jest wymagany")
    private String certificateNumber;

    @Column(name = "valid_until", nullable = false)
    @NotNull(message = "Data ważności wzorcowania jest wymagana")
    private LocalDate validUntil;

    @Column(name = "certificate_file_path", length = 500)
    private String certificateFilePath;

    @Column(name = "channel_number", nullable = false)
    @NotNull(message = "Numer kanału jest wymagany")
    @Builder.Default
    private Integer channelNumber = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thermo_recorder_id", nullable = false)
    private ThermoRecorder thermoRecorder;

    @NotAudited
    @OneToMany(mappedBy = "calibration", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CalibrationPoint> points = new ArrayList<>();

    public void addPoint(CalibrationPoint point) {
        points.add(point);
        point.setCalibration(this);
    }

    public void removePoint(CalibrationPoint point) {
        points.remove(point);
        point.setCalibration(null);
    }

    @PrePersist
    @PreUpdate
    public void calculateValidUntil() {
        if (calibrationDate != null && validUntil == null) {
            validUntil = calibrationDate.plusYears(1);
        }
    }

    public boolean isValid() {
        return validUntil != null && !LocalDate.now().isAfter(validUntil);
    }

    /**
     * Dolna granica zakresu wzorcowania — najniższy punkt ze świadectwa PCA.
     * <p>
     * Świadectwo nie deklaruje zakresu wprost, więc wyprowadzamy go ze
     * zmierzonych punktów. Poza skrajne punkty nie ekstrapolujemy: świadectwo
     * potwierdza tylko to, co faktycznie zmierzono (reguła W8).
     *
     * @return {@code null} gdy świadectwo nie ma ani jednego punktu
     */
    public Double getCalibratedMinTemp() {
        return points.stream()
                .map(CalibrationPoint::getTemperatureValue)
                .filter(java.util.Objects::nonNull)
                .min(BigDecimal::compareTo)
                .map(BigDecimal::doubleValue)
                .orElse(null);
    }

    /**
     * Górna granica zakresu wzorcowania — najwyższy punkt ze świadectwa PCA.
     *
     * @return {@code null} gdy świadectwo nie ma ani jednego punktu
     */
    public Double getCalibratedMaxTemp() {
        return points.stream()
                .map(CalibrationPoint::getTemperatureValue)
                .filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo)
                .map(BigDecimal::doubleValue)
                .orElse(null);
    }

    /**
     * Czy zakres wzorcowania pokrywa zakres dopuszczalny materiału (W8).
     * <p>
     * Zawieranie domknięte, na liczbach ze znakiem — dlatego materiały mrożone
     * obsługiwane są tą samą arytmetyką. Granica materiału równa {@code null}
     * (np. „poniżej −25 °C” bez dolnego ograniczenia) nie jest sprawdzana.
     * <p>
     * Przykłady: materiał 2…6 °C wobec punktów 2 i 8 → pokryty; materiał
     * 2…10 °C wobec tych samych punktów → niepokryty.
     *
     * @return {@code false} także wtedy, gdy świadectwo nie ma punktów
     */
    public boolean coversMaterialRange(Double materialMinTemp, Double materialMaxTemp) {
        Double calMin = getCalibratedMinTemp();
        Double calMax = getCalibratedMaxTemp();
        if (calMin == null || calMax == null) {
            return false;
        }
        if (materialMinTemp != null && calMin > materialMinTemp) {
            return false;
        }
        return materialMaxTemp == null || !(calMax < materialMaxTemp);
    }

    /**
     * Czy świadectwo pozostaje ważne co najmniej do podanej daty (W1).
     */
    public boolean isValidUntilAtLeast(LocalDate requiredDate) {
        return validUntil != null && !validUntil.isBefore(requiredDate);
    }

    public String getCalibratedRange() {
        Double calMin = getCalibratedMinTemp();
        Double calMax = getCalibratedMaxTemp();
        if (calMin == null || calMax == null) {
            return "–";
        }
        return String.format("%.1f°C do %.1f°C", calMin, calMax);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Calibration that = (Calibration) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Calibration{" +
                "id=" + id +
                ", calibrationDate=" + calibrationDate +
                ", certificateNumber='" + certificateNumber + '\'' +
                ", validUntil=" + validUntil +
                '}';
    }
}
