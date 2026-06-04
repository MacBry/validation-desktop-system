package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAuthenticationService {

    private final UserRepository userRepository;

    @Value("${app.security.account-expiration-days:90}")
    private int accountExpirationDays;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    private static final int SESSION_TIMEOUT_MINUTES = 30;

    @Transactional
    public void incrementFailedLoginAttempts(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            int newAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(newAttempts);
            log.warn("Nieudane logowanie dla {}: próba {} z {}", username, newAttempts, MAX_FAILED_ATTEMPTS);

            if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLocked(true);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
                log.warn("KONTO ZABLOKOWANE: {} na {} minut po {} nieudanych próbach",
                        username, LOCK_DURATION_MINUTES, MAX_FAILED_ATTEMPTS);
            }
        });
    }

    @Transactional
    public void resetFailedLoginAttempts(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getFailedLoginAttempts() > 0) {
                user.setFailedLoginAttempts(0);
                user.setLocked(false);
                user.setLockedUntil(null);
            }
        });
    }

    @Transactional
    public String registerSession(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        String token = UUID.randomUUID().toString();
        user.setSessionToken(token);
        user.setLastActivity(LocalDateTime.now());
        user.setLastLogin(LocalDateTime.now());
        return token;
    }

    @Transactional
    public void updateLastLogin(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLogin(LocalDateTime.now());
        });
    }

    @Transactional
    public void clearSession(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setSessionToken(null);
            user.setLastActivity(null);
        });
    }

    @Transactional
    public void updateActivity(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastActivity(LocalDateTime.now());
        });
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyLoggedIn(String username) {
        return false;
    }

    @Transactional
    public void checkAccountExpiration(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getLastLogin() != null) {
                LocalDateTime expirationThreshold = LocalDateTime.now().minusDays(accountExpirationDays);
                if (user.getLastLogin().isBefore(expirationThreshold)) {
                    log.warn("KONTO WYGASŁO: {} nie logował się od {}. Blokada konta.", username, user.getLastLogin());
                    user.setEnabled(false);
                    user.setLocked(true);
                }
            }
        });
    }
}
