# Business Analysis (BA): Automatyczne Wypełnianie Raportu Walidacji (Załącznik nr 3)

## 1. Cel Biznesowy i Kontekst GxP
W Centrach Krwiodawstwa i Krwiolecznictwa (RCKiK) oraz laboratoriach pracujących w reżimie **GxP/GMP**, kluczowym dokumentem potwierdzającym poprawność przechowywania składników krwi oraz odczynników jest **Załącznik nr 3 (Raport z walidacji procesu przechowywania)**. 

Tradycyjne ręczne uzupełnianie tego raportu przez metrologów na podstawie wykresów i tabel pomiarowych:
1. Jest czasochłonne i podatne na pomyłki (błędy przy przepisywaniu wartości temperatur).
2. Niesie za sobą ryzyko utraty spójności danych (**Data Integrity**).
3. Utrudnia standaryzację wniosków i uwag walidacyjnych.

Wprowadzenie automatycznego uzupełniania raportu DOCX opartego o tagi/znaczniki tekstowe ma na celu automatyzację tego procesu przy jednoczesnym zachowaniu elastyczności wyglądu dokumentu (możliwość edycji nagłówków, układu stron czy czcionek bezpośrednio w programie MS Word przez użytkowników biznesowych).

---

## 2. Wymagania Funkcjonalne

### 2.1. Dynamiczne Uzupełnianie Metryk
System musi automatycznie wyciągać z bazy danych i wstrzykiwać do szablonu Word:
* **Nazwę działu i pracowni** powiązaną z walidowanym urządzeniem.
* **Dane urządzenia**: pełną nazwę, typ komory oraz numer seryjny/inwentarzowy.
* **Zakres czasu**: datę początkową i końcową cyklu pomiarowego wyznaczoną na podstawie zsynchronizowanych serii.
* **Dane RPW (Roczne Plany Walidacji)**: numer planu (`$NrRPW$`), skrót laboratorium/pracowni (`$skrotPracowni$`) oraz rok planu (`$rokRPW$`) pobierane z aktywnego planu walidacji przypisanego do urządzenia.

### 2.2. Automatyczny Wybór Kryteriów Akceptacji (Sekcja 7)
System na podstawie typu komory oraz przechowywanego materiału (np. lodówka KKCz, zamrażarka FFP) musi automatycznie zaznaczyć odpowiedni checkbox (`$o1$` do `$o7$`) wpisując znak `[X]`. 

### 2.3. Obsługa Opcji Nietypowych (Opcja 7 - "Inne")
W przypadku urządzeń niewpisujących się w standardowe ramy (np. zamrażarki niskotemperaturowe $-80^\circ\text{C}$ czy mieszacze płytek krwi):
* Zaznaczony zostaje checkbox `$o7$`.
* W polu `$InneOpis$` generowany jest dynamiczny opis (np. *„Inkubator KKP do przechowywania: KKP na mieszaczach”*).
* W polach `$InneMin$` oraz `$InneMax$` wstrzykiwane są rzeczywiste zakresy dopuszczalnych temperatur z konfiguracji urządzenia.

### 2.4. Generowanie Tabeli Wyników (Sekcja 10)
Tabela w sekcji 10 musi zostać uzupełniona parametrami metrologicznymi dla maksymalnie 8 rejestratorów (temperatury minimalne, maksymalne, średnie, numery świadectw wzorcowania i daty ważności). Jeśli sesja wykorzystuje mniej niż 8 czujników (np. 5), tagi dla nieaktywnych rejestratorów (6-8) must zostać całkowicie wyczyszczone.

### 2.5. Automatyczna Decyzja i Wnioskowanie GxP
System musi samodzielnie zweryfikować poprawność pomiarów:
* **Warunek zaliczenia**: Brak jakichkolwiek przekroczeń temperatury roboczej na wszystkich podłączonych rejestratorach w trakcie trwania całej sesji.
* **Scenariusz Pozytywny**: Zaznaczenie checkboxa `$tak$`, wpisanie standardowej formuły o poprawnej pracy urządzenia, brak uwag, wyznaczenie daty kolejnej walidacji (za 12 miesięcy).
* **Scenariusz Negatywny**: Zaznaczenie checkboxa `$nie$`, wpisanie wniosku o niespełnieniu kryteriów, precyzyjne wskazanie czujników i wartości temperatur, które spowodowały przekroczenie w polu `$Uwagi$`, ustawienie daty kolejnej walidacji na *„NIEZWŁOCZNIE PO PODJĘTYCH DZIAŁANIACH NAPRAWCZYCH”*.

---

## 3. Wymagania Niefunkcjonalne i Bezpieczeństwo (FDA 21 CFR Part 11)
* **Integralność szablonu**: Plik wyjściowy must być poprawnie sformatowanym dokumentem DOCX i nie może zawierać żadnych "wiszących" tagów tekstowych (wszystkie niewypełnione pola muszą zostać zastąpione pustym ciągiem znaków).
* **Nienaruszalność**: Wygenerowany dokument musi odzwierciedlać wyłącznie dane znajdujące się w bazie pomiarowej, bez możliwości ręcznej modyfikacji wyników z poziomu aplikacji.

---

## 4. Integracja z Paczką Rewalidacyjną (Archiwum ZIP)
Załącznik nr 3 jest **obligatoryjnym elementem** kompletnego pakietu dokumentów rewalidacyjnych. Podczas finalizacji sesji rewalidacyjnej i eksportu danych, system automatycznie kompiluje paczkę ZIP. 

W skład wygenerowanej paczki rewalidacyjnej muszą wchodzić:
1. **Główny raport PDF** (raport zbiorczy z podsumowaniem i wykresem wielokanałowym).
2. **Załącznik nr 3 (DOCX)** (Raport z walidacji procesu przechowywania - uzupełniony automatycznie na podstawie szablonu).
3. **Załącznik nr 8 (DOCX)** (Graficzny schemat rozmieszczenia rejestratorów temperatury).
4. **Indywidualne wykresy PDF** dla każdej aktywnej lokalizacji czujnika pomiarowego.
5. **Świadectwa wzorcowania (PDF)** dla wszystkich użytych rejestratorów (wczytane z bazy lub wygenerowane makiety).
