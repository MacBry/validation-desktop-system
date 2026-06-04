package com.mac.bry.desktop.security.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_password_history")
@Getter
@Setter
@NoArgsConstructor
public class UserPasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    public UserPasswordHistory(User user, String passwordHash) {
        this.user = user;
        this.passwordHash = passwordHash;
        this.createdDate = LocalDateTime.now();
    }
}
