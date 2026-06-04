package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.model.UserPasswordHistory;
import com.mac.bry.desktop.security.repository.UserPasswordHistoryRepository;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPasswordService {

    private final UserRepository userRepository;
    private final UserPasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public void changeUserPassword(Long userId, String rawPassword) {
        userRepository.findById(userId).ifPresent(user -> {
            if (isPasswordInHistory(userId, rawPassword)) {
                throw new RuntimeException("Hasło zostało już użyte w przeszłości (wymagana historia 5 haseł).");
            }

            updatePasswordWithAging(user, rawPassword);
            log.info("Zmieniono hasło dla użytkownika {} (ID: {}) po wymuszeniu", user.getUsername(), userId);
        });
    }

    @Transactional
    public boolean changePasswordWithOld(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId).orElseThrow();

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("Nieudana próba zmiany hasła dla użytkownika ID: {} (błędne stare hasło)", userId);
            return false;
        }

        if (isPasswordInHistory(userId, newPassword)) {
            throw new RuntimeException("Nowe hasło zostało już użyte w przeszłości (wymagana historia 5 haseł).");
        }

        updatePasswordWithAging(user, newPassword);
        log.info("Użytkownik ID: {} poprawnie zmienił swoje hasło.", userId);
        return true;
    }

    @Transactional
    public boolean resetPassword(String email) {
        return userRepository.findByEmail(email).map(user -> {
            String tempPassword = UUID.randomUUID().toString().substring(0, 8);

            user.setPassword(passwordEncoder.encode(tempPassword));
            user.setMustChangePassword(true);

            LocalDateTime now = LocalDateTime.now();
            user.setPasswordChangedAt(now);
            user.setPasswordExpiresAt(now.plusDays(user.getPasswordExpiryDays() != null ? user.getPasswordExpiryDays() : 90));

            passwordHistoryRepository.save(new UserPasswordHistory(user, user.getPassword()));
            emailService.sendPasswordResetEmail(user.getEmail(), tempPassword);

            log.info("Resetowano hasło dla użytkownika: {}", user.getUsername());
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isPasswordInHistory(Long userId, String rawPassword) {
        List<UserPasswordHistory> history = passwordHistoryRepository.findTop5ByUserIdOrderByCreatedDateDesc(userId);
        return history.stream()
                .anyMatch(h -> passwordEncoder.matches(rawPassword, h.getPasswordHash()));
    }

    private void updatePasswordWithAging(User user, String rawPassword) {
        String encoded = passwordEncoder.encode(rawPassword);
        user.setPassword(encoded);
        user.setMustChangePassword(false);

        LocalDateTime now = LocalDateTime.now();
        user.setPasswordChangedAt(now);
        user.setPasswordExpiresAt(now.plusDays(user.getPasswordExpiryDays() != null ? user.getPasswordExpiryDays() : 90));

        passwordHistoryRepository.save(new UserPasswordHistory(user, encoded));
    }
}
