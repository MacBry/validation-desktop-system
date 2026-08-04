package com.mac.bry.desktop.dto;

/**
 * Pojedynczy wiersz karty szczegółów rejestratora.
 * <p>
 * Widok jest celowo „głupi" — cała logika składania wartości (formaty, jednostki,
 * wyliczenia budżetu pracy, oznaczanie braków danych) siedzi w
 * {@code RecorderDetailsService}, dzięki czemu daje się przetestować bez JavaFX.
 *
 * @param section nagłówek grupy, po którym widok scala wiersze w sekcje
 * @param label   nazwa właściwości
 * @param value   wartość gotowa do wyświetlenia; nigdy {@code null} — brak danych
 *                jest jawnym tekstem, żeby operator odróżnił „nie wiemy" od „zero"
 */
public record RecorderDetailProperty(String section, String label, String value) {
}