package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.model.Role;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import com.mac.bry.desktop.security.repository.UserRepository;
import com.mac.bry.desktop.security.service.UserService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class UserDialogController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final PasswordEncoder passwordEncoder;

    @FXML private Label titleLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField emailField;
    @FXML private CheckBox enabledCheckbox;
    @FXML private CheckBox mustChangePasswordCheckbox;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private ComboBox<Department> deptComboBox;
    @FXML private ComboBox<Laboratory> labComboBox;
    @FXML private VBox rolesContainer;
    @FXML private Label errorLabel;

    @Getter
    private boolean saved = false;

    @FXML
    public void initialize() {
        // Ładowanie działów i reaktywne pracownie
        deptComboBox.setItems(FXCollections.observableArrayList(departmentRepository.findAll()));
        labComboBox.setDisable(true);
        
        deptComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                labComboBox.setItems(FXCollections.observableArrayList(laboratoryRepository.findByDepartmentId(newVal.getId())));
                labComboBox.setDisable(false);
            } else {
                labComboBox.getItems().clear();
                labComboBox.setDisable(true);
            }
        });

        // Dynamiczne generowanie ról
        rolesContainer.getChildren().clear();
        for (Role role : userService.getAllRoles()) {
            CheckBox cb = new CheckBox(role.getName());
            cb.setUserData(role);
            if ("ROLE_USER".equals(role.getName())) {
                cb.setSelected(true); // domyślnie zaznaczamy zwykłego użytkownika
            }
            rolesContainer.getChildren().add(cb);
        }
    }

    @FXML
    private void handleSave() {
        errorLabel.setVisible(false);

        String username = usernameField.getText() != null ? usernameField.getText().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText().trim() : "";
        String email = emailField.getText() != null ? emailField.getText().trim() : "";
        String firstName = firstNameField.getText() != null ? firstNameField.getText().trim() : "";
        String lastName = lastNameField.getText() != null ? lastNameField.getText().trim() : "";

        // 1. Walidacja
        if (username.isEmpty()) {
            showError("Login jest wymagany!");
            return;
        }
        if (password.isEmpty()) {
            showError("Hasło startowe jest wymagane!");
            return;
        }
        if (email.isEmpty()) {
            showError("Adres e-mail jest wymagany!");
            return;
        }

        // Siła hasła
        String pwdErr = LoginController.validatePasswordStrength(password);
        if (pwdErr != null) {
            showError(pwdErr);
            return;
        }

        // Czy login lub email już istnieje
        if (userRepository.findByUsername(username).isPresent()) {
            showError("Użytkownik o podanym loginie już istnieje!");
            return;
        }
        if (userRepository.findByEmail(email).isPresent()) {
            showError("Adres e-mail jest już zajęty!");
            return;
        }

        try {
            // 2. Budowanie encji użytkownika
            User newUser = new User();
            newUser.setUsername(username);
            newUser.setPassword(passwordEncoder.encode(password));
            newUser.setEmail(email);
            newUser.setEnabled(enabledCheckbox.isSelected());
            newUser.setMustChangePassword(mustChangePasswordCheckbox.isSelected());
            newUser.setFirstName(firstName);
            newUser.setLastName(lastName);
            newUser.setDepartment(deptComboBox.getSelectionModel().getSelectedItem());
            newUser.setLaboratory(labComboBox.getSelectionModel().getSelectedItem());

            // Role
            Set<Role> roles = new HashSet<>();
            for (javafx.scene.Node node : rolesContainer.getChildren()) {
                if (node instanceof CheckBox) {
                    CheckBox cb = (CheckBox) node;
                    if (cb.isSelected()) {
                        roles.add((Role) cb.getUserData());
                    }
                }
            }
            newUser.setRoles(roles);

            // Zapis do bazy
            userRepository.save(newUser);
            saved = true;
            
            closeStage();
        } catch (Exception e) {
            log.error("Błąd podczas tworzenia nowego użytkownika", e);
            showError("Błąd zapisu: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        saved = false;
        closeStage();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    private void closeStage() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }
}
