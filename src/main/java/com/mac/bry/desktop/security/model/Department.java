package com.mac.bry.desktop.security.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "departments")
@Audited
@Getter
@Setter
@NoArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String abbreviation;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @NotAudited
    @OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
    private List<Laboratory> laboratories = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
    }
}
