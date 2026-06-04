package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.service.UserService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ForcePasswordChangeController {

    private final UserService userService;
    private final ApplicationContext applicationContext;

    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;

    private User currentUser;

    public void initData(User user) {
        this.currentUser = user;
        log.info("Zainicjowano widok wymuszenia zmiany hasła dla ID: {}", user.getId());
    }

    @FXML
    public void handleChangePassword(ActionEvent event) {
        String newPassword = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (newPassword == null || newPassword.trim().isEmpty()) {
            errorLabel.setText("Hasło nie może być puste!");
            errorLabel.setVisible(true);
            return;
        }

        if (!newPassword.equals(confirm)) {
            errorLabel.setText("Hasła nie są identyczne!");
            errorLabel.setVisible(true);
            return;
        }

        // Pkt 7: Walidacja siły hasła
        String passwordError = LoginController.validatePasswordStrength(newPassword);
        if (passwordError != null) {
            errorLabel.setText(passwordError);
            errorLabel.setVisible(true);
            return;
        }

        try {
            // Zmiana hasła w serwisie (zdejmuje również flagę mustChangePassword)
            userService.changeUserPassword(currentUser.getId(), newPassword);
            log.info("Hasło pomyślnie zmienione dla usera ID: {}", currentUser.getId());

            // Załaduj główny ekran
            loadMainView();

        } catch (Exception e) {
            log.error("Błąd podczas zmiany hasła (historia/walidacja)", e);
            errorLabel.setText(e.getMessage());
            errorLabel.setVisible(true);
        }
    }

    private void loadMainView() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ui/main.fxml"));
            fxmlLoader.setControllerFactory(applicationContext::getBean);
            Parent root = fxmlLoader.load();
            
            Stage stage = (Stage) newPasswordField.getScene().getWindow();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Validation System Desktop - Main Menu");
            stage.centerOnScreen();
        } catch (IOException e) {
            log.error("Failed to load main.fxml", e);
        }
    }
}
