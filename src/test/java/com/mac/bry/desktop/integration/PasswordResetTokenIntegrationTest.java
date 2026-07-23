package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.security.service.UserPasswordService;
import com.mac.bry.desktop.security.service.UserPasswordService.PasswordResetResult;
import com.mac.bry.desktop.security.service.UserService;
import com.mac.bry.desktop.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Testy integracyjne resetu hasła jednorazowym tokenem (P0 3.4).
 * Weryfikują: jednorazowość, ograniczony czas ważności, brak jawnego tokenu w bazie,
 * pełną walidację (historia haseł) oraz brak gotowego hasła w wiadomości e-mail.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordResetTokenIntegrationTest {

    private static final String EMAIL = "reset.token@example.com";
    private static final String OLD_PASSWORD = "OldPassword123!";

    @Autowired
    private UserService userService;

    @Autowired
    private UserPasswordService userPasswordService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.mac.bry.desktop.security.repository.UserPasswordHistoryRepository historyRepository;

    @MockBean
    private EmailService emailService;

    private User persistUser() {
        User user = new User();
        user.setUsername("resettoken");
        user.setEmail(EMAIL);
        user.setPassword(passwordEncoder.encode(OLD_PASSWORD));
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    private String initiateAndCaptureToken() {
        userService.initiatePasswordReset(EMAIL);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(EMAIL), tokenCaptor.capture());
        return tokenCaptor.getValue();
    }

    @Test
    @DisplayName("IT-RESET-01: w bazie przechowywany jest wyłącznie skrót tokenu, nie jawny token")
    void shouldStoreOnlyHashedToken() {
        persistUser();
        String token = initiateAndCaptureToken();

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getPasswordResetTokenHash()).isNotBlank();
        assertThat(user.getPasswordResetTokenHash()).isNotEqualTo(token);   // nie jawny token
        assertThat(user.getPasswordResetTokenHash()).hasSize(64);           // SHA-256 hex
        assertThat(user.getPasswordResetTokenExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("IT-RESET-02: token pozwala ustawić nowe hasło, po czym jest jednorazowo unieważniany")
    void shouldSetNewPasswordAndInvalidateToken() {
        persistUser();
        String token = initiateAndCaptureToken();

        assertThat(userService.resetPasswordWithToken(EMAIL, token, "BrandNewPass123!"))
                .isEqualTo(PasswordResetResult.OK);

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches("BrandNewPass123!", user.getPassword())).isTrue();
        assertThat(user.getPasswordResetTokenHash()).isNull();          // token zużyty
        assertThat(user.getPasswordResetTokenExpiresAt()).isNull();

        // Ponowne użycie tego samego tokenu jest odrzucane.
        assertThat(userService.resetPasswordWithToken(EMAIL, token, "YetAnother123!"))
                .isEqualTo(PasswordResetResult.INVALID_TOKEN);
    }

    @Test
    @DisplayName("IT-RESET-03: token po terminie ważności jest odrzucany")
    void shouldRejectExpiredToken() {
        persistUser();
        String token = initiateAndCaptureToken();

        // Symulujemy wygaśnięcie tokenu.
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.setPasswordResetTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(user);

        assertThat(userService.resetPasswordWithToken(EMAIL, token, "BrandNewPass123!"))
                .isEqualTo(PasswordResetResult.EXPIRED);
        // Hasło pozostaje niezmienione.
        User after = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, after.getPassword())).isTrue();
    }

    @Test
    @DisplayName("IT-RESET-04: błędny token jest odrzucany")
    void shouldRejectInvalidToken() {
        persistUser();
        initiateAndCaptureToken();

        assertThat(userService.resetPasswordWithToken(EMAIL, "całkiem-zły-token", "BrandNewPass123!"))
                .isEqualTo(PasswordResetResult.INVALID_TOKEN);
    }

    @Test
    @DisplayName("IT-RESET-05: nowe hasło przechodzi walidację historii (odrzucenie powtórki)")
    void shouldRejectReusedPassword() {
        User user = persistUser();
        // Bieżące hasło trafia do historii, aby zasymulować regułę 5 ostatnich haseł.
        historyRepository.save(new com.mac.bry.desktop.security.model.UserPasswordHistory(
                user, passwordEncoder.encode(OLD_PASSWORD)));
        String token = initiateAndCaptureToken();

        assertThat(userService.resetPasswordWithToken(EMAIL, token, OLD_PASSWORD))
                .isEqualTo(PasswordResetResult.REUSED);
    }

    @Test
    @DisplayName("IT-RESET-06: wiadomość e-mail zawiera token, a nie gotowe hasło")
    void shouldEmailTokenNotPassword() {
        persistUser();
        String token = initiateAndCaptureToken();

        // Token wysłany mailem jest wartością losową, nie hasłem użytkownika ani jego skrótem.
        assertThat(token).isNotBlank();
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(token).isNotEqualTo(user.getPassword());
        assertThat(passwordEncoder.matches(token, user.getPassword())).isFalse();
    }
}