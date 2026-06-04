# Analiza Biznesowa: Urządzenia Chłodnicze i Typy Materiałów

## 1. Cel i Kontekst Biznesowy
W procesach produkcyjnych, dystrybucyjnych i badawczych w branży farmaceutycznej, medycznej oraz spożywczej (sektory regulowane przez **GMP, GDP, FDA 21 CFR Part 11**), kluczowym wymaganiem jest zagwarantowanie stabilnych i kontrolowanych warunków przechowywania produktów wrażliwych temperaturowo (np. szczepionek, składników krwi, surowców farmaceutycznych).

Urządzenia chłodnicze (komory kontrolowane temperaturowo) stanowią krytyczną infrastrukturę techniczną. System **VCC Desktop** ma za zadanie prowadzić pełną ewidencję tych urządzeń, nadzorować ich plany walidacji przestrzennej oraz gwarantować integralność danych metrologicznych i operacyjnych.

---

## 2. Ewidencja Urządzeń Chłodniczych (Cooling Devices)
Każda komora temperaturowa wprowadzana do systemu jest precyzyjnie opisywana pod kątem metrologicznym i organizacyjnym.

### 2.1. Atrybuty Klasyfikacyjne i Biznesowe
*   **Identyfikowalność (Inventory Number):** Każde urządzenie musi posiadać unikalny w skali przedsiębiorstwa numer inwentarzowy/kod kreskowy, umożliwiający bezbłędną identyfikację podczas audytów.
*   **Typ Komory (Chamber Type):** Podział na fizyczne typy urządzeń (lodówki, zamrażarki, zamrażarki niskotemperaturowe, chłodnie, mroźnie), co determinuje domyślne zakresy pracy oraz procedury walidacyjne.
*   **Zakres Roboczy:** Definiowanie minimalnej i maksymalnej dopuszczalnej temperatury pracy komory (np. 2.0°C do 8.0°C).

### 2.2. Lokalizacja Organizacyjna
Urządzenia są przypisywane bezpośrednio do **Działów (Departments)** oraz opcjonalnie do konkretnych **Pracowni (Laboratories)**. Pozwala to na pełną strukturę właścicielską (Data Ownership) oraz filtrowanie dostępów dla operatorów poszczególnych komórek badawczych.

---

## 3. Standardy Metrologiczne: Klasyfikacja Kubatury (Volume Category)
Zgodnie z uznanymi międzynarodowymi standardami metrologicznymi – w tym **PDA Technical Report No. 64 (Active Temperature-Controlled Systems)** oraz wytycznymi **WHO (World Health Organization)** – liczba czujników (rejestratorów) użytych do walidacji przestrzennej komory (mapowania temperatury) zależy bezpośrednio od jej pojemności.

Wprowadzona w systemie kategoryzacja automatycznie weryfikuje poprawność planów pomiarowych:

| Klasa Kubatury | Przedział Objętości | Przykładowe Zastosowanie | Minimalna Liczba Czujników Pomiarowych |
| :--- | :--- | :--- | :---: |
| **Klasa S (SMALL)** | $\le$ 2 m³ | Lodówki apteczne, małe szafy laboratoryjne | **9 punktów** (narożniki + środek) |
| **Klasa M (MEDIUM)** | 2 – 20 m³ | Szafy chłodnicze walk-in, duże zamrażarki | **15 punktów** (narożniki, środek, płaszczyzny) |
| **Klasa L (LARGE)** | $>$ 20 m³ | Komory chłodnicze, magazyny wysokiego składowania | **27 punktów** (trójwymiarowa siatka mapowania) |

---

## 4. Standaryzacja Przechowywanych Materiałów (Material Types)
Zamiast swobodnego wpisywania tekstu przez użytkowników (co prowadzi do błędów, braku spójności oraz utrudnia raportowanie), system stosuje **słownikowy rejestr kategorii przechowywanych materiałów**.

### 4.1. Korzyści ze Słownika Materiałów:
1.  **Zapewnienie Zgodności (Standardization):** Eliminacja błędów literowych i rozbieżności (np. "szczepionka", "Szczepionki", "Vaccines").
2.  **Walidacja Temperatury:** Słownik przechowuje zatwierdzone zakresy dopuszczalnych temperatur dla danej grupy (np. krew musi być przechowywana w temperaturze od 2.0°C do 6.0°C).
3.  **Kryteria Aktywacji:** Możliwość dezaktywowania kategorii w słowniku bez utraty danych historycznych w urządzeniach, które już z tej kategorii korzystały (mechanizm `active = false`).
4.  **Budżet Niepewności (Activation Energy):** Przechowywanie parametru energii aktywacji ($E_a$ w kJ/mol) pozwala w przyszłych krokach na automatyczne wyliczanie wskaźnika **MKT (Mean Kinetic Temperature)** dla produktów wrażliwych.

---

## 5. Zgodność z GxP i Bezpieczeństwo
*   **Pełny Audyt (Audit Trail / ALCOA+):** Wszystkie dane dotyczące urządzeń chłodniczych i słownika materiałów podlegają automatycznemu śledzeniu wersji (wersjonowanie zmian). Zmiana parametrów pracy urządzenia, przeniesienie do innego działu czy edycja słownika są rejestrowane i powiązane z zalogowanym użytkownikiem.
*   **Separacja Uprawnień:** Standardowi operatorzy posiadają dostęp wyłącznie do urządzeń przypisanych do ich komórek organizacyjnych, podczas gdy administratorzy systemowi (Metrolodzy / QA) mogą zarządzać słownikiem materiałów i globalną ewidencją urządzeń.
