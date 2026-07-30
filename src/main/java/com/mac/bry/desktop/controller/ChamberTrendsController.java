package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.dto.stats.ChamberSessionSnapshot;
import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.CoolingDevice;
import com.mac.bry.desktop.service.CoolingDeviceService;
import com.mac.bry.desktop.service.SessionComparisonService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard porównań między sesjami: trendy jednorodności przestrzennej (ΔT)
 * i stabilności (max std dev) tej samej komory w kolejnych rewalidacjach —
 * wczesne wykrywanie degradacji sprzętu, zanim komora wypadnie ze specyfikacji.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChamberTrendsController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CoolingDeviceService coolingDeviceService;
    private final SessionComparisonService sessionComparisonService;

    @FXML private ComboBox<CoolingDevice> deviceCombo;
    @FXML private ComboBox<CoolingChamber> chamberCombo;
    @FXML private Label lblSessionCount;
    @FXML private Label lblNoData;
    @FXML private LineChart<String, Number> trendChart;
    @FXML private CategoryAxis xAxisSessions;
    @FXML private TableView<ChamberSessionSnapshot> sessionTable;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colDate;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colSeries;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colMean;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colDeltaT;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colDeltaTChange;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colStd;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colStdChange;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colHotspot;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colColdspot;
    @FXML private TableColumn<ChamberSessionSnapshot, String> colSpikes;

    @FXML
    public void initialize() {
        setupCombos();
        setupTable();
        deviceCombo.setItems(FXCollections.observableArrayList(coolingDeviceService.findAll()));
    }

    private void setupCombos() {
        deviceCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(CoolingDevice d) {
                return d != null ? d.getName() + " (" + d.getInventoryNumber() + ")" : "";
            }

            @Override
            public CoolingDevice fromString(String s) {
                return null;
            }
        });
        chamberCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(CoolingChamber c) {
                return c != null ? c.getChamberName() : "";
            }

            @Override
            public CoolingChamber fromString(String s) {
                return null;
            }
        });

        deviceCombo.valueProperty().addListener((obs, old, device) -> {
            chamberCombo.getItems().clear();
            chamberCombo.setDisable(device == null);
            if (device != null) {
                chamberCombo.setItems(FXCollections.observableArrayList(device.getChambers()));
                if (device.getChambers().size() == 1) {
                    chamberCombo.setValue(device.getChambers().get(0));
                }
            }
        });
        chamberCombo.valueProperty().addListener((obs, old, chamber) -> refresh(chamber));
    }

    private void setupTable() {
        colDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSessionDate().format(DATE_FMT)));
        colSeries.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().getSeriesCount())));
        colMean.setCellValueFactory(c -> new SimpleStringProperty(
                fmt(c.getValue().getMeanTemperature())));
        colDeltaT.setCellValueFactory(c -> new SimpleStringProperty(
                fmt(c.getValue().getSpatialDeltaT())));
        colDeltaTChange.setCellValueFactory(c -> new SimpleStringProperty(
                fmtDelta(c.getValue().getDeltaSpatialDeltaT())));
        colStd.setCellValueFactory(c -> new SimpleStringProperty(
                fmt(c.getValue().getMaxStdDeviation())));
        colStdChange.setCellValueFactory(c -> new SimpleStringProperty(
                fmtDelta(c.getValue().getDeltaMaxStdDeviation())));
        colHotspot.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getHotspotPosition() != null
                        ? c.getValue().getHotspotPosition().getLabel() : "–"));
        colColdspot.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getColdspotPosition() != null
                        ? c.getValue().getColdspotPosition().getLabel() : "–"));
        colSpikes.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().getTotalSpikeCount())));
    }

    private void refresh(CoolingChamber chamber) {
        trendChart.getData().clear();
        sessionTable.getItems().clear();
        lblSessionCount.setText("");
        showNoData(false);
        if (chamber == null) {
            return;
        }

        List<ChamberSessionSnapshot> history =
                sessionComparisonService.getSessionHistory(chamber.getId());

        if (history.isEmpty()) {
            showNoData(true);
            return;
        }

        sessionTable.setItems(FXCollections.observableArrayList(history));
        lblSessionCount.setText(I18n.t("chambertrends.session_count", history.size()));

        XYChart.Series<String, Number> deltaTSeries = new XYChart.Series<>();
        deltaTSeries.setName(I18n.t("chambertrends.series_deltat"));
        XYChart.Series<String, Number> stdSeries = new XYChart.Series<>();
        stdSeries.setName(I18n.t("chambertrends.series_std"));

        for (ChamberSessionSnapshot snap : history) {
            String label = snap.getSessionDate().format(DATE_FMT);
            if (snap.getSpatialDeltaT() != null) {
                deltaTSeries.getData().add(new XYChart.Data<>(label, snap.getSpatialDeltaT()));
            }
            if (snap.getMaxStdDeviation() != null) {
                stdSeries.getData().add(new XYChart.Data<>(label, snap.getMaxStdDeviation()));
            }
        }
        trendChart.getData().add(deltaTSeries);
        trendChart.getData().add(stdSeries);
    }

    private void showNoData(boolean show) {
        lblNoData.setVisible(show);
        lblNoData.setManaged(show);
    }

    private static String fmt(Double v) {
        return v != null ? String.format(Locale.forLanguageTag("pl"), "%.2f", v) : "–";
    }

    /** Delta ze znakiem i strzałką kierunku (▲ = pogorszenie, ▼ = poprawa). */
    private static String fmtDelta(Double v) {
        if (v == null) return "–";
        String arrow = v > 0.005 ? " ▲" : (v < -0.005 ? " ▼" : "");
        return String.format(Locale.forLanguageTag("pl"), "%+.2f%s", v, arrow);
    }
}
