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
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // --- Stan sprzętowy egzemplarza (reguła W4, V34) ------------------------

    /**
     * Pozostały czas pracy baterii [dni] z ostatniego odczytu w stacji Testo USB
     * (ramka {@code ab010a}) — ta sama liczba, którą pokazuje oryginalne
     * oprogramowanie producenta („Stan baterii: 388 dni").
     * <p>
     * {@code null} oznacza brak wiarygodnego odczytu i <b>blokuje</b> regułę W4c:
     * budżetu energii nie da się wtedy wyznaczyć, a zgadywanie go jest gorsze niż
     * odmowa zaplanowania badania. Sentinel {@code -1} ze źródeł nieraportujących
     * baterii (import PDF 184) nie jest tu zapisywany.
     * <p>
     * Jednostką są <b>dni</b>, nie procenty — urządzenie nie mierzy stanu
     * naładowania, a reguła W4c i tak porównuje czas z czasem. Historia zmiany:
     * {@code V36__Battery_Remaining_Days.sql}.
     */
    @Column(name = "battery_remaining_days")
    private Integer batteryRemainingDays;

    /**
     * @deprecated Wielkość nigdy nie była mierzona — do 2026-08-05 zapisywano tu
     * młodszy bajt górnego progu alarmowego odczytany jako „procent baterii".
     * Kolumna i historia w {@code _aud} zostają jako ślad audytowy, ale pole
     * <b>nie jest już zapisywane ani używane przez regułę W4c</b>. Aktualny stan:
     * {@link #batteryRemainingDays}.
     */
    @Deprecated(since = "2026-08-05", forRemoval = false)
    @Column(name = "last_battery_level_percent")
    private Integer lastBatteryLevelPercent;

    @Column(name = "last_battery_read_at")
    private LocalDateTime lastBatteryReadAt;

    /** Data ostatniej wymiany baterii — podstawa kontroli wieku ogniwa. */
    @Column(name = "battery_replacement_date")
    private LocalDate batteryReplacementDate;

    /** Pierwsze uruchomienie — punkt odniesienia limitu pracy loggerów jednorazowych. */
    @Column(name = "first_activation_date")
    private LocalDate firstActivationDate;

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
