package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.*;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import com.mac.bry.desktop.security.service.UserService;
import com.mac.bry.desktop.service.EmailService;
import com.mac.bry.desktop.controller.helper.AdminUsersTableHelper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.rgielen.fxweaver.core.FxWeaver;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Kontroler zakładki "Zarządzanie Użytkownikami" w panelu administracyjnym.
 * Wydzielony z AdminPanelController w celu zgodności z zasadą SRP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUsersController {

    private final UserService userService;
    private final FxWeaver fxWeaver;
    private final ApplicationContext applicationContext;
    private final EmailService emailService;
    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;

    // ---- Tabela użytkowników ----
    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Long> idColumn;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, String> deptCol;
    @FXML private TableColumn<User, String> labCol;
    @FXML private TableColumn<User, Boolean> enabledColumn;
    @FXML private TableColumn<User, Boolean> lockedColumn;

    // ---- Panel szczegółów ----
    @FXML private VBox detailsPanel;
    @FXML private Label selectedUserLabel;
    @FXML private CheckBox enabledCheckbox;
    @FXML private CheckBox lockedCheckbox;
    @FXML private CheckBox mustChangePasswordCheckbox;
    @FXML private ComboBox<Department> deptComboBox;
    @FXML private ComboBox<Laboratory> labComboBox;
    @FXML private VBox rolesContainer;
    @FXML private Label statusLabel;

    // ---- Filtry ----
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterCombo;

    private User currentUser;
    private User selectedUser;
    private List<Role> allSystemRoles;
    private final ObservableList<User> masterData = FXCollections.observableArrayList();
    private FilteredList<User> filteredData;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilters();
        setupOrganizationComboBoxes();

        allSystemRoles = userService.getAllRoles();
        currentUser = getCurrentUser();
        deptComboBox.setItems(FXCollections.observableArrayList(departmentRepository.findAll()));

        usersTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        usersTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, nv) -> showUserDetails(nv));

        refreshUsersTable();
    }

    private void setupTableColumns() {
        AdminUsersTableHelper.setupTableColumns(
                idColumn, usernameColumn, emailColumn, deptCol, labCol, enabledColumn, lockedColumn
        );
    }

    private void setupFilters() {
        statusFilterCombo.setItems(FXCollections.observableArrayList("Wszyscy", "Aktywni", "Nieaktywni", "Zablokowani"));
        statusFilterCombo.getSelectionModel().selectFirst();

        filteredData = new FilteredList<>(masterData, p -> true);
        AdminUsersTableHelper.setupFilters(searchField, statusFilterCombo, filteredData);

        SortedList<User> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(usersTable.comparatorProperty());
        usersTable.setItems(sortedData);
    }

    private void setupOrganizationComboBoxes() {
        AdminUsersTableHelper.setupOrganizationComboBoxes(deptComboBox, labComboBox, laboratoryRepository::findByDepartmentId);
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return (principal instanceof User u) ? u : null;
    }

    private void refreshUsersTable() {
        List<User> users;
        if (currentUser != null
                && userService.hasRole(currentUser, "ROLE_DEPT_ADMIN")
                && !userService.hasRole(currentUser, "ROLE_SUPER_ADMIN")) {
            users = currentUser.getDepartment() != null
                    ? userService.getAllUsersByDepartment(currentUser.getDepartment().getId())
                    : java.util.Collections.emptyList();
        } else {
            users = userService.getAllUsers();
        }
        masterData.setAll(users);
    }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        statusFilterCombo.getSelectionModel().selectFirst();
    }

    private void showUserDetails(User user) {
        this.selectedUser = user;
        statusLabel.setVisible(false);
        if (user != null) {
            detailsPanel.setVisible(true);
            detailsPanel.setManaged(true);
            selectedUserLabel.setText("Użytkownik: " + user.getUsername() + " (ID: " + user.getId() + ")");
            enabledCheckbox.setSelected(user.isEnabled());
            lockedCheckbox.setSelected(user.isLocked());
            mustChangePasswordCheckbox.setSelected(user.isMustChangePassword());
            deptComboBox.getSelectionModel().select(user.getDepartment());
            labComboBox.getSelectionModel().select(user.getLaboratory());
            rolesContainer.getChildren().clear();
            for (Role role : allSystemRoles) {
                CheckBox cb = new CheckBox(role.getName());
                cb.setUserData(role);
                if ("ROLE_SUPER_ADMIN".equals(role.getName())
                        && !userService.hasRole(currentUser, "ROLE_SUPER_ADMIN")) {
                    cb.setDisable(true);
                }
                cb.setSelected(user.getRoles().stream().anyMatch(r -> r.getId().equals(role.getId())));
                rolesContainer.getChildren().add(cb);
            }
        } else {
            detailsPanel.setVisible(false);
            detailsPanel.setManaged(false);
        }
    }

    // ---- Akcje ----

    @FXML
    public void handleSaveUser(ActionEvent event) {
        if (selectedUser == null) return;
        try {
            Long id = selectedUser.getId();
            if (enabledCheckbox.isSelected() && !selectedUser.isEnabled()) userService.activateUser(id);
            else if (!enabledCheckbox.isSelected() && selectedUser.isEnabled()) userService.deactivateUser(id);

            if (lockedCheckbox.isSelected() && !selectedUser.isLocked()) userService.lockUser(id);
            else if (!lockedCheckbox.isSelected() && selectedUser.isLocked()) userService.unlockUser(id);

            if (mustChangePasswordCheckbox.isSelected() != selectedUser.isMustChangePassword())
                userService.setMustChangePassword(id, mustChangePasswordCheckbox.isSelected());

            userService.updateUserLocation(id, deptComboBox.getValue(), labComboBox.getValue());

            Set<Role> newRoles = new HashSet<>();
            for (javafx.scene.Node node : rolesContainer.getChildren()) {
                if (node instanceof CheckBox cb && cb.isSelected()) newRoles.add((Role) cb.getUserData());
            }
            userService.updateUserRoles(id, newRoles);

            statusLabel.setText("Zmiany zapisane pomyślnie.");
            statusLabel.setStyle("-fx-text-fill: green;");
            statusLabel.setVisible(true);
            int idx = usersTable.getSelectionModel().getSelectedIndex();
            refreshUsersTable();
            usersTable.getSelectionModel().select(idx);
        } catch (Exception e) {
            log.error("Błąd zapisu użytkownika", e);
            statusLabel.setText("Błąd zapisu: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setVisible(true);
        }
    }

    @FXML
    public void handleResetPassword(ActionEvent event) {
        if (selectedUser == null) return;
        if (confirm("Zresetować hasło dla " + selectedUser.getUsername() + "?\nNowe hasło zostanie wysłane e-mailem.")) {
            userService.resetPassword(selectedUser.getEmail());
            info("Sukces", "Hasło zresetowane i wysłane na: " + selectedUser.getEmail());
        }
    }

    @FXML
    public void handleShowAudit(ActionEvent event) {
        if (selectedUser == null) return;
        openAudit(ctrl -> ctrl.initData(selectedUser));
    }

    @FXML
    private void handleBulkUnlock() {
        List<User> sel = usersTable.getSelectionModel().getSelectedItems();
        if (!sel.isEmpty() && confirm("Odblokować " + sel.size() + " użytkowników?")) {
            sel.forEach(u -> userService.unlockUser(u.getId()));
            refreshUsersTable();
        }
    }

    @FXML
    private void handleBulkLock() {
        List<User> sel = usersTable.getSelectionModel().getSelectedItems();
        if (!sel.isEmpty() && confirm("Zablokować " + sel.size() + " użytkowników?")) {
            sel.forEach(u -> userService.lockUser(u.getId()));
            refreshUsersTable();
        }
    }

    @FXML
    private void handleBulkNotify() {
        List<User> sel = usersTable.getSelectionModel().getSelectedItems();
        if (sel.isEmpty()) return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Powiadomienie Masowe");
        dialog.setHeaderText("Wyślij wiadomość do " + sel.size() + " użytkowników");
        dialog.setContentText("Treść wiadomości:");
        dialog.showAndWait().ifPresent(msg -> {
            List<String> recipients = sel.stream().map(User::getEmail).toList();
            emailService.sendMassEmail(recipients, "Powiadomienie Systemowe - Validation System", msg);
            info("Sukces", "Wysłano do " + recipients.size() + " osób.");
        });
    }

    @FXML
    private void handleCreateUser(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/user_dialog.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            UserDialogController ctrl = loader.getController();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Nowy Użytkownik");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            if (ctrl.isSaved()) refreshUsersTable();
        } catch (IOException e) {
            log.error("Błąd podczas otwierania dialogu użytkownika", e);
        }
    }

    // ---- Helpers ----

    private void openAudit(Consumer<UserAuditController> init) {
        net.rgielen.fxweaver.core.FxControllerAndView<UserAuditController, VBox> cav =
                fxWeaver.load(UserAuditController.class, com.mac.bry.desktop.config.I18n.getBundle());
        init.accept(cav.getController());
        Stage stage = new Stage();
        stage.setTitle("Historia Audytu");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(cav.getView().get()));
        stage.showAndWait();
    }

    private boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Potwierdzenie"); a.setHeaderText(null); a.setContentText(msg);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}
