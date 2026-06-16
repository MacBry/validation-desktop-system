package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.controller.StatsDiagnosticsDialogController;
import com.mac.bry.desktop.controller.TestoRevalidationController.StatsRow;
import com.mac.bry.desktop.model.RevalidationSession;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public class TestoRevalidationDialogHelper {

    private static final Logger log = LoggerFactory.getLogger(TestoRevalidationDialogHelper.class);

    public static void showDiagnosticsDialog(StatsRow row, RevalidationSession session, ApplicationContext applicationContext, Window ownerWindow) {
        if (session == null || row == null) return;

        RevalidationSession.GridPosition pos = row.getGridPosition();
        RevalidationSession.PositionData posData = session.getAssignedPositions().get(pos);
        if (posData == null) return;

        try {
            FXMLLoader loader = new FXMLLoader(TestoRevalidationDialogHelper.class.getResource("/ui/stats_diagnostics_dialog.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent view = loader.load();

            StatsDiagnosticsDialogController controller = loader.getController();

            Double minLimit = session.getCoolingChamber() != null ? session.getCoolingChamber().getMinOperatingTemp() : 2.0;
            Double maxLimit = session.getCoolingChamber() != null ? session.getCoolingChamber().getMaxOperatingTemp() : 8.0;

            controller.setSensorData(posData.getSeries(), row.getPositionName(), minLimit, maxLimit);

            Stage stage = new Stage();
            stage.setTitle("Zaawansowana Diagnostyka SPC & Defrost - " + row.getPositionName());
            stage.setScene(new Scene(view));
            stage.initOwner(ownerWindow);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.show();
        } catch (Exception e) {
            log.error("Failed to load stats diagnostics dialog", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Błąd ładowania widoku");
            alert.setHeaderText("Nie udało się załadować okna diagnostyki SPC.");
            alert.setContentText(e.getMessage() != null ? e.getMessage() : e.toString());
            alert.showAndWait();
        }
    }
}
