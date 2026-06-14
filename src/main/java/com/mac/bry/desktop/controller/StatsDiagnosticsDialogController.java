package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.dto.stats.CapabilityIndexes;
import com.mac.bry.desktop.dto.stats.ControlChartData;
import com.mac.bry.desktop.dto.stats.DefrostCycle;
import com.mac.bry.desktop.dto.stats.SpatialStatsResult;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.service.stats.ControlChartCalculator;
import com.mac.bry.desktop.service.stats.DefrostCycleDetector;
import com.mac.bry.desktop.service.stats.NelsonRulesDetector;
import com.mac.bry.desktop.service.stats.SpcEngine;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class StatsDiagnosticsDialogController {

    @FXML private Label lblSensorTitle;
    @FXML private Label lblSpcStats;

    // Wykresy SPC
    @FXML private LineChart<Number, Number> xBarChart;
    @FXML private LineChart<Number, Number> sChart;

    // Nelson Rules
    @FXML private ListView<String> lstNelsonViolations;

    // Tabela Defrost
    @FXML private TableView<DefrostCycle> defrostTable;
    @FXML private TableColumn<DefrostCycle, String> colDefrostStart;
    @FXML private TableColumn<DefrostCycle, String> colDefrostEnd;
    @FXML private TableColumn<DefrostCycle, Double> colDefrostDuration;
    @FXML private TableColumn<DefrostCycle, Double> colDefrostMax;
    @FXML private TableColumn<DefrostCycle, Double> colDefrostAmp;

    // ---- Zakładka 3: Jednorodność Przestrzenna ----
    /** KPI: maksymalny globalny rozstęp przestrzenny. */
    @FXML private Label lblMaxDeltaGlobal;
    /** KPI: średni globalny rozstęp przestrzenny. */
    @FXML private Label lblMeanDeltaGlobal;
    /** KPI: maksymalny bezwzględny gradient pionowy |GÓRA − DÓŁ|. */
    @FXML private Label lblMaxVerticalGradient;
    /** KPI: średni bezwzględny gradient pionowy |GÓRA − DÓŁ|. */
    @FXML private Label lblMeanVerticalGradient;
    /** Wykres ΔT w czasie z 3 seriami: globalny / poziom GÓRA / poziom DÓŁ. */
    @FXML private LineChart<Number, Number> deltaTimeChart;
    /** Tabela podsumowania 2-poziomowego. */
    @FXML private TableView<LevelRow> levelSummaryTable;
    @FXML private TableColumn<LevelRow, String> colLevelName;
    @FXML private TableColumn<LevelRow, String> colLevelMeanDelta;
    @FXML private TableColumn<LevelRow, String> colLevelMaxDelta;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FMT_DELTA = "%.3f";

    @FXML
    public void initialize() {
        setupDefrostTable();
        setupLevelSummaryTable();
    }

    // -----------------------------------------------------------------------
    // Konfiguracja tabel
    // -----------------------------------------------------------------------

    private void setupDefrostTable() {
        colDefrostStart.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getStartTime().format(DATE_FORMATTER)));
        colDefrostEnd.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getEndTime().format(DATE_FORMATTER)));
        colDefrostDuration.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                Math.round(cellData.getValue().getDurationMinutes() * 10.0) / 10.0));
        colDefrostMax.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                Math.round(cellData.getValue().getMaxTemperature() * 100.0) / 100.0));
        colDefrostAmp.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                Math.round(cellData.getValue().getAmplitude() * 100.0) / 100.0));
    }

    private void setupLevelSummaryTable() {
        colLevelName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        colLevelMeanDelta.setCellValueFactory(cell ->
                new SimpleStringProperty(String.format(FMT_DELTA, cell.getValue().meanDelta())));
        colLevelMaxDelta.setCellValueFactory(cell ->
                new SimpleStringProperty(String.format(FMT_DELTA, cell.getValue().maxDelta())));
    }

    // -----------------------------------------------------------------------
    // Dane dla zakładki SPC + Defrost (bez zmian)
    // -----------------------------------------------------------------------

    public void setSensorData(ThermoMeasurementSeries series, String positionLabel, Double minLimit, Double maxLimit) {
        if (series == null) return;

        lblSensorTitle.setText("Zaawansowana Diagnostyka SPC & Defrost dla pozycji: " + positionLabel);

        List<ThermoMeasurementPoint> measurements = series.getMeasurements();
        double[] rawData = measurements.stream()
                .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                .toArray();

        // 1. Obliczenie wskaźników zdolności (SPC)
        double lsl = minLimit != null ? minLimit : 2.0;
        double usl = maxLimit != null ? maxLimit : 8.0;
        CapabilityIndexes cpIndex = SpcEngine.calculateCapability(rawData, lsl, usl);

        lblSpcStats.setText(String.format(
                "Wskaźnik Cp: %.3f | Wskaźnik Cpk: %.3f (Limity specyfikacji: %.1f - %.1f °C)",
                cpIndex.getCp(), cpIndex.getCpk(), lsl, usl
        ));

        // 2. Obliczenie i rysowanie kart kontrolnych Shewharta
        ControlChartData spcData = ControlChartCalculator.calculateShewhartLimits(rawData);
        renderXBarChart(spcData);
        renderSChart(spcData);

        // 3. Weryfikacja reguł stabilności Nelsona
        log.info("Rozpoczęcie detekcji reguł Nelsona dla: {}", positionLabel);
        List<NelsonRulesDetector.Violation> xbarViolations = NelsonRulesDetector.detectXBarViolations(spcData);
        List<NelsonRulesDetector.Violation> sViolations = NelsonRulesDetector.detectSViolations(spcData);
        log.info("Wykryto naruszeń X-bar: {}, S: {}", xbarViolations.size(), sViolations.size());

        ObservableList<String> nelsonItems = FXCollections.observableArrayList();
        for (NelsonRulesDetector.Violation v : xbarViolations) {
            String msg = String.format("[Karta X-Bar] Podgrupa %d: %s", v.getSubgroupIndex(), v.getDescription());
            log.info("Dodawanie naruszenia X-bar: {}", msg);
            nelsonItems.add(msg);
        }
        for (NelsonRulesDetector.Violation v : sViolations) {
            String msg = String.format("[Karta S] Podgrupa %d: %s", v.getSubgroupIndex(), v.getDescription());
            log.info("Dodawanie naruszenia S: {}", msg);
            nelsonItems.add(msg);
        }

        if (lstNelsonViolations != null) {
            log.info("Ustawianie elementów w lstNelsonViolations (rozmiar: {})", nelsonItems.size());
            lstNelsonViolations.setCellFactory(lv -> new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle(null);
                    } else {
                        setText(item);
                        setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: bold;");
                    }
                }
            });
            lstNelsonViolations.setItems(nelsonItems);
        } else {
            log.error("UWAGA: lstNelsonViolations jest NULL!");
        }

        // 4. Detekcja cykli defrostu
        List<DefrostCycle> defrostCycles = DefrostCycleDetector.detectCycles(measurements, positionLabel, 0.25, 1.5);
        defrostTable.setItems(FXCollections.observableArrayList(defrostCycles));
    }

    // -----------------------------------------------------------------------
    // Dane dla zakładki Jednorodności Przestrzennej
    // -----------------------------------------------------------------------

    /**
     * Wypełnia zakładkę "Jednorodność Przestrzenna (ΔT)" danymi przestrzennymi.
     * Wywołaj tę metodę po {@link #setSensorData} gdy dostępny jest wynik
     * {@link com.mac.bry.desktop.service.stats.SpatialStatsService#calculateSpatialStats}.
     *
     * @param result wynik obliczeń przestrzennych dla całej sesji rewalidacji
     */
    public void setSpatialData(SpatialStatsResult result) {
        if (result == null) {
            log.warn("setSpatialData: result jest null — zakładka przestrzenna pozostanie pusta.");
            return;
        }

        // 1. KPI — wartości globalne
        lblMaxDeltaGlobal.setText(String.format(FMT_DELTA, result.getMaxSpatialRange()));
        lblMeanDeltaGlobal.setText(String.format(FMT_DELTA, result.getMeanSpatialRange()));
        lblMaxVerticalGradient.setText(String.format(FMT_DELTA, result.getMaxVerticalGradient()));
        lblMeanVerticalGradient.setText(String.format(FMT_DELTA, result.getMeanVerticalGradient()));

        // 2. Wykres ΔT w czasie — 3 serie
        renderDeltaTimeChart(result);

        // 3. Tabela podsumowania poziomów
        if (result.hasLevelData()) {
            ObservableList<LevelRow> rows = FXCollections.observableArrayList(
                new LevelRow("🔼 GÓRA (TOP)",    result.getMeanRangeTop(),    result.getMaxRangeTop()),
                new LevelRow("🔽 DÓŁ (BOTTOM)",  result.getMeanRangeBottom(), result.getMaxRangeBottom())
            );
            levelSummaryTable.setItems(rows);
        }
    }

    private void renderDeltaTimeChart(SpatialStatsResult result) {
        deltaTimeChart.getData().clear();

        XYChart.Series<Number, Number> globalSeries = buildDeltaSeries("ΔT globalny", result.getSpatialRangesOverTime());
        XYChart.Series<Number, Number> topSeries    = buildDeltaSeries("ΔT GÓRA",    result.getSpatialRangesOverTimeTop());
        XYChart.Series<Number, Number> bottomSeries = buildDeltaSeries("ΔT DÓŁ",     result.getSpatialRangesOverTimeBottom());

        deltaTimeChart.getData().addAll(List.of(globalSeries, topSeries, bottomSeries));
    }

    /**
     * Buduje serię wykresu z mapy timestamp → wartość ΔT.
     * Oś X = kolejny numer pomiaru (1, 2, 3…) — równomierny rozkład
     * niezależnie od rzeczywistego interwału rejestracji.
     */
    private XYChart.Series<Number, Number> buildDeltaSeries(String name, Map<LocalDateTime, Double> data) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(name);
        if (data == null || data.isEmpty()) return series;
        int idx = 1;
        for (Map.Entry<LocalDateTime, Double> entry : data.entrySet()) {
            series.getData().add(new XYChart.Data<>(idx++, entry.getValue()));
        }
        return series;
    }

    // -----------------------------------------------------------------------
    // Renderowanie kart SPC (niezmienione)
    // -----------------------------------------------------------------------

    private void renderXBarChart(ControlChartData data) {
        xBarChart.getData().clear();

        XYChart.Series<Number, Number> meanSeries = new XYChart.Series<>();
        meanSeries.setName("Średnie podgrup");
        XYChart.Series<Number, Number> uclSeries = new XYChart.Series<>();
        uclSeries.setName("UCL (Górna Granica)");
        XYChart.Series<Number, Number> lclSeries = new XYChart.Series<>();
        lclSeries.setName("LCL (Dolna Granica)");
        XYChart.Series<Number, Number> clSeries = new XYChart.Series<>();
        clSeries.setName("CL (Średnia globalna)");

        List<Double> means = data.getSubgroupMeans();
        for (int i = 0; i < means.size(); i++) {
            int x = i + 1;
            meanSeries.getData().add(new XYChart.Data<>(x, means.get(i)));
            uclSeries.getData().add(new XYChart.Data<>(x, data.getXBarUcl()));
            lclSeries.getData().add(new XYChart.Data<>(x, data.getXBarLcl()));
            clSeries.getData().add(new XYChart.Data<>(x, data.getXBarCentralLine()));
        }

        xBarChart.getData().addAll(List.of(clSeries, uclSeries, lclSeries, meanSeries));
    }

    private void renderSChart(ControlChartData data) {
        sChart.getData().clear();

        XYChart.Series<Number, Number> sSeries = new XYChart.Series<>();
        sSeries.setName("Odchylenia podgrup");
        XYChart.Series<Number, Number> uclSeries = new XYChart.Series<>();
        uclSeries.setName("UCL");
        XYChart.Series<Number, Number> lclSeries = new XYChart.Series<>();
        lclSeries.setName("LCL");
        XYChart.Series<Number, Number> clSeries = new XYChart.Series<>();
        clSeries.setName("CL");

        List<Double> stdDevs = data.getSubgroupStdDevs();
        for (int i = 0; i < stdDevs.size(); i++) {
            int x = i + 1;
            sSeries.getData().add(new XYChart.Data<>(x, stdDevs.get(i)));
            uclSeries.getData().add(new XYChart.Data<>(x, data.getSCentralLine() * 2.089)); // B4 * sCL
            lclSeries.getData().add(new XYChart.Data<>(x, 0.0));                            // B3 = 0.0
            clSeries.getData().add(new XYChart.Data<>(x, data.getSCentralLine()));
        }

        sChart.getData().addAll(List.of(clSeries, uclSeries, lclSeries, sSeries));
    }

    @FXML
    public void handleClose() {
        ((Stage) lblSensorTitle.getScene().getWindow()).close();
    }

    // -----------------------------------------------------------------------
    // Wewnętrzny model wiersza tabeli poziomów
    // -----------------------------------------------------------------------

    /** Wiersz tabeli podsumowania jednorodności dla jednego poziomu fizycznego. */
    private record LevelRow(String name, double meanDelta, double maxDelta) {}
}
