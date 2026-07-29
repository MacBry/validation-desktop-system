package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.model.GxPProcedureType;
import com.mac.bry.desktop.model.ProcedureClassConfig;
import com.mac.bry.desktop.repository.ProcedureClassConfigRepository;
import com.mac.bry.desktop.service.planner.TestoDelayCalculatorService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Konfigurator klas procedur — pięć kroków czasowych definiujących przebieg
 * badania (BA §3).
 * <p>
 * Podgląd przelicza na żywo opóźnienie startu i długość pomiaru, bo to
 * liczby, które trafiają wprost do rejestratora: pomyłka w Kroku 2 lub 3
 * przesuwa pierwszą próbkę i narusza regułę W3.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProcedureClassConfigController {

    private final ProcedureClassConfigRepository configRepository;
    private final TestoDelayCalculatorService delayCalculator;

    @FXML private TableView<ProcedureClassConfig> configTable;
    @FXML private TableColumn<ProcedureClassConfig, String> colName;
    @FXML private TableColumn<ProcedureClassConfig, String> colType;
    @FXML private TableColumn<ProcedureClassConfig, String> colStep1;
    @FXML private TableColumn<ProcedureClassConfig, String> colStep2;
    @FXML private TableColumn<ProcedureClassConfig, String> colStep3;
    @FXML private TableColumn<ProcedureClassConfig, String> colStep4;
    @FXML private TableColumn<ProcedureClassConfig, String> colStep5;
    @FXML private TableColumn<ProcedureClassConfig, String> colActive;

    @FXML private TextField nameField;
    @FXML private ComboBox<GxPProcedureType> typeCombo;
    @FXML private Spinner<Integer> step1Spinner;
    @FXML private Spinner<Integer> step2Spinner;
    @FXML private Spinner<Integer> step3Spinner;
    @FXML private Spinner<Integer> step4IntervalSpinner;
    @FXML private Spinner<Integer> step4CountSpinner;
    @FXML private Spinner<Integer> step5Spinner;
    @FXML private CheckBox activeCheck;
    @FXML private Label lblDerived;

    private ProcedureClassConfig editing;

    @FXML
    public void initialize() {
        setupTable();
        setupForm();
        refresh();
    }

    private void setupTable() {
        colName.setCellValueFactory(c -> text(c.getValue().getName()));
        colType.setCellValueFactory(c -> text(c.getValue().getProcedureType() != null
                ? c.getValue().getProcedureType().getDisplayName() : "–"));
        colStep1.setCellValueFactory(c -> text(c.getValue().getStep1ProgMinutes() + " min"));
        colStep2.setCellValueFactory(c -> text(c.getValue().getStep2PlacementMinutes() + " min"));
        colStep3.setCellValueFactory(c -> text(c.getValue().getStep3StabHours() + " h"));
        colStep4.setCellValueFactory(c -> text(c.getValue().getStep4SampleCount()
                + " × " + c.getValue().getStep4IntervalMinutes() + " min"));
        colStep5.setCellValueFactory(c -> text(c.getValue().getStep5ReadoutBufferHours() + " h"));
        colActive.setCellValueFactory(c -> text(Boolean.TRUE.equals(c.getValue().getActive())
                ? I18n.t("common.tak") : I18n.t("common.nie")));

        configTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, config) -> loadIntoForm(config));
    }

    private void setupForm() {
        typeCombo.setItems(FXCollections.observableArrayList(GxPProcedureType.values()));
        typeCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(GxPProcedureType type) {
                return type != null ? type.getDisplayName() : "";
            }

            @Override
            public GxPProcedureType fromString(String s) {
                return null;
            }
        });

        configureSpinner(step1Spinner, 0, 600, 10);
        configureSpinner(step2Spinner, 0, 600, 20);
        configureSpinner(step3Spinner, 0, 168, 6);
        configureSpinner(step4IntervalSpinner, 1, 1440, 180);
        configureSpinner(step4CountSpinner, 1, 10000, 40);
        configureSpinner(step5Spinner, 0, 168, 6);

        step2Spinner.valueProperty().addListener((obs, old, v) -> updateDerived());
        step3Spinner.valueProperty().addListener((obs, old, v) -> updateDerived());
        step4IntervalSpinner.valueProperty().addListener((obs, old, v) -> updateDerived());
        step4CountSpinner.valueProperty().addListener((obs, old, v) -> updateDerived());
        updateDerived();
    }

    private void configureSpinner(Spinner<Integer> spinner, int min, int max, int initial) {
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial));
        spinner.setEditable(true);
    }

    /**
     * Przelicza wartości pochodne: opóźnienie startu Testo (Krok 2 + Krok 3,
     * bez Kroku 1) oraz długość okresu pomiarowego.
     */
    private void updateDerived() {
        ProcedureClassConfig preview = readForm(new ProcedureClassConfig());
        int delay = delayCalculator.calculateStartDelay(preview);
        int measurementMinutes = delayCalculator.calculateMeasurementDurationMinutes(preview);

        lblDerived.setText(I18n.t("planner.procedure.derived",
                delay, delay / 60, delay % 60, measurementMinutes / 60));
    }

    private void loadIntoForm(ProcedureClassConfig config) {
        editing = config;
        if (config == null) {
            return;
        }
        nameField.setText(config.getName());
        typeCombo.getSelectionModel().select(config.getProcedureType());
        step1Spinner.getValueFactory().setValue(config.getStep1ProgMinutes());
        step2Spinner.getValueFactory().setValue(config.getStep2PlacementMinutes());
        step3Spinner.getValueFactory().setValue(config.getStep3StabHours());
        step4IntervalSpinner.getValueFactory().setValue(config.getStep4IntervalMinutes());
        step4CountSpinner.getValueFactory().setValue(config.getStep4SampleCount());
        step5Spinner.getValueFactory().setValue(config.getStep5ReadoutBufferHours());
        activeCheck.setSelected(Boolean.TRUE.equals(config.getActive()));
        updateDerived();
    }

    private ProcedureClassConfig readForm(ProcedureClassConfig target) {
        target.setName(nameField != null && nameField.getText() != null ? nameField.getText().trim() : null);
        target.setProcedureType(typeCombo != null ? typeCombo.getValue() : null);
        target.setStep1ProgMinutes(step1Spinner.getValue());
        target.setStep2PlacementMinutes(step2Spinner.getValue());
        target.setStep3StabHours(step3Spinner.getValue());
        target.setStep4IntervalMinutes(step4IntervalSpinner.getValue());
        target.setStep4SampleCount(step4CountSpinner.getValue());
        target.setStep5ReadoutBufferHours(step5Spinner.getValue());
        target.setActive(activeCheck == null || activeCheck.isSelected());
        return target;
    }

    @FXML
    public void onNew() {
        editing = null;
        configTable.getSelectionModel().clearSelection();
        nameField.clear();
        typeCombo.getSelectionModel().select(GxPProcedureType.PERIODIC_REVALIDATION);
        step1Spinner.getValueFactory().setValue(10);
        step2Spinner.getValueFactory().setValue(20);
        step3Spinner.getValueFactory().setValue(6);
        step4IntervalSpinner.getValueFactory().setValue(180);
        step4CountSpinner.getValueFactory().setValue(40);
        step5Spinner.getValueFactory().setValue(6);
        activeCheck.setSelected(true);
        updateDerived();
    }

    @FXML
    public void onSave() {
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        if (name.isEmpty()) {
            showError(I18n.t("planner.procedure.name_required"));
            return;
        }
        if (typeCombo.getValue() == null) {
            showError(I18n.t("planner.procedure.type_required"));
            return;
        }
        try {
            ProcedureClassConfig target = editing != null ? editing : new ProcedureClassConfig();
            configRepository.save(readForm(target));
            refresh();
            showInfo(I18n.t("planner.procedure.saved"));
        } catch (Exception e) {
            log.error("Nie udało się zapisać klasy procedury '{}'", name, e);
            showError(I18n.t("planner.procedure.save_failed") + "\n\n" + e.getMessage());
        }
    }

    @FXML
    public void onRefresh() {
        refresh();
    }

    private void refresh() {
        configTable.setItems(FXCollections.observableArrayList(configRepository.findAll()));
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value != null ? value : "–");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.t("planner.procedure.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("planner.procedure.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}