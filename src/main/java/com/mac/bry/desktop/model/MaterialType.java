package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Słownik typów materiałów przechowywanych w urządzeniach chłodniczych.
 */
@Entity
@Table(name = "material_types")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank(message = "Nazwa materiału jest wymagana")
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "min_storage_temp")
    private Double minStorageTemp;

    @Column(name = "max_storage_temp")
    private Double maxStorageTemp;

    @Column(name = "activation_energy", precision = 10, scale = 4)
    private BigDecimal activationEnergy;

    @Column(name = "standard_source", length = 255)
    private String standardSource;

    @Column(name = "application", length = 255)
    private String application;

    @Column(name = "requires_mapping")
    @Builder.Default
    private Boolean requiresMapping = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    public String getFormattedMinStorageTemp() {
        return minStorageTemp != null ? String.format("%.1f°C", minStorageTemp) : "–";
    }

    public String getFormattedMaxStorageTemp() {
        return maxStorageTemp != null ? String.format("%.1f°C", maxStorageTemp) : "–";
    }

    public String getTemperatureRange() {
        if (minStorageTemp == null && maxStorageTemp == null) {
            return "–";
        }
        if (minStorageTemp == null) {
            return "do " + getFormattedMaxStorageTemp();
        }
        if (maxStorageTemp == null) {
            return "od " + getFormattedMinStorageTemp();
        }
        return getFormattedMinStorageTemp() + " do " + getFormattedMaxStorageTemp();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MaterialType that = (MaterialType) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MaterialType{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", tempRange=" + getTemperatureRange() +
                '}';
    }
}
