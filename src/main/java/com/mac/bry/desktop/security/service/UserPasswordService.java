package com.mac.bry.desktop.security.service;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.model.UserPasswordHistory;
import com.mac.bry.desktop.security.repository.UserPasswordHistoryRepository;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPasswordService {

    private final UserRepository userRepository;
    private final UserPasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RESET_TOKEN_BYTES = 32;

    @Value("${app.security.password-reset-token-minutes:30}")
    private int passwordResetTokenMinutes;

    /** Wynik próby ustawienia nowego hasła przy pomocy tokenu resetu. */
    public enum PasswordResetResult {
        OK,
        INVALID_TOKEN,   // brak/niepoprawny token lub nieznany e-mail
        EXPIRED,         // token po terminie ważności
        REUSED           // nowe hasło było już użyte (historia 5 haseł)
    }

    @Transactional
    public void changeUserPassword(Long userId, String rawPassword) {
        userRepository.findById(userId).ifPresent(user -> {
            if (isPasswordReused(user, rawPassword)) {
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

        if (isPasswordReused(user, newPassword)) {
            throw new RuntimeException("Nowe hasło zostało już użyte w przeszłości (wymagana historia 5 haseł).");
        }

        updatePasswordWithAging(user, newPassword);
        log.info("Użytkownik ID: {} poprawnie zmienił swoje hasło.", userId);
        return true;
    }

    /**
     * Inicjuje reset hasła: generuje jednorazowy token, zapisuje w bazie WYŁĄCZNIE jego skrót
     * (SHA-256) wraz z czasem ważności i wysyła jawny token e-mailem. Nie zmienia hasła i nie
     * dotyka historii - dotychczasowe hasło działa aż użytkownik ustawi nowe przez
     * {@link #resetPasswordWithToken}. Dzięki temu nieudana wysyłka maila nie blokuje konta.
     *
     * @return {@code true}, jeśli konto o danym e-mailu istnieje (dla wygody wywołującego;
     *         UI i tak wyświetla neutralny komunikat, by nie umożliwiać enumeracji kont)
     */
    @Transactional
    public boolean initiatePasswordReset(String email) {
        return userRepository.findByEmail(email).map(user -> {
            String rawToken = generateResetToken();

            user.setPasswordResetTokenHash(sha256Hex(rawToken));
            user.setPasswordResetTokenExpiresAt(LocalDateTime.now().plusMinutes(passwordResetTokenMinutes));

            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);

            log.info("Wygenerowano token resetu hasła dla użytkownika: {} (ważny {} min)",
                    user.getUsername(), passwordResetTokenMinutes);
            return true;
        }).orElse(false);
    }

    /**
     * Ustawia nowe hasło na podstawie jednorazowego tokenu resetu. Weryfikuje zgodność skrótu
     * tokenu i jego ważność, sprawdza historię haseł, a po sukcesie unieważnia token (jednorazowość).
     * Siłę hasła waliduje warstwa UI przed wywołaniem (spójnie z pozostałymi ścieżkami zmiany hasła).
     */
    @Transactional
    public PasswordResetResult resetPasswordWithToken(String email, String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            return PasswordResetResult.INVALID_TOKEN;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null
                || user.getPasswordResetTokenHash() == null
                || !constantTimeEquals(user.getPasswordResetTokenHash(), sha256Hex(rawToken))) {
            return PasswordResetResult.INVALID_TOKEN;
        }

        if (user.getPasswordResetTokenExpiresAt() == null
                || user.getPasswordResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            return PasswordResetResult.EXPIRED;
        }

        if (isPasswordReused(user, newPassword)) {
            return PasswordResetResult.REUSED;
        }

        updatePasswordWithAging(user, newPassword);
        // Jednorazowość: unieważnij token po użyciu.
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetTokenExpiresAt(null);

        log.info("Ustawiono nowe hasło tokenem resetu dla użytkownika: {}", user.getUsername());
        return PasswordResetResult.OK;
    }

    private String generateResetToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 niedostępne", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public boolean isPasswordInHistory(Long userId, String rawPassword) {
        List<UserPasswordHistory> history = passwordHistoryRepository.findTop5ByUserIdOrderByCreatedDateDesc(userId);
        return history.stream()
                .anyMatch(h -> passwordEncoder.matches(rawPassword, h.getPasswordHash()));
    }

    /**
     * Czy nowe hasło jest ponownym użyciem: pokrywa zarówno BIEŻĄCE hasło (które nie znajduje się
     * jeszcze w historii, dopóki nie zostanie zastąpione), jak i ostatnie N wpisów historii.
     */
    private boolean isPasswordReused(User user, String rawPassword) {
        if (user.getPassword() != null && passwordEncoder.matches(rawPassword, user.getPassword())) {
            return true;
        }
        return isPasswordInHistory(user.getId(), rawPassword);
    }

    private void updatePasswordWithAging(User user, String rawPassword) {
        // Zapisujemy do historii POPRZEDNI (zastępowany) hash - zanim go nadpiszemy - aby żadne
        // wcześniej używane hasło (w tym początkowe) nie mogło zostać ponownie ustawione.
        String previousHash = user.getPassword();
        if (previousHash != null) {
            passwordHistoryRepository.save(new UserPasswordHistory(user, previousHash));
        }

        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(false);

        LocalDateTime now = LocalDateTime.now();
        user.setPasswordChangedAt(now);
        user.setPasswordExpiresAt(now.plusDays(user.getPasswordExpiryDays() != null ? user.getPasswordExpiryDays() : 90));
    }
}
