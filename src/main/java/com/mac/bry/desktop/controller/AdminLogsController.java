package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.AccessLog;
import com.mac.bry.desktop.security.repository.AccessLogRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Kontroler zakładki dziennika zdarzeń (Access Logs) w panelu administracyjnym.
 * Wydzielony z AdminPanelController w celu zgodności z zasadą SRP.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminLogsController {

    private final AccessLogRepository accessLogRepository;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private TableView<AccessLog> accessLogsTable;
    @FXML private TableColumn<AccessLog, String> logTimestampColumn;
    @FXML private TableColumn<AccessLog, String> logUsernameColumn;
    @FXML private TableColumn<AccessLog, String> logActionColumn;
    @FXML private TableColumn<AccessLog, String> logDetailsColumn;

    @FXML
    public void initialize() {
        logTimestampColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTimestamp().format(DTF)));
        logUsernameColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUsername()));
        logActionColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAction()));
        logDetailsColumn.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDetails()));

        refreshAccessLogs();
    }

    @FXML
    public void refreshAccessLogs() {
        List<AccessLog> logs = accessLogRepository.findTop100ByOrderByTimestampDesc();
        accessLogsTable.setItems(FXCollections.observableArrayList(logs));
        log.debug("Odświeżono dziennik zdarzeń: {} wpisów.", logs.size());
    }
}
