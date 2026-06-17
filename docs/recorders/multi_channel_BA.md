# Analiza Biznesowa (BA) – Obsługa rejestratorów wielokanałowych

## 1. Wprowadzenie i Cel
Obecny system VCC Desktop APP został zaprojektowany z założeniem relacji 1:1 pomiędzy urządzeniem rejestrującym (`ThermoRecorder`) a aktywnym świadectwem wzorcowania. Model ten jest w pełni poprawny dla urządzeń jednokanałowych typu *Testo 174T* oraz *Testo 184 T3*. 

Z biznesowego punktu widzenia, podczas profesjonalnych mapowań rozkładu temperatury używa się również rejestratorów wielokanałowych, takich jak *Testo 176 T4* (posiadający 4 wejścia na termopary). Głównym wymogiem regulacyjnym (Polskie Centrum Akredytacji - PCA) jest to, aby każdy kanał (każda zewnętrzna sonda) takiego urządzenia posiadał własne świadectwo wzorcowania (bądź osobne tabele błędów w jednym zbiorczym dokumencie) traktowane w systemie metrologicznym oddzielnie.

Celem niniejszej modyfikacji jest dostosowanie architektury systemu do obsługi rejestratorów wielokanałowych, z możliwością śledzenia świadectw wzorcowania niezależnie dla każdego kanału.

## 2. Wymagania Biznesowe
1. **Zarządzanie modelami rejestratorów:**
   - W systemie musi istnieć zdefiniowany słownik modeli rejestratorów. 
   - Model urządzenia posiada informacje takie jak: nazwa, liczba kanałów, domyślna rozdzielczość pomiaru.
   - Administrator zyskuje dostęp do oddzielnego okna/zakładki "Słowniki -> Modele Rejestratorów", pozwalającego zarządzać listą urządzeń na rynku bez ingerencji w kod źródłowy.

2. **Rejestrowanie urządzeń:**
   - Ewidencja sprzętu pomiarowego nie opiera się już o wpisywanie tekstu (np. "Testo 174T"), ale o wybór konkretnego modelu z rozwijanej listy.

3. **Świadectwa wzorcowania (Certyfikaty PCA):**
   - Świadectwo wzorcowania powinno być powiązane nie tylko z fizycznym numerem seryjnym urządzenia bazy, ale również z precyzyjnym numerem kanału (od 1 do N).
   - Użytkownik przypisując świadectwo do np. "Testo 176T4", wskazuje dla którego kanału jest to certyfikat (np. "Kanał 3").
   - Dla starszych urządzeń 1-kanałowych, system automatycznie i niejawnie ustawia numer kanału na `1`.

4. **Kreator Rewalidacji (Wizard):**
   - W module mapowania poszczególnych narożników, użytkownik deklarując sprzęt dla "Góra - Tył-Lewy" nie wskazuje tylko Rejestratora S/N. Wskazuje Rejestrator S/N oraz konkretny Kanał (np. S/N 12345, Kanał 2).
   - Umożliwia to zmapowanie do 4 narożników komory używając fizycznie jednego urządzenia typu Testo 176 T4.
   - Algorytmy oceny niepewności (GUM) i walidacji GxP podciągają wzorcowanie przypisane do danego Kanału, a nie całego urządzenia.

## 3. Ryzyka GxP i Wpływ na System (Impact Assessment)
- **Integralność Danych:** Wykrywanie manipulacji polem "Kanał" w kreatorze. Logika przypisywania pliku CSV z mapowania musi ostatecznie uwzględniać sposób mapowania fizycznego pliku na dany kanał w systemie.
- **Kompatybilność Wsteczna:** Istniejące w bazie wpisy "model" jako text muszą zostać zmigrowane do referencji w tabeli modeli z jednoczesnym ustawieniem wszystkim obecnym rejestratorom i świadectwom `channel = 1`.
- **Raportowanie (PDF, Word):** Generatory muszą jasno wyszczególniać, z którego kanału urządzenia wielokanałowego pochodzą obliczenia dla danego punktu przestrzennego komory. (Np. w tabeli sprzętu użytego do rewalidacji: `Testo 176 T4 (SN: 12345) - Kanał 2`).
