package com.mac.bry.desktop.config;

import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.Locale;

/**
 * Konfiguracja ComboBoxa przełącznika języka UI (login + sidebar).
 * Wybór zapisywany trwale przez {@link I18n#switchTo}; widok przeładowywany
 * akcją {@code reloadAction}, bo załadowane FXML-e nie zmieniają tekstów
 * wstecznie.
 */
public final class LanguageSwitcher {

    private LanguageSwitcher() {
    }

    public static void configure(ComboBox<String> combo, Runnable reloadAction) {
        combo.getItems().setAll("pl", "en");
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String tag) {
                if (tag == null) return "";
                return switch (tag) {
                    case "pl" -> "🌐 Polski";
                    case "en" -> "🌐 English";
                    default -> tag;
                };
            }

            @Override
            public String fromString(String s) {
                return null;
            }
        });
        combo.setValue(I18n.getLocale().getLanguage());

        combo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.equals(Locale.of(I18n.getLocale().getLanguage()).getLanguage())
                    && newVal.equals(oldVal)) {
                return;
            }
            if (!newVal.equals(I18n.getLocale().getLanguage())) {
                I18n.switchTo(newVal);
                reloadAction.run();
            }
        });
    }
}
