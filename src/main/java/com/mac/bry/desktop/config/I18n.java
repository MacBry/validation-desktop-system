package com.mac.bry.desktop.config;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

/**
 * Centralny punkt internacjonalizacji UI.
 * <p>
 * Bundle bazowy {@code i18n/messages.properties} zawiera teksty polskie
 * (język domyślny aplikacji); {@code messages_en.properties} — angielskie.
 * Locale ustawiane raz przy starcie z konfiguracji ({@code app.locale},
 * nadpisywalne przez zmienną środowiskową {@code APP_LOCALE}).
 * <p>
 * Statyczny holder — FXMLLoadery i kontrolery tworzone są także poza
 * kontekstem Springa (dialogi, monitor bezczynności), a locale nie zmienia
 * się w trakcie działania aplikacji.
 */
public final class I18n {

    private static final String BUNDLE_BASE = "i18n.messages";

    /**
     * Bez fallbacku przez systemowe default locale — nieznany język ma spadać
     * wprost na bundle bazowy (PL), identycznie na każdej maszynie i na CI.
     */
    private static final ResourceBundle.Control NO_FALLBACK_CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private static volatile Locale locale = Locale.forLanguageTag("pl");
    private static volatile ResourceBundle bundle =
            ResourceBundle.getBundle(BUNDLE_BASE, locale, NO_FALLBACK_CONTROL);

    private I18n() {
    }

    private static final String PREF_KEY = "ui.locale";

    /** Inicjalizacja locale przy starcie aplikacji (np. "pl", "en"). */
    public static void init(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return;
        }
        locale = Locale.forLanguageTag(languageTag.trim());
        bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale, NO_FALLBACK_CONTROL);
    }

    /**
     * Inicjalizacja przy starcie z uwzględnieniem wyboru użytkownika:
     * zapamiętana preferencja (przełącznik w UI) ma pierwszeństwo przed
     * konfiguracją {@code app.locale} / zmienną APP_LOCALE.
     */
    public static void initFromPreferences(String configFallback) {
        String saved = Preferences.userNodeForPackage(I18n.class).get(PREF_KEY, null);
        init(saved != null ? saved : configFallback);
    }

    /**
     * Przełączenie języka z UI: ustawia locale i zapisuje wybór trwale
     * (java.util.prefs — rejestr/konfiguracja per użytkownik systemu).
     * Widoki załadowane przed przełączeniem wymagają przeładowania.
     */
    public static void switchTo(String languageTag) {
        init(languageTag);
        Preferences.userNodeForPackage(I18n.class).put(PREF_KEY, locale.toLanguageTag());
    }

    public static Locale getLocale() {
        return locale;
    }

    /** Bundle do przekazania w {@code new FXMLLoader(url, I18n.getBundle())}. */
    public static ResourceBundle getBundle() {
        return bundle;
    }

    /**
     * Tłumaczenie klucza z opcjonalnymi parametrami {@link MessageFormat}.
     * Brakujący klucz zwraca "!klucz!" zamiast wyjątku — brak tłumaczenia
     * nie może wywracać UI, a marker jest łatwy do wychwycenia w testach.
     */
    public static String t(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }
}
