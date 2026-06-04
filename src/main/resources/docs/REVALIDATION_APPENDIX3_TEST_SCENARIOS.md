# Test Scenarios: Automatyczne Wypełnianie Raportu Walidacji (Załącznik nr 3)

Zestaw scenariuszy testowych (Manual & Automated Integration Tests) do walidacji automatycznego generowania **Załącznika nr 3** w standardzie GxP.

---

## Scenariusz 1: Pozytywny Wynik Walidacji (Standardowa Lodówka KKCz)

### Warunki początkowe (Setup):
1. Urządzenie: Lodówka Liebherr (typ komory: `FRIDGE`, materiał: `KKCz` / Koncentrat krwinek czerwonych, zakres roboczy: `2.0` do `6.0°C`).
2. Przypisane 8 rejestratorów Testo z poprawnymi świadectwami wzorcowania.
3. Wszystkie punkty pomiarowe w sesji mieszczą się w przedziale `2.3°C` do `5.8°C`.
4. Średnia temperatura komory wynosi `4.1°C`.
5. Data ostatniego pomiaru: `2026-05-25`.

### Kroki testowe:
1. Uruchom proces generowania raportu Załącznika nr 3 w systemie.
2. Pobierz wyjściowy plik `.docx` i zweryfikuj jego strukturę tekstową.

### Oczekiwany wynik (Pass Criteria):
* Zaznaczony checkbox: `$o4$` = `[X]` (pozostałe checkboxes `$o1$-$o3$`, `$o5$-$o7$` są puste).
* Tagi `$InneOpis$`, `$InneMin$`, `$InneMax$` zostały podmienione na puste ciągi znaków `""`.
* Akceptacja: `$tak$` = `"[X]"`, `$nie$` = `""`.
* Średnia temperatura `$AVGTemUrzadzenia$` = `4.1°C`.
* `$Wnioski$` zawiera potwierdzenie poprawnej pracy komory w zadanym zakresie 2.0 - 6.0°C.
* `$Uwagi$` zawiera wpis *"Brak uwag. Warunki akceptacji zostały spełnione."*
* `$dataNastepnejWalidacji$` wyznaczona automatycznie na: `2027-05-25` (dokładnie rok później).
* Tagi `$NrRPW$`, `$skrotPracowni$`, `$rokRPW$` zostały podmienione odpowiednio na numer planu, skrót pracowni oraz rok z aktywnego wpisu RPW (lub wartościami domyślnymi/fallbackami `"—"`, a dla skrótu pracowni pobrano skrót LR z urządzenia).

---

## Scenariusz 2: Negatywny Wynik Walidacji (Przekroczenie limitów)

### Warunki początkowe (Setup):
1. Urządzenie: Zamrażarka FFP (typ komory: `FREEZER`, materiał: `FFP`, zakres roboczy: `< -25.0°C`).
2. Przypisane rejestratorom poprane ewidencje wzorcowania.
3. Rejestrator na pozycji **Dół-Tył-Prawy (Pozycja 8)** zanotował chwilowe przekroczenie temperatury do wartości `-21.4°C` (powyżej limitu -25.0°C).
4. Data ostatniego pomiaru: `2026-05-25`.

### Kroki testowe:
1. Wywołaj generowanie raportu dla danej sesji.
2. Dokonaj analizy zawartości pliku wyjściowego.

### Oczekiwany wynik (Pass Criteria):
* Zaznaczony checkbox: `$o1$` = `[X]` (zamrażarka/mroźnia < -25°C).
* Akceptacja: `$tak$` = `""`, `$nie$` = `"[X]"`.
* `$Wnioski$` informuje, że komora NIE spełnia kryteriów akceptacji ze względu na przekroczenia temperatur roboczych.
* `$Uwagi$` precyzyjnie wskazuje czujnik i przekroczenie: *"Wykryto przekroczenia dopuszczalnej temperatury w następujących pozycjach: Dół - Tył-Prawy (temp: -21.4°C > -25.0°C). Wymagany przegląd serwisowy urządzenia oraz powtórzenie procedury rewalidacji."*
* `$dataNastepnejWalidacji$` ustawiona na wartość tekstową: *"NIEZWŁOCZNIE PO PODJĘTYCH DZIAŁANIACH NAPRAWCZYCH"*.

---

## Scenariusz 3: Obsługa Nietypowego Urządzenia (Opcja 7)

### Warunki początkowe (Setup):
1. Urządzenie: Zamrażarka niskotemperaturowa (typ komory: `FREEZER`, materiał: `Odczynniki/Próby`, zakres roboczy: `-80.0` do `-70.0°C`).
2. Wszystkie temperatury w sesji w normie (np. `-75.4°C`).
3. Brak pasującego standardowego wiersza 1-6 kryteriów akceptacji.

### Kroki testowe:
1. Wygeneruj Załącznik nr 3.
2. Sprawdź sekcję 7 w pliku wyjściowym.

### Oczekiwany wynik (Pass Criteria):
* Checkbox `$o7$` = `[X]`. Checkboxy `$o1$-$o6$` są całkowicie wyczyszczone.
* Tag `$InneOpis$` podmieniony na tekst: *"Zamrażarka (mroźnia) do przechowywania: Odczynniki/Próby"*.
* Tag `$InneMin$` = `-80.0`.
* Tag `$InneMax$` = `-70.0`.
* Akceptacja `$tak$` = `"[X]"` (ponieważ nie było przekroczeń zakresu -80 do -70°C).

---

## Scenariusz 4: Nieużywane Pozycje Czujników (< 8 Rejestratorów)

### Warunki początkowe (Setup):
1. Sesja rewalidacji ma podłączone tylko 5 rejestratorów (pozycje 1 do 5).
2. Pozycje 6, 7 i 8 są nieaktywne (brak przypisanych czujników i serii).

### Kroki testowe:
1. Wygeneruj raport.
2. Sprawdź tabelę w sekcji 10.

### Oczekiwany wynik (Pass Criteria):
* Wiersze tabeli od 1 do 5 są poprawnie wypełnione danymi rejestratorów (S/N, certyfikat, min, max, średnia).
* Tagi dla rejestratorów 6, 7 i 8 (np. `$nrSerRej6$`, `$TminRej7$`, `$dataWzorcowaniaRej8$` itd.) są całkowicie usunięte z dokumentu (zastąpione pustym ciągiem znaków).
* Wiersze te na wydruku pozostają puste (brak wiszących nazw zmiennych w wyjściowym pliku Word).

---

## Scenariusz 5: Sprawdzenie zawartości paczki rewalidacyjnej (ZIP)

### Warunki początkowe (Setup):
1. Sesja rewalidacji ukończona z 5 aktywnymi rejestratorami (pozostałe 3 są puste).
2. Przygotowany wykres zbiorczy sesji.

### Kroki testowe:
1. Wywołaj kompilację paczki rewalidacyjnej (klasa `RevalidationZipCompiler`).
2. Otwórz powstały plik ZIP i wylistuj jego zawartość.

### Oczekiwany wynik (Pass Criteria):
* W głównym katalogu archiwum ZIP znajduje się plik o nazwie **`Zalacznik_nr_3_Raport_z_walidacji_procesu_przechowywania.docx`**.
* Plik Załącznika nr 3 nie jest pusty i daje się otworzyć w programie MS Word.
* W głównym katalogu znajduje się również plik `Zalacznik_nr_8_Graficzny_schemat_rozmieszczenia_rejestratorow.docx` oraz główny raport PDF.
* Archiwum zawiera strukturę katalogów `wykresy/` (z 5 wykresami PDF) oraz `certyfikaty/` (z 5 świadectwami wzorcowania).
