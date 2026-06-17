package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Encja reprezentująca pełną serię pomiarową pobraną bezpośrednio z rejestratora Testo 174T.
 * Przechowuje pełne metadane sesji oraz surowy zrzut binarny (zgodność z FDA 21 CFR Part 11).
 */
@Entity
@Table(name = "thermo_measurement_series")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThermoMeasurementSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Powiązanie z fizycznym urządzeniem rejestrującym (S/N)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "thermo_recorder_id", nullable = false)
    @NotNull(message = "Rejestrator jest wymagany")
    private ThermoRecorder thermoRecorder;

    // Powiązanie z konkretną komorą urządzenia chłodniczego, w której umieszczono rejestrator
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cooling_chamber_id", nullable = false)
    @NotNull(message = "Komora chłodnicza jest wymagana")
    private CoolingChamber coolingChamber;

    // Parametry odczytane z ramki statusu (ab 31)
    @Column(name = "battery_level_percent", nullable = false)
    private Integer batteryLevelPercent; // Stan baterii przy odczycie (%)

    @Column(name = "logging_interval_minutes", nullable = false)
    private Integer loggingIntervalMinutes; // Interwał zapisu w minutach

    @Column(name = "measurements_count", nullable = false)
    private Integer measurementsCount; // Zadeklarowana liczba pomiarów w pamięci

    @Column(name = "programming_time_utc", nullable = false)
    private LocalDateTime programmingTimeUtc; // Moment zaprogramowania (konfiguracji) rejestratora w UTC

    @Column(name = "start_delay_minutes", nullable = false)
    private Integer startDelayMinutes; // Opóźnienie startu pierwszego pomiaru w minutach

    // Timestampy wyliczone matematycznie
    @Column(name = "first_measurement_time_utc", nullable = false)
    private LocalDateTime firstMeasurementTimeUtc; // Wyliczony czas pierwszego pomiaru w UTC

    @Column(name = "first_measurement_time_local", nullable = false)
    private LocalDateTime firstMeasurementTimeLocal; // Wyliczony czas pierwszego pomiaru (Czas lokalny hosta z uwzględnieniem DST)

    @Column(name = "channel_number", nullable = false)
    @NotNull(message = "Numer kanału jest wymagany")
    @Builder.Default
    private Integer channelNumber = 1;

    // Metadane GxP (Audit Trail)
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt; // Data i godzina bezpośredniego odczytu USB

    @Column(name = "imported_by", nullable = false, length = 100)
    private String importedBy; // Nazwa użytkownika, który wykonał bezpośredni import

    // Integralność danych: surowy hex dump z transmisji (ab30 + ab31 + ab33 + stream ab32)
    @Lob
    @Column(name = "raw_hex_dump", nullable = false, columnDefinition = "LONGTEXT")
    private String rawHexDump; // Surowe dane szesnastkowe do weryfikacji autentyczności (Raw Data Integrity)

    // --- Nowe atrybuty statystyczne i metrologiczne (GUM & GxP) ---
    @Column(name = "min_temperature")
    private Double minTemperature;

    @Column(name = "max_temperature")
    private Double maxTemperature;

    @Column(name = "avg_temperature")
    private Double avgTemperature;

    @Column(name = "median_temperature")
    private Double medianTemperature;

    @Column(name = "std_deviation")
    private Double stdDeviation;

    @Column(name = "variance")
    private Double variance;

    @Column(name = "cv_percentage")
    private Double cvPercentage;

    @Column(name = "mkt_temperature")
    private Double mktTemperature;

    @Column(name = "percentile_5")
    private Double percentile5;

    @Column(name = "percentile_95")
    private Double percentile95;

    @Column(name = "total_time_in_range_minutes")
    private Long totalTimeInRangeMinutes;

    @Column(name = "total_time_out_of_range_minutes")
    private Long totalTimeOutOfRangeMinutes;

    @Column(name = "violation_count")
    private Integer violationCount;

    @Column(name = "max_violation_duration_minutes")
    private Long maxViolationDurationMinutes;

    @Column(name = "trend_coefficient")
    private Double trendCoefficient;

    @Column(name = "adjusted_trend_coefficient")
    private Double adjustedTrendCoefficient;

    @Column(name = "spike_count")
    private Integer spikeCount;

    @Column(name = "drift_classification", length = 50)
    private String driftClassification;

    @Column(name = "expanded_uncertainty")
    private Double expandedUncertainty;

    @Column(name = "revalidation_group_id", length = 50)
    private String revalidationGroupId;

    @Column(name = "grid_position", length = 50)
    @Enumerated(EnumType.STRING)
    private RevalidationSession.GridPosition gridPosition;

    @Column(name = "procedure_type", length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private GxPProcedureType procedureType = GxPProcedureType.PERIODIC_REVALIDATION;

    // Relacja do poszczególnych punktów pomiarowych w serii
    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("measurementIndex ASC")
    @Builder.Default
    private List<ThermoMeasurementPoint> measurements = new ArrayList<>();

    public void addMeasurement(ThermoMeasurementPoint point) {
        measurements.add(point);
        point.setSeries(this);
    }

    public void removeMeasurement(ThermoMeasurementPoint point) {
        measurements.remove(point);
        point.setSeries(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThermoMeasurementSeries that = (ThermoMeasurementSeries) o;
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
