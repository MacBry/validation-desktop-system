package com.mac.bry.desktop.integration;

import com.mac.bry.desktop.security.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test trybu STANDALONE: pełny kontekst Springa na profilu standalone
 * z plikową bazą H2 i włączonym Flyway (migracje common + h2 przez {vendor}).
 * Odpowiada realnemu startowi aplikacji z DB_MODE=standalone.
 */
@SpringBootTest
@ActiveProfiles("standalone")
class StandaloneProfileBootTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void h2FileInTempDir(DynamicPropertyRegistry registry) {
        // Plikowa H2 w katalogu tymczasowym testu zamiast ./data obok repo
        registry.add("H2_DATA_PATH", () -> tempDir.resolve("standalone_boot").toString());
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("TC-H2-002: Profil standalone startuje z plikową H2, Flyway seeduje admina")
    void tc_h2_002_standaloneContextBootsWithSeededAdmin() {
        assertThat(userRepository.findByUsername("admin"))
                .as("konto admina z seeda migracji Flyway na plikowej H2")
                .isPresent();
        assertThat(tempDir.resolve("standalone_boot.mv.db"))
                .as("plik bazy H2 utworzony w skonfigurowanej ścieżce")
                .exists();
    }
}
