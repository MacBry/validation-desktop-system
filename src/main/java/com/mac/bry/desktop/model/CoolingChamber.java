package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Encja reprezentująca pojedynczą komorę urządzenia chłodniczego.
 */
@Entity
@Table(name = "cooling_chambers")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoolingChamber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cooling_device_id", nullable = false)
    @NotNull(message = "Urządzenie nadrzędne jest wymagane")
    private CoolingDevice coolingDevice;

    @Column(name = "chamber_name", nullable = false, length = 100)
    @NotBlank(message = "Nazwa komory jest wymagana")
    private String chamberName; // np. "Górna (Lodówka)", "Dolna (Zamrażarka)"

    @Enumerated(EnumType.STRING)
    @Column(name = "chamber_type", nullable = false, length = 30)
    @NotNull(message = "Typ komory jest wymagany")
    private ChamberType chamberType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "material_type_id")
    private MaterialType materialType;

    @Column(name = "min_operating_temp")
    private Double minOperatingTemp;

    @Column(name = "max_operating_temp")
    private Double maxOperatingTemp;

    @Column(name = "volume_m3")
    private Double volume;

    @Enumerated(EnumType.STRING)
    @Column(name = "volume_category", length = 10)
    private VolumeCategory volumeCategory;

    @Column(name = "last_mapping_date")
    private LocalDate lastMappingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "hotspot_position", length = 50)
    private RevalidationSession.GridPosition hotspotPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "coldspot_position", length = 50)
    private RevalidationSession.GridPosition coldspotPosition;

    public String getFormattedMinOperatingTemp() {
        return minOperatingTemp != null ? String.format("%.1f°C", minOperatingTemp) : "–";
    }

    public String getFormattedMaxOperatingTemp() {
        return maxOperatingTemp != null ? String.format("%.1f°C", maxOperatingTemp) : "–";
    }

    public String getMaterialName() {
        return materialType != null ? materialType.getName() : "–";
    }

    public String getMaterialTemperatureRange() {
        return materialType != null ? materialType.getTemperatureRange() : "–";
    }

    public String getFormattedVolume() {
        return volume != null ? String.format("%.2f m³", volume) : "–";
    }

    public String getVolumeCategoryDisplayName() {
        return volumeCategory != null ? volumeCategory.getDisplayName() : "–";
    }

    public Integer getMinMeasurementPoints() {
        return volumeCategory != null ? volumeCategory.getMinMeasurementPoints() : null;
    }

    public boolean isValidMeasurementPoints(int measurementPoints) {
        return volumeCategory == null || volumeCategory.isValidMeasurementPoints(measurementPoints);
    }

    public boolean isMappingRequired() {
        return materialType != null && Boolean.TRUE.equals(materialType.getRequiresMapping());
    }

    public boolean isMappingValid() {
        return lastMappingDate != null && !lastMappingDate.isBefore(LocalDate.now().minusYears(5));
    }

    public void updateVolumeCategoryFromVolume() {
        if (volume != null) {
            this.volumeCategory = VolumeCategory.fromVolume(volume);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoolingChamber that = (CoolingChamber) o;
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31;
    }

    @Override
    public String toString() {
        return "CoolingChamber{" +
                "id=" + id +
                ", chamberName='" + chamberName + '\'' +
                ", chamberType=" + chamberType +
                ", volume=" + volume +
                '}';
    }
}
