package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

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
