package com.mac.bry.desktop.security.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "old_value_json", columnDefinition = "json")
    private String oldValueJson;

    @Column(name = "new_value_json", columnDefinition = "json")
    private String newValueJson;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
