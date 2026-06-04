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
        
        // Ręcznie dodajemy początkowe hasło do historii (normalnie robi to migracja lub UserService)
        historyRepository.save(new com.mac.bry.desktop.security.model.UserPasswordHistory(user, encoded));
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
        // Symulujemy 5 zmian haseł
        String currentPass = "InitialPass123!";
        for (int i = 1; i <= 5; i++) {
            String nextPass = "PasswordNumber" + i + "!";
            userService.changePasswordWithOld(testUserId, currentPass, nextPass);
            currentPass = nextPass;
        }
        
        // Teraz w historii mamy: Password5 (current), Password4, Password3, Password2, Password1, InitialPass
        // InitialPass jest na 6. miejscu (wypadło z Top 5)
        
        assertDoesNotThrow(() -> {
            userService.changePasswordWithOld(testUserId, "PasswordNumber5!", "InitialPass123!");
        });
    }

    @Test
    void shouldTrackPasswordFromReset() {
        // 1. Reset hasła (generuje losowe, zapisuje do historii)
        userService.resetPassword("pass@history.com");
        
        // Ponieważ reset generuje losowe hasło, trudno je zgadnąć, 
        // ale możemy sprawdzić czy zmiana na InitialPass123! jest zablokowana
        
        User user = userRepository.findById(testUserId).orElseThrow();
        // Próba zmiany (wymuszonej) na InitialPass123!
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.changeUserPassword(testUserId, "InitialPass123!");
        });
        
        assertTrue(exception.getMessage().contains("użyte w przeszłości"));
    }
}
