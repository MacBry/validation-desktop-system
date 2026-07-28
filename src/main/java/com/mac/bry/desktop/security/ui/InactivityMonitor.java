package com.mac.bry.desktop.security.ui;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.service.UserService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class InactivityMonitor {

    private final ApplicationContext applicationContext;
    private final UserService userService;
    private final com.mac.bry.desktop.security.service.AuditService auditService;
    
    @Value("${app.security.inactivity-timeout-minutes:15}")
    private int timeoutMinutes;

    private Timeline timeline;
    private Timeline activityTimeline;
    private Scene trackedScene;
    private final EventHandler<Event> resetHandler = event -> resetTimer();

    public void startMonitoring(Scene scene) {
        this.trackedScene = scene;
        log.info("Rozpoczęto monitorowanie bezczynności (limit: {} min)", timeoutMinutes);
        
        stopMonitoring();

        scene.addEventFilter(Event.ANY, resetHandler);

        // Licznik do wylogowania
        timeline = new Timeline(new KeyFrame(Duration.minutes(timeoutMinutes), event -> logoutUser()));
        timeline.setCycleCount(1);
        timeline.play();

        // Licznik do odświeżania aktywności w bazie (co 5 min)
        activityTimeline = new Timeline(new KeyFrame(Duration.minutes(5), event -> updateDatabaseActivity()));
        activityTimeline.setCycleCount(Timeline.INDEFINITE);
        activityTimeline.play();
    }

    private void updateDatabaseActivity() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            Long userId = user.getId();
            // Walidacja sesji: jeśli token został zdalnie wyczyszczony (wymuszone wylogowanie
            // przez administratora) lub nadpisany (przejęcie sesji), zakończ tę sesję.
            if (!userService.isSessionValid(userId, user.getSessionToken())) {
                log.warn("Sesja użytkownika ID {} została zakończona zdalnie. Automatyczne wylogowanie.", userId);
                logoutUser();
                return;
            }
            userService.updateActivity(userId);
            log.debug("Odświeżono aktywność sesji w bazie dla użytkownika ID: {}", userId);
        } else {
            log.debug("Nie można odświeżyć aktywności - brak zalogowanego użytkownika.");
            stopMonitoring(); // Jeśli nie ma auth, nie ma sensu dalej monitorować
        }
    }

    public void stopMonitoring() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
        if (activityTimeline != null) {
            activityTimeline.stop();
            activityTimeline = null;
        }
        if (trackedScene != null) {
            trackedScene.removeEventFilter(Event.ANY, resetHandler);
        }
    }

    private void resetTimer() {
        if (timeline != null) {
            timeline.playFromStart();
        }
    }

    private void logoutUser() {
        log.warn("Użytkownik bezczynny przez {} min. Automatyczne wylogowanie.", timeoutMinutes);
        
        // Zatrzymujemy monitorowanie natychmiast
        stopMonitoring();

        // Czyszczenie sesji w bazie danych i audyt
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.mac.bry.desktop.security.model.User) {
            User user = (User) auth.getPrincipal();
            userService.clearSession(user.getId());
            auditService.logAccessEvent(user.getUsername(), "LOGOUT", "Automatyczne wylogowanie (timeout " + timeoutMinutes + " min)");
        }

        // Czyszczenie kontekstu bezpieczeństwa
        SecurityContextHolder.clearContext();
        
        // Powrót do ekranu logowania na wątku UI
        Platform.runLater(() -> {
            try {
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ui/login.fxml"),
                        com.mac.bry.desktop.config.I18n.getBundle());
                fxmlLoader.setControllerFactory(applicationContext::getBean);
                Parent root = fxmlLoader.load();
                
                if (trackedScene != null && trackedScene.getWindow() instanceof Stage) {
                    Stage stage = (Stage) trackedScene.getWindow();
                    stage.setScene(new Scene(root, 800, 600));
                    stage.setTitle("Validation System - Zaloguj się ponownie");
                    stage.centerOnScreen();
                }
            } catch (IOException e) {
                log.error("Błąd podczas ładowania ekranu logowania po timeout", e);
            }
        });
    }
}
