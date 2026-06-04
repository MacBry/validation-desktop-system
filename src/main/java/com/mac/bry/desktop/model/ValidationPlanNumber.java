package com.mac.bry.desktop.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.Objects;

/**
 * Encja reprezentująca numer Rocznego Planu Walidacji (RPW) dla urządzenia chłodniczego.
 */
@Entity
@Table(name = "validation_plan_numbers")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationPlanNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "`year`", nullable = false)
    @NotNull(message = "Rok jest wymagany")
    private Integer year;

    @Column(name = "plan_number", nullable = false)
    @NotNull(message = "Numer planu jest wymagany")
    private Integer planNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cooling_device_id", nullable = false)
    @NotNull(message = "Urządzenie chłodnicze jest wymagane")
    private CoolingDevice coolingDevice;

    /**
     * Zwraca numer w formacie planNumber/skrótPracowni/rok (lub skrótDziału jeśli brak pracowni).
     */
    public String getFormattedRpw() {
        String abbreviation = "???";
        if (coolingDevice != null) {
            if (coolingDevice.getLaboratory() != null) {
                abbreviation = coolingDevice.getLaboratory().getAbbreviation();
            } else if (coolingDevice.getDepartment() != null) {
                abbreviation = coolingDevice.getDepartment().getAbbreviation();
            }
        }
        return String.format("%d/%s/%d", planNumber, abbreviation, year);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationPlanNumber that = (ValidationPlanNumber) o;
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
        return "ValidationPlanNumber{" +
                "id=" + id +
                ", year=" + year +
                ", planNumber=" + planNumber +
                ", formatted='" + getFormattedRpw() + '\'' +
                '}';
    }
}
