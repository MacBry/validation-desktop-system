package com.mac.bry.desktop.service.planner;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dni ustawowo wolne od pracy w Polsce — stałe oraz ruchome wyznaczane
 * z daty Wielkanocy (reguła W9, scenariusz ST-CAL-02).
 * <p>
 * Wielkanoc liczona algorytmem Meeusa/Jonesa/Butchera dla kalendarza
 * gregoriańskiego.
 */
@Component
public class PolishHolidayProvider {

    /** Święta o stałej dacie (ustawa o dniach wolnych od pracy). */
    private static final Map<MonthDay, String> FIXED_HOLIDAYS = Map.of(
            MonthDay.of(1, 1), "Nowy Rok",
            MonthDay.of(1, 6), "Święto Trzech Króli",
            MonthDay.of(5, 1), "Święto Pracy",
            MonthDay.of(5, 3), "Święto Narodowe Trzeciego Maja",
            MonthDay.of(8, 15), "Wniebowzięcie NMP",
            MonthDay.of(11, 1), "Wszystkich Świętych",
            MonthDay.of(11, 11), "Narodowe Święto Niepodległości",
            MonthDay.of(12, 24), "Wigilia Bożego Narodzenia",
            MonthDay.of(12, 25), "Boże Narodzenie (pierwszy dzień)",
            MonthDay.of(12, 26), "Boże Narodzenie (drugi dzień)"
    );

    /** Świąt w roku jest kilkanaście, a planer pyta o nie w pętli — warto trzymać w pamięci. */
    private final Map<Integer, Map<LocalDate, String>> cachePerYear = new ConcurrentHashMap<>();

    /**
     * Niedziela Wielkanocna dla podanego roku (Meeus/Jones/Butcher).
     */
    public LocalDate calculateEasterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    /**
     * Wszystkie dni ustawowo wolne w danym roku wraz z nazwami.
     */
    public Map<LocalDate, String> getHolidays(int year) {
        return cachePerYear.computeIfAbsent(year, this::buildHolidays);
    }

    public boolean isHoliday(LocalDate date) {
        return date != null && getHolidays(date.getYear()).containsKey(date);
    }

    public String getHolidayName(LocalDate date) {
        return date == null ? null : getHolidays(date.getYear()).get(date);
    }

    public Set<LocalDate> getHolidayDates(int year) {
        return getHolidays(year).keySet();
    }

    private Map<LocalDate, String> buildHolidays(int year) {
        Map<LocalDate, String> holidays = new HashMap<>();

        FIXED_HOLIDAYS.forEach((monthDay, name) -> holidays.put(monthDay.atYear(year), name));

        LocalDate easter = calculateEasterSunday(year);
        holidays.put(easter, "Niedziela Wielkanocna");
        holidays.put(easter.plusDays(1), "Poniedziałek Wielkanocny");
        holidays.put(easter.plusDays(49), "Zielone Świątki");
        holidays.put(easter.plusDays(60), "Boże Ciało");

        return Map.copyOf(holidays);
    }
}