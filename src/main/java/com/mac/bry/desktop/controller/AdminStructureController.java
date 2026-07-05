package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.repository.DepartmentRepository;
import com.mac.bry.desktop.security.repository.LaboratoryRepository;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.rgielen.fxweaver.core.FxWeaver;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Kontroler zakładki "Struktura Organizacyjna" (działy i pracownie) w panelu administracyjnym.
 * Wydzielony z AdminPanelController w celu zgodności z zasadą SRP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminStructureController {

    private final DepartmentRepository departmentRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final ApplicationContext applicationContext;
    private final FxWeaver fxWeaver;

    @FXML private TableView<Department> deptTable;
    @FXML private TableColumn<Department, String> deptNameColumn;
    @FXML private TableColumn<Department, String> deptAbbrColumn;

    @FXML private TableView<Laboratory> labsTable;
    @FXML private TableColumn<Laboratory, String> labNameColumn;
    @FXML private TableColumn<Laboratory, String> labAbbrColumn;
    @FXML private Label labsTitleLabel;
    @FXML private Button addLabButton;
    @FXML private Button editLabButton;

    @FXML
    public void initialize() {
        deptNameColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        deptAbbrColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("abbreviation"));
        labNameColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
        labAbbrColumn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("abbreviation"));

        deptTable.getSelectionModel().selectedItemProperty().addListener((obs, old, newDept) -> {
            if (newDept != null) {
                refreshLabsForDept(newDept);
                addLabButton.setDisable(false);
            } else {
                labsTable.setItems(FXCollections.emptyObservableList());
                addLabButton.setDisable(true);
            }
        });

        refreshStructure();
    }

    private void refreshStructure() {
        deptTable.setItems(FXCollections.observableArrayList(departmentRepository.findAll()));
    }

    private void refreshLabsForDept(Department dept) {
        labsTitleLabel.setText("Pracownie w: " + dept.getName());
        labsTable.setItems(FXCollections.observableArrayList(
                laboratoryRepository.findByDepartmentId(dept.getId())));
    }

    // ---- Akcje dla Działów ----

    @FXML private void handleAddDept() {
        openDeptDialog(new Department(), false);
    }

    @FXML private void handleEditDept() {
        Department sel = deptTable.getSelectionModel().getSelectedItem();
        if (sel != null) openDeptDialog(sel, true);
    }

    @FXML private void handleDeleteDept() {
        Department sel = deptTable.getSelectionModel().getSelectedItem();
        if (sel != null && confirm("Usunąć dział: " + sel.getName() + "?\nTo spowoduje odpięcie wszystkich pracowni.")) {
            departmentRepository.delete(sel);
            refreshStructure();
        }
    }

    @FXML private void handleShowDeptAudit() {
        Department sel = deptTable.getSelectionModel().getSelectedItem();
        if (sel != null) openAudit(ctrl -> ctrl.initDeptData(sel));
    }

    // ---- Akcje dla Pracowni ----

    @FXML private void handleAddLab() {
        Department dept = deptTable.getSelectionModel().getSelectedItem();
        if (dept != null) {
            Laboratory lab = new Laboratory();
            lab.setDepartment(dept);
            openLabDialog(lab, false);
        }
    }

    @FXML private void handleEditLab() {
        Laboratory sel = labsTable.getSelectionModel().getSelectedItem();
        if (sel != null) openLabDialog(sel, true);
    }

    @FXML private void handleDeleteLab() {
        Laboratory sel = labsTable.getSelectionModel().getSelectedItem();
        if (sel != null && confirm("Usunąć pracownię: " + sel.getName() + "?")) {
            laboratoryRepository.delete(sel);
            refreshLabsForDept(sel.getDepartment());
        }
    }

    @FXML private void handleShowLabAudit() {
        Laboratory sel = labsTable.getSelectionModel().getSelectedItem();
        if (sel != null) openAudit(ctrl -> ctrl.initLabData(sel));
    }

    // ---- Pomocnicze ----

    private void openDeptDialog(Department dept, boolean isEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/dept_dialog.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            DeptDialogController ctrl = loader.getController();
            ctrl.setDepartment(dept, isEdit);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(isEdit ? "Edycja Działu" : "Nowy Dział");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            if (ctrl.isSaved()) { departmentRepository.save(dept); refreshStructure(); }
        } catch (IOException e) {
            log.error("Błąd podczas otwierania dialogu działu", e);
        }
    }

    private void openLabDialog(Laboratory lab, boolean isEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/lab_dialog.fxml"), com.mac.bry.desktop.config.I18n.getBundle());
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();
            LabDialogController ctrl = loader.getController();
            ctrl.setLaboratory(lab, isEdit);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(isEdit ? "Edycja Pracowni" : "Nowa Pracownia");
            stage.setScene(new Scene(root));
            stage.showAndWait();
            if (ctrl.isSaved()) {
                laboratoryRepository.save(lab);
                refreshLabsForDept(lab.getDepartment());
            }
        } catch (IOException e) {
            log.error("Błąd podczas otwierania dialogu pracowni", e);
        }
    }

    private void openAudit(Consumer<UserAuditController> init) {
        net.rgielen.fxweaver.core.FxControllerAndView<UserAuditController, javafx.scene.layout.VBox> cav =
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
        a.setTitle("Potwierdzenie");
        a.setHeaderText(null);
        a.setContentText(msg);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }
}
