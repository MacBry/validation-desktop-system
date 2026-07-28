package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.security.service.UserService;
import com.mac.bry.desktop.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testy integracyjne mechanizmu blokady konta (P0 3.1).
 * Weryfikują rozdzielenie dwóch rodzajów blokady:
 *  - CZASOWA (po nieudanych logowaniach) — wygasa samoczynnie,
 *  - ADMINISTRACYJNA (flaga {@code locked}) — nie wygasa.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountLockIntegrationTest {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String PASSWORD = "Password123!";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @MockBean
    private EmailService emailService;

    private User persistEnabledUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("IT-LOCK-01: konto blokuje się CZASOWO po 5 nieudanych próbach (bez ustawiania flagi administracyjnej)")
    void shouldTimeLockAfterMaxFailedAttempts() {
        persistEnabledUser("timelock");

        for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
            userService.incrementFailedLoginAttempts("timelock");
        }

        User locked = userRepository.findByUsername("timelock").orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(MAX_FAILED_ATTEMPTS);
        assertThat(locked.getLockedUntil()).isAfter(LocalDateTime.now());
        // Kluczowy warunek regresji: flaga administracyjna NIE może zostać zapalona.
        assertThat(locked.isLocked()).isFalse();
        assertThat(locked.isAccountNonLocked()).isFalse();

        // Nawet z poprawnym hasłem Spring Security odrzuca logowanie zablokowanego konta.
        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("timelock", PASSWORD)))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("IT-LOCK-02: blokada CZASOWA wygasa samoczynnie po upływie okna")
    void shouldAutoUnlockAfterTimeWindowElapses() {
        persistEnabledUser("autounlock");

        for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
            userService.incrementFailedLoginAttempts("autounlock");
        }

        // Symulujemy upływ okna blokady (15 min) cofając lockedUntil w przeszłość.
        User user = userRepository.findByUsername("autounlock").orElseThrow();
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(user);

        User reloaded = userRepository.findByUsername("autounlock").orElseThrow();
        assertThat(reloaded.isAccountNonLocked()).isTrue();

        // Po wygaśnięciu blokady logowanie z poprawnym hasłem przechodzi.
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("autounlock", PASSWORD));
        assertThat(auth.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("IT-LOCK-03: blokada ADMINISTRACYJNA nie wygasa samoczynnie")
    void shouldKeepAdministrativeLockRegardlessOfTime() {
        User user = persistEnabledUser("adminlock");

        userService.lockUser(user.getId());

        User locked = userRepository.findByUsername("adminlock").orElseThrow();
        assertThat(locked.isLocked()).isTrue();
        assertThat(locked.getLockedUntil()).isNull();       // brak okna czasowego
        assertThat(locked.isAccountNonLocked()).isFalse();  // a mimo to zablokowany

        assertThatThrownBy(() -> authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("adminlock", PASSWORD)))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("IT-LOCK-04: udane logowanie (reset) zeruje licznik nieudanych prób")
    void shouldResetFailedAttemptsCounter() {
        User user = persistEnabledUser("resetcounter");

        userService.incrementFailedLoginAttempts("resetcounter");
        userService.incrementFailedLoginAttempts("resetcounter");
        assertThat(userRepository.findByUsername("resetcounter").orElseThrow()
                .getFailedLoginAttempts()).isEqualTo(2);

        userService.resetFailedLoginAttempts(user.getId());

        User reset = userRepository.findByUsername("resetcounter").orElseThrow();
        assertThat(reset.getFailedLoginAttempts()).isZero();
        assertThat(reset.getLockedUntil()).isNull();
        assertThat(reset.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("IT-LOCK-05: nieudana próba po wygaśnięciu okna startuje świeże liczenie (nie blokuje natychmiast)")
    void shouldRestartCounterAfterExpiredWindow() {
        persistEnabledUser("freshwindow");

        // Doprowadzamy do blokady czasowej...
        for (int i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
            userService.incrementFailedLoginAttempts("freshwindow");
        }
        // ...i symulujemy jej wygaśnięcie.
        User user = userRepository.findByUsername("freshwindow").orElseThrow();
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(user);

        // Kolejna pomyłka po cooldownie: licznik startuje od 1, konto pozostaje odblokowane.
        userService.incrementFailedLoginAttempts("freshwindow");

        User after = userRepository.findByUsername("freshwindow").orElseThrow();
        assertThat(after.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(after.isAccountNonLocked()).isTrue();
    }
}