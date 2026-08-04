package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.config.I18n;
import com.mac.bry.desktop.dto.RecorderDetailProperty;
import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.service.RecorderDetailsService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
@Slf4j
public class ThermoRecorderDetailsController {

    private final RecorderDetailsService detailsService;

    @FXML private Label titleLabel;
    @FXML private TableView<RecorderDetailProperty> detailsTable;
    @FXML private TableColumn<RecorderDetailProperty, String> sectionColumn;
    @FXML private TableColumn<RecorderDetailProperty, String> propertyColumn;
    @FXML private TableColumn<RecorderDetailProperty, String> valueColumn;

    @FXML
    public void initialize() {
        sectionColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().section()));
        propertyColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().label()));
        valueColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().value()));

        // Karta, nie arkusz danych — sortowanie rozsypałoby podział na sekcje,
        // a kolejność wierszy niesie tu znaczenie (od identyfikacji po historię).
        sectionColumn.setSortable(false);
        propertyColumn.setSortable(false);
        valueColumn.setSortable(false);
    }

    public void setRecorder(ThermoRecorder recorder) {
        if (recorder == null) {
            return;
        }
        titleLabel.setText(I18n.t("recorderdetails.title") + ": " + recorder.getSerialNumber());
        detailsTable.setItems(FXCollections.observableArrayList(detailsService.buildDetails(recorder)));
        log.debug("Otwarto szczegóły rejestratora {}", recorder.getSerialNumber());
    }

    @FXML
    public void handleClose() {
        ((Stage) detailsTable.getScene().getWindow()).close();
    }
}