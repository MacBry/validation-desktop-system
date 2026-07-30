package com.mac.bry.desktop.service.planner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ST-CAL-02 — święta ruchome wyznaczane z daty Wielkanocy.
 */
class PolishHolidayProviderTest {

    private final PolishHolidayProvider provider = new PolishHolidayProvider();

    @ParameterizedTest(name = "Wielkanoc {0} = {1}")
    @CsvSource({
            "2024, 2024-03-31",
            "2025, 2025-04-20",
            "2026, 2026-04-05",
            "2027, 2027-03-28",
            "2038, 2038-04-25"  // najpóźniejsza możliwa data Wielkanocy
    })
    @DisplayName("Algorytm Meeusa/Jonesa/Butchera wyznacza Niedzielę Wielkanocną")
    void easterSundayIsCorrect(int year, LocalDate expected) {
        assertThat(provider.calculateEasterSunday(year)).isEqualTo(expected);
    }

    @Test
    @DisplayName("ST-CAL-02: Poniedziałek Wielkanocny i Boże Ciało 2026 rozpoznane jako świąteczne")
    void movableHolidays2026() {
        assertThat(provider.isHoliday(LocalDate.of(2026, 4, 6)))
                .as("Poniedziałek Wielkanocny 2026").isTrue();
        assertThat(provider.isHoliday(LocalDate.of(2026, 6, 4)))
                .as("Boże Ciało 2026 (Wielkanoc + 60 dni)").isTrue();
        assertThat(provider.isHoliday(LocalDate.of(2026, 5, 24)))
                .as("Zielone Świątki 2026 (Wielkanoc + 49 dni)").isTrue();

        assertThat(provider.getHolidayName(LocalDate.of(2026, 6, 4))).isEqualTo("Boże Ciało");
    }

    @Test
    @DisplayName("Boże Ciało wypada zawsze w czwartek")
    void corpusChristiIsAlwaysThursday() {
        for (int year = 2024; year <= 2035; year++) {
            LocalDate corpusChristi = provider.calculateEasterSunday(year).plusDays(60);
            assertThat(corpusChristi.getDayOfWeek())
                    .as("Boże Ciało %d", year)
                    .isEqualTo(java.time.DayOfWeek.THURSDAY);
        }
    }

    @ParameterizedTest(name = "{0} jest świętem stałym")
    @CsvSource({
            "2026-01-01", "2026-01-06", "2026-05-01", "2026-05-03",
            "2026-08-15", "2026-11-01", "2026-11-11", "2026-12-25", "2026-12-26"
    })
    @DisplayName("Święta o stałej dacie")
    void fixedHolidays(LocalDate date) {
        assertThat(provider.isHoliday(date)).isTrue();
    }

    @Test
    @DisplayName("Zwykły dzień roboczy nie jest świętem")
    void ordinaryDayIsNotHoliday() {
        assertThat(provider.isHoliday(LocalDate.of(2026, 7, 29))).isFalse();
        assertThat(provider.getHolidayName(LocalDate.of(2026, 7, 29))).isNull();
    }

    @Test
    @DisplayName("null nie wysadza providera")
    void nullIsSafe() {
        assertThat(provider.isHoliday(null)).isFalse();
        assertThat(provider.getHolidayName(null)).isNull();
    }
}