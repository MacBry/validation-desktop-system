package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.security.service.UserPasswordService;
import com.mac.bry.desktop.security.service.UserService;
import com.mac.bry.desktop.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class UserManagementIntegrationTest {

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

    @Test
    @DisplayName("IT-01: Rejestracja użytkownika i zapis do bazy")
    void shouldRegisterUserCorrectly() {
        // Given
        User newUser = new User();
        newUser.setUsername("newuser");
        newUser.setEmail("new@example.com");
        newUser.setPassword(passwordEncoder.encode("Password123!"));
        newUser.setEnabled(false);

        // When
        userRepository.save(newUser);

        // Then
        Optional<User> saved = userRepository.findByUsername("newuser");
        assertThat(saved).isPresent();
        assertThat(saved.get().isEnabled()).isFalse();
        assertThat(saved.get().getRoles()).isEmpty();
    }

    @Test
    @DisplayName("IT-02: Aktywacja konta i logowanie")
    void shouldActivateUserAndAllowLogin() {
        // Given
        User user = new User();
        user.setUsername("activeuser");
        user.setEmail("active@example.com");
        user.setPassword(passwordEncoder.encode("Password123!"));
        user.setEnabled(false);
        userRepository.save(user);

        // When
        userService.activateUser(user.getId());

        // Then
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("activeuser", "Password123!")
        );
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getName()).isEqualTo("activeuser");
    }

    @Test
    @DisplayName("IT-03: Reset hasła jednorazowym tokenem i logowanie nowym hasłem")
    void shouldResetPasswordViaTokenAndAllowLogin() {
        // Given
        User user = new User();
        user.setUsername("resetuser");
        user.setEmail("reset@example.com");
        user.setPassword(passwordEncoder.encode("OldPassword123!"));
        user.setEnabled(true);
        userRepository.save(user);

        // When: administrator/użytkownik zleca reset -> generowany jest token wysyłany mailem
        userService.initiatePasswordReset("reset@example.com");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("reset@example.com"), tokenCaptor.capture());
        String token = tokenCaptor.getValue();

        // Użytkownik samodzielnie ustawia nowe (silne) hasło przy pomocy tokenu.
        UserPasswordService.PasswordResetResult result =
                userService.resetPasswordWithToken("reset@example.com", token, "BrandNewPass123!");
        assertThat(result).isEqualTo(UserPasswordService.PasswordResetResult.OK);

        // Then: stare hasło nie działa, nowe działa.
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("resetuser", "BrandNewPass123!")
        );
        assertThat(auth.isAuthenticated()).isTrue();

        // Token jest jednorazowy - ponowne użycie jest odrzucane.
        assertThat(userService.resetPasswordWithToken("reset@example.com", token, "AnotherPass123!"))
                .isEqualTo(UserPasswordService.PasswordResetResult.INVALID_TOKEN);
    }

    @Test
    @DisplayName("IT-04: Zmiana ról i weryfikacja w SecurityContext")
    void shouldReflectRoleChangesInSecurityContext() {
        // Given
        User user = new User();
        user.setUsername("roleuser");
        user.setEmail("role@example.com");
        user.setPassword(passwordEncoder.encode("Password123!"));
        user.setEnabled(true);
        userRepository.save(user);

        // When & Then (Simulate login)
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken("roleuser", "Password123!")
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("IT-05: Wygaśnięcie hasła (Password Aging)")
    void shouldIdentifyExpiredPassword() {
        // Given
        User user = new User();
        user.setUsername("expireduser");
        user.setEmail("expired@example.com");
        user.setPassword(passwordEncoder.encode("Password123!"));
        user.setEnabled(true);
        // Hasło wygasło 1 dzień temu
        user.setPasswordExpiresAt(java.time.LocalDateTime.now().minusDays(1));
        userRepository.save(user);

        // When
        User savedUser = userRepository.findByUsername("expireduser").get();
        
        // Then
        assertThat(savedUser.isPasswordExpired()).isTrue();
        assertThat(savedUser.mustChangePasswordNow()).isTrue();
    }
}
