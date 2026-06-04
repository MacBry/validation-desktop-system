package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.DetailRow;
import com.mac.bry.desktop.model.ProcedureRow;
import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.ThermoMeasurementPoint;
import com.mac.bry.desktop.model.ThermoMeasurementSeries;
import com.mac.bry.desktop.repository.ThermoMeasurementPointRepository;
import com.mac.bry.desktop.service.TestoRevalidationPdfService;
import com.mac.bry.desktop.service.GxPProcedureService;
import com.mac.bry.desktop.service.JavaFxChartRenderer;
import com.mac.bry.desktop.controller.helper.ProceduresTableHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class ProceduresListController {

    private final ThermoMeasurementPointRepository pointRepository;
    private final TestoRevalidationPdfService pdfService;
    private final GxPProcedureService gxpProcedureService;
    private final JavaFxChartRenderer javaFxChartRenderer;

    @FXML private TableView<ProcedureRow> proceduresTable;
    @FXML private TableColumn<ProcedureRow, String> typeCol;
    @FXML private TableColumn<ProcedureRow, String> locationCol;
    @FXML private TableColumn<ProcedureRow, String> dateCol;
    @FXML private TableColumn<ProcedureRow, String> sensorsCol;
    @FXML private TableColumn<ProcedureRow, Integer> countCol;
    @FXML private TableColumn<ProcedureRow, String> statusCol;
    @FXML private TableColumn<ProcedureRow, Void> actionsCol;
    @FXML private Label proceduresCountLabel;

    // FXML Bindings dla panelu szczegółów metrologicznych GxP (Master-Detail)
    @FXML private VBox detailsContainer;
    @FXML private VBox detailsContent;
    @FXML private StackPane placeholderPane;
    @FXML private Label lblDetailsTitle;
    @FXML private Label lblDetailsDate;
    @FXML private TableView<DetailRow> detailsTableView;
    @FXML private TableColumn<DetailRow, String> colDetailPos;
    @FXML private TableColumn<DetailRow, String> colDetailSn;
    @FXML private TableColumn<DetailRow, String> colDetailMin;
    @FXML private TableColumn<DetailRow, String> colDetailMax;
    @FXML private TableColumn<DetailRow, String> colDetailAvg;
    @FXML private TableColumn<DetailRow, String> colDetailMkt;
    @FXML private TableColumn<DetailRow, String> colDetailUncertainty;
    @FXML private TableColumn<DetailRow, String> colDetailSpikes;
    @FXML private TableColumn<DetailRow, String> colDetailDrift;

    private final ObservableList<ProcedureRow> tableData = FXCollections.observableArrayList();
    private final ObservableList<DetailRow> detailsData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        ProceduresTableHelper.setupTableColumns(
                typeCol, locationCol, dateCol, sensorsCol, countCol, statusCol, actionsCol, this::handleGeneratePdf
        );
        ProceduresTableHelper.setupDetailsTableColumns(
                colDetailPos, colDetailSn, colDetailMin, colDetailMax, colDetailAvg, colDetailMkt,
                colDetailUncertainty, colDetailSpikes, colDetailDrift
        );
        setupSelectionListener();
        loadProcedures();

        proceduresTable.setItems(tableData);
        detailsTableView.setItems(detailsData);
    }

    private void setupSelectionListener() {
        proceduresTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showProcedureDetails(newVal);
            } else {
                hideProcedureDetails();
            }
        });
    }

    private void hideProcedureDetails() {
        placeholderPane.setVisible(true);
        placeholderPane.setManaged(true);
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        detailsData.clear();
    }

    private void showProcedureDetails(ProcedureRow row) {
        placeholderPane.setVisible(false);
        placeholderPane.setManaged(false);
        detailsContent.setVisible(true);
        detailsContent.setManaged(true);

        lblDetailsTitle.setText("Analiza Metrologiczna: " + row.getLocation());
        lblDetailsDate.setText("Wykonano: " + row.getDateImported());

        detailsData.clear();

        Task<List<DetailRow>> loadDetailsTask = new Task<>() {
            @Override
            protected List<DetailRow> call() throws Exception {
                return gxpProcedureService.loadDetailRows(row.getAssociatedSeries());
            }
        };

        loadDetailsTask.setOnSucceeded(e -> detailsData.setAll(loadDetailsTask.getValue()));
        loadDetailsTask.setOnFailed(e -> log.error("Błąd ładowania szczegółów metrologicznych procedury", loadDetailsTask.getException()));
        new Thread(loadDetailsTask).start();
    }

    private void loadProcedures() {
        Task<List<ProcedureRow>> task = new Task<>() {
            @Override
            protected List<ProcedureRow> call() throws Exception {
                return gxpProcedureService.loadProcedures();
            }
        };

        task.setOnSucceeded(e -> {
            tableData.setAll(task.getValue());
            proceduresCountLabel.setText("Łącznie: " + tableData.size() + " procedur");
        });

        task.setOnFailed(e -> {
            log.error("Błąd podczas ładowania listy procedur GxP", task.getException());
            Alert alert = new Alert(Alert.AlertType.ERROR, "Nie udało się załadować listy procedur z bazy danych.");
            alert.show();
        });

        new Thread(task).start();
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadProcedures();
    }

    private void handleGeneratePdf(ProcedureRow row) {
        log.info("Rozpoczęcie regeneracji raportu PDF dla procedury: {}", row.getType());
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Zapisz Raport GxP PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pliki PDF (*.pdf)", "*.pdf"));
        
        String defaultFileName = "RAPORT_HISTORYCZNY_GxP_" 
                + (row.getDevice() != null ? row.getDevice().getInventoryNumber() : "LOG") + "_"
                + row.getDateImported().replace(":", "-").replace(" ", "_") + ".pdf";
        fileChooser.setInitialFileName(defaultFileName);

        Stage stage = (Stage) proceduresTable.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file == null) {
            return;
        }

        File tempImageFile = null;
        try {
            // 1. Rekonstrukcja zsynchronizowanych punktów pomiarowych z bazy
            List<ThermoMeasurementSeries> seriesList = new ArrayList<>();
            for (ThermoMeasurementSeries s : row.getAssociatedSeries()) {
                List<ThermoMeasurementPoint> pts = pointRepository.findBySeriesIdOrderByMeasurementIndexAsc(s.getId());
                s.setMeasurements(pts);
                seriesList.add(s);
            }

            // 2. Off-screen renderowanie wykresu
            tempImageFile = javaFxChartRenderer.renderMultipleSeriesToPng(seriesList);

            // 3. Rekonstrukcja sesji rewalidacji w pamięci
            RevalidationSession session = RevalidationSession.builder()
                    .coolingDevice(row.getDevice())
                    .coolingChamber(row.getChamber())
                    .build();

            var positions = RevalidationSession.GridPosition.values();
            for (int i = 0; i < seriesList.size(); i++) {
                var s = seriesList.get(i);
                var posData = RevalidationSession.PositionData.builder()
                        .serialNumber(s.getThermoRecorder().getSerialNumber())
                        .model(s.getThermoRecorder().getModel())
                        .recorder(s.getThermoRecorder())
                        .latestCalibration(s.getThermoRecorder().getLatestCalibration())
                        .series(s)
                        .build();
                
                RevalidationSession.GridPosition gridPos = s.getGridPosition() != null 
                        ? s.getGridPosition() 
                        : positions[i % positions.length];
                session.getAssignedPositions().put(gridPos, posData);
            }

            // 4. Kompilacja raportu PDF
            pdfService.generateRevalidationReport(session, file, tempImageFile);
            
            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Raport Wygenerowany");
            success.setHeaderText("Sukces kompilacji PDF!");
            success.setContentText("Historyczny raport metrologiczny GxP został pomyślnie zrekonstruowany i zapisany w pliku:\n" + file.getAbsolutePath());
            success.showAndWait();

        } catch (Exception e) {
            log.error("Błąd podczas regeneracji raportu PDF", e);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Błąd Zapisu PDF");
            error.setHeaderText("Nie udało się zrekonstruować raportu PDF");
            error.setContentText(e.getMessage() != null ? e.getMessage() : e.toString());
            error.showAndWait();
        } finally {
            if (tempImageFile != null && tempImageFile.exists()) {
                tempImageFile.delete();
            }
        }
    }
}
