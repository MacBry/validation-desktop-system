# Specyfikacja Techniczna: Protokół USB Rejestratora Testo 176 T4
## Warstwa transportowa, ramkowanie, mapa komend i struktura konfiguracji

> **Status:** warstwa programowania rozszyfrowana i zweryfikowana różnicowo.
> Warstwa odczytu danych pomiarowych — **nierozszyfrowana**, wymaga przechwycenia
> odczytu zakończonej misji.
>
> **Podstawa:** sondowanie urządzenia S/N 40736122 oraz dwa przechwyty USBPcap
> sesji programowania z Testo Comfort Software Basic 6.0.40 (2026-09-03),
> skonfrontowane z logiem DDK producenta (`%APPDATA%\Testo\tcddk_log.txt`).

---

## 1. Warstwa fizyczna i transport

Testo 176 T4 **nie używa** transportu żadnego z obsługiwanych dotąd modeli:

| model | transport | biblioteka |
|---|---|---|
| testo 174 T | kołyska FTDI D2XX | `ftd2xx64.dll` |
| testo 184 T3 | pamięć masowa (PDF + XML) | — |
| **testo 176 T4** | **wirtualny port COM (CDC)** | **pyserial** |

```
VID_128D  PID_0019        Testo AG
sterownik: testousbser    (oem104.inf, "testo 175-176-2010")
CompatibleID: USB\Class_02&SubClass_02&Prot_01   (CDC-ACM)
```

**Prędkość transmisji jest nieistotna.** Urządzenie odpowiada identycznie od 2400
do 115200 bodów, bo pod spodem jest rura USB, a nie prawdziwy UART. ComSoft
deklaruje `57600,N,8,1`, ale to tylko wartość przekazywana sterownikowi.

ComSoft łączy się nie przez port COM, lecz przez ścieżkę urządzenia USB
(`\\?\usb#vid_128d&pid_0019#<S/N>#{a5dcbf10-6530-11d2-901f-00c04fb951ed}`).
**Konsekwencja:** sniffer portu szeregowego nie zobaczy ruchu ComSoftu —
do przechwycenia konieczny jest USBPcap.

**ComSoft trzyma port na wyłączność.** Dopóki działa, żaden inny proces nie
otworzy COM-a („Odmowa dostępu"). Zawsze zamykać przed odczytem.

---

## 2. Ramkowanie

```
Żądanie:    12 <cmd> <8 B parametrów> [<len uint16 LE> <dane>]
Odpowiedź:  21 <cmd> <8 B echa parametrów> <len uint16 LE> <payload[len]>
```

Odpowiedź ma **zawsze co najmniej 12 bajtów**. Pole długości na pozycji `[10:12]`
jest kluczowe: przy nierozpoznanej komendzie wynosi zero, przez co ramka wygląda
jak echo żądania. To mylące — nie jest to echo, tylko poprawna odpowiedź o pustym
ładunku.

Ładunki bloków konfiguracyjnych kończą się **2-bajtowym CRC**.

### Kody błędów (w polu `cmd` odpowiedzi)

| kod | znaczenie |
|---|---|
| `f0` | nieznana komenda |
| `f1` | zarezerwowany (odpowiedź na `cmd` = `0x12`) |
| `f2` | komenda znana, **złe parametry lub niedozwolony stan** |
| `f4` | komenda znana, inny błąd stanu |

> `E_FAIL` (`HRESULT`) zgłaszany przez ComSoft przy zapisie konfiguracji to
> wyłącznie opakowanie `response f2` z urządzenia. **Najczęstsza przyczyna:
> wyczerpane baterie** — urządzenie odmawia programowania, gdy nie może
> zagwarantować zasilania na czas misji, mimo że przez USB odpowiada na odczyty.

---

## 3. Mapa komend

### 3.1 Odczyt

| cmd | parametry | payload | znaczenie |
|---|---|---|---|
| `0x23` | — | 7 B | **RTC**: rok uint16 LE, mies., dzień, godz., min., sek. — **UTC** |
| `0x24` | — | 1 B | **Mode** — patrz tabela niżej |
| `0x25` | — | 5 B | wersja firmware, ASCII (np. `"02.01"`) |
| `0x26` | — | 1 B | stała urządzenia (`08`) |
| `0x27` | — | 4 B | licznik / znacznik sesji |
| `0x31` | `00 <bank> <offset u16 LE> 00 00 <len u16 LE>` | zmienny | **odczyt bloku pamięci** |
| `0x41` | `00 00 <addr u16 LE> 00 00 <len>` | 512 B | odczyt obszaru stronicowanego |

### Tryby urządzenia (`0x24`) — pole bitowe

| bit | maska | znaczenie |
|---|---|---|
| 1 | `0x02` | urządzenie skonfigurowane / uzbrojone |
| 2 | `0x04` | **sesja zapisu otwarta** |

| wartość | stan |
|---|---|
| `0` | brak konfiguracji, sesja zamknięta |
| `2` | uzbrojony, sesja zamknięta |
| `4` | sesja zapisu otwarta |
| `6` | sesja otwarta, urządzenie skonfigurowane |

> **`0x11` przy już otwartej sesji zwraca `f2`.** Sesję otwiera `0x11`
> (tryb dostaje bit `0x04`), a zamyka `0x10`. ComSoft, zastając urządzenie
> z ustawionym tym bitem, **pomija `0x11`** i przechodzi wprost do `0x42` —
> potwierdzone przechwytem `cap1.pcapng`, gdzie urządzenie było w trybie 4.
>
> Most musi więc sprawdzić bit `0x04` przed otwarciem sesji, zamiast wysyłać
> `0x11` bezwarunkowo.

> **Uwaga:** tryb **nie** informuje o obecności danych w pamięci. Do tego służy
> licznik rekordów w bloku B2 (§6a) — i to jego, nie numeru trybu, należy używać
> jako zabezpieczenia przed skasowaniem cudzych pomiarów. Blok skasowany (same
> `0xff`) oznacza pustą pamięć, a nie 65535 rekordów.

**Mapa banków** (odczyt `0x31`, potwierdzona 2026-09-03):

| bank | rozmiar | zawartość |
|---|---|---|
| `00` | — | brak odpowiedzi |
| `01` | 128 B | **tożsamość i kalibracja urządzenia** (patrz §4.1) |
| `02` | 128 B | **konfiguracja misji** (patrz §4.2) |
| `03` | — | **pamięć pomiarów**, kasowana przed misją (`0xff` = pusta) |
| `04` | — | **pamięć pomiarów**, kasowana przed misją (`0xff` = pusta) |
| `05` | — | brak odpowiedzi |

Banki 1 i 2 **zawijają się co 128 bajtów** — odczyt spoza tego zakresu zwraca
powtórzoną zawartość, nie dane spod dalszych adresów.

| bank | offset | dł. | zawartość |
|---|---|---|---|
| `01` | `0x0c` | 20 | nazwa urządzenia, ASCII (`"testo 176-T4        "`) |
| `01` | `0x22` | 8 | numer seryjny, ASCII (`"40736122"`) |
| `02` | `0x02` | 68 | **blok konfiguracji B0** |
| `02` | `0x4c` | 26 | blok B1 (interwał, wielkości wyliczane) |
| `02` | `0x68` | 22 | blok B2 (stan pamięci pomiarów) |

### 3.3 Bank 1 — tożsamość i kalibracja

| offset | dł. | pole | zaobserwowano |
|---|---|---|---|
| `[0x02:0x0a]` | 8 | numer katalogowy, ASCII | `"05721764"` |
| `[0x0a:0x1e]` | 20 | **nazwa urządzenia**, ASCII, dopełniona spacjami | `"testo 176-T4"` |
| `[0x20:0x22]` | 2 | rok produkcji, uint16 LE | `e7 07` = 2023 |
| `[0x22:0x2a]` | 8 | numer seryjny, ASCII | `"40736122"` |
| `[0x2a:0x2f]` | 5 | wersja firmware, ASCII | `"02.01"` |
| `[0x2f:0x34]` | 5 | wersja sprzętowa, ASCII | `"003.2"` |
| `[0x50:0x60]` | 16 | **współczynniki kalibracyjne**, float32 LE ×4 | `0,99669` `0,99647` `−1,2600` `−1,2600` |
| `[0x60:0x6c]` | 12 | float32 LE ×3 | `0,03647` `1,0` `1,0` |
| `[0x7c:0x7e]` | 2 | CRC banku | — |

To jest źródło danych dla `get_CalInfo` i `get_Serial` z logu DDK. Wartości `1,0`
oznaczają kanał bez korekty kalibracyjnej — para współczynników to wzmocnienie
i offset.

### 3.2 Zapis

| cmd | parametry | dane | znaczenie |
|---|---|---|---|
| `0x11` | — | `00 00` | **otwarcie sesji zapisu** (ustawia bit `0x04` trybu, paruje się z `0x10`) |
| `0x42` | `…<flaga u16 LE>` | `00 00` | **transakcja metadanych**: flaga `0` otwiera, `1` zamyka |
| `0x32` | `00 <bank> 00 …` | `00 00` | kasowanie banku |
| `0x30` | `00 02 00 00 00 00 68 00` | 104 B | **zapis konfiguracji** |
| `0x17` | — | `07 00` + 7 B | **ustawienie zegara** (format jak `0x23`) |
| `0x10` | — | `00 00` | **zatwierdzenie** |
| `0x40` | `00 00 <addr u16 LE> 00 00 20 00` | `20 00` + 32 B | zapis obszaru stronicowanego |

---

## 4. Blok konfiguracji (104 B, zapisywany komendą `0x30`)

| offset | dł. | pole | weryfikacja |
|---|---|---|---|
| `[0:6]` | 6 | nagłówek `01 01 01 00 00 00` | stały |
| `[6:8]` | 2 | **liczba odczytów** (uint16 LE) | `28 00` = 40 → `0a 00` = 10 |
| `[10:14]` | 4 | **opóźnienie startu [sekundy]** (uint32 LE), liczone od czasu programowania | patrz niżej |
| `[14]` | 1 | **maska aktywnych kanałów** | `03` = 1+2 → `0f` = 1‥4 |
| `[15:22]` | 7 | **czas programowania**: sek., min., godz., dzień, mies., rok uint16 LE — **UTC** | `24 11 06 03 09 ea 07` = 2026-09-03 06:17:36 |
| `[22:38]` | 16 | **zakresy kanałów — 4 B na kanał** | patrz §5 |
| `[38:62]` | 24 | wartości domyślne kanałów nieaktywnych | — |
| `[62:64]` | 2 | `80 10` | stały |
| `[64:66]` | 2 | maski pomocnicze | `03 03` → `0f 0f` |
| `[66:68]` | 2 | **CRC bloku B0** | — |
| `[74:78]` | 4 | nagłówek B1 `01 01 01 01` | stały |
| `[78:80]` | 2 | **interwał [sekundy]** (uint16 LE) | `30 2a` = 10800 (3 h) → `10 0e` = 3600 (1 h) |
| `[82:84]` | 2 | wielkość wyliczana | `55 03` → `65 03` |
| `[84:86]` | 2 | `80 07` = 1920 | zgodne z `energycalc` z logu DDK |
| `[86:90]` | 4 | wielkość wyliczana (uint32 LE) | 138417 → 144409, zgodne z `energycalc` |
| `[98:100]` | 2 | **CRC bloku B1** | — |

---

## 5. Kodowanie temperatur — **kluczowe**

Każdy kanał zajmuje 4 bajty: **dwa int16 LE, najpierw granica górna, potem dolna.**

$$\text{zapisane} = T_{°C} \times 10 - 20000$$

$$T_{°C} = \frac{\text{zapisane} + 20000}{10}$$

Weryfikacja na obu przechwytach:

| bajty | zapisane | wyliczone | zadane w ComSoft |
|---|---|---|---|
| `88 af` | −20600 | **−60,0 °C** | −60 ✓ |
| `c0 ae` | −20800 | **−80,0 °C** | −80 ✓ |
| `7c b1` | −20100 | **−10,0 °C** | −10 ✓ |
| `d8 aa` | −21800 | **−180,0 °C** | −180 ✓ |
| `80 c1` | −16000 | **+400,0 °C** | zakres termopary T |
| `10 aa` | −22000 | **−200,0 °C** | zakres termopary T |

> **Uwaga na pułapkę interpretacyjną.** Wartość `e0 b1` = −20000 **nie oznacza
> −20 °C** — to **zero tej skali**. Tak zapisywane są kanały nieaktywne.
> Naiwne dzielenie przez 1000 daje pozornie sensowne „−20,0 °C" i prowadzi
> do fałszywego odczytu. Dekoder musi stosować wzór z przesunięciem.

Przy zmianie typu termopary ComSoft **nadpisuje zadany zakres fizycznymi
granicami czujnika** (typ T: −200…+400 °C). Most programujący musi to
odwzorować albo jawnie udokumentować odstępstwo.

### 5.1 Opóźnienie startu

Pole `[10:14]` bloku B0 to **liczba sekund od chwili programowania do pierwszego
pomiaru** (uint32 LE) — ten sam model co `startDelayMinutes` w moście 174 T,
tyle że w sekundach. Weryfikacja na obu przechwytach:

| | czas programowania (UTC) | zadany start (UTC) | różnica | zapisane |
|---|---|---|---|---|
| A | 06:17:36 | 13:00:00 | 24144 s | 24144 ✓ |
| B | 06:25:32 | 10:00:00 | 12868 s | 12868 ✓ |

### 5.2 Suma kontrolna bloków

**Nie jest to CRC.** Żaden standardowy wariant CRC-16 nie pasuje. Algorytm to
dopełnienie jedynkowe sumy bajtów bloku, zapisane jako uint16 LE:

```python
def block_checksum(data: bytes) -> int:
    return (~sum(data)) & 0xFFFF
```

Suma liczona jest po wszystkich bajtach bloku **z wyłączeniem samego pola sumy**
(B0: bajty 0‥65, B1: bajty 0‥23). Wyznaczona obliczeniowo i potwierdzona na
czterech niezależnych parach (blok, suma) z obu przechwytów:

| blok | suma bajtów | dopełnienie | w przechwycie |
|---|---|---|---|
| B0 / A | `0x1b5a` | `0xe4a5` | `a5 e4` ✓ |
| B0 / B | `0x19a6` | `0xe659` | `59 e6` ✓ |
| B1 / A | `0x020c` | `0xfdf3` | `f3 fd` ✓ |
| B1 / B | `0x0160` | `0xfe9f` | `9f fe` ✓ |

---

## 6. Sekwencja programowania

```
0x11  param = 00 …                      + 00 00      OTWARCIE SESJI ZAPISU  (wymagane!)
0x42  param = 00 …                      + 00 00      rozpoczecie transakcji (wymagane!)
0x32  param = 00 04 00 00 00 00 00 00   + 00 00      KASOWANIE banku 4 (pamiec pomiarow)
0x32  param = 00 03 00 00 00 00 00 00   + 00 00      KASOWANIE banku 3 (pamiec pomiarow)
0x32  param = 00 02 00 00 00 00 00 00   + 00 00      KASOWANIE banku 2 (konfiguracja)
0x30  param = 00 02 00 00 00 00 68 00   + 104 B      zapis konfiguracji
0x17  param = 00 …                      + 07 00 + 7 B   ustawienie zegara (UTC)
0x10  param = 00 …                      + 00 00      zatwierdzenie
```

> **`0x11` i `0x42` są obowiązkowe.** Bez otwarcia sesji zapisu urządzenie
> odrzuca kasowanie banku kodem `f2` — potwierdzone empirycznie podczas
> pierwszego uruchomienia mostu (2026-09-03). Kod `f2` nie oznacza więc wyłącznie
> problemu z zasilaniem, ale również **niedozwolony stan sesji**.

> **`0x32` kasuje, nie „przygotowuje".** Banki 3 i 4 to pamięć pomiarów —
> po zaprogramowaniu odczytują się jako `0xff`. **Programowanie bezpowrotnie
> niszczy dane poprzedniej misji.** Most musi wymusić potwierdzenie operatora
> i sprawdzić `0x24` (Mode): wartość `6` oznacza zakończoną misję z danymi,
> których nikt jeszcze nie pobrał.

Urządzenie odsyła na `0x30` zapisany blok (104 B), co daje **darmową weryfikację
round-trip** — należy ją wykonać, analogicznie do mostu 184 T3.

Przed sekwencją zapisu ComSoft kasuje obszar stronicowany: **128 wywołań `0x40`**
po 32 bajty, adresy co `0x20`, łącznie 4 KB.

### Obszar stronicowany (`0x40` / `0x41`)

Obszar ma **4096 B** i jest zapisywany porcjami po 32 B (128 wywołań `0x40`).
Odczyt: `0x41` z parametrem `00 00 <addr u16 LE> 00 00 <długość u16 LE>` — pole
długości jest **w bajtach**, tak samo jak w `0x31`, a nie w stronach.
Odpowiedź bywa dzielona na pakiety po 512 B.

| offset | zawartość |
|---|---|
| 640 (`0x280`) | **nazwa kanału 1**, 32 B UTF-16LE |
| 768 (`0x300`) | **nazwa kanału 2**, 32 B UTF-16LE (kolejne kanały co `0x80`) |
| 1280 (`0x500`) | `Programtime`, uint32 LE — **epoka 1980-01-01** |
| 1284 (`0x504`) | nazwa operatora, UTF-16LE |

Pole nazwy mieści **dwa łańcuchy UTF-16LE rozdzielone znakiem zerowym**.
**ComSoft wyświetla w nagłówku raportu pierwszy z nich.** Zweryfikowane
empirycznie 2026-09-03: zapis `"1\0LWT/2014"` — czyli wierne odwzorowanie
układu, jaki zastaliśmy na urządzeniu — dał w eksporcie CSV kolumnę `1[°C]`,
a nie `LWT/2014[°C]`.

Wniosek praktyczny: **nazwa, która ma się pojawić w raporcie, musi być na
pierwszej pozycji.** Znaczenie drugiego łańcucha pozostaje nieustalone.

Potwierdzone przeciwtestem 2026-09-03: po przeniesieniu nazwy na pierwszą
pozycję eksport CSV z ComSoftu dał nagłówki `LWT/2014[°C]` i `LWT/2017[°C]`,
podczas gdy poprzedni układ dawał `1[°C]` i `2[°C]`.

> Każdy kanał musi dostać niepustą nazwę. Puste pole ComSoft renderuje jako
> 32 znaki `?` w nagłówku kolumny.

> **Pominięcie tego obszaru daje znaki zapytania w raportach ComSoftu.**
> Potwierdzone empirycznie: most, który zapisywał wyłącznie blok konfiguracji,
> wyprodukował poprawne dane pomiarowe, ale nagłówki kolumn w eksporcie CSV
> zawierały 32 znaki `?` w miejscu nazwy kanału. Dane pomiarowe pozostały
> nienaruszone — to wyłącznie metadane opisowe.

> **Epoka `Programtime` to 1980-01-01T00:00:00Z**, czyli
> `Programtime = unix − 315 532 800`. Wyznaczona z dwóch przechwytów:
> wartość 1472883929 odpowiada 2026-09-03 06:25:32 UTC (błąd +3 s wynika
> z tego, że ComSoft pobiera czas chwilę przed zapisem bloku konfiguracji).

> Nazwy kanałów wyglądają na numery świadectw wzorcowania. Jeśli tak jest
> w praktyce laboratorium, moduł wielokanałowy powinien je czytać i konfrontować
> z `Calibration.channelNumber` — to gotowy mechanizm kontroli spójności.

> **`Programtime` nie jest czasem uniksowym.** Wartość 1472883929 odpowiada
> rzeczywistemu 2026-09-03 06:25 UTC, co w epoce uniksowej dałoby rok 2016.
> Różnica dwóch przechwytów (477 s) zgadza się z odstępem programowań co do
> sekundy, więc jednostką jest sekunda, ale punkt zerowy trzeba wyznaczyć
> eksperymentalnie, zanim most zacznie liczyć oś czasu.

---

## 6a. Odczyt danych pomiarowych

Ustalone na przechwycie odczytu misji 10 rekordów × 2 kanały (2026-09-03 09:46)
i skonfrontowane z eksportem CSV z ComSoftu.

### Blok B2 po zakończonej misji (`bank 02, offset 0x68`, 22 B)

```
dc 05 00 00 | 00 00 | ff ff ff ff | 0a 00 | 00 00 | 00 20 | 00 00 | 28 00 | d0 fa
```

| offset | pole | wartość |
|---|---|---|
| `[0:4]` | opóźnienie startu [s] (kopia z B0) | 1500 |
| `[10:12]` | **liczba zapisanych rekordów** (`dwNumRecordsTaken`) | 10 |
| `[14:16]` | `dwFlashAdr` | 8192 |
| `[18:20]` | **liczba bajtów danych** | 40 |
| `[20:22]` | suma kontrolna (jak w §5.2) | ✓ |

### Lokalizacja i format próbek

Pomiary odczytuje się komendą `0x31` z **banku 4** (`00 04 <offset> 00 00 <len>`).
Próbki to **int16 LE w tym samym kodowaniu co zakresy** (§5), z kanałami
**przeplatanymi**: `kan1, kan2, kan1, kan2, …`. Znacznik pustego slotu to
`05 80` (odpowiednik `−32752` w protokole 174 T).

Weryfikacja wobec eksportu CSV — zgodność **wszystkich 20 wartości**:

| bajty | int16 LE | °C | CSV |
|---|---|---|---|
| `cb b2` | −19765 | 23,5 | w. 1, kan. 1 ✓ |
| `ca b2` | −19766 | 23,4 | w. 1, kan. 2 ✓ |
| `c9 b2` | −19767 | 23,3 | w. 2, kan. 1 ✓ |
| … | | | |
| `c6 b2` | −19770 | 23,0 | w. 10, kan. 1 ✓ |

Liczbę rekordów bierze się z B2 `[10:12]`, a oś czasu odtwarza z czasu
programowania (B0 `[15:22]`), opóźnienia startu (B0 `[10:14]`) i interwału
(B1 `[4:6]`) — tak samo jak w moście 174 T.

Bank 3 przy krótkiej misji jest pusty (`05 80`); pełni rolę drugiego obszaru
przy dłuższych seriach. Odczyt `0x41` spod adresu `dwFlashAdr` zwraca dane
w innym kodowaniu — **niezbadane**, prawdopodobnie archiwum długich misji.

### Pomiar bieżący

`0x22` z parametrem `11` (kanał 1) lub `12` (kanał 2) zwraca **float32 LE
w °C** — np. `5d 64 bd 41` = 23,67 °C. Przydatne do podglądu na żywo
bez czekania na misję.

---

## 7. Czego jeszcze nie wiemy

1. **Kod typu termopary — prawdopodobnie nie istnieje na łączu.** Log DDK podaje
   mapowanie `K = 11`, `T = 12`, `J = 8`, `wyłączony = 1`, ale **żadna z tych
   wartości nie występuje w żadnym banku ani w obszarze `0x40`** — sprawdzono
   banki 0‥5 przy konfiguracji, w której kanał 3 miał typ T, a pozostałe K.

   Kontekst w logu (`try match :(K) Termoelement NiCr-Ni (176): / match … 11`)
   wskazuje, że są to **indeksy listy rozwijanej w ComSofcie**, a nie wartości
   sprzętowe. Roboczy wniosek: do urządzenia trafia wyłącznie **zakres pomiarowy
   kanału**, a typ czujnika jest atrybutem po stronie oprogramowania i most musi
   go trzymać we własnej kartotece rejestratora.

   **Test rozstrzygający:** zaprogramować wszystkie 4 kanały *tym samym zakresem*,
   ale różnymi typami termopar. Jeśli zapisane bajty będą identyczne dla wszystkich
   kanałów — hipoteza potwierdzona.
2. ~~Zrzut pamięci pomiarów~~ — **rozwiązane**, patrz §6a.
3. ~~Format próbki wielokanałowej~~ — **rozwiązane**: kanały przeplatane, §6a.
4. **Komenda `0x2b`** (42 B) — przeżywa przeprogramowanie i wyzerowanie
   konfiguracji. Kandydat: `get_CalInfo` z logu DDK, ale bez dowodu.
5. **Pola `energycalc`** w bloku B1: `[8:10]` (853 → 869) i `[12:16]`
   (138417 → 144409). Log DDK pokazuje je jako `energycalc 1920 138417`,
   czyli wynik obliczeń ComSoftu, najpewniej szacunek zużycia baterii i pamięci.
   Wzoru nie znamy. Most przepisuje je z szablonu bez przeliczenia — należy
   ustalić, czy wpływają wyłącznie na prezentację w oprogramowaniu producenta,
   czy również na zachowanie urządzenia.

---

## 7a. Implementacja

| plik | rola |
|---|---|
| `resources/testo/testo_176_programmer.py` | most programujący (pyserial, wyjście JSON) |
| `resources/testo/testo_176_selftest.py` | test regresyjny kodera na danych z przechwytów |
| `java/…/service/Testo176ProgrammingService.java` | warstwa Spring, przez `PythonBridgeRunner` |

Most stosuje **programowanie szablonowe**: odczytuje bieżący blok konfiguracji
z urządzenia i modyfikuje wyłącznie pola o potwierdzonym znaczeniu, pozostawiając
bajty o nieustalonej semantyce bez zmian. Ten sam wzorzec stosuje most 174 T dla
bloków metadanych (`ab33` → `ab63`).

**Dowód poprawności kodera.** `testo_176_selftest.py` bierze blok zapisany
w przechwycie A jako szablon, nanosi parametry przechwytu B i porównuje wynik
z blokiem, który ComSoft faktycznie wysłał. Blok B0 wychodzi **identyczny co do
bajtu**, włącznie z sumą kontrolną. Rozbieżne pozostają wyłącznie pola
`energycalc` w B1 (patrz §7 pkt 5). Test kończy się kodem 0 przy zgodności.

---

## 8. Uwagi metodyczne

**Log DDK producenta** (`%APPDATA%\Testo\tcddk_log.txt`) jest bezpłatnym źródłem
semantyki: podaje nazwy funkcji (`get_FlashB0/B1/B2`, `get_Serial`, `get_CalInfo`,
`get_Mode`, `get_StringVerFw`), nazwy i wartości pól struktur
(`m_Progb0.byChannelMask`, `byTempUnit`, `dwFlashAdr`, `dwNumRecordsTaken`,
`energycalc`) oraz kody odpowiedzi. **Nie zrzuca surowych bajtów** — do adresów
i opcode'ów konieczny jest USBPcap.

**Metoda różnicowa jest jedyną właściwą** do mapowania pól. Dwa przechwyty
programowania różniące się kilkoma parametrami naraz izolują każde pole
jednoznacznie i bez zgadywania. Tak ustalono wszystkie pozycje w §4 i wzór w §5.

> **Ostrzeżenie.** Nie wolno mapować protokołu przez ślepe skanowanie opcode'ów.
> Rodzina `0x30`–`0x42` zawiera komendy zapisu i kasowania; wysłanie ich
> z przypadkowymi parametrami może rozspójnić konfigurację urządzenia
> metrologicznego. Zakres o nieustalonej semantyce zostawiać do czasu, aż
> potwierdzi go przechwycenie ruchu oprogramowania producenta.

---

## 9. Materiał referencyjny

`C:\Users\pcs\Desktop\TESTO` zawiera 1788 plików `.vi2` (kontener OLE2 Compound
File), z czego **74 dotyczą rejestratorów 176** (S/N 40736122 i 40706916).
To gotowy zbiór do kwalifikacji porównawczej dekodera zgodnie z
`TESTO_COMPARATIVE_QUALIFICATION_PLAN.md`.
