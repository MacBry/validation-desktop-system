package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.service.TestoRevalidationFacade;
import com.mac.bry.desktop.controller.helper.TestoRevalidationTableHelper;
import com.mac.bry.desktop.service.stats.SensorStatsEngine;
import com.mac.bry.desktop.service.stats.SpcEngine;
import org.springframework.context.ApplicationContext;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.fxml.FXMLLoader;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import com.mac.bry.desktop.dto.stats.AnovaResult;
import com.mac.bry.desktop.dto.stats.TostResult;
import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kontroler kreatora rewalidacji komór chłodniczych GxP.
 *
 * Po refaktoryzacji pełni wyłącznie rolę orkiestratora UI:
 *  - zarządza stanem sesji i nawigacją po krokach kreatora
 *  - obsługuje kliknięcia siatki sensorów i asynchroniczny odczyt USB
 *  - buduje widok podsumowania (tabele + wykres wielokanałowy)
 *  - deleguje rendering wykresów do {@link JavaFxChartRenderer}
 *  - deleguje kompilację pakietu ZIP do {@link RevalidationZipCompiler}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TestoRevalidationController {

    private final TestoRevalidationFacade facade;
    private final ApplicationContext applicationContext;

    private RevalidationSession session;
    private RevalidationSession.GridPosition selectedPosition;

    // ---- KROK 1 ----
    @FXML private TabPane revalidationTabPane;
    @FXML private ComboBox<CoolingDevice> deviceComboBox;
    @FXML private ComboBox<CoolingChamber> chamberComboBox;
    @FXML private ComboBox<GxPProcedureType> procedureTypeComboBox;
    @FXML private ComboBox<com.mac.bry.desktop.model.regime.RunMode> runModeComboBox;
    @FXML private Label lblRunModeHint;
    @FXML private Label lblProcedureWarning;
    @FXML private TextField chamberNameField;
    @FXML private TextField chamberTypeField;
    @FXML private TextField chamberRangeField;
    @FXML private TextField chamberVolumeField;
    @FXML private TextField chamberRecommendationField;
    @FXML private TextField chamberMappingRequiredField;
    @FXML private TextField chamberLastMappingDateField;
    @FXML private TextField chamberMappingSpotsField;
    @FXML private Button btnStep1Next;

    // ---- NAGŁÓWEK KREATORA (dynamiczny) ----
    @FXML private Label lblWizardTitle;
    @FXML private Label lblWizardSubtitle;
    @FXML private Label lblStep1Header;

    // ---- KROK 2 ----
    @FXML private Button btnTopFrontLeft;
    @FXML private Button btnTopFrontRight;
    @FXML private Button btnTopBackLeft;
    @FXML private Button btnTopBackRight;
    @FXML private Button btnBottomFrontLeft;
    @FXML private Button btnBottomFrontRight;
    @FXML private Button btnBottomBackLeft;
    @FXML private Button btnBottomBackRight;

    @FXML private VBox usbImportPanel;
    @FXML private Label lblSelectedPosition;
    @FXML private Button btnReadUsb;
    @FXML private Button btnReadPdf184;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private ProgressBar progressBar;
    @FXML private TextField txtSensorModel;
    @FXML private TextField txtSensorSn;
    @FXML private TextField txtSensorBattery;
    @FXML private TextField txtSensorCert;
    @FXML private TextField txtSensorCertValid;
    @FXML private TextField txtSensorPointCount;
    @FXML private Label lblReadStatus;
    @FXML private Label lblMappingSummary;
    @FXML private Button btnStep2Next;

    // ---- KROK 3 ----
    @FXML private LineChart<Number, Number> multiChannelChart;
    @FXML private NumberAxis xAxisTime;
    @FXML private NumberAxis yAxisTemp;

    @FXML private TableView<SummaryRow> summaryTableView;
    @FXML private TableColumn<SummaryRow, String> colPosName;
    @FXML private TableColumn<SummaryRow, String> colPosSn;
    @FXML private TableColumn<SummaryRow, String> colPosModel;
    @FXML private TableColumn<SummaryRow, String> colPosCert;
    @FXML private TableColumn<SummaryRow, String> colPosCertValid;
    @FXML private TableColumn<SummaryRow, Integer> colPosCount;
    @FXML private TableColumn<SummaryRow, String> colPosStatus;

    @FXML private TableView<MetrologicalRow> metrologicalTableView;
    @FXML private TableColumn<MetrologicalRow, String> colMetroPos;
    @FXML private TableColumn<MetrologicalRow, String> colMetroSn;
    @FXML private TableColumn<MetrologicalRow, String> colMetroMin;
    @FXML private TableColumn<MetrologicalRow, String> colMetroMax;
    @FXML private TableColumn<MetrologicalRow, String> colMetroAvg;
    @FXML private TableColumn<MetrologicalRow, String> colMetroMkt;
    @FXML private TableColumn<MetrologicalRow, String> colMetroUnc;
    @FXML private TableColumn<MetrologicalRow, String> colMetroSpikes;
    @FXML private TableColumn<MetrologicalRow, String> colMetroDrift;

    @FXML private TableView<StatsRow> statsTableView;
    @FXML private TableColumn<StatsRow, String> colStatsPos;
    @FXML private TableColumn<StatsRow, Double> colStatsMedian;
    @FXML private TableColumn<StatsRow, Double> colStatsStdDev;
    @FXML private TableColumn<StatsRow, Double> colStatsRsd;
    @FXML private TableColumn<StatsRow, Double> colStatsSkewness;
    @FXML private TableColumn<StatsRow, Double> colStatsKurtosis;
    @FXML private TableColumn<StatsRow, Double> colStatsCp;
    @FXML private TableColumn<StatsRow, Double> colStatsCpk;
    @FXML private TableColumn<StatsRow, Double> colStatsJbPVal;
    @FXML private TableColumn<StatsRow, Void> colStatsAction;

    @FXML private Label lblTestInterval;
    @FXML private Label lblTestPoints;
    @FXML private Label lblTestStart;
    @FXML private Label lblTestCertificates;
    @FXML private Label lblValidationSummary;
    @FXML private Button btnSaveAndGenerate;

    // ---- KROK 3 — Wyniki mapowania PDA TR-64 ----
    @FXML private javafx.scene.layout.VBox mappingResultBox;
    @FXML private Label lblHotspotLocation;
    @FXML private Label lblHotspotTemp;
    @FXML private Label lblColdspotLocation;
    @FXML private Label lblColdspotTemp;
    @FXML private Label lblMappingDeltaT;

    @FXML private VBox spatialTab;
    @FXML private SpatialTabController spatialTabController;

    @FXML private VBox hypothesisTab;
    @FXML private HypothesisTabController hypothesisTabController;

    private final Map<RevalidationSession.GridPosition, Button> gridButtons = new HashMap<>();
    private final ObservableList<SummaryRow> summaryRows = FXCollections.observableArrayList();
    private final ObservableList<MetrologicalRow> metrologicalRows = FXCollections.observableArrayList();
    private final ObservableList<StatsRow> statsRows = FXCollections.observableArrayList();

    // ============================================================
    // INICJALIZACJA
    // ============================================================

    @FXML
    public void initialize() {
        log.info("Inicjalizacja kontrolera kreatora rewalidacji");
        gridButtons.put(RevalidationSession.GridPosition.TOP_FRONT_LEFT,    btnTopFrontLeft);
        gridButtons.put(RevalidationSession.GridPosition.TOP_FRONT_RIGHT,   btnTopFrontRight);
        gridButtons.put(RevalidationSession.GridPosition.TOP_BACK_LEFT,     btnTopBackLeft);
        gridButtons.put(RevalidationSession.GridPosition.TOP_BACK_RIGHT,    btnTopBackRight);
        gridButtons.put(RevalidationSession.GridPosition.BOTTOM_FRONT_LEFT, btnBottomFrontLeft);
        gridButtons.put(RevalidationSession.GridPosition.BOTTOM_FRONT_RIGHT,btnBottomFrontRight);
        gridButtons.put(RevalidationSession.GridPosition.BOTTOM_BACK_LEFT,  btnBottomBackLeft);
        gridButtons.put(RevalidationSession.GridPosition.BOTTOM_BACK_RIGHT, btnBottomBackRight);

        setupStep1();
        setupRunModeCombo();

        TestoRevalidationTableHelper.setupSummaryTable(summaryTableView, colPosName, colPosSn, colPosModel, colPosCert, colPosCertValid, colPosCount, colPosStatus);
        TestoRevalidationTableHelper.setupMetrologicalTable(metrologicalTableView, colMetroPos, colMetroSn, colMetroMin, colMetroMax, colMetroAvg, colMetroMkt, colMetroUnc, colMetroSpikes, colMetroDrift);
        TestoRevalidationTableHelper.setupStatsTable(statsTableView, colStatsPos, colStatsMedian, colStatsStdDev, colStatsRsd, colStatsSkewness, colStatsKurtosis, colStatsCp, colStatsCpk, colStatsJbPVal, colStatsAction, this::handleShowDiagnostics);
        


        summaryTableView.setItems(summaryRows);
        metrologicalTableView.setItems(metrologicalRows);
        statsTableView.setItems(statsRows);
        
        resetGridButtons();
        // Domyślnie: Rewalidacja Okresowa
        applyProcedureTypeUI(GxPProcedureType.PERIODIC_REVALIDATION);
    }

    /**
     * Konfiguruje ComboBox deklaracji trybu runu (DP-001 §4.5).
     * Wybór operatora przenoszony do sesji przy jej inicjalizacji oraz na bieżąco.
     */
    private void setupRunModeCombo() {
        runModeComboBox.setItems(FXCollections.observableArrayList(
                com.mac.bry.desktop.model.regime.RunMode.values()));
        runModeComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(com.mac.bry.desktop.model.regime.RunMode mode) {
                if (mode == null) return "";
                return switch (mode) {
                    case QUALIFICATION -> "Kwalifikacja (IQ/OQ/PQ)";
                    case CHARACTERIZATION -> "Charakteryzacja";
                    case MONITORING -> "Monitoring okresowy";
                };
            }

            @Override
            public com.mac.bry.desktop.model.regime.RunMode fromString(String s) {
                return null;
            }
        });
        runModeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            lblRunModeHint.setText(switch (newVal) {
                case QUALIFICATION -> "Kwalifikacja: rygorystyczne kryteria WHO/GMP — ekskursja w fazie ustalonej oznacza FAIL.";
                case CHARACTERIZATION -> "Charakteryzacja: obserwacja zachowania — zdarzenia raportowane jako FINDING.";
                case MONITORING -> "Monitoring: porównanie ze stanem bazowym — odchylenia raportowane jako WARNING.";
            });
            if (session != null) {
                session.setRunMode(newVal);
                log.info("Operator zadeklarował tryb runu: {}", newVal);
            }
        });
        // Domyślnie CHARACTERIZATION — bezpieczna wartość (nie nakłada kryteriów kwalifikacyjnych)
        runModeComboBox.setValue(com.mac.bry.desktop.model.regime.RunMode.CHARACTERIZATION);
    }

    /**
     * Wywoływana przez MainController gdy użytkownik wybierze procedurę z paska bocznego.
     * Preselektuje typ procedury i aktualizuje tytuły kreatora.
     */
    public void initWithProcedureType(GxPProcedureType type) {
        procedureTypeComboBox.setValue(type);
        applyProcedureTypeUI(type);
    }

    /**
     * Ustawia tytuły, podtytuły i etykiety kreatora zgodnie z typem procedury.
     */
    private void applyProcedureTypeUI(GxPProcedureType type) {
        if (type == GxPProcedureType.MAPPING) {
            lblWizardTitle.setText("🗺️ Kreator Mapowania Komory Chłodniczej GxP");
            lblWizardSubtitle.setText("Procedura wyznaczania Hotspotu i Coldspotu — mapowanie 5-letnie zgodnie z PDA TR-64");
            lblStep1Header.setText("Krok 1: Wybór Urządzenia i Komory do Mapowania");
        } else {
            lblWizardTitle.setText("🔁 Kreator Rewalidacji Okresowej Komory Chłodniczej GxP");
            lblWizardSubtitle.setText("Procedura weryfikacji warunków temperaturowych w oparciu o wyniki mapowania (PDA TR-64)");
            lblStep1Header.setText("Krok 1: Wybór Urządzenia i Komory do Rewalidacji");
        }
    }

    // ============================================================
    // KROK 1 – WYBÓR URZĄDZENIA I KOMORY
    // ============================================================

    private void setupStep1() {
        deviceComboBox.setConverter(new StringConverter<>() {
            @Override public String toString(CoolingDevice d) {
                return d == null ? "" : d.getName() + " [" + d.getInventoryNumber() + "]";
            }
            @Override public CoolingDevice fromString(String s) { return null; }
        });
        chamberComboBox.setConverter(new StringConverter<>() {
            @Override public String toString(CoolingChamber c) {
                return c == null ? "" : c.getChamberName() + " (" + c.getChamberType().getDisplayName() + ")";
            }
            @Override public CoolingChamber fromString(String s) { return null; }
        });
        procedureTypeComboBox.setConverter(new StringConverter<>() {
            @Override public String toString(GxPProcedureType t) {
                return t == null ? "" : t.getDisplayName();
            }
            @Override public GxPProcedureType fromString(String s) { return null; }
        });
        procedureTypeComboBox.setItems(FXCollections.observableArrayList(GxPProcedureType.values()));

        deviceComboBox.setItems(FXCollections.observableArrayList(facade.findAllDevices()));

        deviceComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, nv) -> {
            chamberComboBox.getSelectionModel().clearSelection();
            clearChamberDetails();
            if (nv != null) {
                chamberComboBox.setItems(FXCollections.observableArrayList(
                        facade.findChambersByDeviceId(nv.getId())));
                chamberComboBox.setDisable(false);
            } else {
                chamberComboBox.setDisable(true);
            }
        });

        chamberComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, nv) -> {
            if (nv != null) {
                populateChamberDetails(nv);
                // Jeśli typ procedury nie jest jeszcze ustawiony (np. wejście bez preselekcji), ustaw domyślny
                if (procedureTypeComboBox.getSelectionModel().getSelectedItem() == null) {
                    procedureTypeComboBox.getSelectionModel().select(GxPProcedureType.PERIODIC_REVALIDATION);
                }
                validateStep1Selection();
            } else {
                clearChamberDetails();
                validateStep1Selection();
            }
        });

        // Listener na wypadek gdyby ktoś programowo zmienił wartość ComboBox
        procedureTypeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, nv) -> {
            if (nv != null) applyProcedureTypeUI(nv);
            validateStep1Selection();
        });

        // Defensywne ukrywanie wyboru procedury w kreatorze (na wypadek nieodświeżonego pliku FXML)
        if (procedureTypeComboBox != null) {
            procedureTypeComboBox.setVisible(false);
            procedureTypeComboBox.setManaged(false);
            
            // Jeśli ComboBox znajduje się w dedykowanym VBox z opisem (starsze FXML), ukrywamy cały VBox
            javafx.scene.Parent parent = procedureTypeComboBox.getParent();
            if (parent instanceof VBox) {
                VBox parentVBox = (VBox) parent;
                if (parentVBox.getChildren().size() == 2 && parentVBox.getChildren().get(0) instanceof Label) {
                    parentVBox.setVisible(false);
                    parentVBox.setManaged(false);
                }
            }
        }
    }

    private void validateStep1Selection() {
        CoolingChamber chamber = chamberComboBox.getSelectionModel().getSelectedItem();
        GxPProcedureType procedureType = procedureTypeComboBox.getSelectionModel().getSelectedItem();
        
        if (chamber == null || procedureType == null) {
            btnStep1Next.setDisable(true);
            lblProcedureWarning.setText("");
            return;
        }

        if (procedureType == GxPProcedureType.PERIODIC_REVALIDATION) {
            if (chamber.isMappingRequired()) {
                if (!chamber.isMappingValid()) {
                    lblProcedureWarning.setText("❌ Brak ważnego mapowania 5-letniego komory! Wykonaj najpierw procedurę mapowania.");
                    btnStep1Next.setDisable(true);
                    return;
                }
            }
        }

        lblProcedureWarning.setText("");
        btnStep1Next.setDisable(false);
    }

    private void populateChamberDetails(CoolingChamber c) {
        chamberNameField.setText(c.getChamberName());
        chamberTypeField.setText(c.getChamberType().getDisplayName());
        chamberRangeField.setText(c.getFormattedMinOperatingTemp() + " do " + c.getFormattedMaxOperatingTemp());
        chamberVolumeField.setText(c.getFormattedVolume() + " (" + c.getVolumeCategoryDisplayName() + ")");
        chamberRecommendationField.setText(c.getVolumeCategory() != null
                ? c.getVolumeCategory().getValidationRequirements() : "Brak określonych wymagań.");
        chamberMappingRequiredField.setText(c.isMappingRequired() ? "TAK" : "NIE");
        chamberLastMappingDateField.setText(c.getLastMappingDate() != null ? c.getLastMappingDate().toString() : "brak");
        if (c.getHotspotPosition() != null && c.getColdspotPosition() != null) {
            chamberMappingSpotsField.setText("Hotspot: " + c.getHotspotPosition().getLabel() + " / Coldspot: " + c.getColdspotPosition().getLabel());
        } else {
            chamberMappingSpotsField.setText("brak");
        }
    }

    private void clearChamberDetails() {
        chamberNameField.clear(); chamberTypeField.clear();
        chamberRangeField.clear(); chamberVolumeField.clear(); chamberRecommendationField.clear();
        chamberMappingRequiredField.clear(); chamberLastMappingDateField.clear(); chamberMappingSpotsField.clear();
        lblProcedureWarning.setText("");
    }

    // ============================================================
    // NAWIGACJA
    // ============================================================

    @FXML public void handleStep1Next(ActionEvent e) {
        CoolingDevice device = deviceComboBox.getSelectionModel().getSelectedItem();
        CoolingChamber chamber = chamberComboBox.getSelectionModel().getSelectedItem();
        GxPProcedureType procedureType = procedureTypeComboBox.getSelectionModel().getSelectedItem();
        if (device != null && chamber != null && procedureType != null) {
            session = facade.initSession(device, chamber, procedureType);
            if (runModeComboBox.getValue() != null) {
                session.setRunMode(runModeComboBox.getValue());
            }
            resetGridButtons();
            refreshGridHighlight();
            selectedPosition = null;
            lblSelectedPosition.setText("Wybrana pozycja: brak");
            usbImportPanel.setDisable(true);
            clearSensorFields();
            updateStep2Summary();
            revalidationTabPane.getSelectionModel().select(1);
        }
    }

    @FXML public void handleStep2Back(ActionEvent e) { revalidationTabPane.getSelectionModel().select(0); }

    @FXML public void handleStep2Next(ActionEvent e) {
        if (session.getProcedureType() == GxPProcedureType.MAPPING) {
            com.mac.bry.desktop.service.helper.MappingValidator.MappingResult validationResult =
                    com.mac.bry.desktop.service.helper.MappingValidator.validate(session);
            if (!validationResult.isSuccess()) {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setTitle("Błąd walidacji mapowania GxP");
                a.setHeaderText("Procedura mapowania nie spełnia kryteriów PDA TR-64");
                a.setContentText(validationResult.getErrorMessage());
                a.showAndWait();
                return;
            }
        }
        revalidationTabPane.getSelectionModel().select(2);
        buildSummaryAndValidation();
    }

    @FXML public void handleStep3Back(ActionEvent e) { revalidationTabPane.getSelectionModel().select(1); }

    // ============================================================
    // KROK 2 – SIATKA SENSORÓW I ODCZYT USB
    // ============================================================

    private void resetGridButtons() {
        if (session != null) {
            refreshGridHighlight();
        } else {
            gridButtons.forEach((pos, btn) -> { btn.setStyle(""); btn.setText(pos.getLabel().replace(" - ", "\n")); });
        }
    }

    @FXML public void handleGridClick(ActionEvent event) {
        Button clicked = (Button) event.getSource();
        for (var entry : gridButtons.entrySet()) {
            if (entry.getValue() == clicked) { selectedPosition = entry.getKey(); break; }
        }
        if (selectedPosition == null) return;

        lblSelectedPosition.setText("Wybrana pozycja: " + selectedPosition.getLabel());
        usbImportPanel.setDisable(false);
        refreshGridHighlight();

        RevalidationSession.PositionData posData = session.getAssignedPositions().get(selectedPosition);
        if (posData != null) {
            fillSensorFields(posData);
            lblReadStatus.setText("Status: Pozycja załadowana danymi pomiarowymi.");
        } else {
            clearSensorFields();
            lblReadStatus.setText("Status: Oczekiwanie na odczyt z kołyski USB...");
        }
    }

    private void refreshGridHighlight() {
        CoolingChamber chamber = session != null ? session.getCoolingChamber() : null;
        RevalidationSession.GridPosition hotspot = (chamber != null && session.getProcedureType() == GxPProcedureType.PERIODIC_REVALIDATION && chamber.isMappingRequired())
                ? chamber.getHotspotPosition() : null;
        RevalidationSession.GridPosition coldspot = (chamber != null && session.getProcedureType() == GxPProcedureType.PERIODIC_REVALIDATION && chamber.isMappingRequired())
                ? chamber.getColdspotPosition() : null;

        gridButtons.forEach((pos, btn) -> {
            boolean loaded = session != null && session.getAssignedPositions().containsKey(pos);
            boolean selected = pos == selectedPosition;
            boolean isHotspot = pos == hotspot;
            boolean isColdspot = pos == coldspot;

            // Zbuduj tekst przycisku
            if (loaded) {
                RevalidationSession.PositionData data = session.getAssignedPositions().get(pos);
                String suffix = data != null ? "\n(" + data.getSerialNumber() + ")" : "";
                String prefix = isHotspot ? "🔴 Hotspot\n" : (isColdspot ? "🔵 Coldspot\n" : "[OK] ");
                btn.setText(prefix + pos.getLabel().replace(" - ", "\n") + suffix);
            } else {
                String prefix = isHotspot ? "🔴 Hotspot\n" : (isColdspot ? "🔵 Coldspot\n" : "");
                btn.setText(prefix + pos.getLabel().replace(" - ", "\n"));
            }

            // Ustaw styl przycisku
            if (loaded && selected && isHotspot)
                btn.setStyle("-fx-background-color: #dc6803; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-color: -color-accent-emphasis; -fx-border-width: 3px; -fx-border-radius: 4px;");
            else if (loaded && selected && isColdspot)
                btn.setStyle("-fx-background-color: #1e6ab4; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-color: -color-accent-emphasis; -fx-border-width: 3px; -fx-border-radius: 4px;");
            else if (loaded && selected)
                btn.setStyle("-fx-background-color: -color-success-emphasis; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-color: -color-accent-emphasis; -fx-border-width: 3px; -fx-border-radius: 4px;");
            else if (loaded && isHotspot)
                btn.setStyle("-fx-background-color: #dc6803; -fx-text-fill: white; -fx-font-weight: bold;");
            else if (loaded && isColdspot)
                btn.setStyle("-fx-background-color: #1e6ab4; -fx-text-fill: white; -fx-font-weight: bold;");
            else if (loaded)
                btn.setStyle("-fx-background-color: -color-success-emphasis; -fx-text-fill: white; -fx-font-weight: bold;");
            else if (selected && isHotspot)
                btn.setStyle("-fx-background-color: rgba(220,104,3,0.2); -fx-border-color: #dc6803; -fx-border-width: 3px; -fx-border-radius: 4px; -fx-font-weight: bold;");
            else if (selected && isColdspot)
                btn.setStyle("-fx-background-color: rgba(30,106,180,0.2); -fx-border-color: #1e6ab4; -fx-border-width: 3px; -fx-border-radius: 4px; -fx-font-weight: bold;");
            else if (selected)
                btn.setStyle("-fx-border-color: -color-accent-emphasis; -fx-border-width: 3px; -fx-border-radius: 4px; -fx-font-weight: bold;");
            else if (isHotspot)
                btn.setStyle("-fx-background-color: rgba(220,104,3,0.15); -fx-border-color: #dc6803; -fx-border-width: 2px; -fx-border-radius: 4px;");
            else if (isColdspot)
                btn.setStyle("-fx-background-color: rgba(30,106,180,0.15); -fx-border-color: #1e6ab4; -fx-border-width: 2px; -fx-border-radius: 4px;");
            else
                btn.setStyle("");
        });
    }

    private void fillSensorFields(RevalidationSession.PositionData d) {
        txtSensorModel.setText(d.getModel() != null ? d.getModel().getName() : "");
        txtSensorSn.setText(d.getSerialNumber());
        int battery = d.getSeries().getBatteryLevelPercent();
        txtSensorBattery.setText(battery >= 0 ? battery + "%" : "N/D (import PDF)");
        txtSensorCert.setText(d.getLatestCalibration() != null ? d.getLatestCalibration().getCertificateNumber() : "Brak wzorcowania");
        txtSensorCertValid.setText(d.getLatestCalibration() != null ? d.getLatestCalibration().getValidUntil().toString() : "–");
        txtSensorPointCount.setText(String.valueOf(d.getSeries().getMeasurements().size()));
    }

    private void clearSensorFields() {
        txtSensorModel.clear(); txtSensorSn.clear(); txtSensorBattery.clear();
        txtSensorCert.clear(); txtSensorCertValid.clear(); txtSensorPointCount.clear();
    }

    @FXML public void handleReadPositionUsb(ActionEvent event) {
        if (selectedPosition == null) return;
        setProgressVisible(true);
        lblReadStatus.setText("Status: Wykrywanie kołyski i odczyt danych pomiarowych...");

        Task<RevalidationSession.PositionData> task = new Task<>() {
            @Override protected RevalidationSession.PositionData call() throws Exception {
                return facade.readPositionData(session, selectedPosition, false);
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> handleSuccessfulReadout(task.getValue()));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            log.warn("Brak sprzętu Testo lub błąd: {}", ex.getMessage());
            setProgressVisible(false);
            progressBar.progressProperty().unbind();
            btnReadUsb.setDisable(false);
            offerSimulation(ex.getMessage());
        });
        new Thread(task).start();
    }

    /**
     * Importuje serię pomiarową z pliku PDF raportu Testo 184T.
     * Stosuje identyczny rygor GxP jak USB: blokada S/N spoza ewidencji VCC.
     */
    @FXML public void handleReadPdf184(ActionEvent event) {
        if (selectedPosition == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Wybierz raport PDF z Testo 184T");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Raport PDF Testo 184T (*.pdf)", "*.pdf"));
        File pdfFile = fc.showOpenDialog(btnReadPdf184.getScene().getWindow());
        if (pdfFile == null) return;

        setProgressVisible(true);
        btnReadPdf184.setDisable(true);
        lblReadStatus.setText("Status: Odczytywanie raportu PDF Testo 184T...");

        Task<RevalidationSession.PositionData> task = new Task<>() {
            @Override protected RevalidationSession.PositionData call() throws Exception {
                return facade.readPositionDataFromPdf(session, selectedPosition, pdfFile);
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> {
            handleSuccessfulReadout(task.getValue());
            Platform.runLater(() -> btnReadPdf184.setDisable(false));
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            log.error("Błąd importu PDF 184T: {}", ex.getMessage());
            Platform.runLater(() -> {
                setProgressVisible(false);
                progressBar.progressProperty().unbind();
                btnReadUsb.setDisable(false);
                btnReadPdf184.setDisable(false);
                lblReadStatus.setText("Status: Błąd importu PDF.");
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Błąd importu PDF Testo 184T");
                alert.setHeaderText("Nie udało się wczytać raportu PDF");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            });
        });
        new Thread(task).start();
    }

    private void offerSimulation(String errorMsg) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Brak połączenia USB");
        dialog.setHeaderText("Nie wykryto fizycznego rejestratora Testo 174T");
        dialog.setContentText("Szczegóły: " + errorMsg + "\n\nCzy chcesz uruchomić tryb symulacji metrologicznej dla: " + selectedPosition.getLabel() + "?");
        ButtonType btnSim = new ButtonType("Uruchom Symulację");
        ButtonType btnCancel = new ButtonType("Anuluj", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getButtonTypes().setAll(btnSim, btnCancel);
        dialog.showAndWait().ifPresent(choice -> {
            if (choice == btnSim) runSimulationReadout();
            else lblReadStatus.setText("Status: Brak fizycznego połączenia USB.");
        });
    }

    private void runSimulationReadout() {
        setProgressVisible(true);
        lblReadStatus.setText("Status: Generowanie symulacji 3D lodówki...");
        Task<RevalidationSession.PositionData> task = new Task<>() {
            @Override protected RevalidationSession.PositionData call() throws Exception {
                Thread.sleep(1200);
                return facade.readPositionData(session, selectedPosition, true);
            }
        };
        progressBar.progressProperty().bind(task.progressProperty());
        task.setOnSucceeded(e -> handleSuccessfulReadout(task.getValue()));
        new Thread(task).start();
    }

    private void handleSuccessfulReadout(RevalidationSession.PositionData result) {
        setProgressVisible(false);
        progressBar.progressProperty().unbind();
        btnReadUsb.setDisable(false);
        session.getAssignedPositions().put(selectedPosition, result);
        fillSensorFields(result);
        lblReadStatus.setText("Status: Pomyślnie zgrano serię pomiarową!");
        refreshGridHighlight();
        updateStep2Summary();
    }

    /**
     * Aktualizuje licznik serii i stan przycisku "Dalej" w Kroku 2,
     * uwzględniając reguły dla mapowania i rewalidacji.
     */
    private void updateStep2Summary() {
        if (session == null) return;
        int loaded = session.getAssignedPositions().size();
        GxPProcedureType type = session.getProcedureType();

        if (type == GxPProcedureType.MAPPING) {
            lblMappingSummary.setText("Wgrano: " + loaded + " z 8 serii. Wymagane: wszystkie 8 pozycji.");
            btnStep2Next.setDisable(loaded < 8);
        } else {
            // Rewalidacja okresowa: wymagane min 2 sensory
            CoolingChamber chamber = session.getCoolingChamber();
            boolean mappingRequired = chamber.isMappingRequired();
            if (mappingRequired) {
                // Sprawdź czy hotspot i coldspot są załadowane
                RevalidationSession.GridPosition hotspot = chamber.getHotspotPosition();
                RevalidationSession.GridPosition coldspot = chamber.getColdspotPosition();
                boolean hotLoaded = hotspot != null && session.getAssignedPositions().containsKey(hotspot);
                boolean coldLoaded = coldspot != null && session.getAssignedPositions().containsKey(coldspot);

                String hotspostInfo = hotspot != null ? hotspot.getLabel() : "?";
                String coldspotInfo = coldspot != null ? coldspot.getLabel() : "?";

                if (!hotLoaded || !coldLoaded) {
                    lblMappingSummary.setText("Wgrano: " + loaded + ". Wymagane: Hotspot (" + hotspostInfo + ")"
                            + (hotLoaded ? " ✅" : " ❌") + ", Coldspot (" + coldspotInfo + ")"
                            + (coldLoaded ? " ✅" : " ❌") + ".");
                    btnStep2Next.setDisable(true);
                } else {
                    lblMappingSummary.setText("✅ Wgrano: " + loaded + " serii. Hotspot (" + hotspostInfo + ") i Coldspot (" + coldspotInfo + ") załadowane.");
                    btnStep2Next.setDisable(loaded < 2);
                }
            } else {
                lblMappingSummary.setText("Wgrano: " + loaded + " serii. Wymagane minimum: 2.");
                btnStep2Next.setDisable(loaded < 2);
            }
        }
    }

    private void setProgressVisible(boolean visible) {
        progressIndicator.setVisible(visible); progressIndicator.setManaged(visible);
        progressBar.setVisible(visible);       progressBar.setManaged(visible);
        btnReadUsb.setDisable(visible);
    }

    // ============================================================
    // KROK 3 – PODSUMOWANIE I WALIDACJA SPÓJNOŚCI
    // ============================================================



    private void handleShowDiagnostics(StatsRow row) {
        com.mac.bry.desktop.controller.helper.TestoRevalidationDialogHelper.showDiagnosticsDialog(row, session, applicationContext, statsTableView.getScene().getWindow());
    }

    private void buildSummaryAndValidation() {
        try {
            summaryRows.clear(); metrologicalRows.clear(); statsRows.clear(); multiChannelChart.getData().clear();
            if (session == null || session.getAssignedPositions().isEmpty()) return;

            double lsl = session.getCoolingChamber() != null && session.getCoolingChamber().getMinOperatingTemp() != null
                    ? session.getCoolingChamber().getMinOperatingTemp() : 2.0;
            double usl = session.getCoolingChamber() != null && session.getCoolingChamber().getMaxOperatingTemp() != null
                    ? session.getCoolingChamber().getMaxOperatingTemp() : 8.0;

            session.getAssignedPositions().forEach((pos, data) -> {
                if (data == null) return;

                String certNo   = data.getLatestCalibration() != null ? data.getLatestCalibration().getCertificateNumber() : "Brak";
                String validity = (data.getLatestCalibration() != null && data.getLatestCalibration().getValidUntil() != null)
                        ? data.getLatestCalibration().getValidUntil().toString() : "–";
                boolean certValid = data.getLatestCalibration() != null && data.getLatestCalibration().isValid();
                String status = certValid ? "Wzorcowany" : (data.getLatestCalibration() == null ? "⚠️ Brak Świadectwa" : "⚠️ Błąd GxP");

                int pointCount = 0;
                if (data.getSeries() != null && data.getSeries().getMeasurements() != null) {
                    pointCount = data.getSeries().getMeasurements().size();
                }

                String modelName = data.getModel() != null ? data.getModel().getName() : "Nieznany";
                summaryRows.add(new SummaryRow(pos.getLabel(), data.getSerialNumber(), modelName,
                        certNo, validity, pointCount, status));

                if (data.getSeries() != null) {
                    metrologicalRows.add(new MetrologicalRow(
                            pos.getLabel(), data.getSerialNumber(),
                            String.format("%.1f°C", orZero(data.getSeries().getMinTemperature())),
                            String.format("%.1f°C", orZero(data.getSeries().getMaxTemperature())),
                            String.format("%.1f°C", orZero(data.getSeries().getAvgTemperature())),
                            String.format("%.1f°C", orZero(data.getSeries().getMktTemperature())),
                            String.format("±%.3f°C", orZero(data.getSeries().getExpandedUncertainty())),
                            String.valueOf(data.getSeries().getSpikeCount() != null ? data.getSeries().getSpikeCount() : 0),
                            data.getSeries().getDriftClassification() != null ? data.getSeries().getDriftClassification() : "STABLE"
                    ));

                    double[] rawData = data.getSeries().getMeasurements() != null
                            ? data.getSeries().getMeasurements().stream()
                                    .mapToDouble(ThermoMeasurementPoint::getRawCelsius)
                                    .toArray()
                            : new double[0];

                    double median = rawData.length > 0 ? SensorStatsEngine.calculateMedian(rawData) : 0.0;
                    double stdDev = rawData.length >= 2 ? SensorStatsEngine.calculateStdDev(rawData) : 0.0;
                    double rsd = rawData.length > 0 ? SensorStatsEngine.calculateRsd(rawData) : 0.0;
                    double skewness = rawData.length >= 3 ? SensorStatsEngine.calculateSkewness(rawData) : 0.0;
                    double kurtosis = rawData.length >= 4 ? SensorStatsEngine.calculateKurtosis(rawData) : 0.0;

                    com.mac.bry.desktop.dto.stats.CapabilityIndexes cpIndex = SpcEngine.calculateCapability(rawData, lsl, usl);
                    double jbPVal = rawData.length >= 4 ? facade.performJarqueBera(rawData) : 1.0;

                    statsRows.add(new StatsRow(
                            pos.getLabel(),
                            data.getSerialNumber(),
                            median,
                            stdDev,
                            rsd,
                            skewness,
                            kurtosis,
                            cpIndex.getCp(),
                            cpIndex.getCpk(),
                            jbPVal,
                            pos
                    ));
                }
            });

            runValidationTests();
            updateMappingResultPanel();
            com.mac.bry.desktop.controller.helper.TestoRevalidationChartHelper.renderMultiChannelChart(multiChannelChart, xAxisTime, session);
            hypothesisTabController.initSession(session);

            com.mac.bry.desktop.service.stats.SpatialStatsService spatialService = applicationContext.getBean(com.mac.bry.desktop.service.stats.SpatialStatsService.class);
            java.util.List<com.mac.bry.desktop.model.ThermoMeasurementSeries> allSeries = session.getAssignedPositions().values().stream()
                    .map(RevalidationSession.PositionData::getSeries)
                    .collect(java.util.stream.Collectors.toList());
            com.mac.bry.desktop.dto.stats.SpatialStatsResult spatialResult = spatialService.calculateSpatialStats(allSeries);
            session.setSpatialStats(spatialResult);
            spatialTabController.setSpatialData(spatialResult);
        } catch (Throwable t) {
            log.error("Krytyczny błąd podczas budowania podsumowania rewalidacji GxP", t);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Błąd Walidacji / Analizy GxP");
            alert.setHeaderText("Wystąpił nieoczekiwany wyjątek podczas przetwarzania danych");
            alert.setContentText(t.toString() + "\n" + java.util.Arrays.toString(t.getStackTrace()));
            alert.showAndWait();
        }
    }

    /**
     * Wyświetla lub ukrywa panel wyników mapowania PDA TR-64.
     * Widoczny tylko dla procedury MAPPING — pokazuje lokalizację Hotspotu i Coldspotu
     * wraz z zarejestrowaną temperaturą maksymalną i minimalną.
     */
    private void updateMappingResultPanel() {
        if (session == null || session.getProcedureType() != GxPProcedureType.MAPPING) {
            mappingResultBox.setVisible(false);
            mappingResultBox.setManaged(false);
            return;
        }

        com.mac.bry.desktop.service.helper.MappingValidator.MappingResult result =
                com.mac.bry.desktop.service.helper.MappingValidator.validate(session);

        if (!result.isSuccess()) {
            // Błąd walidacji — panel ukryty (błąd blokuje już przejście w kroku 2)
            mappingResultBox.setVisible(false);
            mappingResultBox.setManaged(false);
            return;
        }

        // Wypełnienie danych hotspotu
        lblHotspotLocation.setText(result.getHotspot().getLabel() + String.format(" (spójność: %.0f%%)", result.getHotspotStrength() * 100));
        lblHotspotTemp.setText(String.format("%.2f °C", result.getMaxTemperature()));

        // Wypełnienie danych coldspotu
        lblColdspotLocation.setText(result.getColdspot().getLabel() + String.format(" (spójność: %.0f%%)", result.getColdspotStrength() * 100));
        lblColdspotTemp.setText(String.format("%.2f °C", result.getMinTemperature()));

        // Rozpiętość ΔT
        double delta = result.getMaxTemperature() - result.getMinTemperature();
        lblMappingDeltaT.setText(String.format("%.2f °C", delta));

        if (result.isWeakConsensus()) {
            mappingResultBox.setStyle("-fx-background-color: -color-bg-default; -fx-padding: 15; -fx-background-radius: 6px; -fx-border-color: -color-danger-emphasis; -fx-border-width: 2px; -fx-border-radius: 6px;");
            lblValidationSummary.setText(lblValidationSummary.getText() + "\n⚠️ OSTRZEŻENIE: Słaba spójność metod detekcji hotspot/coldspot (poniżej 50%). Zweryfikuj dane ręcznie.");
            lblValidationSummary.setStyle("-fx-text-fill: -color-danger-emphasis; -fx-font-weight: bold;");
        } else {
            mappingResultBox.setStyle("-fx-background-color: -color-bg-default; -fx-padding: 15; -fx-background-radius: 6px; -fx-border-color: #dc6803; -fx-border-width: 1px; -fx-border-radius: 6px;");
        }

        mappingResultBox.setVisible(true);
        mappingResultBox.setManaged(true);
    }

    private double orZero(Double v) { return v != null ? v : 0.0; }

    private void runValidationTests() {
        var iter = session.getAssignedPositions().values().iterator();
        RevalidationSession.PositionData base = iter.next();
        int baseInterval = base.getSeries().getLoggingIntervalMinutes();
        int baseCount    = base.getSeries().getMeasurementsCount();
        LocalDateTime baseStart = base.getSeries().getFirstMeasurementTimeLocal();

        boolean intervalOk = true, pointsOk = true, startOk = true, certsOk = true;
        for (RevalidationSession.PositionData d : session.getAssignedPositions().values()) {
            if (d.getSeries().getLoggingIntervalMinutes() != baseInterval) intervalOk = false;
            if (d.getSeries().getMeasurementsCount()      != baseCount)    pointsOk  = false;
            if (!d.getSeries().getFirstMeasurementTimeLocal().equals(baseStart)) startOk = false;
            if (d.getLatestCalibration() == null || !d.getLatestCalibration().isValid()) certsOk = false;
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        setLabel(lblTestInterval,    intervalOk, "✅ Zgodny (" + baseInterval + " min)", "❌ Różne interwały!");
        setLabel(lblTestPoints,      pointsOk,   "✅ Zgodna (" + baseCount + " pkt)",    "❌ Różna liczba punktów!");
        setLabel(lblTestStart,       startOk,    "✅ Zgodny (" + baseStart.format(fmt) + ")", "❌ Niezgodny start!");
        setLabel(lblTestCertificates,certsOk,    "✅ Aktualne świadectwa wzorcowania", "⚠️ Wykryto przeterminowane certyfikaty!");

        boolean allOk = intervalOk && pointsOk && startOk;

        // Sprawdź wskaźniki zdolności (Cpk < 1.0)
        boolean lowCpk = false;
        for (StatsRow row : statsRows) {
            if (row.getCpk() < 1.0) {
                lowCpk = true;
                break;
            }
        }

        if (allOk) {
            if (lowCpk) {
                lblValidationSummary.setText("⚠️ OSTRZEŻENIE GxP: Wykryto wskaźnik zdolności Cpk < 1.0 (niestabilność procesu). Spójność serii pomiarowych potwierdzona.");
                lblValidationSummary.setStyle("-fx-text-fill: -color-warning-emphasis; -fx-font-weight: bold;");
            } else {
                lblValidationSummary.setText("✅ Spójność serii pomiarowych potwierdzona. Dane są zsynchronizowane.");
                lblValidationSummary.setStyle("-fx-text-fill: -color-success-emphasis; -fx-font-weight: bold;");
            }
            btnSaveAndGenerate.setDisable(false);
        } else {
            lblValidationSummary.setText("❌ BŁĄD SPÓJNOŚCI GxP: Zablokowano generowanie raportu.");
            lblValidationSummary.setStyle("-fx-text-fill: -color-danger-emphasis; -fx-font-weight: bold;");
            btnSaveAndGenerate.setDisable(true);
        }
    }

    private void setLabel(Label lbl, boolean ok, String okText, String failText) {
        lbl.setText(ok ? okText : failText);
        lbl.setStyle(ok ? "-fx-text-fill: -color-success-emphasis;" : "-fx-text-fill: -color-danger-emphasis;");
    }




    // ============================================================
    // GENEROWANIE PAKIETU ZIP (delegacja do RevalidationZipCompiler)
    // ============================================================

    @FXML public void handleSaveAndGeneratePdf(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Zapisz Pakiet Walidacyjny GxP (ZIP)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archiwa ZIP (*.zip)", "*.zip"));
        fc.setInitialFileName("PAKIET_WALIDACYJNY_GxP_" + session.getCoolingDevice().getInventoryNumber()
                + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")) + ".zip");

        File outputZip = fc.showSaveDialog((Stage) btnSaveAndGenerate.getScene().getWindow());
        if (outputZip == null) return;

        File tempChartPng = null;
        try {
            // 1. Zrzut wielokanałowego wykresu z UI
            tempChartPng = facade.snapshotExistingChart(multiChannelChart);

            // 2. Zapis sesji w bazie danych
            facade.saveSession(session);
            log.info("Sesja zarchiwizowana w bazie danych.");

            // 3. Kompilacja pakietu ZIP
            facade.compileZip(session, tempChartPng, outputZip);

            new Alert(Alert.AlertType.INFORMATION, "Dane zostały zapisane, a pakiet ZIP skompilowany:\n" + outputZip.getAbsolutePath(),
                    ButtonType.OK).showAndWait();

        } catch (Exception e) {
            log.error("Błąd podczas kończenia rewalidacji", e);
            new Alert(Alert.AlertType.ERROR, e.getMessage() != null ? e.getMessage() : e.toString()).showAndWait();
        } finally {
            if (tempChartPng != null && tempChartPng.exists()) tempChartPng.delete();
        }
    }



    // ============================================================
    // DTO – pomocnicze modele danych dla TableView w Kroku 3
    // ============================================================

    @Data @AllArgsConstructor
    public static class SummaryRow {
        private String positionName;
        private String serialNumber;
        private String model;
        private String certificateNumber;
        private String validityDate;
        private int pointCount;
        private String status;
    }

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class MetrologicalRow {
        private String positionName, serialNumber, minTemp, maxTemp, avgTemp,
                mktTemp, uncertainty, spikes, driftClassification;
    }

    @Data @AllArgsConstructor @NoArgsConstructor @Builder
    public static class StatsRow {
        private String positionName;
        private String serialNumber;
        private double median;
        private double stdDev;
        private double rsd;
        private double skewness;
        private double kurtosis;
        private double cp;
        private double cpk;
        private double jbPVal;
        private RevalidationSession.GridPosition gridPosition;
    }
}
