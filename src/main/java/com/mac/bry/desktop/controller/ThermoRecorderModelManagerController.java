package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.ThermoRecorderModel;
import com.mac.bry.desktop.repository.ThermoRecorderModelRepository;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ThermoRecorderModelManagerController {

    private final ThermoRecorderModelRepository modelRepository;
    private final ApplicationContext applicationContext;

    @FXML private TextField searchField;
    @FXML private TableView<ThermoRecorderModel> modelsTable;
    @FXML private TableColumn<ThermoRecorderModel, String> idColumn;
    @FXML private TableColumn<ThermoRecorderModel, String> nameColumn;
    @FXML private TableColumn<ThermoRecorderModel, Number> channelsColumn;
    @FXML private TableColumn<ThermoRecorderModel, String> resolutionColumn;
    @FXML private TableColumn<ThermoRecorderModel, Boolean> activeColumn;
    @FXML private TableColumn<ThermoRecorderModel, Void> actionsColumn;

    private ObservableList<ThermoRecorderModel> masterData = FXCollections.observableArrayList();
    private FilteredList<ThermoRecorderModel> filteredData;

    @FXML
    public void initialize() {
        log.info("Initializing ThermoRecorderModelManagerController");
        setupTableColumns();
        setupFilters();
        loadData();
    }

    private void setupTableColumns() {
        idColumn.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getId())));
        nameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        channelsColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getChannelCount()));
        resolutionColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getDefaultResolution() != null ? d.getValue().getDefaultResolution().toString() : "-"
        ));
        
        activeColumn.setCellValueFactory(d -> new SimpleBooleanProperty(d.getValue().getActive() != null ? d.getValue().getActive() : false));
        activeColumn.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item ? "Aktywny" : "Nieaktywny");
                    badge.getStyleClass().addAll("status-badge", item ? "status-active" : "status-inactive");
                    setGraphic(badge);
                }
            }
        });

        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("Edytuj");

            {
                editBtn.getStyleClass().addAll("action-button", "accent");
                editBtn.setOnAction(event -> {
                    ThermoRecorderModel model = getTableView().getItems().get(getIndex());
                    openDialog(model);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(5, editBtn);
                    setGraphic(box);
                }
            }
        });
    }

    private void setupFilters() {
        filteredData = new FilteredList<>(masterData, p -> true);
        searchField.textProperty().addListener((obs, old, newValue) -> {
            filteredData.setPredicate(model -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lowerCaseFilter = newValue.toLowerCase();
                return model.getName().toLowerCase().contains(lowerCaseFilter);
            });
        });
        modelsTable.setItems(filteredData);
    }

    private void loadData() {
        List<ThermoRecorderModel> all = modelRepository.findAll();
        masterData.setAll(all);
    }

    @FXML
    public void handleAddNewModel() {
        openDialog(null);
    }

    private void openDialog(ThermoRecorderModel model) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/thermo_recorder_model_dialog.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent view = loader.load();

            ThermoRecorderModelDialogController controller = loader.getController();
            boolean isEdit = (model != null);
            controller.initData(isEdit ? model : new ThermoRecorderModel(), isEdit);

            Stage stage = new Stage();
            stage.setTitle(isEdit ? "Edytuj model" : "Dodaj nowy model");
            stage.setScene(new Scene(view));
            stage.initOwner(modelsTable.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.showAndWait();

            if (controller.isSaved()) {
                loadData();
            }

        } catch (IOException e) {
            log.error("Failed to open dialog", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Błąd");
            alert.setContentText("Nie można otworzyć okna dialogowego.");
            alert.showAndWait();
        }
    }
}
