package com.mac.bry.desktop.model;

import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

/**
 * Encja reprezentująca urządzenie chłodnicze.
 */
@Entity
@Table(name = "cooling_devices")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoolingDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_number", nullable = false, unique = true, length = 50)
    @NotBlank(message = "Numer inwentarzowy jest wymagany")
    private String inventoryNumber;

    @Column(name = "name", nullable = false, length = 200)
    @NotBlank(message = "Nazwa urządzenia jest wymagana")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @NotNull(message = "Status urządzenia jest wymagany")
    @Builder.Default
    private DeviceStatus status = DeviceStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", nullable = false)
    @NotNull(message = "Dział jest wymagany")
    private Department department;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "laboratory_id", nullable = true)
    private Laboratory laboratory;

    @OneToMany(mappedBy = "coolingDevice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<CoolingChamber> chambers = new ArrayList<>();

    @org.hibernate.envers.NotAudited
    @OneToMany(mappedBy = "coolingDevice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ValidationPlanNumber> validationPlanNumbers = new ArrayList<>();

    public void addChamber(CoolingChamber chamber) {
        chambers.add(chamber);
        chamber.setCoolingDevice(this);
    }

    public void removeChamber(CoolingChamber chamber) {
        chambers.remove(chamber);
        chamber.setCoolingDevice(null);
    }

    public void addValidationPlanNumber(ValidationPlanNumber planNumber) {
        validationPlanNumbers.add(planNumber);
        planNumber.setCoolingDevice(this);
    }

    public void removeValidationPlanNumber(ValidationPlanNumber planNumber) {
        validationPlanNumbers.remove(planNumber);
        planNumber.setCoolingDevice(null);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CoolingDevice that = (CoolingDevice) o;
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
        return "CoolingDevice{" +
                "id=" + id +
                ", inventoryNumber='" + inventoryNumber + '\'' +
                ", name='" + name + '\'' +
                ", chambersCount=" + (chambers != null ? chambers.size() : 0) +
                '}';
    }
}
