package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.model.CoolingChamber;
import com.mac.bry.desktop.model.VolumeCategory;
import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import java.util.function.Function;

public class CoolingDeviceCellFactoryHelper {

    public static void setupVolumeCategoryCell(TableColumn<CoolingChamber, String> detChamberPdaCol) {
        detChamberPdaCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "BRAK".equals(item)) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label tagLabel = new Label();
                    tagLabel.getStyleClass().add("tag");

                    try {
                        VolumeCategory cat = VolumeCategory.valueOf(item);
                        switch (cat) {
                            case SMALL -> {
                                tagLabel.setText("Klasa S (≤ 2 m³) / 9 pkt");
                                tagLabel.getStyleClass().add(Styles.SUCCESS);
                            }
                            case MEDIUM -> {
                                tagLabel.setText("Klasa M (2–20 m³) / 15 pkt");
                                tagLabel.getStyleClass().add(Styles.ACCENT);
                            }
                            case LARGE -> {
                                tagLabel.setText("Klasa L (> 20 m³) / 27 pkt");
                                tagLabel.getStyleClass().add(Styles.WARNING);
                            }
                        }
                    } catch (Exception e) {
                        tagLabel.setText(item);
                    }
                    setGraphic(tagLabel);
                    setText(null);
                }
            }
        });
    }

    public static void setupRevalidationStatusCell(
            TableColumn<CoolingChamber, String> detChamberRevalCol,
            Function<Long, String> statusProvider) {

        detChamberRevalCol.setCellValueFactory(c -> {
            String status = statusProvider.apply(c.getValue().getId());
            return new SimpleStringProperty(status);
        });

        detChamberRevalCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label tagLabel = new Label();
                    tagLabel.getStyleClass().add("tag");
                    if (item.startsWith("Ważna")) {
                        tagLabel.setText("✅ " + item);
                        tagLabel.getStyleClass().add(Styles.SUCCESS);
                    } else if (item.startsWith("Brak") || item.startsWith("Wymagana")) {
                        tagLabel.setText("❌ " + item);
                        tagLabel.getStyleClass().add(Styles.DANGER);
                    } else {
                        tagLabel.setText("⚠️ " + item);
                        tagLabel.getStyleClass().add(Styles.WARNING);
                    }
                    setGraphic(tagLabel);
                    setText(null);
                }
            }
        });
    }
}
