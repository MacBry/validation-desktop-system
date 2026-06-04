# Plan Kwalifikacji Porównawczej (Comparative Qualification Plan): Moduł Testo

**Dokumentacja Systemowa CSV (Computerized System Validation) w standardzie GxP**

**Nazwa Systemu Badającego:** Validation Desktop  
**Nazwa Systemu Referencyjnego:** Testo Comfort Software / Fabryczne Szablony XFA (Adobe Reader)  
**Dotyczy Urządzeń:** Testo 174T, Testo 184T  

---

## 1. Cel i Zakres Kwalifikacji

### 1.1. Cel
Celem niniejszego planu kwalifikacji jest przedstawienie dowodów walidacyjnych na to, że moduły programowania oraz odczytu danych w aplikacji **Validation Desktop** działają w sposób tożsamy (identyczny) z oficjalnym oprogramowaniem referencyjnym producenta (**Testo Comfort Software** oraz fabryczne generatory raportów). 

W branży regulowanej (GMP/GDP) wykazanie zgodności i braku odchyleń (drifru danych/czasu) w stosunku do oprogramowania certyfikowanego przez producenta jest kluczowym warunkiem dopuszczenia systemu do użytku produkcyjnego.

### 1.2. Zakres
Kwalifikacja porównawcza obejmuje trzy główne obszary:
1. **Część A (Programowanie):** Porównanie spójności danych konfiguracyjnych zapisywanych do rejestratora (pliki XML/XDP oraz ramki USB).
2. **Część B (Odczyt surowy):** Porównanie odczytanych punktów pomiarowych (wartości temperatur i stempli czasowych).
3. **Część C (Obliczenia metrologiczne):** Porównanie statystyk wyliczanych przez system (Min, Max, Średnia, MKT) z wartościami referencyjnymi raportu fabrycznego.

---

## 2. Środowisko i Narzędzia Testowe
Do przeprowadzenia kwalifikacji wymagane jest:
* Komputer testowy z zainstalowanym systemem operacyjnym Windows, aplikacją **Validation Desktop** oraz **Testo Comfort Software**.
* Rejestrator **Testo 174T** wraz z kołyską USB.
* Rejestrator **Testo 184T3**.
* Oprogramowanie porównawcze plików (np. **WinMerge** lub komenda `fc` w systemie Windows).
* Arkusz kalkulacyjny (np. Excel) do zrzucenia i matematycznego porównania tabel danych pomiarowych.

---

## 3. Protokół Kwalifikacji Porównawczej

### Protokół A: Kwalifikacja Programowania (Zapis Konfiguracji)

| ID Kroku | Opis Działania | System Referencyjny (Testo) | System Badany (Validation Desktop) | Kryteria Akceptacji |
| :--- | :--- | :--- | :--- | :--- |
| **QP-A-01** | **Porównanie struktury XML (Testo 184T)** | Wygeneruj plik konfiguracji XML przy pomocy oryginalnego pliku PDF. Zapisz jako `config_ref.xml`. | Wygeneruj plik konfiguracji XML przy pomocy formularza w Validation Desktop o tych samych parametrach. Zapisz jako `config_test.xml`. | Wartości w polu `<alldata>` w obu plikach (odkodowane z Hex ASCII) muszą być identyczne (spójność interwału, czasów startu, alarmów). |
| **QP-A-02** | **Weryfikacja opóźnienia startu ($Start\ Delay$)** | Zaprogramuj Testo 174T na start za 2 godziny. Zczytaj ramkę USB za pomocą Wiresharka. | Zaprogramuj ten sam Testo 174T w Validation Desktop na ten sam czas startu. Zczytaj ramkę USB. | Różnica w bajtach odpowiedzialnych za `Start Delay` w payloadzie `ab 61` nie może być większa niż 1 minuta (wynikająca z różnicy czasu fizycznego kliknięcia). |

---

### Protokół B: Kwalifikacja Odczytu Pomiarów (Dane Surowe)

| ID Kroku | Opis Działania | System Referencyjny (Testo) | System Badany (Validation Desktop) | Kryteria Akceptacji |
| :--- | :--- | :--- | :--- | :--- |
| **QP-B-01** | **Spójność punktów pomiarowych (Wartości)** | Wyeksportuj serię pomiarową z sesji do pliku CSV przy użyciu Comfort Software (dla T174) lub odczytaj tabelę z PDF (dla T184). | Zaimportuj tę samą sesję pomiarową w Validation Desktop i wyeksportuj do pliku CSV. | Różnica wartości temperatury dla każdego pojedynczego punktu musi wynosić **dokładnie 0.0°C** (tolerancja błędu: 0). |
| **QP-B-02** | **Spójność stempli czasowych (Czas lokalny/DST)** | Pobierz czasy pomiaru z oficjalnego raportu Testo (uwzględniając zmianę czasu letni/zimowy). | Pobierz czasy tych samych pomiarów zaimportowanych w Validation Desktop. | Wszystkie stemple czasowe muszą zgadzać się **co do sekundy** ($T_{ref} = T_{bad}$), bez przesunięć strefowych (tolerancja błędu: 0s). |

---

### Protokół C: Kwalifikacja Obliczeń Statystycznych (Metrologia)

| ID Kroku | Opis Działania | System Referencyjny (Testo) | System Badany (Validation Desktop) | Kryteria Akceptacji |
| :--- | :--- | :--- | :--- | :--- |
| **QP-C-01** | **Weryfikacja wartości granicznych** | Odczytaj z raportu PDF wartości: Max Temp, Min Temp, Avg Temp. | Odczytaj wartości statystyk wyliczone automatycznie w Validation Desktop dla tej samej serii pomiarowej. | Wartości muszą być identyczne (dopuszczalne zaokrąglenie do 1 miejsca po przecinku, zgodne z raportem PDF). |
| **QP-C-02** | **Weryfikacja MKT (Średnia Temperatura Kinetyczna)** | Odczytaj wartość MKT wyliczoną w oryginalnym raporcie PDF Testo 184 (przy energii aktywacji $E_a = 83.144 \text{ kJ/mol}$). | Sprawdź wartość MKT wyliczoną w Validation Desktop dla tej samej energii aktywacji. | Maksymalna dopuszczalna różnica w wyniku wyliczenia MKT wynosi **$\le 0.1^\circ\text{C}$** (tolerancja na drobne różnice w metodach zaokrągleń ułamków w procesorach). |

---

## 4. Raport z Odchyleń i Kwalifikacji (Formularz GxP)

W przypadku wykrycia jakichkolwiek różnic w odczytach (temperatury, czasy, statystyki) między systemem referencyjnym a badanym, fakt ten musi zostać udokumentowany jako **Odchylenie (Deviation)** z określeniem:
* Wpływu odchylenia na bezpieczeństwo produktu (Product Safety Impact).
* Wpływu na integralność danych (Data Integrity Impact).
* Działań korygujących i zapobiegawczych (CAPA).

---

## 5. Podpisy i Akceptacja

**Osoba przeprowadzająca testy:**
* Podpis: _________________________
* Data: ___________________________

**Weryfikacja Działu Zapewnienia Jakości (QA):**
* Decyzja walidacyjna: **ZATWIERDZONO / ODRZUCONO**
* Podpis: _________________________
* Data: ___________________________
