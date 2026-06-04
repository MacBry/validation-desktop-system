# Technical Specification: Interfejs Użytkownika JavaFX (Jednostka 4)

## 1. Architektura UI i Kontrolery JavaFX

Do obsługi interfejsu zostanie dodany nowy kontroler `TestoTransportValidationController.java` sterowany plikiem FXML `/ui/testo_transport_validation.fxml`.

Układ widoków i przejść między krokami realizowany jest za pomocą kontenera `StackPane` w JavaFX, gdzie każdy krok (Step 1-5) jest osobnym kontenerem `VBox`/`GridPane` przełączanym programowo (metoda `showStep(int stepIndex)`).

### Struktura Klasy Kontrolera:
```java
package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.TransportRoute;
import com.mac.bry.desktop.model.TransportUnit;
import com.mac.bry.desktop.service.TransportValidationService;
import com.mac.bry.desktop.service.TransportZipCompiler;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestoTransportValidationController {

    private final TransportValidationService validationService;
    private final TransportZipCompiler zipCompiler;

    @FXML private ComboBox<TransportUnit> cbTransportUnit;
    @FXML private ComboBox<TransportRoute> cbTransportRoute;
    @FXML private DatePicker dpTransportDate;
    @FXML private TextField tfDriverName;

    @FXML private TextField tfKkczCount;
    @FXML private TextField tfFfpCount;
    @FXML private Checkbox chkPowerFailure;

    @FXML private LineChart<Number, Number> transportLineChart;
    @FXML private Slider sliderStartTime;
    @FXML private Slider sliderEndTime;
    @FXML private Slider sliderFailureTime;

    @FXML private Label lblValidationStatus;
    @FXML private TextArea txtConclusions;
    @FXML private TextArea txtRemarks;

    @FXML private VBox step1Container;
    @FXML private VBox step2Container;
    @FXML private VBox step3Container;
    @FXML private VBox step4Container;
    @FXML private VBox step5Container;

    @FXML
    private void handleNextStep() {
        // Przełączanie kroków i walidacja pól
    }

    @FXML
    private void handlePrevStep() {
        // Powrót do poprzedniego kroku
    }
}
```

---

## 2. Interaktywne Slajdery na Wykresie (Trim Range Interaction)

Wykres temperaturowy w kroku 4 będzie rysowany na standardowym `LineChart<Number, Number>` w JavaFX. Suwaki `sliderStartTime` oraz `sliderEndTime` zostaną powiązane z osiami czasu:
1. Min/Max suwaków reprezentuje indeksy punktów pomiarowych w zaimportowanej serii (od `0` do `measurements.size() - 1`).
2. Zmiana pozycji suwaka wywołuje zdarzenie `Listener`, które przerysowuje pionowe linie pomocnicze na wykresie wskazujące wybrane punkty $T_{\text{start}}$ oraz $T_{\text{stop}}$.
3. Wartości suwaków są bezpośrednio przekazywane do serwisu `TransportValidationService` w celu przeliczenia wyników w czasie rzeczywistym.

---

## 3. Plik FXML (`testo_transport_validation.fxml`)
Plik FXML będzie zlokalizowany w zasobach projektu: `validation-desktop/src/main/resources/ui/testo_transport_validation.fxml`. 

Stylistyka będzie zgodna z biblioteką **AtlantaFX** (styl Pine/Primer), zapewniając harmonijny wygląd, nowoczesne kolory (ciemny motyw) oraz mikroanimacje przycisków nawigacji.
