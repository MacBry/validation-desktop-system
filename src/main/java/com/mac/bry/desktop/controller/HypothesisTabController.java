package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.dto.stats.AnovaResult;
import com.mac.bry.desktop.dto.stats.TostResult;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.service.TestoRevalidationFacade;
import com.mac.bry.desktop.service.stats.SensorStatsEngine;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class HypothesisTabController {

    private final TestoRevalidationFacade facade;
    private RevalidationSession session;

    @FXML private ComboBox<String> tostSensor1Combo;
    @FXML private ComboBox<String> tostSensor2Combo;
    @FXML private TextField txtTostTheta;
    @FXML private HBox tostResultBox;
    @FXML private Label lblTostBadge;
    @FXML private Label lblTostDifference;
    @FXML private Label lblTostDetails;

    @FXML private ComboBox<String> fSensor1Combo;
    @FXML private ComboBox<String> fSensor2Combo;
    @FXML private HBox fResultBox;
    @FXML private Label lblFBadge;
    @FXML private Label lblFRatio;
    @FXML private Label lblFDetails;

    @FXML private VBox homogeneityResultBox;
    @FXML private Label lblHomogeneityBadge;
    @FXML private Label lblHomogeneityTestName;
    @FXML private Label lblHomogeneityPValue;
    @FXML private Label lblHomogeneityDetails;

    public void initSession(RevalidationSession session) {
        this.session = session;
        setupHypothesisTestingComboBoxes();
        // Hide previous results when switching/re-initializing
        tostResultBox.setVisible(false);
        tostResultBox.setManaged(false);
        fResultBox.setVisible(false);
        fResultBox.setManaged(false);
        homogeneityResultBox.setVisible(false);
        homogeneityResultBox.setManaged(false);
    }

    public void setupHypothesisTestingComboBoxes() {
        if (session == null || session.getAssignedPositions().isEmpty()) {
            tostSensor1Combo.getItems().clear();
            tostSensor2Combo.getItems().clear();
            fSensor1Combo.getItems().clear();
            fSensor2Combo.getItems().clear();
            return;
        }

        List<String> labels = session.getAssignedPositions().keySet().stream()
                .map(RevalidationSession.GridPosition::getLabel)
                .sorted()
                .toList();

        tostSensor1Combo.setItems(FXCollections.observableArrayList(labels));
        tostSensor2Combo.setItems(FXCollections.observableArrayList(labels));
        fSensor1Combo.setItems(FXCollections.observableArrayList(labels));
        fSensor2Combo.setItems(FXCollections.observableArrayList(labels));

        if (labels.size() >= 2) {
            tostSensor1Combo.getSelectionModel().select(0);
            tostSensor2Combo.getSelectionModel().select(1);
            fSensor1Combo.getSelectionModel().select(0);
            fSensor2Combo.getSelectionModel().select(1);
        } else if (labels.size() == 1) {
            tostSensor1Combo.getSelectionModel().select(0);
            tostSensor2Combo.getSelectionModel().select(0);
            fSensor1Combo.getSelectionModel().select(0);
            fSensor2Combo.getSelectionModel().select(0);
        }
    }

    private double[] getRawDataFromLabel(String label) {
        if (session == null || label == null) return null;
        for (Map.Entry<RevalidationSession.GridPosition, RevalidationSession.PositionData> entry : session.getAssignedPositions().entrySet()) {
            if (entry.getKey().getLabel().equals(label)) {
                return entry.getValue().getSeries().getMeasurements().stream()
                        .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                        .toArray();
            }
        }
        return null;
    }

    @FXML
    public void handleRunTostTest(ActionEvent event) {
        double theta = 0.50;
        try {
            theta = Double.parseDouble(txtTostTheta.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.WARNING, "Nieprawidłowy format wartości tolerancji θ. Użyto domyślnej wartości 0.50.", ButtonType.OK).showAndWait();
            txtTostTheta.setText("0.50");
        }

        String s1 = tostSensor1Combo.getValue();
        String s2 = tostSensor2Combo.getValue();
        if (s1 == null || s2 == null) {
            new Alert(Alert.AlertType.WARNING, "Wybierz dwa sensory do przeprowadzenia testu TOST.", ButtonType.OK).showAndWait();
            return;
        }

        double[] sample1 = getRawDataFromLabel(s1);
        double[] sample2 = getRawDataFromLabel(s2);
        if (sample1 == null || sample2 == null || sample1.length < 2 || sample2.length < 2) {
            new Alert(Alert.AlertType.ERROR, "Wybrane sensory nie posiadają wystarczającej ilości danych pomiarowych.", ButtonType.OK).showAndWait();
            return;
        }

        TostResult result = facade.performTostEquivalence(sample1, sample2, theta);

        lblTostDifference.setText(String.format("Średnia różnica: %.3f°C w granicach ±%.2f°C", result.getMeanDifference(), result.getTheta()));
        lblTostDetails.setText(String.format("p1 (H0: diff <= -θ) = %.4f, p2 (H0: diff >= θ) = %.4f", result.getPValue1(), result.getPValue2()));
        if (result.isEquivalent()) {
            lblTostBadge.setText("✅ RÓWNOWAŻNE");
            lblTostBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: -color-success-emphasis; -fx-padding: 4 8; -fx-background-radius: 4;");
        } else {
            lblTostBadge.setText("❌ RÓŻNE");
            lblTostBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: -color-danger-emphasis; -fx-padding: 4 8; -fx-background-radius: 4;");
        }
        tostResultBox.setVisible(true);
        tostResultBox.setManaged(true);
    }

    @FXML
    public void handleRunFTest(ActionEvent event) {
        String s1 = fSensor1Combo.getValue();
        String s2 = fSensor2Combo.getValue();
        if (s1 == null || s2 == null) {
            new Alert(Alert.AlertType.WARNING, "Wybierz dwa sensory do przeprowadzenia testu F.", ButtonType.OK).showAndWait();
            return;
        }

        double[] sample1 = getRawDataFromLabel(s1);
        double[] sample2 = getRawDataFromLabel(s2);
        if (sample1 == null || sample2 == null || sample1.length < 2 || sample2.length < 2) {
            new Alert(Alert.AlertType.ERROR, "Wybrane sensory nie posiadają wystarczającej ilości danych pomiarowych.", ButtonType.OK).showAndWait();
            return;
        }

        double pValue = facade.performFTest(sample1, sample2);
        double var1 = SensorStatsEngine.calculateVariance(sample1);
        double var2 = SensorStatsEngine.calculateVariance(sample2);
        double ratio = var2 == 0.0 ? 0.0 : var1 / var2;

        lblFRatio.setText(String.format("Stosunek wariancji (F): %.3f (Wariancja 1: %.4f, Wariancja 2: %.4f)", ratio, var1, var2));
        lblFDetails.setText(String.format("Wartość p (p-value): %.4f (α = 0.05)", pValue));
        if (pValue >= 0.05) {
            lblFBadge.setText("✅ JEDNODRODNE (PASS)");
            lblFBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: -color-success-emphasis; -fx-padding: 4 8; -fx-background-radius: 4;");
        } else {
            lblFBadge.setText("❌ NIEJEDNORODNE (FAIL)");
            lblFBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: -color-danger-emphasis; -fx-padding: 4 8; -fx-background-radius: 4;");
        }
        fResultBox.setVisible(true);
        fResultBox.setManaged(true);
    }

    @FXML
    public void handleRunGlobalHomogeneityTest(ActionEvent event) {
        if (session == null || session.getAssignedPositions().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Brak przypisanych sensorów z danymi do wykonania testu globalnego.", ButtonType.OK).showAndWait();
            return;
        }

        List<double[]> samples = new ArrayList<>();
        boolean normal = true;
        for (Map.Entry<RevalidationSession.GridPosition, RevalidationSession.PositionData> entry : session.getAssignedPositions().entrySet()) {
            double[] rawData = entry.getValue().getSeries().getMeasurements().stream()
                    .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                    .toArray();
            if (rawData.length >= 2) {
                samples.add(rawData);
                double jbPVal = facade.performJarqueBera(rawData);
                if (jbPVal < 0.05) {
                    normal = false;
                }
            }
        }

        if (samples.size() < 2) {
            new Alert(Alert.AlertType.WARNING, "Wymagane są dane z co najmniej 2 sensorów do przeprowadzenia testu globalnego.", ButtonType.OK).showAndWait();
            return;
        }

        double pValue;
        String testName;
        boolean significantDifference;
        if (normal) {
            testName = "Jednoczynnikowa ANOVA (Rozkład normalny)";
            AnovaResult anovaResult = facade.performAnova(samples);
            pValue = anovaResult.getPValue();
            significantDifference = anovaResult.isSignificantDifference();
        } else {
            testName = "Test Kruskala-Wallisa (Brak normalności rozkładu)";
            pValue = facade.performKruskalWallis(samples);
            significantDifference = pValue < 0.05;
        }

        lblHomogeneityTestName.setText(testName);
        lblHomogeneityPValue.setText(String.format("%.4f", pValue));
        if (!significantDifference) {
            lblHomogeneityBadge.setText("✅ JEDNORODNOŚĆ POTWIERDZONA (PASS)");
            lblHomogeneityBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: -color-success-emphasis; -fx-padding: 4 8; -fx-background-radius: 4;");
            lblHomogeneityDetails.setText("Wszystkie strefy komory wykazują statystycznie jednorodne rozkłady temperatur (brak istotnych różnic).");
        } else {
            lblHomogeneityBadge.setText("❌ NIEJEDNORODNOŚĆ WYKAZANA (FAIL)");
            lblHomogeneityBadge.setStyle("-fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: -color-danger-emphasis; -fx-padding: 4 8; -fx-background-radius: 4;");
            lblHomogeneityDetails.setText("Istnieją statystycznie istotne różnice w temperaturach pomiędzy strefami komory.");
        }
        homogeneityResultBox.setVisible(true);
        homogeneityResultBox.setManaged(true);
    }
}
