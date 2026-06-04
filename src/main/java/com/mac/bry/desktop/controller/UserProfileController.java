package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.security.service.UserService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserService userService;
    private final UserRepository userRepository;
    
    // Pola profilu
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private Label profileMessageLabel;

    // Pola lewej karty (avatar & info)
    @FXML private Label avatarInitialsLabel;
    @FXML private Label fullNameLabel;
    @FXML private Label emailLabel;
    @FXML private Label roleBadgeLabel;

    // Pola hasła
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmNewPasswordField;
    @FXML private Label passwordMessageLabel;

    // Pola wskaźnika siły hasła
    @FXML private ProgressBar passwordStrengthBar;
    @FXML private Label passwordStrengthLabel;

    private User currentUser;

    @FXML
    public void initialize() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User) {
            // Zawsze pobieramy najświeższą encję z bazy zamiast ufać sesji
            User sessionUser = (User) auth.getPrincipal();
            this.currentUser = userRepository.findById(sessionUser.getId()).orElse(null);
            
            if (this.currentUser != null) {
                firstNameField.setText(currentUser.getFirstName());
                lastNameField.setText(currentUser.getLastName());
                emailField.setText(currentUser.getEmail());
                phoneField.setText(currentUser.getPhone());

                // Aktualizacja lewej karty profilu
                String fName = currentUser.getFirstName() != null ? currentUser.getFirstName() : "";
                String lName = currentUser.getLastName() != null ? currentUser.getLastName() : "";
                fullNameLabel.setText(fName + " " + lName);
                emailLabel.setText(currentUser.getEmail());

                // Wyliczenie inicjałów dla avatara
                String initials = "";
                if (!fName.isEmpty()) initials += fName.substring(0, 1).toUpperCase();
                if (!lName.isEmpty()) initials += lName.substring(0, 1).toUpperCase();
                avatarInitialsLabel.setText(initials.isEmpty() ? "U" : initials);

                // Rola systemowa
                if (currentUser.getRoles() != null && !currentUser.getRoles().isEmpty()) {
                    String roleName = currentUser.getRoles().iterator().next().getName().replace("ROLE_", "");
                    roleBadgeLabel.setText(roleName);
                } else {
                    roleBadgeLabel.setText("USER");
                }
            }
        }

        // Reaktywny nasłuchiwacz siły hasła w czasie rzeczywistym
        newPasswordField.textProperty().addListener((obs, old, val) -> updatePasswordStrengthBar(val));
    }

    private void updatePasswordStrengthBar(String password) {
        if (password == null || password.isEmpty()) {
            passwordStrengthBar.setProgress(0);
            passwordStrengthBar.getStyleClass().removeAll("danger", "warning", "success");
            passwordStrengthLabel.setText("");
            return;
        }

        int score = 0;
        if (password.length() >= 8) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':.,].*")) score++;

        double progress = score / 5.0;
        passwordStrengthBar.setProgress(progress);

        passwordStrengthBar.getStyleClass().removeAll("danger", "warning", "success");
        if (score <= 2) {
            passwordStrengthBar.getStyleClass().add("danger");
            passwordStrengthLabel.setText("Słabe");
            passwordStrengthLabel.setStyle("-fx-text-fill: -color-danger-emphasis;");
        } else if (score <= 4) {
            passwordStrengthBar.getStyleClass().add("warning");
            passwordStrengthLabel.setText("Średnie");
            passwordStrengthLabel.setStyle("-fx-text-fill: -color-warning-emphasis;");
        } else {
            passwordStrengthBar.getStyleClass().add("success");
            passwordStrengthLabel.setText("Silne");
            passwordStrengthLabel.setStyle("-fx-text-fill: -color-success-emphasis;");
        }
    }

    @FXML
    public void handleSaveProfile(ActionEvent event) {
        if (currentUser == null) return;
        
        String firstName = firstNameField.getText();
        String lastName = lastNameField.getText();
        String email = emailField.getText();
        String phone = phoneField.getText();
        
        if (email == null || email.trim().isEmpty()) {
            profileMessageLabel.setText("Adres e-mail jest wymagany!");
            profileMessageLabel.setStyle("-fx-text-fill: red;");
            profileMessageLabel.setVisible(true);
            return;
        }

        try {
            userService.updateUserProfile(currentUser.getId(), firstName, lastName, email, phone);
            profileMessageLabel.setText("Profil został pomyślnie zaktualizowany.");
            profileMessageLabel.setStyle("-fx-text-fill: green;");
            profileMessageLabel.setVisible(true);
            
            // Reaktywne odświeżenie lewej karty profilu w czasie rzeczywistym
            fullNameLabel.setText(firstName + " " + lastName);
            emailLabel.setText(email);
            String initials = "";
            if (!firstName.isEmpty()) initials += firstName.substring(0, 1).toUpperCase();
            if (!lastName.isEmpty()) initials += lastName.substring(0, 1).toUpperCase();
            avatarInitialsLabel.setText(initials.isEmpty() ? "U" : initials);
        } catch (Exception e) {
            log.error("Błąd podczas aktualizacji profilu", e);
            profileMessageLabel.setText("Błąd zapisu. Adres e-mail może być już zajęty.");
            profileMessageLabel.setStyle("-fx-text-fill: red;");
            profileMessageLabel.setVisible(true);
        }
    }

    @FXML
    public void handleChangePassword(ActionEvent event) {
        if (currentUser == null) return;
        
        String oldPass = oldPasswordField.getText();
        String newPass = newPasswordField.getText();
        String confirmPass = confirmNewPasswordField.getText();
        
        if (oldPass == null || oldPass.trim().isEmpty() || newPass == null || newPass.trim().isEmpty()) {
            passwordMessageLabel.setText("Wypełnij wszystkie pola hasła.");
            passwordMessageLabel.setStyle("-fx-text-fill: red;");
            passwordMessageLabel.setVisible(true);
            return;
        }
        
        if (!newPass.equals(confirmPass)) {
            passwordMessageLabel.setText("Nowe hasła nie są identyczne.");
            passwordMessageLabel.setStyle("-fx-text-fill: red;");
            passwordMessageLabel.setVisible(true);
            return;
        }
        
        // Walidacja siły hasła
        String passwordError = LoginController.validatePasswordStrength(newPass);
        if (passwordError != null) {
            passwordMessageLabel.setText(passwordError);
            passwordMessageLabel.setStyle("-fx-text-fill: red;");
            passwordMessageLabel.setVisible(true);
            return;
        }
        
        try {
            boolean success = userService.changePasswordWithOld(currentUser.getId(), oldPass, newPass);
            
            if (success) {
                passwordMessageLabel.setText("Hasło zostało pomyślnie zmienione!");
                passwordMessageLabel.setStyle("-fx-text-fill: green;");
                passwordMessageLabel.setVisible(true);
                
                oldPasswordField.clear();
                newPasswordField.clear();
                confirmNewPasswordField.clear();
                passwordStrengthBar.setProgress(0);
                passwordStrengthLabel.setText("");
            } else {
                passwordMessageLabel.setText("Obecne hasło jest nieprawidłowe.");
                passwordMessageLabel.setStyle("-fx-text-fill: red;");
                passwordMessageLabel.setVisible(true);
            }
        } catch (Exception e) {
            log.error("Błąd podczas zmiany hasła (historia/walidacja)", e);
            passwordMessageLabel.setText(e.getMessage());
            passwordMessageLabel.setStyle("-fx-text-fill: red;");
            passwordMessageLabel.setVisible(true);
        }
    }
}
