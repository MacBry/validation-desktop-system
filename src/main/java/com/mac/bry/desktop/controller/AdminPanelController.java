package com.mac.bry.desktop.controller;

import com.mac.bry.desktop.security.model.User;
import com.mac.bry.desktop.security.service.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Główny kontroler panelu administracyjnego – pełni rolę cienkiego kontenera (thin shell).
 * Odpowiada wyłącznie za widoczność zakładki Struktury w zależności od uprawnień.
 *
 * Logika poszczególnych zakładek jest wydzielona do dedykowanych kontrolerów:
 *   - AdminUsersController      (zakładka "Użytkownicy")
 *   - AdminStructureController  (zakładka "Struktura")
 *   - AdminLogsController       (zakładka "Logi Dostępu")
 *   - AdminMaterialsController  (zakładka "Słownik Materiałów")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminPanelController {

    private final UserService userService;

    /** Zakładka struktury organizacyjnej – widoczna tylko dla ROLE_SUPER_ADMIN */
    @FXML private Tab structureTab;

    @FXML
    public void initialize() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User currentUser) {
            boolean isSuperAdmin = userService.hasRole(currentUser, "ROLE_SUPER_ADMIN");
            if (structureTab != null && !isSuperAdmin) {
                structureTab.setDisable(true);
                log.debug("Zakładka Struktury wyłączona dla użytkownika: {} (brak ROLE_SUPER_ADMIN)",
                        currentUser.getUsername());
            }
        }
    }
}
