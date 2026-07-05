package com.mac.bry.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext applicationContext;

    @Override
    public void init() {
        DotenvLoader.load();
        String[] args = getParameters().getRaw().toArray(new String[0]);

        // Tryb bazy danych: server (MySQL, domyślny) lub standalone (wbudowane H2)
        String dbMode = System.getProperty("DB_MODE",
                System.getenv().getOrDefault("DB_MODE", "server"));

        SpringApplicationBuilder builder = new SpringApplicationBuilder()
                .sources(ValidationDesktopApplication.class);
        if ("standalone".equalsIgnoreCase(dbMode.trim())) {
            builder.profiles("standalone");
        }
        // Inicjalizacja kontekstu Spring Boot w tle
        this.applicationContext = builder.run(args);

        // Locale UI: zapamiętany wybór użytkownika > app.locale/APP_LOCALE > pl
        com.mac.bry.desktop.config.I18n.initFromPreferences(
                applicationContext.getEnvironment().getProperty("app.locale", "pl"));
    }

    @Override
    public void start(Stage stage) throws Exception {
        // Ustawienie motywu AtlantaFX
        Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());

        // Załadowanie widoku przy użyciu standardowego FXMLLoader z wstrzykiwaniem Springa
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ui/login.fxml"),
                com.mac.bry.desktop.config.I18n.getBundle());
        fxmlLoader.setControllerFactory(applicationContext::getBean);
        Parent root = fxmlLoader.load();
        
        Scene scene = new Scene(root, 800, 600);
        
        // Dodanie globalnego arkusza stylów
        String css = getClass().getResource("/ui/style.css").toExternalForm();
        scene.getStylesheets().add(css);

        stage.setScene(scene);
        stage.setTitle("Validation System - Desktop Edition");
        stage.show();
    }

    @Override
    public void stop() {
        // Bezpieczne zamknięcie kontekstu Springa i wyjście z aplikacji
        this.applicationContext.close();
        Platform.exit();
    }
}
