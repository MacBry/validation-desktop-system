# Wymagania Biznesowe (BA): Integracja Rejestratorów Testo 184T (Programowanie i Odczyt PDF)

## 1. Cel Biznesowy i Kontekst
Rejestratory temperatury serii **Testo 184** (ze szczególnym uwzględnieniem modeli **T3**) są powszechnie stosowane w logistyce farmaceutycznej oraz laboratoriach krwiodawstwa (RCKiK) ze względu na wbudowany interfejs USB i automatyczne generowanie raportów PDF. 

Dotychczas proces ten wymagał korzystania z zewnętrznego oprogramowania producenta do konfiguracji urządzeń oraz ręcznej obsługi plików PDF w celu wyciągnięcia danych. Celem biznesowym tej integracji jest:
* **Pełna automatyzacja procesu kwalifikacji:** Umożliwienie programowania rejestratorów oraz pobierania pomiarów bezpośrednio z poziomu aplikacji *Validation Desktop*.
* **Gwarancja Integralności Danych (Data Integrity):** Zgodność z wytycznymi FDA 21 CFR Part 11 i ALCOA+ poprzez wyeliminowanie ręcznego przepisywania danych (zastąpienie go bezpośrednim odczytem binarnej sygnatury z pliku PDF).
* **Uproszczenie procedury audytowej:** Automatyczne łączenie odczytów z Rocznymi Planami Walidacji (RPW) urządzeń chłodniczych.

---

## 2. Grupa Docelowa i Interesariusze
* **Metrolodzy / Inspektorzy Walidacji:** Osoby fizycznie podłączające rejestratory do USB, konfigurujące parametry startu i alarmów, a po zakończeniu pomiarów wgrywające wygenerowany plik PDF do bazy.
* **Dział Zapewnienia Jakości (QA):** Wymaga, aby parametry programowania były rejestrowane w Audit Trail oraz by importowane pomiary były chronione przed manipulacją (zgodność ze specyfikacją GxP).
* **Audytorzy Zewnętrzni (Główny Inspektorat Farmaceutyczny / Urząd Rejestracji):** Oczekują jednoznacznej ścieżki audytu wykazującej poprawność i autentyczność importu danych.

---

## 3. Kluczowe Wymagania Funkcjonalne

### F.1. Programowanie Parametrów Rejestratora (USB Mass Storage)
* **Opis:** System musi pozwolić użytkownikowi na pełne skonfigurowanie parametrów pomiaru i zapisanie pliku konfiguracyjnego XML na rejestratorze wykrytym jako pamięć masowa.
* **Kryteria Akceptacji (AC):**
  * Użytkownik ma dostęp do następujących pól konfiguracyjnych:
    * **Tryb Startu:** Ręczny (przycisk START na urządzeniu) lub Czasowy (automatyczne uruchomienie o zadanej dacie/godzinie).
    * **Harmonogram:** Data i czas startu oraz zakończenia pomiarów.
    * **Opóźnienie Startu (Start Delay):** Czas w minutach, po którym loger zacznie mierzyć (dla trybu ręcznego).
    * **Interwał Pomiarowy:** Zakres od 1 do 1440 minut.
    * **Progi Alarmowe:** Osobna konfiguracja dla alarmów MAX (Górny) i MIN (Dolny) zawierająca temperaturę graniczną (°C) oraz skumulowany dozwolony czas naruszenia (w minutach).
    * **Metadane:** Imię/nazwisko osoby konfigurującej oraz komentarz (np. nazwa walidowanej lodówki).
  * Zapis konfiguracji następuje poprzez wygenerowanie pliku `testo 184 configuration_data.xml` bezpośrednio na dysku rejestratora.

### F.2. Wykrywanie Podłączonych Rejestratorów
* **Opis:** System automatycznie wykrywa dostępne w systemie litery dysków USB i pozwala użytkownikowi wybrać docelowy dysk rejestratora Testo 184T z listy rozwijanej (ComboBox).
* **Kryteria Akceptacji (AC):**
  * System filtruje napędy pod kątem typu (REMOVABLE/wymienne).
  * W przypadku braku wykrytych dysków wyświetla stosowny komunikat ostrzegawczy i pozwala wybrać docelową ścieżkę zapisu ręcznie.

### F.3. Bezpośredni Odczyt Pomiarów z Pliku PDF
* **Opis:** Import danych pomiarowych odbywa się poprzez wskazanie wygenerowanego przez rejestrator pliku raportu PDF. Dane są ekstrahowane bezpośrednio z zaszytego w pliku strumienia binarnego, co eliminuje błędy zaokrągleń wykresów PDF.
* **Kryteria Akceptacji (AC):**
  * System pozwala na wskazanie pliku PDF za pomocą standardowego okna dialogowego wyboru pliku.
  * System automatycznie dekoduje precyzyjne odczyty z wbudowanego strumienia binarnego z dokładnością do 4 miejsc po przecinku.
  * Zaimportowana seria pomiarowa jest wizualizowana na wykresie w aplikacji i zapisywana do bazy danych MySQL w powiązaniu z wybranym Rocznym Planem Walidacji (RPW).

---

## 4. Wymagania Niefunkcjonalne (NFR)
* **Responsywność:** Generowanie konfiguracji oraz parsowanie pliku PDF musi odbywać się w wątku tła (Task), nie powodując zamrożenia interfejsu JavaFX.
* **Zabezpieczenie przed uszkodzeniem plików:** Zapis pliku XML na dysk rejestratora musi zachowywać spójną strukturę plików i nie może powodować korupcji pamięci masowej urządzenia.
* **Odporność na błędy:** Jeśli plik PDF nie posiada wewnętrznego strumienia pomiarowego (np. jest zwykłym, wyeksportowanym z innego programu plikiem PDF), system musi to wykryć, wyświetlić czytelny komunikat błędu i nie dopuścić do zanieczyszczenia bazy danych.

---

## 5. Zgodność z Prawem (Compliance GxP / ALCOA+)
Akcja programowania rejestratora oraz importu pomiarów jest krytyczna pod kątem walidacji systemów komputerowych (CSV).
* Każde programowanie urządzenia i import danych generują zdarzenie w **Audit Trail** (kto, kiedy, jakie parametry, jaki numer seryjny urządzenia).
* Autonomiczny import pomiarów z binarnego strumienia PDF uniemożliwia ręczną manipulację punktami pomiarowymi przez użytkownika przed ich zapisaniem w bazie danych MySQL.
