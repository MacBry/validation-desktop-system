# Analiza Implementacyjna (Technical Spec): Integracja Rejestratorów Testo 184T

Dokument opisuje architekturę techniczną, algorytmy kodowania/dekodowania oraz specyfikację zmian w kodzie Java mających na celu integrację rejestratorów **Testo 184T** (np. T3) za pośrednictwem mostu Pythonowego (Python Bridge).

---

## 1. Architektura Integracji (Python Bridge)
Integracja zostanie zrealizowana w oparciu o istniejący wzorzec mostu Pythonowego, używany obecnie dla rejestratorów Testo 174T. Zamiast pisać niskopoziomowe parsowanie plików PDF i kodowanie XML w języku Java, wykorzystamy dedykowane skrypty Python umieszczone w zasobach Javy (`src/main/resources/testo/`), które będą uruchamiane jako osobne procesy systemowe.

```text
+----------------------------+
|     Validation Desktop     |
|      (JavaFX Application)  |
+-------------+--------------+
              | (Uruchomienie procesu przez ProcessBuilder)
              v
+-------------+--------------+
|     Python Bridge          |
| (testo_184_programmer.py / |
|  testo_184_pdf_reader.py)  |
+-------------+--------------+
              | (Interakcja ze sprzętem / plikami)
              v
+-------------+--------------+
|     Rejestrator USB        |
|  (XML Config / PDF Report) |
+----------------------------+
```

---

## 2. Programowanie Rejestratora (Zapis Konfiguracji)
Konfiguracja logera Testo 184T odbywa się poprzez zapis pliku XML o nazwie `testo 184 configuration_data.xml` w katalogu głównym pamięci rejestratora. 

### 2.1. Payload XML i pole `<alldata>`
Kluczowe nastawy mikrokontrolera rejestratora znajdują się wewnątrz znacznika `<alldata>`. Wartości wewnątrz tego pola są rozdzielane ciągiem `%%` i kodowane jako znaki szesnastkowe ASCII (Hex ASCII).

**Główne zasady kodowania:**
1. **String do Hex:** Każda litera ciągu tekstowego zamieniana jest na jej kod ASCII w formacie szesnastkowym (np. spacja `' '` $\rightarrow$ `20`, cyfra `'3'` $\rightarrow$ `33`).
2. **Kompensacja temperatur (Offset +200.0):** Wartości temperatur przed konwersją do Hex są powiększane o **200.0** (np. `25.5°C` $\rightarrow$ `225.5` $\rightarrow$ Hex ASCII: `3232352e35`). Zapobiega to przesyłaniu wartości ujemnych. Jeśli alarm jest wyłączony, przesyłany jest ciąg `"NaN"`.
3. **Konkatenacja pól flagowych:** Stop-checki (zatrzymanie pomiaru) oraz flagi wyświetlacza LCD/LED są łączone bez separatora (np. `stopCheck1` + `stopCheck2` + `stopCheck3` $\rightarrow$ `001`).

Wygenerowany payload XML jest zapisywany według szablonu XDP (XFA dataset) i zapisywany na dysku rejestratora.

---

## 3. Odczyt Danych Pomiarowych (Dekodowanie PDF)
Po zakończeniu rejestracji urządzenie generuje w swojej pamięci masowej raport PDF. Surowe dane pomiarowe są zaszyte w prywatnym obiekcie binarnym PDF.

### 3.1. Lokalizacja Strumienia Prywatnego w PDF
Dekoder przeszukuje strukturę pliku PDF w poszukiwaniu referencji `/PieceInfo` powiązanej ze słownikiem katalogu dokumentu:

```pdf
/PieceInfo <<
  /Testo-Logger <<
    /Private <<
      /Data 13 0 R
    >>
  >>
>>
```
Obiekt oznaczony jako `/Data` (zazwyczaj **Object 13**) zawiera surowy strumień danych, skompresowany algorytmem Flate. Dekoder wyodrębnia dane między słowami kluczowymi `stream` i `endstream`, a następnie dekompresuje je za pomocą systemowej biblioteki `zlib` (`zlib.decompress`).

### 3.2. Struktura Bufora Pomiarowego (Długość: 2117 bajtów)
Rozpakowany strumień binarny ma stałą strukturę pamięci:
* **Offset 0 (7 bajtów):** Czas startu pomiaru w formacie BCD (rok, miesiąc, dzień, godzina, minuta, sekunda).
* **Offset 10 (4 bajty):** Liczba zapisanych pomiarów (uint32 Little-Endian).
* **Offset 554 (4 bajty):** Numer seryjny urządzenia.
* **Offset do 1476:** Tablica wartości pomiarowych (każdy pomiar zajmuje 4 bajty w formacie uint32 Little-Endian).

### 3.3. Algorytm Dekodowania Temperatur (Q16.16 Fixed Point)
Wartości pomiarowe zapisane są jako 32-bitowe liczby stałoprzecinkowe w formacie **Q16.16** ze znakiem, ze skalowaniem fabrycznym przez 100 oraz offsetem 100.

**Wzór dekodowania wartości stałoprzecinkowej:**
$$T(^\circ\text{C}) = \frac{V_{\text{raw}}}{6\,553\,600.0} - 100.0$$

*Mnożnik $6\,553\,600$ to wynik operacji $2^{16} \times 100$ (skalowanie formatu Q16 pomnożone przez fabryczny mnożnik Testo).*

---

## 4. Zmiany w Kodzie Javy (`validation-desktop`)

### 4.1. Klasy Serwisów Backendowych (Spring Boot)
1. **`Testo184ProgrammingService.java` [NEW]:**
   * Metoda `boolean programDevice(Testo184ConfigParams params, String driveLetter)`
   * Wywołuje skrypt Python za pomocą `ProcessBuilder`. Przekazuje parametry linii komend (`--drive`, `--interval`, `--start-mode`, `--start-time`, `--alarm-max`, `--alarm-min`, `--comment`, `--operator`).
   * Zwraca `true` jeśli skrypt zakończy się z kodem `0` i wypisze `[OK]`.
2. **`Testo184UsbImportService.java` [NEW]:**
   * Metoda `TestoImportResult importFromPdf(File pdfFile)`
   * Wywołuje skrypt Python z parametrem ścieżki do pliku PDF.
   * Odczytuje wyjściowy JSON i mapuje go na obiekt `TestoImportResult` przy użyciu klasy `ObjectMapper` (Jackson).

### 4.2. Warstwa Kontrolera GUI i Widoku (JavaFX)
1. **`TestoProgrammingDialogController.java` [MODIFY]:**
   * Dodanie komponentów UI: `ComboBox<String> deviceModelSelector` (wybór Testo 174T vs Testo 184T).
   * Dodanie `ComboBox<String> driveLetterSelector` (wybór litery dysku wymiennego).
   * Powiązanie widoczności selektora dysków z wyborem modelu "Testo 184T".
   * Implementacja metody skanującej dyski wymienne w Javie:
     ```java
     List<String> getRemovableDrives() {
         List<String> drives = new ArrayList<>();
         for (File root : File.listRoots()) {
             // Weryfikacja czy dysk jest wymienny i dostępny do zapisu
             if (root.canWrite()) {
                 drives.add(root.getAbsolutePath());
             }
         }
         return drives;
     }
     ```
   * Rozbudowa obsługi akcji przycisku "Programuj" o wywołanie `Testo184ProgrammingService` w przypadku wyboru modelu 184T.

2. **`TestoProgrammingDialog.fxml` [MODIFY]:**
   * Wzbogacenie layoutu FXML (przy użyciu biblioteki styli AtlantaFX) o kontrolki wyboru modelu rejestratora oraz dysku docelowego.
