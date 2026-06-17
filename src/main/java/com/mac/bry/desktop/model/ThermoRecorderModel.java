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
