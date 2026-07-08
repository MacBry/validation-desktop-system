package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.security.ui.InactivityMonitor;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Interpolator;
import javafx.util.Duration;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class MainController {

    private final ApplicationContext applicationContext;
    private final InactivityMonitor inactivityMonitor;
    private final com.mac.bry.desktop.security.service.AuditService auditService;
    private final com.mac.bry.desktop.security.service.UserService userService;

    @FXML private StackPane contentArea;
    @FXML private Label userInfoLabel;
    @FXML private Label adminMenuLabel;
    @FXML private Button adminUsersButton;
    @FXML private Button adminStructureButton;
    @FXML private Button adminLogsButton;
    @FXML private Button adminMaterialsButton;
    @FXML private Label sidebarUserLabel;
    @FXML private Label sidebarRoleLabel;
    @FXML private javafx.scene.control.ComboBox<String> languageCombo;

    @FXML private VBox sectionMenuGlowne;
    @FXML private VBox sectionMenuGlowneItems;
    @FXML private VBox sectionObslugaTesto;
    @FXML private VBox sectionObslugaTestoItems;
    @FXML private VBox sectionProceduryGxp;
    @FXML private VBox sectionProceduryGxpItems;
    @FXML private VBox sectionAdministracja;
    @FXML private VBox sectionAdministracjaItems;

    // Przechowujemy oryginalny domyślny widok powitalny
    private Parent defaultDashboardView;

    @FXML
    public void initialize() {
        // Inicjalizacja monitora bezczynności po załadowaniu sceny
        Platform.runLater(() -> {
            Scene scene = sidebarUserLabel.getScene();
            if (scene != null) {
                inactivityMonitor.startMonitoring(scene);
            }
        });

        // Zapisujemy domyślną treść wygenerowaną w FXML
        if (contentArea.getChildren().size() > 0) {
            defaultDashboardView = (Parent) contentArea.getChildren().get(0);
        }

        // Przełącznik języka — zmiana przeładowuje główne okno (powrót do dashboardu)
        com.mac.bry.desktop.config.LanguageSwitcher.configure(languageCombo, this::reloadMainView);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            userInfoLabel.setText(I18n.t("main.user.loggedInAs", auth.getName()));

            if (auth.getPrincipal() instanceof com.mac.bry.desktop.security.model.User) {
                com.mac.bry.desktop.security.model.User user = (com.mac.bry.desktop.security.model.User) auth.getPrincipal();
                sidebarUserLabel.setText(user.getFullName() + "\n(" + user.getEmail() + ")");
            } else {
                sidebarUserLabel.setText(I18n.t("main.user.loggedInNamed", auth.getName()));
            }

            String roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(r -> r.replace("ROLE_", ""))
                    .collect(java.util.stream.Collectors.joining(", "));
            if (roles.isEmpty()) roles = "–";
            sidebarRoleLabel.setText(I18n.t("main.user.roles", roles));

            // Sprawdzenie uprawnień
            boolean isAdmin = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(role -> role.equals("ROLE_SUPER_ADMIN") || role.equals("ROLE_DEPT_ADMIN"));

            boolean isSuperAdmin = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(role -> role.equals("ROLE_SUPER_ADMIN"));

            if (isAdmin) {
                sectionAdministracja.setVisible(true);
                sectionAdministracja.setManaged(true);
                adminMenuLabel.setVisible(true);
                adminMenuLabel.setManaged(true);
                
                adminUsersButton.setVisible(true);
                adminUsersButton.setManaged(true);
                
                adminLogsButton.setVisible(true);
                adminLogsButton.setManaged(true);

                adminMaterialsButton.setVisible(true);
                adminMaterialsButton.setManaged(true);
                
                if (isSuperAdmin) {
                    adminStructureButton.setVisible(true);
                    adminStructureButton.setManaged(true);
                }
            }
        }

        // Rejestracja animacji najechania dla menu bocznego
        registerHoverAnimation(sectionMenuGlowne, sectionMenuGlowneItems);
        registerHoverAnimation(sectionObslugaTesto, sectionObslugaTestoItems);
        registerHoverAnimation(sectionProceduryGxp, sectionProceduryGxpItems);
        registerHoverAnimation(sectionAdministracja, sectionAdministracjaItems);
        
        Platform.runLater(() -> showDashboard(null));
    }

    @FXML
    public void showDashboard(ActionEvent event) {
        contentArea.getChildren().clear();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/dashboard.fxml"), I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent dashboardView = loader.load();
            contentArea.getChildren().add(dashboardView);
        } catch (IOException e) {
            log.error("Nie udało się załadować dynamicznego widoku Dashboard", e);
            if (defaultDashboardView != null) {
                contentArea.getChildren().add(defaultDashboardView);
            }
        }
    }

    @FXML
    public void showUserProfile(ActionEvent event) {
        loadView("/ui/user_profile.fxml");
    }

    @FXML
    public void showThermoRecorders(ActionEvent event) {
        loadView("/ui/thermo_recorders.fxml");
    }

    @FXML
    public void showThermoRecorderModels(ActionEvent event) {
        loadView("/ui/thermo_recorder_model_manager.fxml");
    }

    @FXML
    public void showCoolingDevices(ActionEvent event) {
        loadView("/ui/cooling_devices.fxml");
    }

    @FXML
    public void showTestoProgramming(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/testo_programming_dialog.fxml"), I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent view = loader.load();
            
            Stage stage = new Stage();
            stage.setTitle("Programowanie Rejestratora Testo 174T");
            stage.setScene(new Scene(view));
            stage.setResizable(false);
            stage.initOwner(contentArea.getScene().getWindow());
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.show();
        } catch (IOException e) {
            log.error("Failed to load Testo programming dialog", e);
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(I18n.t("common.error.viewLoad.title"));
            alert.setHeaderText("Nie udało się załadować okna programowania.");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    public void showTestoRead(ActionEvent event) {
        loadView("/ui/testo_read.fxml");
    }

    @FXML
    public void showTestoReadHistory(ActionEvent event) {
        loadView("/ui/testo_read_history.fxml");
    }

    @FXML
    public void showTestoProgrammingHistory(ActionEvent event) {
        loadView("/ui/testo_programming_history.fxml");
    }

    @FXML
    public void showTestoRevalidation(ActionEvent event) {
        loadRevalidationView(null);
    }

    @FXML
    public void showPeriodicRevalidation(ActionEvent event) {
        loadRevalidationView(com.mac.bry.desktop.model.GxPProcedureType.PERIODIC_REVALIDATION);
    }

    @FXML
    public void showChamberMapping(ActionEvent event) {
        loadRevalidationView(com.mac.bry.desktop.model.GxPProcedureType.MAPPING);
    }

    private void loadRevalidationView(com.mac.bry.desktop.model.GxPProcedureType procedureType) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/testo_revalidation.fxml"), I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent view = loader.load();

            if (procedureType != null) {
                TestoRevalidationController ctrl = loader.getController();
                ctrl.initWithProcedureType(procedureType);
            }

            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            log.error("Failed to load revalidation view with procedure type: {}", procedureType, e);
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(I18n.t("common.error.viewLoad.title"));
            alert.setHeaderText("Nie udało się załadować kreatora rewalidacji.");
            alert.setContentText(e.getMessage() != null ? e.getMessage() : e.toString());
            alert.showAndWait();
        }
    }

    @FXML
    public void showProceduresList(ActionEvent event) {
        loadView("/ui/procedures_list.fxml");
    }

    @FXML
    public void showChamberTrends(ActionEvent event) {
        loadView("/ui/chamber_trends.fxml");
    }

    @FXML
    public void showAdminUsers(ActionEvent event) {
        loadAdminTab(0);
    }

    @FXML
    public void showAdminStructure(ActionEvent event) {
        loadAdminTab(1);
    }

    @FXML
    public void showAdminLogs(ActionEvent event) {
        loadAdminTab(2);
    }

    @FXML
    public void showAdminMaterials(ActionEvent event) {
        loadAdminTab(3);
    }

    private void loadAdminTab(int tabIndex) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/admin_panel.fxml"), I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            TabPane tabPane = loader.load();
            tabPane.getSelectionModel().select(tabIndex);
            
            contentArea.getChildren().clear();
            contentArea.getChildren().add(tabPane);
        } catch (Exception e) {
            log.error("Failed to load admin tab: " + tabIndex, e);
            
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(I18n.t("common.error.viewLoad.title"));
            alert.setHeaderText("Nie udało się załadować panelu administracyjnego");
            alert.setContentText(e.getMessage() != null ? e.getMessage() : e.toString());
            alert.showAndWait();
        }
    }

    @FXML
    public void handleLogout(ActionEvent event) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.mac.bry.desktop.security.model.User) {
            com.mac.bry.desktop.security.model.User user = (com.mac.bry.desktop.security.model.User) auth.getPrincipal();
            userService.clearSession(user.getId());
            auditService.logAccessEvent(auth.getName(), "LOGOUT", "Wylogowanie manualne");
        }

        inactivityMonitor.stopMonitoring();
        SecurityContextHolder.clearContext();
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ui/login.fxml"), I18n.getBundle());
            fxmlLoader.setControllerFactory(applicationContext::getBean);
            Parent root = fxmlLoader.load();
            
            Stage stage = (Stage) sidebarUserLabel.getScene().getWindow();
            stage.setScene(new Scene(root, 800, 600));
            stage.setTitle("Validation System Desktop - Login");
            stage.centerOnScreen();
        } catch (IOException e) {
            log.error("Failed to load login.fxml during logout", e);
        }
    }

    /** Przeładowanie głównego okna po zmianie języka (świeży bundle). */
    private void reloadMainView() {
        try {
            Stage stage = (Stage) contentArea.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main.fxml"), I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            stage.getScene().setRoot(root);
        } catch (IOException e) {
            log.error("Failed to reload main view after language switch", e);
        }
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath), I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent view = loader.load();
            
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
        } catch (Exception e) {
            log.error("Failed to load view: " + fxmlPath, e);
            
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(I18n.t("common.error.viewLoad.title"));
            alert.setHeaderText("Nie udało się załadować ekranu: " + fxmlPath);
            alert.setContentText(e.getMessage() != null ? e.getMessage() : e.toString());
            alert.showAndWait();
        }
    }

    private void registerHoverAnimation(VBox sectionContainer, VBox itemsContainer) {
        // Tworzenie maski obcinającej (clip) dla płynnego ukrywania
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(itemsContainer.widthProperty());
        clip.heightProperty().bind(itemsContainer.heightProperty());
        itemsContainer.setClip(clip);

        // Stan początkowy - zwinięty
        itemsContainer.setMinHeight(0);
        itemsContainer.setMaxHeight(0);
        itemsContainer.setPrefHeight(0);
        itemsContainer.setOpacity(0.0);

        // Linie czasu animacji
        Timeline expandTimeline = new Timeline();
        Timeline collapseTimeline = new Timeline();

        sectionContainer.setOnMouseEntered(event -> {
            collapseTimeline.stop();
            expandTimeline.stop();

            // Obliczamy wysokość dynamicznie na podstawie widocznych i zarządzanych dzieci (przydatne przy warunkowej sekcji Admina)
            double dynamicHeight = 0;
            for (Node node : itemsContainer.getChildren()) {
                if (node.isVisible() && node.isManaged()) {
                    dynamicHeight += 40.0;
                }
            }

            expandTimeline.getKeyFrames().clear();
            expandTimeline.getKeyFrames().addAll(
                new KeyFrame(Duration.millis(300),
                    new KeyValue(itemsContainer.maxHeightProperty(), dynamicHeight, Interpolator.EASE_BOTH),
                    new KeyValue(itemsContainer.prefHeightProperty(), dynamicHeight, Interpolator.EASE_BOTH),
                    new KeyValue(itemsContainer.opacityProperty(), 1.0, Interpolator.EASE_BOTH)
                )
            );
            expandTimeline.play();
        });

        sectionContainer.setOnMouseExited(event -> {
            expandTimeline.stop();
            collapseTimeline.stop();

            collapseTimeline.getKeyFrames().clear();
            collapseTimeline.getKeyFrames().addAll(
                new KeyFrame(Duration.millis(300),
                    new KeyValue(itemsContainer.maxHeightProperty(), 0.0, Interpolator.EASE_BOTH),
                    new KeyValue(itemsContainer.prefHeightProperty(), 0.0, Interpolator.EASE_BOTH),
                    new KeyValue(itemsContainer.opacityProperty(), 0.0, Interpolator.EASE_BOTH)
                )
            );
            collapseTimeline.play();
        });
    }
}
