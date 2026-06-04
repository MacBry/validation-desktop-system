# Test Plan / Scenarios: Interfejs Użytkownika JavaFX (Jednostka 4)

Zestaw scenariuszy testów manualnych (oraz automatycznych testów UI przy użyciu TestFX) weryfikujących zachowanie interfejsu kreatora walidacji transportu.

---

## Scenariusz 1: Przepływ Nawigacji i Blokady Walidacyjne Kroków (Wizard Flow)

### Warunki początkowe (Setup):
1. Aplikacja uruchomiona w trybie deweloperskim.
2. Otwarty kreator walidacji warunków transportu (Krok 1).

### Kroki testowe:
1. Kliknij przycisk *„Dalej”* bez wypełniania pól (wybór pojazdu, trasy, kierowcy).
2. Wybierz pojazd i trasę z listy, wpisz kierowcę, a następnie kliknij *„Dalej”*.
3. W Kroku 2 zaznacz opcję *„Test awarii zasilania”* i kliknij *„Dalej”*.
4. W Kroku 3 kliknij *„Dalej”* bez załadowania pliku pomiarowego.
5. Załaduj plik pomiarowy Testo i kliknij *„Dalej”*.

### Oczekiwany wynik (Pass Criteria):
* W kroku 1 system blokuje przejście dalej i wyświetla czerwone etykiety walidacji przy pustych polach.
* Po uzupełnieniu pól w kroku 1, przejście do kroku 2 działa prawidłowo.
* W kroku 3 przejście dalej jest zablokowane do momentu udanego zaimportowania pliku pomiarowego (PDF/XML).
* Po wgraniu pliku, system odblokowuje przycisk nawigacji do kroku 4.

---

## Scenariusz 2: Interaktywne Kadrowanie Wykresu (Trim Range Interaction)

### Warunki początkowe (Setup):
1. Zaimportowana seria pomiarowa w kroku 3.
2. Kreator wyświetla Krok 4 (Wykres).
3. Suwak startowy ustawiony na indeks 0 (pomiary od 08:00), suwak końcowy ustawiony na ostatni indeks (pomiary do 12:00).

### Kroki testowe:
1. Przesuń lewy suwak w prawo (np. na czas 08:30).
2. Przesuń prawy suwak w lewo (np. na czas 11:30).
3. Zweryfikuj, czy linie pomocnicze na wykresie przesunęły się na wskazane godziny.
4. Kliknij *„Dalej”* i przejdź do podsumowania (Krok 5).

### Oczekiwany wynik (Pass Criteria):
* Linie pomocnicze na wykresie płynnie podążają za zmianą wartości suwaków.
* Zmiana zakresu czasu automatycznie wyzwala przeliczenie statystyk temperatury (min, max, avg).
* W kroku 5 raport zbiorczy uwzględnia wyłącznie wykadrowany przedział czasowy `[08:30, 11:30]`.
