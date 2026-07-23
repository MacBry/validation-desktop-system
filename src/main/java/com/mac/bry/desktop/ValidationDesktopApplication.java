package com.mac.bry.desktop;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class ValidationDesktopApplication {

    public static void main(String[] args) {
        // Zamiast SpringApplication.run, uruchamiamy aplikację JavaFX, 
        // która sama w środku zainicjuje kontekst Springa.
        Application.launch(JavaFxApplication.class, args);
    }
}