package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "thermo_recorder_models")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThermoRecorderModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    @NotBlank(message = "Nazwa modelu jest wymagana")
    private String name;

    @Column(name = "channel_count", nullable = false)
    @NotNull(message = "Liczba kanałów jest wymagana")
    @Builder.Default
    private Integer channelCount = 1;

    @Column(name = "default_resolution", precision = 4, scale = 3, nullable = false)
    @NotNull(message = "Domyślna rozdzielczość jest wymagana")
    @Builder.Default
    private BigDecimal defaultResolution = new BigDecimal("0.100");

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    // --- Dane sprzętowe reguły W4 (karty katalogowe producenta, V34) ---------

    /** Pojemność pamięci pomiarowej [odczyty], wspólna dla wszystkich kanałów. */
    @Column(name = "sample_capacity", nullable = false)
    @NotNull(message = "Pojemność pamięci jest wymagana")
    @Builder.Default
    private Integer sampleCapacity = 16000;

    /** Dolna granica zakresu pracy urządzenia [°C] — {@code null} = dane niepotwierdzone. */
    @Column(name = "min_operating_temp_c")
    private Double minOperatingTempC;

    /** Górna granica zakresu pracy urządzenia [°C] — {@code null} = dane niepotwierdzone. */
    @Column(name = "max_operating_temp_c")
    private Double maxOperatingTempC;

    @Column(name = "battery_type", length = 50)
    private String batteryType;

    /** {@code false} dla loggerów jednorazowych (testo 184 T1/T2) — nie mają wymiany baterii. */
    @Column(name = "battery_replaceable", nullable = false)
    @Builder.Default
    private Boolean batteryReplaceable = true;

    /**
     * Katalogowa żywotność baterii [dni] w warunkach referencyjnych.
     * <p>
     * Producent nie podaje procentowego współczynnika degradacji z temperaturą —
     * podaje liczbę dni przy konkretnym cyklu i konkretnej temperaturze
     * (testo 174 T: 500 dni @ 15 min / +25 °C, testo 184 T4: 100 dni @ 15 min /
     * -80 °C). Reguła W4c porównuje więc czas misji z budżetem dni.
     */
    @Column(name = "battery_life_days")
    private Integer batteryLifeDays;

    /** Cykl pomiarowy, przy którym podano {@link #batteryLifeDays} [min]. */
    @Column(name = "battery_life_ref_cycle_min", nullable = false)
    @NotNull
    @Builder.Default
    private Integer batteryLifeRefCycleMin = 15;

    /** Temperatura, przy której podano {@link #batteryLifeDays} [°C]. */
    @Column(name = "battery_life_ref_temp_c")
    private Double batteryLifeRefTempC;

    /** Sztywny limit pracy loggera jednorazowego [dni] — testo 184 T1: 90, T2: 150. */
    @Column(name = "operating_duration_days")
    private Integer operatingDurationDays;

    /** Dopuszczalny wiek zamontowanej baterii [miesiące]. */
    @Column(name = "battery_shelf_life_months")
    private Integer batteryShelfLifeMonths;

    /**
     * Pamięć przypadająca na jeden kanał — w modelach wielokanałowych
     * (testo 175 T3, 176 T3/T4) bufor jest dzielony między aktywne kanały,
     * więc pojemność katalogowa nie jest liczbą próbek dostępną dla pomiaru.
     */
    public int getSampleCapacityPerChannel() {
        int capacity = sampleCapacity != null ? sampleCapacity : 0;
        int channels = channelCount != null && channelCount > 0 ? channelCount : 1;
        return capacity / channels;
    }

    /** Czy kartoteka modelu zawiera dane sprzętowe wymagane przez regułę W4. */
    public boolean hasHardwareSpecification() {
        return minOperatingTempC != null && maxOperatingTempC != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThermoRecorderModel that = (ThermoRecorderModel) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name != null ? name : "Nieznany model";
    }
}
