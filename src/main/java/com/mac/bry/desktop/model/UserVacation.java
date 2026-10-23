package com.mac.bry.desktop.model;

import com.mac.bry.desktop.security.model.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Nieobecność operatora — urlop planowany albo nieplanowane L4 (reguły W9, W10).
 * <p>
 * Zakres jest domknięty obustronnie: {@code startDate} i {@code endDate}
 * to dni nieobecności.
 */
@Entity
@Table(name = "user_vacations")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVacation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "start_date", nullable = false)
    @NotNull(message = "Data początku nieobecności jest wymagana")
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    @NotNull(message = "Data końca nieobecności jest wymagana")
    private LocalDate endDate;

    @Column(name = "reason", length = 255)
    private String reason;

    /**
     * Nieplanowane L4 wymaga rekalkulacji już zaplanowanych zadań (W10);
     * urlop planowany jest uwzględniany od razu przy generowaniu planu.
     */
    @Column(name = "is_unplanned_l4", nullable = false)
    @Builder.Default
    private Boolean unplannedL4 = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Czy dana data mieści się w nieobecności (granice domknięte).
     */
    public boolean covers(LocalDate date) {
        return date != null
                && !date.isBefore(startDate)
                && !date.isAfter(endDate);
    }

    /**
     * Pierwszy dzień po powrocie z nieobecności.
     */
    public LocalDate firstDayBack() {
        return endDate.plusDays(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserVacation that = (UserVacation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserVacation{" +
                "id=" + id +
                ", " + startDate + " – " + endDate +
                ", unplannedL4=" + unplannedL4 +
                '}';
    }
}