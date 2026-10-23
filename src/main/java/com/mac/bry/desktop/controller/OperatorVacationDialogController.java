package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.model.UserVacation;
import com.mac.bry.desktop.repository.UserVacationRepository;
import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.service.planner.event.UnplannedAbsenceReportedEvent;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Zgłaszanie nieobecności operatora — urlopu planowanego albo nieplanowanego L4.
 * <p>
 * L4 publikuje zdarzenie, na które reaguje planer: zadania jeszcze
 * nierozpoczęte zwalniają sprzęt, a pomiary w toku biegną dalej z przesuniętym
 * odczytem. Urlop planowany nie wymaga rekalkulacji, bo generator planu
 * uwzględni go od razu.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OperatorVacationDialogController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final UserVacationRepository vacationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextField reasonField;
    @FXML private CheckBox unplannedL4Check;
    @FXML private Label lblHint;
    @FXML private TableView<UserVacation> vacationTable;
    @FXML private TableColumn<UserVacation, String> colStart;
    @FXML private TableColumn<UserVacation, String> colEnd;
    @FXML private TableColumn<UserVacation, String> colReason;
    @FXML private TableColumn<UserVacation, String> colType;

    @FXML
    public void initialize() {
        colStart.setCellValueFactory(c -> text(c.getValue().getStartDate() != null
                ? c.getValue().getStartDate().format(DATE_FMT) : "–"));
        colEnd.setCellValueFactory(c -> text(c.getValue().getEndDate() != null
                ? c.getValue().getEndDate().format(DATE_FMT) : "–"));
        colReason.setCellValueFactory(c -> text(c.getValue().getReason()));
        colType.setCellValueFactory(c -> text(Boolean.TRUE.equals(c.getValue().getUnplannedL4())
                ? I18n.t("planner.vacation.type_l4")
                : I18n.t("planner.vacation.type_planned")));

        unplannedL4Check.selectedProperty().addListener((obs, old, selected) -> updateHint(selected));
        startDatePicker.setValue(LocalDate.now());
        endDatePicker.setValue(LocalDate.now());
        updateHint(false);
        refresh();
    }

    private void updateHint(boolean unplannedL4) {
        lblHint.setText(unplannedL4
                ? I18n.t("planner.vacation.hint_l4")
                : I18n.t("planner.vacation.hint_planned"));
    }

    @FXML
    public void onSave() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();

        if (start == null || end == null) {
            showError(I18n.t("planner.vacation.dates_required"));
            return;
        }
        if (end.isBefore(start)) {
            showError(I18n.t("planner.vacation.end_before_start"));
            return;
        }

        try {
            UserVacation vacation = UserVacation.builder()
                    .user(currentUser())
                    .startDate(start)
                    .endDate(end)
                    .reason(reasonField.getText() != null ? reasonField.getText().trim() : null)
                    .unplannedL4(unplannedL4Check.isSelected())
                    .build();
            UserVacation saved = vacationRepository.save(vacation);

            if (Boolean.TRUE.equals(saved.getUnplannedL4())) {
                eventPublisher.publishEvent(new UnplannedAbsenceReportedEvent(
                        saved.getId(), currentUsername()));
                showInfo(I18n.t("planner.vacation.saved_l4"));
            } else {
                showInfo(I18n.t("planner.vacation.saved_planned"));
            }
            refresh();
        } catch (Exception e) {
            log.error("Nie udało się zapisać nieobecności {} – {}", start, end, e);
            showError(I18n.t("planner.vacation.save_failed") + "\n\n" + e.getMessage());
        }
    }

    @FXML
    public void onRefresh() {
        refresh();
    }

    private void refresh() {
        LocalDate from = LocalDate.now().minusMonths(6);
        LocalDate to = LocalDate.now().plusMonths(12);
        vacationTable.setItems(FXCollections.observableArrayList(
                vacationRepository.findOverlapping(currentUser(), from, to)));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication.getPrincipal() instanceof User user ? user : null;
    }

    private String currentUsername() {
        User user = currentUser();
        return user != null && user.getUsername() != null ? user.getUsername() : "SYSTEM";
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value != null ? value : "–");
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.t("planner.vacation.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(I18n.t("planner.vacation.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}