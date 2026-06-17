package com.mac.bry.desktop.model;

import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "thermo_recorders")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThermoRecorder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, unique = true, length = 50)
    @NotBlank(message = "Numer seryjny jest wymagany")
    private String serialNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "model_id", nullable = false)
    @NotNull(message = "Model jest wymagany")
    private ThermoRecorderModel model;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @NotNull(message = "Status jest wymagany")
    private RecorderStatus status;

    @Column(name = "resolution", precision = 4, scale = 3)
    private BigDecimal resolution;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", nullable = false)
    @NotNull(message = "Dział jest wymagany")
    private Department department;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "laboratory_id", nullable = true)
    private Laboratory laboratory;

    @NotAudited
    @OneToMany(mappedBy = "thermoRecorder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("calibrationDate DESC")
    @Builder.Default
    private List<Calibration> calibrations = new ArrayList<>();

    public void addCalibration(Calibration calibration) {
        calibrations.add(calibration);
        calibration.setThermoRecorder(this);
    }

    public void removeCalibration(Calibration calibration) {
        calibrations.remove(calibration);
        calibration.setThermoRecorder(null);
    }
    
    public Calibration getLatestCalibration() {
        return calibrations.isEmpty() ? null : calibrations.get(0);
    }

    public Calibration getLatestCalibrationForChannel(int channelNumber) {
        return calibrations.stream()
                .filter(c -> c.getChannelNumber() != null && c.getChannelNumber() == channelNumber)
                .findFirst()
                .orElse(null);
    }

    public BigDecimal getResolution() {
        if (resolution != null) {
            return resolution;
        }
        if (model != null && model.getDefaultResolution() != null) {
            return model.getDefaultResolution();
        }
        return new BigDecimal("0.100");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThermoRecorder that = (ThermoRecorder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ThermoRecorder{" +
                "id=" + id +
                ", serialNumber='" + serialNumber + '\'' +
                ", model='" + model + '\'' +
                ", status=" + status +
                '}';
    }
}
