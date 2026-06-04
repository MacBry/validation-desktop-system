package com.mac.bry.desktop.controller.helper;

import com.mac.bry.desktop.security.model.Department;
import com.mac.bry.desktop.security.model.Laboratory;
import com.mac.bry.desktop.security.model.User;
import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.function.Function;

@Slf4j
public class AdminUsersTableHelper {

    public static void setupTableColumns(
            TableColumn<User, Long> idColumn,
            TableColumn<User, String> usernameColumn,
            TableColumn<User, String> emailColumn,
            TableColumn<User, String> deptCol,
            TableColumn<User, String> labCol,
            TableColumn<User, Boolean> enabledColumn,
            TableColumn<User, Boolean> lockedColumn) {

        idColumn.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getId()));
        usernameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        emailColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        deptCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDepartment() != null ? c.getValue().getDepartment().getName() : "-"));
        labCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getLaboratory() != null ? c.getValue().getLaboratory().getName() : "-"));

        enabledColumn.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isEnabled()));
        enabledColumn.setCellFactory(col -> statusTagCell("Aktywny", "Nieaktywny"));

        lockedColumn.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isLocked()));
        lockedColumn.setCellFactory(col -> statusTagCell("Zablokowany", "Aktywne"));

        log.debug("User management table columns setup completed");
    }

    private static TableCell<User, Boolean> statusTagCell(String trueLabel, String falseLabel) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label tag = new Label(item ? trueLabel : falseLabel);
                tag.getStyleClass().add("tag");
                boolean isDanger = trueLabel.equals("Zablokowany") ? item : !item;
                tag.getStyleClass().add(isDanger
                        ? Styles.DANGER
                        : Styles.SUCCESS);
                setGraphic(tag);
                setText(null);
            }
        };
    }

    public static void setupFilters(
            TextField searchField,
            ComboBox<String> statusFilterCombo,
            FilteredList<User> filteredData) {

        searchField.textProperty().addListener((obs, old, nv) -> updateFilter(searchField, statusFilterCombo, filteredData));
        statusFilterCombo.valueProperty().addListener((obs, old, nv) -> updateFilter(searchField, statusFilterCombo, filteredData));

        log.debug("User management filters setup completed");
    }

    public static void updateFilter(
            TextField searchField,
            ComboBox<String> statusFilterCombo,
            FilteredList<User> filteredData) {

        String text = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String status = statusFilterCombo.getValue();
        filteredData.setPredicate(user -> {
            boolean matchesText = text.isEmpty()
                    || user.getUsername().toLowerCase().contains(text)
                    || (user.getFirstName() != null && user.getFirstName().toLowerCase().contains(text))
                    || (user.getLastName() != null && user.getLastName().toLowerCase().contains(text))
                    || user.getEmail().toLowerCase().contains(text);
            boolean matchesStatus = switch (status == null ? "Wszyscy" : status) {
                case "Aktywni" -> user.isEnabled();
                case "Nieaktywni" -> !user.isEnabled();
                case "Zablokowani" -> user.isLocked();
                default -> true;
            };
            return matchesText && matchesStatus;
        });
    }

    public static void setupOrganizationComboBoxes(
            ComboBox<Department> deptComboBox,
            ComboBox<Laboratory> labComboBox,
            Function<Long, List<Laboratory>> labListProvider) {

        deptComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Department d) { return d == null ? "" : d.getName(); }
            @Override public Department fromString(String s) { return null; }
        });
        labComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Laboratory l) { return l == null ? "" : l.getName(); }
            @Override public Laboratory fromString(String s) { return null; }
        });
        deptComboBox.getSelectionModel().selectedItemProperty().addListener((obs, old, nv) -> {
            if (nv != null) labComboBox.setItems(FXCollections.observableArrayList(labListProvider.apply(nv.getId())));
            else labComboBox.setItems(FXCollections.emptyObservableList());
        });
    }
}
