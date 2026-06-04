# Specyfikacja Techniczna: Protokół USB Rejestratora Testo 174T
## Niskopoziomowy Sterownik FTDI, Specyfikacja Wykresów GUI & Generator Raportów GxP PDF

Niniejszy dokument opisuje szczegóły techniczne transmisji szeregowej USB, strukturę ramek bajtowych rejestratora Testo 174T oraz pełną architekturę i implementację interaktywnego panelu wykresu oraz generatora raportów walidacyjnych GxP (JavaFX / OpenPDF).

---

## 1. Warstwa Fizyczna i Parametry Transmisji

Komunikacja z kołyską USB odbywa się za pośrednictwem układu scalonego FTDI wbudowanego w kołyskę. Oficjalne oprogramowanie Testo dostarcza sterownik FTDI D2XX (`ftd2xx64.dll`).

*   **Prędkość transmisji (Baud Rate):** `57600` bps
*   **Bity danych (Data Bits):** `8`
*   **Bity stopu (Stop Bits):** `1`
*   **Parzystość (Parity):** Brak (`None`)
*   **Czas oczekiwania (Timeouts):** `2000` ms (odczyt i zapis)
*   **Biblioteka sterownika:** `ftd2xx64.dll` (ładowana dynamicznie)

---

## 2. Protokół Komunikacyjny (Byteframes & Handshake)

Komunikacja z rejestratorem odbywa się w trybie zapytanie-odpowiedź. Każdy blok konfiguracyjny lub żądanie odczytu jest poprzedzone sygnałem resetu `\xf0` wysyłanym z prędkością 57600 baud.

### 2.1. Inicjalizacja i Sygnał Powitalny (HELLO)
Wysłanie bajtów inicjalizujących w celu wybudzenia rejestratora w kołysce:
*   **Komenda (TX):** `ab 01 0d 00 00 02`
*   **Odpowiedź (RX):** Potwierdzenie gotowości (bity statusowe oraz echo).

### 2.2. Pobranie Informacji o Urządzeniu (GET_DEVICE_INFO)
*   **Komenda (TX):** `ab 30 00 02 0b 37`
*   **Odpowiedź (RX):** Blok bajtów o długości payloadu zdefiniowanej w nagłówku.
    *   Bajt 4: Długość danych (`length`).
    *   Bajty 5-6 (Payload 0-1): Rok produkcji (np. `07 ea` = 2026).
    *   Bajt 7 (Payload 2): Miesiąc produkcji.
    *   Bajt 8 (Payload 3): Dzień produkcji.
    *   Bajty 9-12 (Payload 4-7): **Numer seryjny (S/N)** jako `uint32 BE` (Big-Endian).

### 2.3. Pobranie Statusu Sesji i Czasu (GET_STATUS)
*   **Komenda (TX):** `ab 31 00 42 1b 66`
*   **Odpowiedź (RX):** Zawiera kluczowe metadane pomiarowe.
    *   Bajty 2-3 (Payload 2-3): **Interwał zapisu** (w minutach, `uint16 BE`).
    *   Bajty 4-5 (Payload 4-5): **Liczba zapisanych próbek** (`count`, `uint16 BE`).
    *   Bajty 6-8 (Payload 6-8): **Opóźnienie startu** (Start Delay w minutach, `3B uint BE`).
    *   Bajty 9-14 (Payload 9-14): **Czas Programowania** (Programming Time BCD - 6 bajtów: Rok, Miesiąc, Dzień, Godzina, Minuta, Sekunda).
    *   Bajt 20 (Payload 20): **Stan baterii** (%).

### 2.4. Żądanie Zrzutu Pamięci Pomiarów (START_DUMP)
*   **Komenda (TX):** `ab 01 09 00 00 06`
*   **Odpowiedź (RX):** Rejestrator przechodzi w tryb strumieniowania. Wysyła serię ramek `ab 32` zawierających bloki temperatur po 32 bajty payloadu na ramkę (16 pomiarów w ramce).

---

## 3. Matematyczny Algorytm Dekodowania Danych

### 3.1. Wyznaczanie Czasu Pierwszego Pomiaru (Start Timestamp)
Czas pierwszego pomiaru ($T_{\text{start}}$) w strefie UTC obliczany jest ze wzoru:

$$T_{\text{start\_UTC}} = T_{\text{programowania\_UTC}} + \Delta t_{\text{opóźnienia}}$$

Gdzie:
*   $T_{\text{programowania\_UTC}}$ to czas pobrany z bajtów BCD ramki `ab 31`.
*   $\Delta t_{\text{opóźnienia}}$ to wartość minutowa pobrana z 3-bajtowego pola `Start Delay` tej samej ramki.

#### Konwersja na czas lokalny w Polsce (CET/CEST):
Timestamp UTC musi zostać przekonwertowany na polski czas lokalny z uwzględnieniem czasu letniego (DST):
*   **Czas letni (CEST = UTC+2):** Od ostatniej niedzieli marca (godz. 01:00 UTC) do ostatniej niedzieli października (godz. 01:00 UTC).
*   **Czas zimowy (CET = UTC+1):** Poza tym okresem.

### 3.2. Dekodowanie Wartości Temperatury
*   Każda próbka to dokładnie 2 bajty w pamięci flash.
*   Zapisana jest jako **16-bitowa liczba całkowita ze znakiem (Big-Endian)**.
*   **Wzór fizyczny:**
    $$Temp(^\circ\text{C}) = \frac{\text{Wartość\_Sygnału\_int16\_BE}}{10.0}$$
*   **Suma kontrolna (Checksum):** Ostatni bajt w bloku danych `ab 32` to suma bajtów całej ramki modulo 256.
*   **Znacznik końca danych:** Wartość `0x8010` (dziesiętnie `-32752`) oznacza brak kolejnych próbek.

---

## 4. Architektura Wykresu UI: Oś Liczbowa i Pełna Interaktywność

W celu wyeliminowania zlewania się i nakładania podpisów czasu na osi X, wdrożono zaawansowany silnik osi czasowej oparty na indeksach numerycznych i dynamicznym mapowaniu.

### 4.1. Time-Series NumberAxis Engine
Tradycyjna oś kategoryczna (`CategoryAxis`) została zastąpiona osią liczbową (`NumberAxis`). 
*   **Generowanie serii:** Dane wejściowe do wykresu są mapowane jako `XYChart.Data<Number, Number>`, gdzie współrzędna X to indeks numeryczny pomiaru (`1, 2, ..., N`), a Y to wartość temperatury.
*   **Dynamiczny Format X-Axis:** Aby pod osią wyświetlić czytelne czasy, zaimplementowano niestandardowy konwerter tekstowy `StringConverter<Number>`:
    ```java
    xAxisTime.setTickLabelFormatter(new StringConverter<Number>() {
        @Override
        public String toString(Number object) {
            int idx = object.intValue();
            if (idx >= 1 && idx <= pointsList.size()) {
                return pointsList.get(idx - 1).getTimestampLocal().format(DateTimeFormatter.ofPattern("HH:mm"));
            }
            return "";
        }
        @Override
        public Number fromString(String string) { return 0; }
    });
    ```
Dzięki temu `NumberAxis` matematycznie dopasowuje odległości ticków w poziomie, wyświetlając podpisy w sposób czytelny, nie nakładając ich na siebie nawet przy 200 punktach pomiarowych.

### 4.2. Węzły Pomiarowe z Obsługą Zdarzeń (Hover & Rich Tooltips)
Wątek JavaFX renderuje każdy punkt pomiarowy jako graficzny symbol (`Node`). Zastosowaliśmy nasłuchiwanie właściwości `nodeProperty()` każdego punktu serii, aby po jego inicjalizacji dołączyć interaktywne zdarzenia:
*   **Błyskawiczne Tooltipy GxP:** Wykorzystując klasę `Tooltip`, wstrzykujemy stylizowany dymek informacyjny (ciemne tło `rgba(30, 41, 59, 0.9)`, biała pogrubiona czcionka, zaokrąglenie krawędzi `6px` oraz trójwymiarowy cień `dropshadow`) o minimalnym opóźnieniu wyświetlania (`50ms`). Pokazuje on Indeks, dokładną datę oraz temperaturę.
*   **Mikro-animacja Zoom na Hover:** Po najechaniu myszką (`setOnMouseEntered`) symbol punktu ulega **powiększeniu do 1.8x** i zmienia kolor tła na systemowy akcent, a kursor przybiera kształt `Cursor.HAND`. Po opuszczeniu obszaru (`setOnMouseExited`) właściwości są przywracane.

---

## 5. Generator Raportów GxP PDF (OpenPDF / iText)

Usługa **[TestoPdfReportService](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/TestoPdfReportService.java)** tworzy profesjonalny, wielostronicowy dokument walidacyjny.

### 5.1. Wirtualny Silnik Off-Screen Wykresu
Aby zapobiec spłaszczaniu wykresu w PDF (które występuje przy skalowaniu szerokich wykresów z ekranów panoramicznych), zaimplementowano off-screenowy potok renderowania:
1. System tworzy **w pamięci podręcznej osobną instancję `LineChart<Number, Number>`** o stałych, idealnych dla druku wymiarach: **`760x420`**.
2. Wykres w pamięci jest podpinany pod wirtualną scenę (`new Scene(offscreenChart)`), co wymusza pełną inicjalizację silnika CSS Modena (kolory, linie siatki, symbole).
3. Wykonywany jest ostry snapshot matrycowy (`WritableImage`), który przez `SwingFXUtils.fromFXImage` jest konwertowany do standardu AWT `BufferedImage` i zapisywany jako plik tymczasowy.
4. Biblioteka PDF osadza obrazek na pierwszej stronie, gwarantując idealne proporcje i wysoką ostrość druku.

### 5.2. Suma Kontrolna SHA-256 (Nienaruszalność Danych)
Spełniając wymogi FDA 21 CFR Part 11, silnik wylicza kryptograficzny skrót serii pomiarowej (indeksy, czasy i temperatury):
*   Skrót generowany jest przy użyciu algorytmu SHA-256 (`MessageDigest.getInstance("SHA-256")`).
*   **Premium Formatting:** Aby zachować nienaganną estetykę, komórka sumy kontrolnej została zrealizowana jako obiekt `Paragraph` o ustalonej interlinii **`14.0f`** (`setLeading(14.0f)`), oddzielając pogrubioną etykietę od hasha. Sam hash pisany jest ciemnogranatową czcionką o rozmiarze **`8`** o normalnej grubości wewnątrz komórki z marginesem wewnętrznym (`padding`) ustawionym na **`10`**.
*   **Odstęp przed ramką:** Do `hashTable` dodano jawne, silne wymuszenie odstępu od góry: `hashTable.setSpacingBefore(15.0f);`, co zapobiega stykaniu się z poprzednią tabelą metryki.

### 5.3. Prawidłowa Numeracja Stron i Nagłówki (Multi-Page Flow)
*   **Repeating Headers:** Tabela pomiarowa na kolejnych stronach automatycznie powtarza nagłówek kolumn dzięki wywołaniu:
    ```java
    dataTable.setHeaderRows(1);
    ```
*   **Dwufazowy `PdfTemplate` (Strona X z Y):** Klasa **[PdfHeaderFooterHandler](file:///c:/Users/macie/Desktop/VCC%20Desktop%20APP/validation-desktop/src/main/java/com/mac/bry/desktop/service/PdfHeaderFooterHandler.java)** tworzy dynamiczną stopkę. Podczas zamykania dokumentu w `onCloseDocument` wyliczana jest ostateczna liczba stron.
*   **Korekta matematyczna:** Ponieważ w momencie zamykania dokumentu iText ma już zainicjalizowany licznik dla następnej (nieistniejącej) strony (`Total+1`), całkowita liczba stron jest wyznaczana jako:
    ```java
    totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
    ```
Dzięki temu dokument o rozmiarze 2 stron zawsze wyświetli poprawną stopkę: *"Strona 1 z 2"*, *"Strona 2 z 2"* bez ryzyka błędnego przeskoku do 3.
