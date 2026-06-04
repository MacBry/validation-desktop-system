package com.mac.bry.desktop.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordValidationTest {

    // ===== 1.5 Walidacja Siły Hasła =====

    @Nested
    @DisplayName("UT-20..25: Walidacja Siły Hasła")
    class PasswordStrengthTests {

        @Test
        @DisplayName("UT-20: Poprawne hasło 'Abcdef1!' — brak błędu")
        void shouldAcceptStrongPassword() {
            String result = LoginController.validatePasswordStrength("Abcdef1!");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("UT-21: Hasło 'abc' — za krótkie")
        void shouldRejectTooShortPassword() {
            String result = LoginController.validatePasswordStrength("abc");
            assertThat(result).contains("8 znaków");
        }

        @Test
        @DisplayName("UT-22: Hasło 'abcdefgh1!' — brak wielkiej litery")
        void shouldRejectPasswordWithoutUppercase() {
            String result = LoginController.validatePasswordStrength("abcdefgh1!");
            assertThat(result).contains("wielką literę");
        }

        @Test
        @DisplayName("UT-23: Hasło 'ABCDEFGH1!' — brak małej litery")
        void shouldRejectPasswordWithoutLowercase() {
            String result = LoginController.validatePasswordStrength("ABCDEFGH1!");
            assertThat(result).contains("małą literę");
        }

        @Test
        @DisplayName("UT-24: Hasło 'Abcdefgh!' — brak cyfry")
        void shouldRejectPasswordWithoutDigit() {
            String result = LoginController.validatePasswordStrength("Abcdefgh!");
            assertThat(result).contains("cyfrę");
        }

        @Test
        @DisplayName("UT-25: Hasło 'Abcdefgh1' — brak znaku specjalnego")
        void shouldRejectPasswordWithoutSpecialChar() {
            String result = LoginController.validatePasswordStrength("Abcdefgh1");
            assertThat(result).contains("znak specjalny");
        }
    }
}
