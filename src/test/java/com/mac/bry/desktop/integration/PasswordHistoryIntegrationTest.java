package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.security.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PasswordHistoryIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.mac.bry.desktop.security.repository.UserPasswordHistoryRepository historyRepository;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("pass_history_user");
        user.setEmail("pass@history.com");
        String encoded = passwordEncoder.encode("InitialPass123!");
        user.setPassword(encoded);
        user.setEnabled(true);
        user = userRepository.saveAndFlush(user);
        testUserId = user.getId();
        // Uwaga: hasła początkowego NIE dodajemy ręcznie do historii - po naprawie 3.5
        // mechanizm zapisuje poprzedni hash przy pierwszej zmianie.
    }

    @Test
    void shouldBlockPasswordIfInHistory() {
        // 1. Zmiana na Pass2
        userService.changePasswordWithOld(testUserId, "InitialPass123!", "NewPassword2@");
        
        // 2. Próba powrotu do InitialPass123! - powinno rzucić wyjątek
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.changePasswordWithOld(testUserId, "NewPassword2@", "InitialPass123!");
        });
        
        assertTrue(exception.getMessage().contains("użyte w przeszłości"));
    }

    @Test
    void shouldAllowPasswordIfOutsideTop5() {
        // Store-old: przy pierwszej zmianie do historii trafia InitialPass. Aby wypadło ono
        // poza okno Top-5, potrzeba 6 kolejnych zmian (Initial + 5 nowszych wpisów).
        String currentPass = "InitialPass123!";
        for (int i = 1; i <= 6; i++) {
            String nextPass = "PasswordNumber" + i + "!";
            userService.changePasswordWithOld(testUserId, currentPass, nextPass);
            currentPass = nextPass;
        }

        // Historia (od najnowszego): P5,P4,P3,P2,P1  -> InitialPass jest już poza Top-5.
        assertDoesNotThrow(() -> {
            userService.changePasswordWithOld(testUserId, "PasswordNumber6!", "InitialPass123!");
        });
    }

    @Test
    void shouldBlockReuseOfCurrentPassword() {
        // Kluczowa część naprawy 3.5: bieżące hasło (jeszcze nie w historii) nie może być
        // ustawione ponownie - blokuje je jawna kontrola względem user.getPassword().
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                userService.changePasswordWithOld(testUserId, "InitialPass123!", "InitialPass123!"));
        assertTrue(exception.getMessage().contains("użyte w przeszłości"));
    }

    @Test
    void shouldBlockReuseOfInitialPasswordAfterOneChange() {
        // Po jednej zmianie hasło początkowe trafia do historii i nie może zostać przywrócone.
        userService.changePasswordWithOld(testUserId, "InitialPass123!", "NextPassword2@");

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                userService.changePasswordWithOld(testUserId, "NextPassword2@", "InitialPass123!"));
        assertTrue(exception.getMessage().contains("użyte w przeszłości"));
    }

    @Test
    void shouldNotChangePasswordWhenInitiatingReset() {
        // Model tokenowy: zlecenie resetu NIE zmienia hasła ani nie zaśmieca historii -
        // jedynie generuje token (skrót w bazie). Stare hasło nadal działa.
        User before = userRepository.findById(testUserId).orElseThrow();
        String passwordBefore = before.getPassword();

        userService.initiatePasswordReset("pass@history.com");

        User after = userRepository.findById(testUserId).orElseThrow();
        assertEquals(passwordBefore, after.getPassword(), "Zlecenie resetu nie może zmieniać hasła");
        assertNotNull(after.getPasswordResetTokenHash(), "Powinien zostać ustawiony skrót tokenu resetu");
        assertNotNull(after.getPasswordResetTokenExpiresAt(), "Token resetu powinien mieć czas ważności");
    }
}
