package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.ThermoRecorderModel;
import com.mac.bry.desktop.repository.ThermoRecorderModelRepository;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class ThermoRecorderModelDialogController {

    private final ThermoRecorderModelRepository modelRepository;

    @FXML private TextField nameField;
    @FXML private Spinner<Integer> channelsSpinner;
    @FXML private TextField resolutionField;
    @FXML private CheckBox activeCheckBox;

    // --- Dane katalogowe producenta wymagane przez regułę W4 ----------------
    @FXML private TextField sampleCapacityField;
    @FXML private TextField minOperatingTempField;
    @FXML private TextField maxOperatingTempField;
    @FXML private TextField batteryTypeField;
    @FXML private CheckBox batteryReplaceableCheckBox;
    @FXML private TextField batteryLifeDaysField;
    @FXML private TextField batteryLifeRefCycleField;
    @FXML private TextField batteryLifeRefTempField;
    @FXML private TextField operatingDurationDaysField;
    @FXML private TextField batteryShelfLifeMonthsField;

    private ThermoRecorderModel model;
    private boolean saved = false;

    /** Ustawiany przez parsery pól liczbowych — komunikat pokazał się już użytkownikowi. */
    private boolean parseFailed;

    @FXML
    public void initialize() {
        SpinnerValueFactory<Integer> factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 16, 1);
        channelsSpinner.setValueFactory(factory);

        // Logger jednorazowy nie ma wymiany baterii — rozlicza się ze sztywnego
        // limitu pracy urządzenia, więc żywotność katalogowa go nie dotyczy.
        batteryReplaceableCheckBox.selectedProperty().addListener((obs, was, isReplaceable) ->
                applyBatteryModeVisibility(isReplaceable));
    }

    public void initData(ThermoRecorderModel model, boolean isEdit) {
        this.model = model;
        if (isEdit) {
            nameField.setText(model.getName());
            channelsSpinner.getValueFactory().setValue(model.getChannelCount());
            resolutionField.setText(model.getDefaultResolution() != null ? model.getDefaultResolution().toString() : "0.1");
            activeCheckBox.setSelected(model.getActive() != null ? model.getActive() : false);

            sampleCapacityField.setText(text(model.getSampleCapacity()));
            minOperatingTempField.setText(text(model.getMinOperatingTempC()));
            maxOperatingTempField.setText(text(model.getMaxOperatingTempC()));
            batteryTypeField.setText(model.getBatteryType() != null ? model.getBatteryType() : "");
            batteryReplaceableCheckBox.setSelected(!Boolean.FALSE.equals(model.getBatteryReplaceable()));
            batteryLifeDaysField.setText(text(model.getBatteryLifeDays()));
            batteryLifeRefCycleField.setText(text(model.getBatteryLifeRefCycleMin()));
            batteryLifeRefTempField.setText(text(model.getBatteryLifeRefTempC()));
            operatingDurationDaysField.setText(text(model.getOperatingDurationDays()));
            batteryShelfLifeMonthsField.setText(text(model.getBatteryShelfLifeMonths()));
        } else {
            activeCheckBox.setSelected(true);
            resolutionField.setText("0.100");
            batteryReplaceableCheckBox.setSelected(true);
            batteryLifeRefCycleField.setText("15");
        }
        applyBatteryModeVisibility(batteryReplaceableCheckBox.isSelected());
    }

    private void applyBatteryModeVisibility(boolean batteryReplaceable) {
        batteryTypeField.setDisable(!batteryReplaceable);
        batteryLifeDaysField.setDisable(!batteryReplaceable);
        batteryLifeRefCycleField.setDisable(!batteryReplaceable);
        batteryLifeRefTempField.setDisable(!batteryReplaceable);
        batteryShelfLifeMonthsField.setDisable(!batteryReplaceable);
        operatingDurationDaysField.setDisable(batteryReplaceable);
    }

    private String text(Object value) {
        return value != null ? value.toString() : "";
    }

    @FXML
    public void handleSave() {
        try {
            if (nameField.getText().trim().isEmpty()) {
                showError("Nazwa modelu jest wymagana");
                return;
            }

            model.setName(nameField.getText().trim());
            model.setChannelCount(channelsSpinner.getValue());
            try {
                model.setDefaultResolution(new BigDecimal(resolutionField.getText().trim().replace(",", ".")));
            } catch (NumberFormatException ex) {
                showError("Nieprawidłowy format rozdzielczości");
                return;
            }
            model.setActive(activeCheckBox.isSelected());

            if (!applyHardwareSpecification()) {
                return;
            }

            modelRepository.save(model);
            saved = true;
            close();
        } catch (Exception e) {
            log.error("Błąd zapisu modelu", e);
            showError("Błąd zapisu: " + e.getMessage());
        }
    }

    /**
     * Przepisuje dane katalogowe producenta na encję (reguła W4).
     * <p>
     * Zakres pracy jest wymagany: bez niego planer nie potrafi stwierdzić, czy
     * rejestrator wolno włożyć do danej komory, i blokuje każde zadanie z tym
     * modelem. Reszta pól może zostać pusta — wtedy blokowane jest tylko
     * kryterium budżetu energii.
     *
     * @return {@code false} gdy dane są niepoprawne i zapis ma zostać przerwany
     */
    private boolean applyHardwareSpecification() {
        parseFailed = false;

        Integer sampleCapacity = parseInteger(sampleCapacityField, "Pojemność pamięci");
        Double minTemp = parseDouble(minOperatingTempField, "Dolna granica zakresu pracy");
        Double maxTemp = parseDouble(maxOperatingTempField, "Górna granica zakresu pracy");
        if (parseFailed) {
            return false;
        }

        if (minTemp == null ^ maxTemp == null) {
            showError("Zakres pracy wymaga podania obu granic albo pozostawienia obu pustych");
            return false;
        }
        if (minTemp != null && minTemp >= maxTemp) {
            showError("Dolna granica zakresu pracy musi być mniejsza od górnej");
            return false;
        }

        model.setSampleCapacity(sampleCapacity != null ? sampleCapacity : 16000);
        model.setMinOperatingTempC(minTemp);
        model.setMaxOperatingTempC(maxTemp);
        model.setBatteryType(emptyToNull(batteryTypeField.getText()));
        model.setBatteryReplaceable(batteryReplaceableCheckBox.isSelected());

        if (batteryReplaceableCheckBox.isSelected()) {
            model.setBatteryLifeDays(parseInteger(batteryLifeDaysField, "Żywotność baterii"));
            Integer refCycle = parseInteger(batteryLifeRefCycleField, "Referencyjny cykl pomiarowy");
            model.setBatteryLifeRefCycleMin(refCycle != null ? refCycle : 15);
            model.setBatteryLifeRefTempC(parseDouble(batteryLifeRefTempField, "Referencyjna temperatura"));
            model.setBatteryShelfLifeMonths(parseInteger(batteryShelfLifeMonthsField, "Dopuszczalny wiek baterii"));
            model.setOperatingDurationDays(null);
        } else {
            // Logger jednorazowy: liczy się wyłącznie limit pracy urządzenia.
            model.setOperatingDurationDays(parseInteger(operatingDurationDaysField, "Limit pracy urządzenia"));
            model.setBatteryLifeDays(null);
            model.setBatteryLifeRefTempC(null);
            model.setBatteryShelfLifeMonths(null);
        }
        return !parseFailed;
    }

    private Integer parseInteger(TextField field, String label) {
        String raw = field.getText() != null ? field.getText().trim() : "";
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            parseFailed = true;
            showError(label + ": oczekiwana liczba całkowita");
            return null;
        }
    }

    private Double parseDouble(TextField field, String label) {
        String raw = field.getText() != null ? field.getText().trim().replace(",", ".") : "";
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(raw);
        } catch (NumberFormatException ex) {
            parseFailed = true;
            showError(label + ": oczekiwana liczba");
            return null;
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @FXML
    public void handleCancel() {
        close();
    }

    private void close() {
        ((Stage) nameField.getScene().getWindow()).close();
    }

    public boolean isSaved() {
        return saved;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Błąd walidacji");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
