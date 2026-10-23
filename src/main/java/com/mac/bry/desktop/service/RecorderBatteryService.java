package com.mac.bry.desktop.service;

import com.mac.bry.desktop.model.ThermoRecorder;
import com.mac.bry.desktop.repository.ThermoRecorderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Jedyne miejsce, w którym pozostały czas pracy baterii trafia do kartoteki
 * rejestratora.
 * <p>
 * Wartość pochodzi z ramki {@code ab010a} i jest tą samą liczbą, którą pokazuje
 * oryginalne oprogramowanie producenta. Zasila regułę W4c planera (budżet energii),
 * więc każda ścieżka odczytu sprzętu musi ją zapisywać — inaczej operator widzi
 * stan baterii na ekranie, a planer nadal uważa go za nieznany i blokuje badanie.
 * <p>
 * Zapis jest wydzielony z {@code TestoRevalidationService}, bo odczyt w zakładce
 * „Odczyt Testo” nie prowadzi sesji rewalidacyjnej i nie może zależeć od jej reguł
 * (m.in. blokady GxP na nieznanym numerze seryjnym).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecorderBatteryService {

    private final ThermoRecorderRepository thermoRecorderRepository;

    /**
     * Zapisuje odczyt dla rejestratora wskazanego numerem seryjnym.
     * <p>
     * Brak egzemplarza w kartotece <b>nie jest błędem</b> na tej ścieżce: zakładka
     * odczytu służy też do podglądu sprzętu jeszcze niezarejestrowanego. Rygor GxP
     * (blokada na nieznanym S/N) obowiązuje w procedurze rewalidacji, nie tutaj.
     *
     * @return {@code true}, gdy kartoteka została zaktualizowana
     */
    @Transactional
    public boolean recordReading(String serialNumber, int batteryRemainingDays) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return false;
        }

        Optional<ThermoRecorder> recorder = thermoRecorderRepository.findBySerialNumber(serialNumber);
        if (recorder.isEmpty()) {
            log.info("Rejestrator {} nie figuruje w kartotece — stan baterii nie został zapisany", serialNumber);
            return false;
        }

        return recordReading(recorder.get(), batteryRemainingDays);
    }

    /**
     * Zapisuje odczyt dla znanego już egzemplarza.
     * <p>
     * Sentinel {@code -1} („N/D”, gdy źródło nie raportuje baterii — np. import
     * z PDF 184) jest odrzucany: w kartotece ma zostać ostatni <b>rzeczywisty</b>
     * odczyt, a nie znacznik braku danych. Wartość ujemna nigdy nie trafia do bazy,
     * bo reguła W4c czyta to pole jako liczbę dni i arytmetyka na sentinelu dałaby
     * ujemny budżet energii.
     *
     * @return {@code true}, gdy kartoteka została zaktualizowana
     */
    @Transactional
    public boolean recordReading(ThermoRecorder recorder, int batteryRemainingDays) {
        if (batteryRemainingDays < 0) {
            log.debug("Pomijam zapis stanu baterii dla {} — źródło nie podało pozostałych dni ({})",
                    recorder.getSerialNumber(), batteryRemainingDays);
            return false;
        }

        recorder.setBatteryRemainingDays(batteryRemainingDays);
        recorder.setLastBatteryReadAt(LocalDateTime.now());
        thermoRecorderRepository.save(recorder);
        log.info("Zaktualizowano stan baterii rejestratora {}: pozostało {} dni",
                recorder.getSerialNumber(), batteryRemainingDays);
        return true;
    }
}