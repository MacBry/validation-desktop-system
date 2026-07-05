package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.model.RevalidationSession;
import com.mac.bry.desktop.model.RevalidationSession.GridPosition;
import com.mac.bry.desktop.model.regime.MeasurementSegment;
import com.mac.bry.desktop.model.regime.SegmentType;
import com.mac.bry.desktop.service.regime.SegmentAnnotationService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kontroler dialogu przeglądu i adnotacji segmentów (DP-001 Faza 4, human-in-the-loop).
 * Operator potwierdza lub odrzuca segmenty wykryte algorytmicznie; decyzje
 * deleguje do {@link SegmentAnnotationService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SegmentAnnotationDialogController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    /** Typy będące zdarzeniami — domyślny widok filtruje do nich. */
    private static final Set<SegmentType> EVENT_TYPES = EnumSet.of(
            SegmentType.DEFROST, SegmentType.DOOR_EVENT,
            SegmentType.EXCURSION, SegmentType.SETPOINT_CHANGE);

    private final SegmentAnnotationService annotationService;

    @FXML private TableView<SegmentRow> segmentTable;
    @FXML private TableColumn<SegmentRow, String> colPosition;
    @FXML private TableColumn<SegmentRow, String> colType;
    @FXML private TableColumn<SegmentRow, String> colFrom;
    @FXML private TableColumn<SegmentRow, String> colTo;
    @FXML private TableColumn<SegmentRow, String> colDuration;
    @FXML private TableColumn<SegmentRow, String> colConfidence;
    @FXML private TableColumn<SegmentRow, String> colNote;
    @FXML private TableColumn<SegmentRow, SegmentRow> colStatus;
    @FXML private CheckBox chkOnlyEvents;
    @FXML private Label lblCounter;
    @FXML private Label lblSelectionHint;
    @FXML private Button btnAccept;
    @FXML private Button btnReject;
    @FXML private Button btnAcceptAll;

    private final ObservableList<SegmentRow> allRows = FXCollections.observableArrayList();
    private FilteredList<SegmentRow> filteredRows;

    /** Wiersz tabeli — para pozycja + segment. */
    @Value
    public static class SegmentRow {
        GridPosition position;
        MeasurementSegment segment;
    }

    @FXML
    public void initialize() {
        colPosition.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPosition().getLabel()));
        colType.setCellValueFactory(c -> new SimpleStringProperty(
                typeLabel(c.getValue().getSegment().getType())));
        colFrom.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSegment().getFromTimestamp().format(TIME_FMT)));
        colTo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSegment().getToTimestamp().format(TIME_FMT)));
        colDuration.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().getSegment().durationMinutes())));
        colConfidence.setCellValueFactory(c -> {
            Double conf = c.getValue().getSegment().getConfidence();
            return new SimpleStringProperty(conf != null ? String.format("%.2f", conf) : "–");
        });
        colNote.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getSegment().getNote() != null ? c.getValue().getSegment().getNote() : "–"));

        colStatus.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(SegmentRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                MeasurementSegment seg = row.getSegment();
                if (seg.getConfirmedBy() == null) {
                    setText("Do weryfikacji");
                    setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
                } else if (seg.isAccepted()) {
                    setText("Potwierdzony (" + seg.getConfirmedBy() + ")");
                    setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                } else {
                    setText("Odrzucony (" + seg.getConfirmedBy() + ")");
                    setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                }
            }
        });

        filteredRows = new FilteredList<>(allRows);
        segmentTable.setItems(filteredRows);
        chkOnlyEvents.selectedProperty().addListener((obs, o, n) -> applyFilter());
        applyFilter();

        segmentTable.getSelectionModel().selectedItemProperty().addListener((obs, o, row) -> {
            boolean hasSelection = row != null;
            btnAccept.setDisable(!hasSelection);
            btnReject.setDisable(!hasSelection);
            lblSelectionHint.setText(hasSelection
                    ? "Segment: " + typeLabel(row.getSegment().getType()) + " @ " + row.getPosition().getLabel()
                    : "Zaznacz segment, aby go potwierdzić lub odrzucić.");
        });
    }

    /**
     * Wypełnia dialog segmentami sesji. Wywoływane przez helper po detekcji.
     */
    public void setSession(RevalidationSession session) {
        Map<GridPosition, List<MeasurementSegment>> segmentsMap =
                annotationService.detectForSession(session);

        allRows.clear();
        segmentsMap.forEach((pos, segments) ->
                segments.forEach(seg -> allRows.add(new SegmentRow(pos, seg))));
        allRows.sort((a, b) -> a.getSegment().getFromTimestamp()
                .compareTo(b.getSegment().getFromTimestamp()));
        applyFilter();
    }

    private void applyFilter() {
        boolean onlyEvents = chkOnlyEvents.isSelected();
        filteredRows.setPredicate(row -> !onlyEvents
                || EVENT_TYPES.contains(row.getSegment().getType()));
        updateCounter();
    }

    private void updateCounter() {
        long pending = filteredRows.stream()
                .filter(r -> r.getSegment().getConfirmedBy() == null).count();
        lblCounter.setText(String.format("Segmentów: %d | Do weryfikacji: %d",
                filteredRows.size(), pending));
    }

    @FXML
    private void handleAccept() {
        SegmentRow row = segmentTable.getSelectionModel().getSelectedItem();
        if (row == null) return;
        annotationService.accept(row.getSegment());
        segmentTable.refresh();
        updateCounter();
    }

    @FXML
    private void handleReject() {
        SegmentRow row = segmentTable.getSelectionModel().getSelectedItem();
        if (row == null) return;
        annotationService.reject(row.getSegment());
        segmentTable.refresh();
        updateCounter();
    }

    @FXML
    private void handleAcceptAll() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Potwierdzenie zbiorcze");
        confirm.setHeaderText("Potwierdzić wszystkie widoczne segmenty?");
        confirm.setContentText("Operacja oznaczy " + filteredRows.size()
                + " segmentów jako zweryfikowane przez Ciebie. Decyzja jest audytowana.");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                filteredRows.forEach(row -> annotationService.accept(row.getSegment()));
                segmentTable.refresh();
                updateCounter();
            }
        });
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) segmentTable.getScene().getWindow();
        stage.close();
    }

    private String typeLabel(SegmentType type) {
        return switch (type) {
            case STEADY_STATE     -> "Stan ustalony";
            case EQUILIBRATION    -> "Dochodzenie";
            case DEFROST          -> "Defrost";
            case DOOR_EVENT       -> "Otwarcie drzwi";
            case SETPOINT_CHANGE  -> "Zmiana nastawy";
            case EXCURSION        -> "Ekskursja";
            case NORMAL_USE       -> "Normalne użycie";
        };
    }
}
