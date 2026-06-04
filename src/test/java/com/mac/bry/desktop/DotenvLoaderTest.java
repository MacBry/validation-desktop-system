package com.mac.bry.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DotenvLoaderTest {

    private static final String TEST_ENV_FILE = ".env";
    private File envFile;

    @BeforeEach
    void setUp() {
        envFile = new File(TEST_ENV_FILE);
        // Backup existing .env if any
        if (envFile.exists()) {
            envFile.renameTo(new File(".env.bak"));
        }
    }

    @AfterEach
    void tearDown() {
        if (envFile.exists()) {
            envFile.delete();
        }
        File backup = new File(".env.bak");
        if (backup.exists()) {
            backup.renameTo(envFile);
        }
        System.clearProperty("TEST_KEY_1");
        System.clearProperty("TEST_KEY_2");
        System.clearProperty("TEST_KEY_3");
    }

    @Test
    void shouldLoadVariablesToSystemProperties() throws IOException {
        // Given
        try (FileWriter writer = new FileWriter(envFile)) {
            writer.write("# To jest komentarz\n");
            writer.write("TEST_KEY_1=value1\n");
            writer.write("TEST_KEY_2=\"value2 with quotes\"\n");
            writer.write("  TEST_KEY_3 = 'value3 with single quotes'  \n");
            writer.write("\n"); // pusta linia
        }

        // When
        DotenvLoader.load();

        // Then
        assertEquals("value1", System.getProperty("TEST_KEY_1"));
        assertEquals("value2 with quotes", System.getProperty("TEST_KEY_2"));
        assertEquals("value3 with single quotes", System.getProperty("TEST_KEY_3"));
    }

    @Test
    void shouldIgnoreMissingEnvFile() {
        // Given - no .env file
        if (envFile.exists()) {
            envFile.delete();
        }

        // When/Then
        assertDoesNotThrow(DotenvLoader::load);
    }
}
