# Plan Refaktoryzacji: TestoRevalidationController (Wzorzec Fasady)

## Przegląd

Kontroler `TestoRevalidationController` urósł do ponad 1300 linii kodu, stając się klasą typu "God Class", która miesza odpowiedzialności zarządzania stanem interfejsu (JavaFX), obsługi zdarzeń UI, konfiguracji tabel, renderowania wykresów, komunikacji z czytnikami USB oraz wyliczania i przeprowadzania testów statystycznych.

W celu poprawy czytelności, ułatwienia testowania oraz separacji odpowiedzialności (SRP), planujemy przeprowadzić refaktoryzację z użyciem **Wzorca Fasady (Facade)** oraz **Statycznych Pomocników UI (Helpers)**. 

Refaktoryzacja zachowa **100% wstecznej kompatybilności** z widokiem FXML (identyczne `@FXML` pola, typy, identyfikatory oraz sygnatury metod obsługi zdarzeń).

---

## Architektura Po Refaktoryzacji

```
TestoRevalidationController (Orkiestracja UI - ~350-400 linii)
├── Odpowiedzialny wyłącznie za obsługę zdarzeń FXML i stan kroków kreatora
└── Zachowuje pełną zgodność z testo_revalidation.fxml
      │
      ├──> TestoRevalidationFacade (Fasada logiki biznesowej - ~150 linii) [Spring Service]
      │    ├── Agreguje: TestoRevalidationService, HypothesisTestingService, Testo184UsbImportService
      │    ├── Agreguje: RevalidationZipCompiler, JavaFxChartRenderer, Repozytoria JPA
      │    └── Udostępnia uproszczony, jednolity interfejs dla kontrolera
      │
      └──> TestoRevalidationTableHelper (Formatowanie i konfiguracja tabel - ~150 linii) [UI Helper]
           ├── setupSummaryTable() - bindowanie kolumn podsumowania i statusu GxP
           ├── setupMetrologicalTable() - bindowanie i formatowanie wskaźników metrologicznych
           └── setupStatsTable() - konfiguracja statystyk (mediana, wskaźniki zdolności Cp/Cpk, Jarque-Bera)
```

---

## Krok 1: TestoRevalidationFacade (Fasada Biznesowa)

Fasada ukrywa przed kontrolerem złożoność wtryskiwania 8 różnych zależności Springa i udostępnia spójny interfejs API do operacji na urządzeniach, komorach, sesjach walidacyjnych, testach statystycznych i kompilacji raportów.

```java
package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.*;
import com.mac.bry.desktop.dto.stats.AnovaResult;
import com.mac.bry.desktop.dto.stats.TostResult;
import com.mac.bry.desktop.repository.CoolingChamberRepository;
import com.mac.bry.desktop.repository.CoolingDeviceRepository;
import com.mac.bry.desktop.service.stats.HypothesisTestingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javafx.scene.chart.LineChart;
import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestoRevalidationFacade {

    private final TestoRevalidationService revalidationService;
    private final CoolingDeviceRepository coolingDeviceRepository;
    private final CoolingChamberRepository coolingChamberRepository;
    private final JavaFxChartRenderer chartRenderer;
    private final RevalidationZipCompiler zipCompiler;
    private final Testo184UsbImportService testo184UsbImportService;
    private final HypothesisTestingService hypothesisTestingService;

    // --- REPOZYTORIA ---
    public List<CoolingDevice> findAllDevices() {
        return coolingDeviceRepository.findAll();
    }

    public List<CoolingChamber> findChambersByDeviceId(Long deviceId) {
        return coolingChamberRepository.findByCoolingDeviceId(deviceId);
    }

    // --- ZARZĄDZANIE SESJĄ ---
    public RevalidationSession initSession(CoolingDevice device, CoolingChamber chamber, GxPProcedureType type) {
        return revalidationService.initSession(device, chamber, type);
    }

    public void saveSession(RevalidationSession session) {
        revalidationService.saveRevalidationSession(session);
    }

    // --- ODCZYT DANYCH SENSORÓW ---
    public RevalidationSession.PositionData readPositionData(RevalidationSession session, RevalidationSession.GridPosition pos, boolean simulate) throws Exception {
        return revalidationService.readPositionData(session, pos, simulate);
    }

    public RevalidationSession.PositionData readPositionDataFromPdf(RevalidationSession session, RevalidationSession.GridPosition pos, File pdfFile) throws Exception {
        return revalidationService.readPositionDataFromPdf(session, pos, pdfFile);
    }

    // --- WYKRESY I RAPORTY ---
    public File snapshotExistingChart(LineChart<Number, Number> chart) throws Exception {
        return chartRenderer.snapshotExistingChart(chart);
    }

    public void compileZip(RevalidationSession session, File chartPng, File outputZip) throws Exception {
        zipCompiler.compile(session, chartPng, outputZip);
    }

    // --- TESTOWANIE HIPOTEZ GxP ---
    public TostResult performTostEquivalence(double[] sample1, double[] sample2, double theta) {
        return hypothesisTestingService.performTostEquivalence(sample1, sample2, theta);
    }

    public double performFTest(double[] sample1, double[] sample2) {
        return hypothesisTestingService.performFTest(sample1, sample2);
    }

    public AnovaResult performAnova(List<double[]> samples) {
        return hypothesisTestingService.performAnova(samples);
    }

    public double performKruskalWallis(List<double[]> samples) {
        return hypothesisTestingService.performKruskalWallis(samples);
    }

    public double performJarqueBera(double[] sample) {
        return hypothesisTestingService.performJarqueBera(sample);
    }
}
```

---

## Krok 2: TestoRevalidationTableHelper (Pomocnik UI do tabel)

Odpowiada za bindowanie właściwości obiektów do kolumn tabeli, formatowanie jednostek (°C, %) oraz koloryzowanie komórek statusu zgodnie z wytycznymi GxP. Klasa ta jest całkowicie odpięta od Springa (statyczne metody pomocnicze).

```java
package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.controller.TestoRevalidationController.SummaryRow;
import com.mac.bry.desktop.controller.TestoRevalidationController.MetrologicalRow;
import com.mac.bry.desktop.controller.TestoRevalidationController.StatsRow;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import atlantafx.base.theme.Styles;
import java.util.function.Consumer;

public class TestoRevalidationTableHelper {

    public static void setupSummaryTable(
            TableView<SummaryRow> table,
            TableColumn<SummaryRow, String> colPosName,
            TableColumn<SummaryRow, String> colPosSn,
            TableColumn<SummaryRow, String> colPosModel,
            TableColumn<SummaryRow, String> colPosCert,
            TableColumn<SummaryRow, String> colPosCertValid,
            TableColumn<SummaryRow, Integer> colPosCount,
            TableColumn<SummaryRow, String> colPosStatus) {

        colPosName.setCellValueFactory(new PropertyValueFactory<>("positionName"));
        colPosSn.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colPosModel.setCellValueFactory(new PropertyValueFactory<>("model"));
        colPosCert.setCellValueFactory(new PropertyValueFactory<>("certificateNumber"));
        colPosCertValid.setCellValueFactory(new PropertyValueFactory<>("validityDate"));
        colPosCount.setCellValueFactory(new PropertyValueFactory<>("pointCount"));
        colPosStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    public static void setupMetrologicalTable(
            TableView<MetrologicalRow> table,
            TableColumn<MetrologicalRow, String> colMetroPos,
            TableColumn<MetrologicalRow, String> colMetroSn,
            TableColumn<MetrologicalRow, String> colMetroMin,
            TableColumn<MetrologicalRow, String> colMetroMax,
            TableColumn<MetrologicalRow, String> colMetroAvg,
            TableColumn<MetrologicalRow, String> colMetroMkt,
            TableColumn<MetrologicalRow, String> colMetroUnc,
            TableColumn<MetrologicalRow, String> colMetroSpikes,
            TableColumn<MetrologicalRow, String> colMetroDrift) {

        colMetroPos.setCellValueFactory(new PropertyValueFactory<>("positionName"));
        colMetroSn.setCellValueFactory(new PropertyValueFactory<>("serialNumber"));
        colMetroMin.setCellValueFactory(new PropertyValueFactory<>("minTemp"));
        colMetroMax.setCellValueFactory(new PropertyValueFactory<>("maxTemp"));
        colMetroAvg.setCellValueFactory(new PropertyValueFactory<>("avgTemp"));
        colMetroMkt.setCellValueFactory(new PropertyValueFactory<>("mktTemp"));
        colMetroUnc.setCellValueFactory(new PropertyValueFactory<>("uncertainty"));
        colMetroSpikes.setCellValueFactory(new PropertyValueFactory<>("spikes"));
        colMetroDrift.setCellValueFactory(new PropertyValueFactory<>("driftClassification"));

        colMetroDrift.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label tag = new Label(item);
                tag.getStyleClass().add("tag");
                tag.getStyleClass().add(switch (item) {
                    case "STABLE" -> Styles.SUCCESS;
                    case "SPIKE"  -> Styles.ACCENT;
                    case "DRIFT"  -> Styles.DANGER;
                    default       -> Styles.WARNING;
                });
                setGraphic(tag); setText(null);
            }
        });
    }

    public static void setupStatsTable(
            TableView<StatsRow> table,
            TableColumn<StatsRow, String> colStatsPos,
            TableColumn<StatsRow, Double> colStatsMedian,
            TableColumn<StatsRow, Double> colStatsStdDev,
            TableColumn<StatsRow, Double> colStatsRsd,
            TableColumn<StatsRow, Double> colStatsSkewness,
            TableColumn<StatsRow, Double> colStatsKurtosis,
            TableColumn<StatsRow, Double> colStatsCp,
            TableColumn<StatsRow, Double> colStatsCpk,
            TableColumn<StatsRow, Double> colStatsJbPVal,
            TableColumn<StatsRow, Void> colStatsAction,
            Consumer<StatsRow> diagnosticsTrigger) {

        colStatsPos.setCellValueFactory(new PropertyValueFactory<>("positionName"));
        colStatsMedian.setCellValueFactory(new PropertyValueFactory<>("median"));
        colStatsStdDev.setCellValueFactory(new PropertyValueFactory<>("stdDev"));
        colStatsRsd.setCellValueFactory(new PropertyValueFactory<>("rsd"));
        colStatsSkewness.setCellValueFactory(new PropertyValueFactory<>("skewness"));
        colStatsKurtosis.setCellValueFactory(new PropertyValueFactory<>("kurtosis"));
        colStatsCp.setCellValueFactory(new PropertyValueFactory<>("cp"));
        colStatsCpk.setCellValueFactory(new PropertyValueFactory<>("cpk"));
        colStatsJbPVal.setCellValueFactory(new PropertyValueFactory<>("jbPVal"));

        colStatsMedian.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.2f °C", item));
            }
        });

        colStatsStdDev.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.3f °C", item));
            }
        });

        colStatsRsd.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.2f%%", item));
            }
        });

        colStatsSkewness.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.3f", item));
            }
        });

        colStatsKurtosis.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.3f", item));
            }
        });

        colStatsCp.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                setText(String.format("%.3f", item));
                setStyle("");
                if (item < 1.0) setStyle("-fx-text-fill: -color-danger-emphasis;");
                else if (item < 1.33) setStyle("-fx-text-fill: -color-warning-emphasis;");
                else setStyle("-fx-text-fill: -color-success-emphasis;");
            }
        });

        colStatsCpk.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                setText(String.format("%.3f", item));
                setStyle("");
                if (item < 1.0) setStyle("-fx-text-fill: -color-danger-emphasis; -fx-font-weight: bold;");
                else if (item < 1.33) setStyle("-fx-text-fill: -color-warning-emphasis; -fx-font-weight: bold;");
                else setStyle("-fx-text-fill: -color-success-emphasis;");
            }
        });

        colStatsJbPVal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                setText(String.format("%.4f", item));
                setStyle("");
                if (item < 0.05) {
                    setStyle("-fx-text-fill: -color-warning-emphasis;");
                    setTooltip(new Tooltip("Rozkład odbiega od normalnego (p < 0.05)"));
                } else {
                    setStyle("-fx-text-fill: -color-success-emphasis;");
                }
            }
        });

        colStatsAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Diagnozuj 📊");
            {
                btn.getStyleClass().add(Styles.ACCENT);
                btn.setOnAction(e -> {
                    StatsRow row = getTableView().getItems().get(getIndex());
                    diagnosticsTrigger.accept(row);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }
}
```

---

## Krok 3: Refaktoryzacja TestoRevalidationController

Zamiast wstrzykiwać 8 różnych obiektów, kontroler wstrzykuje tylko `TestoRevalidationFacade` oraz `ApplicationContext` do ładowania kontrolek diagnostycznych:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class TestoRevalidationController {

    private final TestoRevalidationFacade facade;
    private final ApplicationContext applicationContext;

    // Wszystkie wstrzyknięte pola @FXML pozostają niezmienione dla pełnej kompatybilności FXML
    @FXML private TabPane revalidationTabPane;
    ...
```

### Zmiany w initialize():
Delegacja konfiguracji widoków tabeli do nowego pomocnika:

```java
    @FXML
    public void initialize() {
        log.info("Inicjalizacja kontrolera kreatora rewalidacji");
        gridButtons.put(RevalidationSession.GridPosition.TOP_FRONT_LEFT,    btnTopFrontLeft);
        ...
        setupStep1();
        
        // Delegacja tabel do statycznego helpera
        TestoRevalidationTableHelper.setupSummaryTable(summaryTableView, colPosName, colPosSn, colPosModel, colPosCert, colPosCertValid, colPosCount, colPosStatus);
        TestoRevalidationTableHelper.setupMetrologicalTable(metrologicalTableView, colMetroPos, colMetroSn, colMetroMin, colMetroMax, colMetroAvg, colMetroMkt, colMetroUnc, colMetroSpikes, colMetroDrift);
        TestoRevalidationTableHelper.setupStatsTable(statsTableView, colStatsPos, colStatsMedian, colStatsStdDev, colStatsRsd, colStatsSkewness, colStatsKurtosis, colStatsCp, colStatsCpk, colStatsJbPVal, colStatsAction, this::handleShowDiagnostics);
        
        resetGridButtons();
        applyProcedureTypeUI(GxPProcedureType.PERIODIC_REVALIDATION);
        setupHypothesisTestingComboBoxes();
    }
```

### Zmiany w metodach obsługi i logice biznesowej:
Wszystkie wywołania serwisów zewnętrznych zostaną uproszczone przez skierowanie ich do `facade`, np:

```java
    @FXML 
    public void handleStep1Next(ActionEvent e) {
        ...
        session = facade.initSession(device, chamber, procedureType);
        ...
    }
```

```java
    @FXML 
    public void handleSaveAndGeneratePdf(ActionEvent event) {
        ...
        tempChartPng = facade.snapshotExistingChart(multiChannelChart);
        facade.saveSession(session);
        facade.compileZip(session, tempChartPng, outputZip);
        ...
    }
```

---

## Podsumowanie Zmian (Metryki)

| Aspekt | Przed | Po | Zysk / Poprawa |
|--------|-------|----|----------------|
| **Rozmiar klasy kontrolera** | ~1350 linii | ~380-450 linii | **-68%** redukcja linii kodu |
| **Separacja odpowiedzialności** | Niska (God Class) | Wysoka (SRP) | Doskonała izolacja logiki od widoku |
| **Zależności Springa (DI)** | 8 wstrzykiwanych pól | 2 wstrzykiwane pola | **-75%** spadek sprzężenia (coupling) |
| **Konfiguracja tabel UI** | Rozproszona (kod inline) | Scentralizowana w Helperze | Łatwe modyfikowanie wyglądu tabel |
| **Wsteczna kompatybilność** | N/D | 100% zachowana | Brak konieczności dotykania plików FXML |

---

## Lista Zadań (Implementation Checklist)

- [ ] Utworzyć klasę fasady `TestoRevalidationFacade.java` w pakiecie `com.mac.bry.desktop.service`
- [ ] Utworzyć klasę pomocnika `TestoRevalidationTableHelper.java` w pakiecie `com.mac.bry.desktop.controller.helper`
- [ ] Zastąpić wstrzykiwane zależności w `TestoRevalidationController.java` pojedynczą referencją do `TestoRevalidationFacade`
- [ ] Zintegrować wywołania tabel do `TestoRevalidationTableHelper` w metodzie `initialize()`
- [ ] Przekierować całą logikę biznesową (odczyty USB, PDF, symulacja, kompilacja ZIP, testy statystyczne) do fasady
- [ ] Uruchomić pełną kompilację i testy jednostkowe (`mvn clean test`) w celu weryfikacji regresji
- [ ] Zweryfikować poprawność integracji z widokiem JavaFX
