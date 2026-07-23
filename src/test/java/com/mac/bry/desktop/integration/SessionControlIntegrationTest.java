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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testy integracyjne kontroli aktywnej sesji (P0 3.2).
 * Weryfikują realne (nie-atrapowe) działanie pojedynczej sesji: blokadę równoległego
 * logowania, wygasanie sesji osieroconej, wymuszone wylogowanie przez administratora
 * oraz walidację tokenu wykrywającą przejęcie sesji.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionControlIntegrationTest {

    private static final String PASSWORD = "Password123!";
    // Zgodne z domyślą wartością app.security.session-timeout-minutes (30 min).
    private static final int SESSION_TIMEOUT_MINUTES = 30;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private EmailService emailService;

    private User persistEnabledUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEnabled(true);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("IT-SESSION-01: druga aktywna sesja tego samego użytkownika jest blokowana")
    void shouldBlockParallelLogin() {
        User user = persistEnabledUser("session1");

        // Stanowisko A loguje się -> rejestruje sesję.
        assertThat(userService.isAlreadyLoggedIn("session1")).isFalse();
        userService.registerSession(user.getId());

        // Stanowisko B próbuje zalogować się na to samo konto -> wykrycie aktywnej sesji.
        assertThat(userService.isAlreadyLoggedIn("session1")).isTrue();
    }

    @Test
    @DisplayName("IT-SESSION-02: wylogowanie usuwa token i pozwala zalogować się ponownie")
    void shouldClearSessionOnLogout() {
        User user = persistEnabledUser("session2");
        userService.registerSession(user.getId());
        assertThat(userService.isAlreadyLoggedIn("session2")).isTrue();

        userService.clearSession(user.getId());

        assertThat(userService.isAlreadyLoggedIn("session2")).isFalse();
        User cleared = userRepository.findByUsername("session2").orElseThrow();
        assertThat(cleared.getSessionToken()).isNull();
    }

    @Test
    @DisplayName("IT-SESSION-03: osierocona sesja (brak aktywności) wygasa i nie blokuje logowania")
    void shouldExpireOrphanedSession() {
        User user = persistEnabledUser("session3");
        userService.registerSession(user.getId());

        // Symulujemy awarię stanowiska: brak odświeżenia aktywności ponad okno timeoutu.
        User u = userRepository.findByUsername("session3").orElseThrow();
        u.setLastActivity(LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES + 1));
        userRepository.saveAndFlush(u);

        // Token wciąż jest w bazie, ale sesja jest przeterminowana -> konto nie jest zakleszczone.
        assertThat(u.getSessionToken()).isNotNull();
        assertThat(userService.isAlreadyLoggedIn("session3")).isFalse();
    }

    @Test
    @DisplayName("IT-SESSION-04: administrator wymusza zakończenie aktywnej sesji")
    void shouldForceLogoutBySession() {
        User user = persistEnabledUser("session4");
        userService.registerSession(user.getId());
        assertThat(userService.isAlreadyLoggedIn("session4")).isTrue();

        boolean hadSession = userService.forceLogout(user.getId());

        assertThat(hadSession).isTrue();
        assertThat(userService.isAlreadyLoggedIn("session4")).isFalse();
        // Wymuszenie na koncie bez sesji zwraca false.
        assertThat(userService.forceLogout(user.getId())).isFalse();
    }

    @Test
    @DisplayName("IT-SESSION-05: walidacja tokenu wykrywa przejęcie sesji przez nowe logowanie")
    void shouldInvalidateOldTokenAfterSessionTakeover() {
        User user = persistEnabledUser("session5");

        // Sesja nr 1 (stanowisko A).
        String tokenA = userService.registerSession(user.getId());
        assertThat(userService.isSessionValid(user.getId(), tokenA)).isTrue();

        // Nowe logowanie (stanowisko B) nadpisuje token.
        String tokenB = userService.registerSession(user.getId());
        assertThat(tokenB).isNotEqualTo(tokenA);

        // Klient A przy najbliższym heartbeacie dostanie false -> sam się wyloguje.
        assertThat(userService.isSessionValid(user.getId(), tokenA)).isFalse();
        assertThat(userService.isSessionValid(user.getId(), tokenB)).isTrue();
    }

    @Test
    @DisplayName("IT-SESSION-06: walidacja odrzuca sesję po wymuszonym wylogowaniu (token wyczyszczony)")
    void shouldInvalidateTokenAfterForceLogout() {
        User user = persistEnabledUser("session6");
        String token = userService.registerSession(user.getId());
        assertThat(userService.isSessionValid(user.getId(), token)).isTrue();

        userService.forceLogout(user.getId());

        assertThat(userService.isSessionValid(user.getId(), token)).isFalse();
    }
}