# Wymagania Biznesowe (BA): Moduł Programowania Rejestratorów Testo 174T przez USB

## 1. Cel Biznesowy i Kontekst
Głównym celem modułu jest uniezależnienie Regionalnych Centrów Krwiodawstwa i Krwiolecznictwa (RCKiK) oraz laboratoriów farmaceutycznych od zamkniętego, płatnego oprogramowania Comfort Software (Testo). 

Wdrożenie funkcjonalności **bezpośredniego programowania rejestratorów Testo 174T z poziomu aplikacji VCC Desktop** pozwoli na:
*   Zamknięcie całego cyklu walidacji GxP w jednym systemie (od konfiguracji logera, przez pomiar, po wygenerowanie raportu PDF).
*   Gwarancję nienaruszalności danych (Data Integrity) zgodnie z FDA 21 CFR Part 11.
*   Automatyzację żmudnego procesu konfiguracji dziesiątek rejestratorów przed rozpoczęciem audytu mapowania przestrzennego komór chłodniczych.

## 2. Grupa Docelowa i Interesariusze
*   **Metrolodzy / Inżynierowie Walidacji:** Osoby fizycznie podłączające rejestratory do portu USB i konfigurujące czas startu przed włożeniem ich do badanej komory (lodówki/zamrażarki).
*   **Dział Zapewnienia Jakości (QA):** Wymaga, aby proces programowania był rejestrowany w Audit Trail (kto zaprogramował loger i na jakie wartości).
*   **Zarząd (RCKiK):** Wymaga optymalizacji kosztów licencyjnych (brak konieczności kupowania licencji na dedykowane oprogramowanie hardware'owe).

## 3. Kluczowe Wymagania Funkcjonalne (Epic: USB Programming)

### F.1. Konfiguracja Czasu Startu Pomiaru (Start Time)
*   **Opis:** System musi pozwolić użytkownikowi na określenie dokładnej, lokalnej daty i godziny rozpoczęcia pomiarów przez rejestrator. 
*   **Kryteria Akceptacji (AC):**
    *   Użytkownik wybiera datę/godzinę z intuicyjnego kontrolera Date/Time Picker.
    *   System automatycznie radzi sobie ze strefami czasowymi (Polska - CEST/CET) bez ingerencji użytkownika, przeliczając czas lokalny na odpowiedni delay (Start Delay w UTC) wysyłany do urządzenia.

### F.2. Konfiguracja Cyklu Pomiarowego (Interwał i Ilość)
*   **Opis:** Możliwość zdefiniowania, jak często loger ma dokonywać odczytów oraz ile punktów łącznie ma zebrać.
*   **Kryteria Akceptacji (AC):**
    *   Pole "Interwał" (w minutach) z walidacją (min: 1 min, max: 1440 min = 24h).
    *   Pole "Liczba pomiarów" (Count) z walidacją pojemności pamięci (max 16 000 dla Testo 174T).

### F.3. Konfiguracja Progów Alarmowych GxP (Opcjonalnie)
*   **Opis:** System pozwoli na ustawienie sprzętowych alarmów przekroczenia temperatury (High / Low Limit).
*   **Kryteria Akceptacji (AC):**
    *   Możliwość wpisania limitu dolnego i górnego z precyzją do 0.1°C (np. 2.0°C i 8.0°C dla lodówek na krew).
    *   Jeżeli użytkownik nie wpisze alarmów, system domyślnie wyśle bezpieczne granice komory, do której przypisany jest dany loger w sesji.

### F.4. Bezpośrednia Komunikacja USB z Testo 174T
*   **Opis:** Przesłanie skompresowanej ramki konfiguracyjnej bezpośrednio do urządzenia podłączonego do interfejsu (Interface) Testo wpiętego w port USB.
*   **Kryteria Akceptacji (AC):**
    *   System blokuje przycisk "Programuj", jeśli loger nie jest fizycznie wykryty w stacji dokującej.
    *   Wyświetlenie czytelnego komunikatu (Sukces/Błąd) z wykorzystaniem notyfikacji AtlantaFX (Pills / Alerts).

## 4. Wymagania Niefunkcjonalne (NFR)
*   **Niezawodność protokołu:** Brak możliwości "zbrickowania" (uszkodzenia oprogramowania) sprzętowego rejestratora. Błędny payload musi skutkować wyłącznie odrzuceniem ramki przez urządzenie (NACK `ab e1`).
*   **Responsywność (UX):** Zapis parametrów do pamięci EEPROM/Flash rejestratora po kablu USB musi odbywać się w wydzielonym wątku tła (np. JavaFX Task). Wątek UI nie może zostać zamrożony.
*   **Obsługa Błędów:** Każdy przypadek wyciągnięcia rejestratora ze stacji dokującej w trakcie programowania musi zostać przechwycony i obsłużony ładnym błędem aplikacji, a nie crashem systemu (Exception Handling).

## 5. Zgodność z Prawem (Compliance GxP)
Zgodnie z wymaganiami walidacyjnymi, proces programowania parametrów metrologicznych w urządzeniach pomiarowych jest akcją GxP-krytyczną. Fakt pomyślnego zaprogramowania sprzętu musi zostać odnotowany w warstwie logowania systemu, a dane konfiguracyjne muszą stanowić metrykę (metadata) dla późniejszych surowych odczytów, co blokuje możliwość podmiany czasu próbkowania po fakcie.
