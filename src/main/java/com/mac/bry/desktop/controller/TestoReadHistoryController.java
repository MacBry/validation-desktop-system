package com.mac.bry.desktop.controller;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.mac.bry.desktop.security.model.AccessLog;
import com.mac.bry.desktop.security.repository.AccessLogRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestoReadHistoryController {

    private final AccessLogRepository accessLogRepository;

    @FXML private TextField searchField;
    @FXML private TableView<AccessLog> historyTable;
    @FXML private TableColumn<AccessLog, String> colTimestamp;
    @FXML private TableColumn<AccessLog, String> colUsername;
    @FXML private TableColumn<AccessLog, String> colDetails;

    private final ObservableList<AccessLog> logsList = FXCollections.observableArrayList();
    private FilteredList<AccessLog> filteredLogs;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    public void initialize() {
        // Konfiguracja stylów
        historyTable.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);

        // Ustawienie właściwości komórek
        colTimestamp.setCellValueFactory(cellData -> {
            AccessLog log = cellData.getValue();
            String formattedDate = log.getTimestamp() != null ? log.getTimestamp().format(FORMATTER) : "";
            return new SimpleStringProperty(formattedDate);
        });
        
        colUsername.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUsername()));
        colDetails.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDetails()));

        // Wyszukiwarka - Live Filtering
        filteredLogs = new FilteredList<>(logsList, p -> true);
        historyTable.setItems(filteredLogs);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredLogs.setPredicate(logEvent -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                
                if (logEvent.getUsername() != null && logEvent.getUsername().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                } else if (logEvent.getDetails() != null && logEvent.getDetails().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return false;
            });
        });

        // Wypełnienie tabeli danymi startowymi
        loadData();
    }

    private void loadData() {
        logsList.clear();
        try {
            // Wyszukujemy zdarzenia wyzwolone przez odczyt (USB_READING oraz PDF_IMPORT)
            List<AccessLog> readingEvents = accessLogRepository.findByActionInOrderByTimestampDesc(List.of("USB_READING", "PDF_IMPORT"));
            logsList.addAll(readingEvents);
            log.info("Załadowano {} zdarzeń z logu odczytów USB/PDF.", readingEvents.size());
        } catch (Exception e) {
            log.error("Błąd podczas odpytywania bazy o historię odczytów", e);
        }
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        loadData();
    }
}
