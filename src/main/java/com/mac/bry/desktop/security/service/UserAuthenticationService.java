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

    /**
     * Okno, po którym nieodświeżona sesja jest uznawana za osieroconą (np. po awarii stanowiska)
     * i może zostać przejęta przez nowe logowanie. Musi być większe niż limit bezczynności
     * ({@code app.security.inactivity-timeout-minutes}) powiększony o cykl odświeżania aktywności,
     * aby żywa sesja nigdy nie została błędnie zwolniona.
     */
    @Value("${app.security.session-timeout-minutes:30}")
    private int sessionTimeoutMinutes;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    @Transactional
    public void incrementFailedLoginAttempts(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            // Jeśli poprzednie okno blokady czasowej już minęło, rozpocznij świeże liczenie prób,
            // aby pojedyncza pomyłka po cooldownie nie blokowała konta natychmiast.
            if (user.getLockedUntil() != null
                    && user.getLockedUntil().isBefore(LocalDateTime.now())) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
            }

            int newAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(newAttempts);
            log.warn("Nieudane logowanie dla {}: próba {} z {}", username, newAttempts, MAX_FAILED_ATTEMPTS);

            if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                // Wyłącznie blokada CZASOWA. Flaga `locked` jest zarezerwowana dla blokady
                // administracyjnej i nie może być tu ustawiana, bo nie wygasa samoczynnie
                // (co powodowałoby trwałe zablokowanie konta po upływie okna czasowego).
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

    /**
     * Czy użytkownik ma już aktywną (żywą) sesję na innym stanowisku.
     * Sesja przeterminowana (nieodświeżana dłużej niż {@code sessionTimeoutMinutes}) jest
     * traktowana jako osierocona i NIE blokuje nowego logowania - dzięki temu awaria
     * stanowiska nie zakleszcza konta na stałe.
     */
    @Transactional(readOnly = true)
    public boolean isAlreadyLoggedIn(String username) {
        return userRepository.findByUsername(username)
                .map(this::hasLiveSession)
                .orElse(false);
    }

    /**
     * Walidacja sesji po stronie klienta (heartbeat): sesja jest ważna tylko wtedy,
     * gdy token zgadza się z zapisanym w bazie i nie jest przeterminowana.
     * Zwraca {@code false}, gdy token wyczyszczono (wymuszone wylogowanie przez administratora)
     * lub nadpisano (przejęcie sesji przez nowe logowanie) - klient powinien się wtedy wylogować.
     */
    @Transactional(readOnly = true)
    public boolean isSessionValid(Long userId, String token) {
        if (token == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> token.equals(user.getSessionToken()) && hasLiveSession(user))
                .orElse(false);
    }

    /**
     * Wymuszenie zakończenia sesji użytkownika przez administratora.
     * Czyści token sesji w bazie; działający klient wykryje to przy najbliższej walidacji
     * ({@link #isSessionValid}) i sam się wyloguje.
     *
     * @return {@code true}, jeśli istniała aktywna sesja, którą zakończono
     */
    @Transactional
    public boolean forceLogout(Long userId) {
        return userRepository.findById(userId).map(user -> {
            boolean hadSession = user.getSessionToken() != null;
            user.setSessionToken(null);
            user.setLastActivity(null);
            log.warn("ADMIN: wymuszono zakończenie sesji użytkownika {} (ID: {})",
                    user.getUsername(), userId);
            return hadSession;
        }).orElse(false);
    }

    private boolean hasLiveSession(User user) {
        return user.getSessionToken() != null
                && user.getLastActivity() != null
                && user.getLastActivity().plusMinutes(sessionTimeoutMinutes).isAfter(LocalDateTime.now());
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
