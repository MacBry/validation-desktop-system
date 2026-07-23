package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.security.service.UserService;
import com.mac.bry.desktop.service.EmailService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.scene.control.Button;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationContext applicationContext;
    private final com.mac.bry.desktop.security.service.AuditService auditService;

    // Login Tab
    @FXML private TextField loginUsernameField;
    @FXML private PasswordField loginPasswordField;
    @FXML private Label loginErrorLabel;
    @FXML private Button loginButton;

    // Mechanizm progresywnego cooldown (ochrona przed brute-force)
    private int localFailedAttempts = 0;
    private static final int[] COOLDOWN_SECONDS = {3, 6, 10, 15, 30};
    private Timeline cooldownTimeline;

    // Register Tab
    @FXML private TextField regUsernameField;
    @FXML private TextField regEmailField;
    @FXML private TextField regFirstNameField;
    @FXML private TextField regLastNameField;
    @FXML private PasswordField regPasswordField;
    @FXML private PasswordField regPasswordConfirmField;
    @FXML private Label registerMessageLabel;

    // Reset Tab
    @FXML private TextField resetEmailField;
    @FXML private Label resetMessageLabel;

    // Przełącznik języka UI
    @FXML private javafx.scene.control.ComboBox<String> languageCombo;

    @FXML
    public void initialize() {
        log.info("LoginController initialized");
        com.mac.bry.desktop.config.LanguageSwitcher.configure(languageCombo, this::reloadLoginView);
    }

    /** Przeładowanie ekranu logowania po zmianie języka. */
    private void reloadLoginView() {
        try {
            Stage stage = (Stage) languageCombo.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ui/login.fxml"),
                    com.mac.bry.desktop.config.I18n.getBundle());
            fxmlLoader.setControllerFactory(applicationContext::getBean);
            Parent root = fxmlLoader.load();
            stage.getScene().setRoot(root);
        } catch (IOException e) {
            log.error("Failed to reload login view after language switch", e);
        }
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String username = loginUsernameField.getText();
        String password = loginPasswordField.getText();

        try {
            // Sprawdzenie czy konto nie wygasło (90 dni bezczynności)
            userService.checkAccountExpiration(username);

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // Pkt 3: Blokada jednoczesnych logowań - po potwierdzeniu tożsamości,
            // aby nie ujawniać stanu sesji przed weryfikacją hasła.
            if (userService.isAlreadyLoggedIn(username)) {
                SecurityContextHolder.clearContext();
                loginErrorLabel.setText("Użytkownik jest już zalogowany na innym stanowisku.");
                loginErrorLabel.setVisible(true);
                auditService.logAccessEvent(username, "LOGIN_BLOCKED", "Aktywna sesja na innym stanowisku");
                return;
            }

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("Zalogowano pomyślnie: {}", username);
            auditService.logAccessEvent(username, "LOGIN_SUCCESS", "Udana autentykacja");

            User user = userRepository.findByUsername(username).orElse(null);

            if (user != null) {
                // Pkt 4: Reset licznika nieudanych prób po pomyślnym logowaniu
                userService.resetFailedLoginAttempts(user.getId());
                // Rejestracja nowej sesji w bazie + utrwalenie tokenu na principalu,
                // aby monitor bezczynności mógł walidować sesję (wykrycie force-logout / przejęcia).
                String sessionToken = userService.registerSession(user.getId());
                if (authentication.getPrincipal() instanceof User principal) {
                    principal.setSessionToken(sessionToken);
                }
            }
            
            if (user != null && user.mustChangePasswordNow()) {
                log.info("Wymuszona zmiana hasła (flaga lub wygaśnięcie) dla użytkownika: {}", username);
                loadForcePasswordChangeView(user);
            } else {
                // Switch to Main View
                loadMainView();
            }

        } catch (BadCredentialsException e) {
            // Pkt 4: Inkrementacja nieudanych prób logowania
            userService.incrementFailedLoginAttempts(username);
            localFailedAttempts++;
            
            // Pobierz bieżący licznik prób z bazy
            int remainingAttempts = getRemainingAttempts(username);
            
            String msg = "Nieprawidłowy login lub hasło.";
            if (remainingAttempts > 0 && remainingAttempts <= 3) {
                msg += " Pozostało prób: " + remainingAttempts;
            }
            loginErrorLabel.setText(msg);
            loginErrorLabel.setVisible(true);
            auditService.logAccessEvent(username, "LOGIN_FAILED", "Błędne hasło. Pozostało prób: " + remainingAttempts);
            
            // Uruchom progresywny cooldown przycisku
            startCooldown();
            
        } catch (org.springframework.security.authentication.LockedException e) {
            loginErrorLabel.setText("Konto zostało tymczasowo zablokowane po zbyt wielu nieudanych próbach logowania.");
            loginErrorLabel.setVisible(true);
            auditService.logAccessEvent(username, "SECURITY_ALARM", "Konto zablokowane (brute-force protection)");
            startCooldown();
        } catch (DisabledException e) {
            loginErrorLabel.setText("Konto nieaktywne! Oczekuje na akceptację administratora.");
            loginErrorLabel.setVisible(true);
            auditService.logAccessEvent(username, "LOGIN_FAILED", "Próba logowania na nieaktywne konto");
            startCooldown();
        } catch (Exception e) {
            log.error("Login error", e);
            loginErrorLabel.setText("Błąd logowania: " + e.getMessage());
            loginErrorLabel.setVisible(true);
            auditService.logAccessEvent(username, "LOGIN_FAILED", "Błąd systemowy: " + e.getMessage());
            startCooldown();
        }
    }

    private int getRemainingAttempts(String username) {
        return userRepository.findByUsername(username)
                .map(u -> 5 - u.getFailedLoginAttempts())
                .orElse(5);
    }

    private void startCooldown() {
        int index = Math.min(localFailedAttempts - 1, COOLDOWN_SECONDS.length - 1);
        int seconds = COOLDOWN_SECONDS[index];
        
        loginButton.setDisable(true);
        loginButton.setText("Zaczekaj " + seconds + "s...");
        loginButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
        
        final int[] remaining = {seconds};
        
        if (cooldownTimeline != null) {
            cooldownTimeline.stop();
        }
        
        cooldownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remaining[0]--;
            if (remaining[0] > 0) {
                loginButton.setText("Zaczekaj " + remaining[0] + "s...");
            } else {
                loginButton.setDisable(false);
                loginButton.setText("Zaloguj się");
                loginButton.setStyle("-fx-background-color: #0f172a; -fx-text-fill: white;");
                cooldownTimeline.stop();
            }
        }));
        cooldownTimeline.setCycleCount(seconds);
        cooldownTimeline.play();
    }

    @FXML
    public void handleRegister(ActionEvent event) {
        String username = regUsernameField.getText();
        String email = regEmailField.getText();
        String password = regPasswordField.getText();
        String confirm = regPasswordConfirmField.getText();

        // Pkt 1: Walidacja pustych pól
        if (username == null || username.trim().isEmpty()) {
            registerMessageLabel.setText("Login jest wymagany!");
            registerMessageLabel.setStyle("-fx-text-fill: red;");
            registerMessageLabel.setVisible(true);
            return;
        }
        if (email == null || email.trim().isEmpty()) {
            registerMessageLabel.setText("Adres e-mail jest wymagany!");
            registerMessageLabel.setStyle("-fx-text-fill: red;");
            registerMessageLabel.setVisible(true);
            return;
        }
        if (password == null || password.trim().isEmpty()) {
            registerMessageLabel.setText("Hasło jest wymagane!");
            registerMessageLabel.setStyle("-fx-text-fill: red;");
            registerMessageLabel.setVisible(true);
            return;
        }

        if (!password.equals(confirm)) {
            registerMessageLabel.setText("Hasła nie są identyczne!");
            registerMessageLabel.setStyle("-fx-text-fill: red;");
            registerMessageLabel.setVisible(true);
            return;
        }

        // Pkt 7: Walidacja siły hasła
        String passwordError = validatePasswordStrength(password);
        if (passwordError != null) {
            registerMessageLabel.setText(passwordError);
            registerMessageLabel.setStyle("-fx-text-fill: red;");
            registerMessageLabel.setVisible(true);
            return;
        }

        if (userRepository.findByUsername(username).isPresent()) {
            registerMessageLabel.setText("Użytkownik o podanym loginie już istnieje!");
            registerMessageLabel.setStyle("-fx-text-fill: red;");
            registerMessageLabel.setVisible(true);
            return;
        }

        if (userRepository.findByEmail(email).isPresent()) {
            registerMessageLabel.setText("Podany adres e-mail jest już zarejestrowany w systemie!");
            registerMessageLabel.setStyle("-fx-text-fill: red;");
            registerMessageLabel.setVisible(true);
            return;
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(regFirstNameField.getText());
        user.setLastName(regLastNameField.getText());
        user.setPassword(passwordEncoder.encode(password));
        
        // Opcja A - Rejestracja wymaga akceptacji QA / Administratora
        user.setEnabled(false);

        userRepository.save(user);

        // Wyślij powiadomienie do adminów
        java.util.List<String> adminEmails = userService.getSuperAdminEmails();
        emailService.sendNewUserAdminNotification(adminEmails, user.getEmail(), user.getFullName());

        registerMessageLabel.setText("Konto utworzone pomyślnie. Oczekuje na akceptację Administratora!");
        registerMessageLabel.setStyle("-fx-text-fill: green;");
        registerMessageLabel.setVisible(true);
        
        regUsernameField.clear();
        regEmailField.clear();
        regPasswordField.clear();
        regPasswordConfirmField.clear();
    }

    @FXML
    public void handlePasswordReset(ActionEvent event) {
        String email = resetEmailField.getText();
        
        if (email == null || email.trim().isEmpty()) {
            resetMessageLabel.setText("Podaj adres e-mail!");
            resetMessageLabel.setStyle("-fx-text-fill: red;");
            resetMessageLabel.setVisible(true);
            return;
        }

        boolean resetSuccessful = userService.resetPassword(email);
        
        // Ze względów bezpieczeństwa (enumeracja kont), przeważnie wyświetla się 
        // ten sam komunikat niezależnie czy e-mail istnieje czy nie. 
        // Dla wygody użytkownika możemy to ewentualnie rozdzielić, ale zostawiamy bezpieczniejszą opcję.
        resetMessageLabel.setText("Jeśli email istnieje w bazie, wysłano instrukcje resetu na podany adres.");
        resetMessageLabel.setStyle("-fx-text-fill: green;");
        resetMessageLabel.setVisible(true);
        resetEmailField.clear();
    }

    // Pkt 7: Wspólna metoda walidacji siły hasła
    public static String validatePasswordStrength(String password) {
        if (password.length() < 8) {
            return "Hasło musi mieć minimum 8 znaków.";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Hasło musi zawierać co najmniej jedną wielką literę.";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Hasło musi zawierać co najmniej jedną małą literę.";
        }
        if (!password.matches(".*\\d.*")) {
            return "Hasło musi zawierać co najmniej jedną cyfrę.";
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':.,].*")) {
            return "Hasło musi zawierać co najmniej jeden znak specjalny (!@#$%^&* itp.).";
        }
        return null; // hasło poprawne
    }

    private void loadMainView() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ui/main.fxml"),
                    com.mac.bry.desktop.config.I18n.getBundle());
            fxmlLoader.setControllerFactory(applicationContext::getBean);
            Parent root = fxmlLoader.load();
            
            Stage stage = (Stage) loginUsernameField.getScene().getWindow();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Validation System Desktop - Main Menu");
            stage.centerOnScreen();
        } catch (IOException e) {
            log.error("Failed to load main.fxml", e);
        }
    }

    private void loadForcePasswordChangeView(User user) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ui/force_password_change.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            fxmlLoader.setControllerFactory(applicationContext::getBean);
            Parent root = fxmlLoader.load();
            
            ForcePasswordChangeController controller = fxmlLoader.getController();
            controller.initData(user);
            
            Stage stage = (Stage) loginUsernameField.getScene().getWindow();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Validation System Desktop - Wymuszona zmiana hasła");
            stage.centerOnScreen();
        } catch (IOException e) {
            log.error("Failed to load force_password_change.fxml", e);
            loginErrorLabel.setText("Błąd wczytywania widoku zmiany hasła.");
            loginErrorLabel.setVisible(true);
        }
    }
}
