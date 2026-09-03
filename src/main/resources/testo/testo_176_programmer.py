#!/usr/bin/env python3
"""
Most programujący rejestrator Testo 176 T4 przez wirtualny port COM.

Protokół opisany w docs/TESTO_176_USB_ANALYSIS.md. W skrócie:

    Żądanie:    12 <cmd> <8 B parametrów> [<len uint16 LE> <dane>]
    Odpowiedź:  21 <cmd> <8 B echa> <len uint16 LE> <payload>

Zasada działania: **programowanie szablonowe**. Skrypt najpierw odczytuje
bieżący blok konfiguracji z urządzenia i modyfikuje wyłącznie pola, których
znaczenie jest potwierdzone. Bajty o nieustalonej semantyce przechodzą bez
zmian. Ten sam wzorzec stosuje most 174 T dla bloków metadanych (ab33/ab63) —
nie fabrykujemy zawartości, której nie rozumiemy.

Wyjście: JSON na stdout (zgodnie z konwencją pozostałych mostów).
"""

import argparse
import json
import struct
import sys
import time
from datetime import datetime, timedelta, timezone

try:
    import serial
    from serial.tools import list_ports
except ImportError:
    print(json.dumps({
        "status": "ERROR",
        "message": "Brak biblioteki pyserial. Zainstaluj: pip install pyserial"
    }, ensure_ascii=False))
    sys.exit(0)


# --- Stałe protokołu -------------------------------------------------------

TESTO_VID = 0x128D
TESTO_PID = 0x0019

REQ_PREFIX = 0x12
RSP_PREFIX = 0x21

CMD_COMMIT = 0x10          # zatwierdzenie konfiguracji / zamkniecie sesji zapisu
CMD_BEGIN_SESSION = 0x11   # otwarcie sesji zapisu (paruje sie z 0x10)
CMD_SET_CLOCK = 0x17       # ustawienie zegara (UTC)
CMD_BEGIN_WRITE = 0x42     # rozpoczecie transakcji zapisu
CMD_GET_MODE = 0x24        # 0 = brak konfiguracji, 2 = uzbrojony, 6 = po misji
CMD_GET_FIRMWARE = 0x25
CMD_WRITE_CONFIG = 0x30    # zapis bloku konfiguracji
CMD_READ_BLOCK = 0x31      # odczyt bloku pamięci
CMD_ERASE_BANK = 0x32      # KASOWANIE banku
CMD_WRITE_PAGE = 0x40      # zapis obszaru stronicowanego (metadane opisowe)

ERROR_CODES = {
    0xF0: "nieznana komenda",
    0xF1: "błąd f1",
    0xF2: "komenda odrzucona — złe parametry albo niedozwolony stan urządzenia "
          "(najczęstsza przyczyna: wyczerpane baterie)",
    0xF4: "komenda odrzucona — błąd stanu urządzenia",
}

# Tryb jest polem bitowym: bit 1 (2) = skonfigurowany, bit 2 (4) = sesja otwarta.
MODE_NO_CONFIG = 0
MODE_ARMED = 2
MODE_SESSION_OPEN = 4      # sesja zapisu juz otwarta — 0x11 zwroci wtedy f2
MODE_SESSION_AND_ARMED = 6

MODE_NAMES = {
    0: "brak konfiguracji",
    2: "uzbrojony, czeka na start",
    4: "otwarta sesja zapisu",
    6: "otwarta sesja zapisu, urządzenie skonfigurowane",
}

SESSION_OPEN_BIT = 0x04

# Blok B2: stan pamieci pomiarow (bank 2, offset 0x68)
B2_BANK_OFFSET = 0x68
B2_LEN = 22
B2_RECORD_COUNT = 10       # uint16 LE — dwNumRecordsTaken
B2_DATA_BYTES = 18         # uint16 LE — liczba bajtow danych

BANK_IDENTITY = 0x01
BANK_CONFIG = 0x02
BANK_DATA_A = 0x03
BANK_DATA_B = 0x04

# Układ banku 2 (128 B, zawija się co 128 B)
CONFIG_PAYLOAD_LEN = 104   # bajty [0:104] banku 2 = ładunek komendy 0x30
B0_OFFSET = 2              # blok B0 w ładunku
B0_LEN = 68                # 66 B danych + 2 B sumy kontrolnej
B1_OFFSET = 76
B1_LEN = 26                # 24 B danych + 2 B sumy kontrolnej

# Pola w bloku B0 (offsety względem początku B0)
B0_COUNT = 6               # uint16 LE — liczba odczytów
B0_START_DELAY = 10        # uint32 LE — opóźnienie startu [s] od czasu programowania
B0_CHANNEL_MASK = 14       # uint8 — bitowa maska aktywnych kanałów
B0_PROG_TIME = 15          # sek., min., godz., dzień, mies., rok uint16 LE (UTC)
B0_RANGES = 22             # 4 kanały × (górna int16 LE, dolna int16 LE)
B0_MASK_COPY = 64          # dwie kopie maski kanałów
B0_CHECKSUM = 66

# Pola w bloku B1
B1_INTERVAL = 4            # uint16 LE — interwał [sekundy]
B1_CHECKSUM = 24

# Kodowanie temperatur: zapisane = T[°C] × 10 − 20000
TEMP_SCALE = 10
TEMP_OFFSET = 20000

MAX_CHANNELS = 4

# --- Obszar stronicowany (metadane opisowe, komendy 0x40 / 0x41) -----------
#
# ComSoft zapisuje tu 4 KB w porcjach po 32 B. Bez tego raporty producenta
# pokazują w miejscu nazwy urządzenia i kanałów znaki zapytania — pole zostaje
# puste, a oprogramowanie renderuje surowe bajty.
PAGE_AREA_SIZE = 4096
PAGE_CHUNK = 32
PAGE_CHANNEL_NAME_BASE = 0x280   # nazwa kanału 1
PAGE_CHANNEL_NAME_STRIDE = 0x80  # kolejne kanały co 128 B
PAGE_CHANNEL_NAME_LEN = 32       # 16 znaków UTF-16LE
PAGE_PROGRAMTIME = 0x500         # uint32 LE + nazwa operatora UTF-16LE

# Epoka pola Programtime: 1980-01-01T00:00:00Z.
# Wyznaczona z dwóch przechwytów — patrz docs/TESTO_176_USB_ANALYSIS.md.
PROGRAMTIME_EPOCH = 315532800


class TestoError(Exception):
    """Błąd komunikacji lub odrzucenie komendy przez urządzenie."""


# --- Kodowanie -------------------------------------------------------------

def encode_temperature(celsius: float) -> int:
    """Temperatura [°C] -> wartość zapisywana w bloku konfiguracji."""
    return int(round(celsius * TEMP_SCALE)) - TEMP_OFFSET


def decode_temperature(raw: int) -> float:
    """Wartość z bloku konfiguracji -> temperatura [°C]."""
    return (raw + TEMP_OFFSET) / TEMP_SCALE


def encode_channel_name(channel: int, name: str) -> bytes:
    """
    Pole nazwy kanału: 32 B UTF-16LE, czyli 15 znaków plus terminator.

    Pole mieści **dwa** łańcuchy rozdzielone znakiem zerowym. ComSoft wyświetla
    w nagłówku raportu **pierwszy** z nich — zweryfikowane empirycznie
    2026-09-03: zapis `"1\\0LWT/2014"` dał w eksporcie CSV kolumnę `1[°C]`,
    a nie `LWT/2014[°C]`.

    Dlatego nazwa użytkownika (docelowo numer świadectwa wzorcowania) idzie na
    pierwszą pozycję, a numer kanału służy tylko jako wartość zastępcza, gdy
    nazwy nie podano.
    """
    text = name.strip() if name and name.strip() else str(channel)
    raw = text.encode('utf-16-le')[:PAGE_CHANNEL_NAME_LEN - 2]
    return raw + b'\x00' * (PAGE_CHANNEL_NAME_LEN - len(raw))


def build_page_area(channel_names: list, operator: str,
                    prog_time_utc: datetime) -> bytes:
    """Składa 4 KB obszaru metadanych opisowych."""
    page = bytearray(PAGE_AREA_SIZE)

    # Wszystkie kanały dostają nazwę — bez niej ComSoft renderuje puste pole
    # jako ciąg znaków zapytania. Brak nazwy od użytkownika daje numer kanału.
    names = list(channel_names[:MAX_CHANNELS])
    names += [""] * (MAX_CHANNELS - len(names))
    for idx, name in enumerate(names):
        base = PAGE_CHANNEL_NAME_BASE + idx * PAGE_CHANNEL_NAME_STRIDE
        page[base:base + PAGE_CHANNEL_NAME_LEN] = encode_channel_name(idx + 1, name)

    programtime = int(prog_time_utc.timestamp()) - PROGRAMTIME_EPOCH
    struct.pack_into('<I', page, PAGE_PROGRAMTIME, programtime)
    op_raw = (operator or "").encode('utf-16-le')[:28]
    page[PAGE_PROGRAMTIME + 4:PAGE_PROGRAMTIME + 4 + len(op_raw)] = op_raw

    return bytes(page)


def block_checksum(data: bytes) -> int:
    """
    Suma kontrolna bloku konfiguracji: dopełnienie jedynkowe sumy bajtów.

    Wyznaczona obliczeniowo na czterech parach (blok, suma) z dwóch przechwytów
    USBPcap — patrz docs/TESTO_176_USB_ANALYSIS.md §4.
    """
    return (~sum(data)) & 0xFFFF


# --- Warstwa transportowa --------------------------------------------------

class Testo176:
    def __init__(self, port: str = None, timeout: float = 0.2):
        self.port_name = port or self._find_port()
        if not self.port_name:
            raise TestoError(
                "Nie znaleziono rejestratora Testo 176 (VID_128D/PID_0019). "
                "Sprawdź kabel USB.")
        try:
            self.ser = serial.Serial(self.port_name, 115200,
                                     timeout=timeout, write_timeout=2.0)
        except serial.SerialException as exc:
            raise TestoError(
                f"Nie można otworzyć portu {self.port_name}: {exc}. "
                f"Jeśli działa Testo ComfortSoftware, zamknij go — trzyma port "
                f"na wyłączność.") from exc

    @staticmethod
    def _find_port():
        for p in list_ports.comports():
            if (p.vid, p.pid) == (TESTO_VID, TESTO_PID):
                return p.device
        return None

    def close(self):
        try:
            self.ser.close()
        except Exception:
            pass

    def request(self, cmd: int, params: bytes = b'', data: bytes = None,
                settle: float = 0.3) -> bytes:
        """Wysyła ramkę i zwraca payload odpowiedzi. Rzuca TestoError przy błędzie."""
        frame = bytes([REQ_PREFIX, cmd]) + (params + b'\x00' * 8)[:8]
        if data is not None:
            frame += struct.pack('<H', len(data)) + data

        self.ser.reset_input_buffer()
        self.ser.write(frame)
        self.ser.flush()
        time.sleep(settle)

        buf = b''
        deadline = time.time() + 5.0
        while time.time() < deadline:
            chunk = self.ser.read(4096)
            if chunk:
                buf += chunk
                deadline = time.time() + 0.4
            elif len(buf) >= 12:
                if len(buf) >= 12 + struct.unpack_from('<H', buf, 10)[0]:
                    break
                time.sleep(0.05)
            else:
                time.sleep(0.05)

        if len(buf) < 12:
            raise TestoError(
                f"Brak odpowiedzi na komendę {cmd:#04x} "
                f"(odebrano {len(buf)} B, oczekiwano min. 12).")

        resp_cmd = buf[1]
        if resp_cmd in ERROR_CODES:
            raise TestoError(
                f"Urządzenie odrzuciło komendę {cmd:#04x}: {ERROR_CODES[resp_cmd]} "
                f"(kod {resp_cmd:#04x}).")
        if resp_cmd != cmd:
            raise TestoError(
                f"Niespójna odpowiedź: wysłano {cmd:#04x}, otrzymano {resp_cmd:#04x}.")

        declared = struct.unpack_from('<H', buf, 10)[0]
        payload = buf[12:12 + declared]
        if len(payload) < declared:
            raise TestoError(
                f"Niekompletny payload komendy {cmd:#04x}: "
                f"{len(payload)}/{declared} B.")
        return payload

    # --- Operacje wysokopoziomowe -----------------------------------------

    def read_block(self, bank: int, offset: int, length: int) -> bytes:
        params = bytes([0x00, bank]) + struct.pack('<H', offset) \
                 + b'\x00\x00' + struct.pack('<H', length)
        return self.request(CMD_READ_BLOCK, params)

    def get_mode(self) -> int:
        return self.request(CMD_GET_MODE)[0]

    def get_stored_record_count(self) -> int:
        """
        Liczba rekordów w pamięci pomiarów, z bloku B2.

        Kryterium odporniejsze niż numer trybu: wartości trybu nie są w pełni
        rozpoznane (zaobserwowano 0, 2, 4 i 6), a licznik rekordów mówi wprost,
        czy kasowanie zniszczyłoby czyjeś dane.
        """
        b2 = self.read_block(BANK_CONFIG, B2_BANK_OFFSET, B2_LEN)
        if len(b2) < B2_LEN:
            raise TestoError("Blok B2 krótszy niż oczekiwano — nie mogę ustalić, "
                             "czy w pamięci są dane. Przerywam dla bezpieczeństwa.")
        # Blok skasowany (same 0xff) oznacza pustą pamięć, nie 65535 rekordów.
        if all(b == 0xFF for b in b2):
            return 0
        count = struct.unpack_from('<H', b2, B2_RECORD_COUNT)[0]
        return 0 if count == 0xFFFF else count

    def get_firmware(self) -> str:
        return self.request(CMD_GET_FIRMWARE).decode('ascii', 'replace').strip()

    def get_identity(self) -> dict:
        """Bank 1: nazwa, numer seryjny, wersje, rok produkcji."""
        blk = self.read_block(BANK_IDENTITY, 0x00, 0x80)
        if len(blk) < 0x34:
            raise TestoError("Blok tożsamości (bank 1) krótszy niż oczekiwano.")
        return {
            "articleNumber": blk[0x02:0x0A].decode('ascii', 'replace').strip(),
            "model": blk[0x0A:0x1E].decode('ascii', 'replace').strip(),
            "manufacturingYear": struct.unpack_from('<H', blk, 0x20)[0],
            "serialNumber": blk[0x22:0x2A].decode('ascii', 'replace').strip(),
            "firmwareVersion": blk[0x2A:0x2F].decode('ascii', 'replace').strip(),
            "hardwareVersion": blk[0x2F:0x34].decode('ascii', 'replace').strip(),
        }

    def read_config_payload(self) -> bytearray:
        """Bajty [0:104] banku 2 — dokładnie ten ładunek, który przyjmuje 0x30."""
        payload = self.read_block(BANK_CONFIG, 0x00, CONFIG_PAYLOAD_LEN)
        if len(payload) != CONFIG_PAYLOAD_LEN:
            raise TestoError(
                f"Blok konfiguracji ma {len(payload)} B, oczekiwano "
                f"{CONFIG_PAYLOAD_LEN} B.")
        return bytearray(payload)

    def open_session_if_needed(self, mode: int):
        """
        Otwiera sesję zapisu, o ile nie jest już otwarta.

        Bit {@code 0x04} trybu oznacza otwartą sesję. Wysłanie `0x11` przy
        otwartej sesji kończy się błędem f2 — ComSoft w takiej sytuacji pomija
        tę komendę i przechodzi wprost do `0x42` (potwierdzone przechwytem
        cap1.pcapng, urządzenie zastane w trybie 4).
        """
        if mode & SESSION_OPEN_BIT:
            return False
        self.request(CMD_BEGIN_SESSION, b'', data=b'', settle=0.5)
        return True

    def page_transaction(self, closing: bool):
        """
        Otwarcie ({@code closing=False}) albo zamknięcie ({@code closing=True})
        transakcji zapisu metadanych. Flaga siedzi w bajtach [6:8] parametru:
        `00` otwiera, `01` zamyka. W przechwycie obejmuje 128 wywołań `0x40`.
        """
        params = b'\x00' * 6 + struct.pack('<H', 1 if closing else 0)
        self.request(CMD_BEGIN_WRITE, params, data=b'', settle=0.5)

    def write_page_area(self, page: bytes):
        """
        Zapisuje obszar metadanych opisowych porcjami po 32 B.

        Urządzenie odsyła w odpowiedzi zapisaną porcję, więc każdy fragment
        jest weryfikowany od razu — bez tego pusta nazwa wychodzi dopiero
        w raporcie producenta, jako ciąg znaków zapytania.
        """
        if len(page) != PAGE_AREA_SIZE:
            raise TestoError(
                f"Obszar metadanych ma {len(page)} B, oczekiwano {PAGE_AREA_SIZE} B.")

        for addr in range(0, PAGE_AREA_SIZE, PAGE_CHUNK):
            chunk = page[addr:addr + PAGE_CHUNK]
            params = b'\x00\x00' + struct.pack('<H', addr) + b'\x00\x00' \
                     + struct.pack('<H', PAGE_CHUNK)
            echoed = self.request(CMD_WRITE_PAGE, params, data=chunk, settle=0.05)
            if echoed != chunk:
                raise TestoError(
                    f"Weryfikacja zapisu metadanych pod adresem {addr:#06x} "
                    f"nie powiodła się: wysłano {chunk.hex()}, "
                    f"urządzenie odesłało {echoed.hex()}.")

    def erase_bank(self, bank: int):
        self.request(CMD_ERASE_BANK, bytes([0x00, bank]), data=b'', settle=0.5)

    def write_config(self, payload: bytes) -> bytes:
        params = bytes([0x00, BANK_CONFIG]) + b'\x00\x00\x00\x00' \
                 + struct.pack('<H', len(payload))
        return self.request(CMD_WRITE_CONFIG, params, data=payload, settle=1.0)

    def set_clock(self, when_utc: datetime):
        data = struct.pack('<H', when_utc.year) + bytes([
            when_utc.month, when_utc.day,
            when_utc.hour, when_utc.minute, when_utc.second])
        self.request(CMD_SET_CLOCK, b'', data=data, settle=0.5)

    def commit(self):
        self.request(CMD_COMMIT, b'', data=b'', settle=1.0)


# --- Budowa konfiguracji ---------------------------------------------------

def patch_config(template: bytearray, *, count: int, channel_mask: int,
                 interval_seconds: int, start_delay_seconds: int,
                 ranges: list, prog_time_utc: datetime) -> bytearray:
    """
    Nanosi parametry na szablon odczytany z urządzenia.

    Modyfikuje WYŁĄCZNIE pola o potwierdzonym znaczeniu; reszta bajtów
    przechodzi bez zmian.
    """
    cfg = bytearray(template)
    b0 = B0_OFFSET
    b1 = B1_OFFSET

    struct.pack_into('<H', cfg, b0 + B0_COUNT, count)
    struct.pack_into('<I', cfg, b0 + B0_START_DELAY, start_delay_seconds)
    cfg[b0 + B0_CHANNEL_MASK] = channel_mask

    cfg[b0 + B0_PROG_TIME + 0] = prog_time_utc.second
    cfg[b0 + B0_PROG_TIME + 1] = prog_time_utc.minute
    cfg[b0 + B0_PROG_TIME + 2] = prog_time_utc.hour
    cfg[b0 + B0_PROG_TIME + 3] = prog_time_utc.day
    cfg[b0 + B0_PROG_TIME + 4] = prog_time_utc.month
    struct.pack_into('<H', cfg, b0 + B0_PROG_TIME + 5, prog_time_utc.year)

    for ch in range(MAX_CHANNELS):
        upper, lower = ranges[ch]
        base = b0 + B0_RANGES + ch * 4
        struct.pack_into('<h', cfg, base, encode_temperature(upper))
        struct.pack_into('<h', cfg, base + 2, encode_temperature(lower))

    cfg[b0 + B0_MASK_COPY] = channel_mask
    cfg[b0 + B0_MASK_COPY + 1] = channel_mask

    struct.pack_into('<H', cfg, b1 + B1_INTERVAL, interval_seconds)

    # Sumy kontrolne liczone PO naniesieniu wszystkich zmian
    struct.pack_into('<H', cfg, b0 + B0_CHECKSUM,
                     block_checksum(cfg[b0:b0 + B0_CHECKSUM]))
    struct.pack_into('<H', cfg, b1 + B1_CHECKSUM,
                     block_checksum(cfg[b1:b1 + B1_CHECKSUM]))
    return cfg


def describe_config(cfg: bytes) -> dict:
    """Odczytuje z bloku pola o potwierdzonym znaczeniu — do weryfikacji."""
    b0, b1 = B0_OFFSET, B1_OFFSET
    mask = cfg[b0 + B0_CHANNEL_MASK]
    channels = []
    for ch in range(MAX_CHANNELS):
        base = b0 + B0_RANGES + ch * 4
        upper = struct.unpack_from('<h', cfg, base)[0]
        lower = struct.unpack_from('<h', cfg, base + 2)[0]
        channels.append({
            "channel": ch + 1,
            "enabled": bool(mask & (1 << ch)),
            "rangeUpperC": decode_temperature(upper),
            "rangeLowerC": decode_temperature(lower),
        })
    return {
        "count": struct.unpack_from('<H', cfg, b0 + B0_COUNT)[0],
        "startDelaySeconds": struct.unpack_from('<I', cfg, b0 + B0_START_DELAY)[0],
        "channelMask": mask,
        "intervalSeconds": struct.unpack_from('<H', cfg, b1 + B1_INTERVAL)[0],
        "channels": channels,
    }


def parse_channels(spec: str) -> int:
    mask = 0
    for part in spec.split(','):
        part = part.strip()
        if not part:
            continue
        try:
            n = int(part)
        except ValueError:
            raise SystemExit(f"Niepoprawny numer kanału: '{part}'")
        if not 1 <= n <= MAX_CHANNELS:
            raise SystemExit(f"Kanał poza zakresem 1..{MAX_CHANNELS}: {n}")
        mask |= 1 << (n - 1)
    if mask == 0:
        raise SystemExit("Trzeba włączyć przynajmniej jeden kanał.")
    return mask


def main():
    ap = argparse.ArgumentParser(
        description="Programowanie rejestratora Testo 176 T4 przez USB.")
    ap.add_argument("--interval-minutes", type=int, required=True,
                    help="Interwał pomiarów w minutach")
    ap.add_argument("--count", type=int, required=True,
                    help="Liczba odczytów")
    ap.add_argument("--channels", default="1",
                    help="Aktywne kanały po przecinku, np. '1,2,3,4'")
    ap.add_argument("--start", required=True,
                    help="Czas pierwszego pomiaru, czas LOKALNY, "
                         "format RRRR-MM-DD HH:MM:SS")
    ap.add_argument("--range-upper", type=float, required=True,
                    help="Górna granica zakresu kanału [°C]")
    ap.add_argument("--range-lower", type=float, required=True,
                    help="Dolna granica zakresu kanału [°C]")
    ap.add_argument("--channel-names", default="",
                    help="Nazwy kanałów po przecinku, w kolejności 1..4. "
                         "Docelowo numery świadectw wzorcowania, np. "
                         "'LWT/2014,LWT/2017'. Puste = nazwa pominięta.")
    ap.add_argument("--operator", default="",
                    help="Nazwa operatora zapisywana w metadanych urządzenia")
    ap.add_argument("--port", help="Wymuszenie portu COM (domyślnie: autodetekcja)")
    ap.add_argument("--force", action="store_true",
                    help="Programuj mimo niepobranych danych poprzedniej misji")
    args = ap.parse_args()

    result = {"status": "ERROR", "message": "Nieznany błąd."}
    device = None

    try:
        if args.count <= 0:
            raise TestoError("Liczba odczytów musi być dodatnia.")
        if args.interval_minutes <= 0:
            raise TestoError("Interwał musi być dodatni.")
        if args.range_lower >= args.range_upper:
            raise TestoError(
                f"Dolna granica ({args.range_lower} °C) musi być mniejsza od "
                f"górnej ({args.range_upper} °C).")

        interval_seconds = args.interval_minutes * 60
        if interval_seconds > 0xFFFF:
            raise TestoError(
                f"Interwał {args.interval_minutes} min przekracza pojemność pola "
                f"(maks. {0xFFFF // 60} min).")

        channel_mask = parse_channels(args.channels)

        try:
            start_local = datetime.strptime(args.start, "%Y-%m-%d %H:%M:%S")
        except ValueError:
            raise TestoError(
                f"Niepoprawny format --start: '{args.start}'. "
                f"Oczekiwano RRRR-MM-DD HH:MM:SS.")
        start_utc = start_local.astimezone().astimezone(timezone.utc)

        device = Testo176(args.port)
        identity = device.get_identity()
        mode = device.get_mode()

        stored = device.get_stored_record_count()
        if stored > 0 and not args.force:
            raise TestoError(
                f"W pamięci urządzenia jest {stored} rekordów pomiarowych "
                f"(tryb {mode} — {MODE_NAMES.get(mode, 'nierozpoznany')}). "
                f"Programowanie SKASUJE je bezpowrotnie. Pobierz dane najpierw "
                f"albo powtórz z --force.")

        template = device.read_config_payload()

        # Czas programowania = teraz, z zerowaną częścią ułamkową
        prog_utc = datetime.now(timezone.utc).replace(microsecond=0)
        delay = int((start_utc - prog_utc).total_seconds())
        if delay < 0:
            raise TestoError(
                f"Czas startu ({start_local}) jest w przeszłości względem "
                f"chwili programowania.")

        ranges = [(args.range_upper, args.range_lower)] * MAX_CHANNELS
        payload = patch_config(
            template,
            count=args.count,
            channel_mask=channel_mask,
            interval_seconds=interval_seconds,
            start_delay_seconds=delay,
            ranges=ranges,
            prog_time_utc=prog_utc,
        )

        # Kolejność odtworzona z przechwytu ComSoftu — bez otwarcia sesji
        # urządzenie odrzuca kasowanie kodem f2.
        device.open_session_if_needed(mode)

        # Metadane opisowe PRZED kasowaniem — taka jest kolejność u producenta.
        # Ich pominięcie daje znaki zapytania w raportach ComSoftu.
        channel_names = [n.strip() for n in args.channel_names.split(',')] \
            if args.channel_names else []
        channel_names += [""] * (MAX_CHANNELS - len(channel_names))

        device.page_transaction(closing=False)
        device.write_page_area(
            build_page_area(channel_names, args.operator, prog_utc))
        device.page_transaction(closing=True)

        # Kasowanie banków danych, potem konfiguracji
        device.erase_bank(BANK_DATA_B)
        device.erase_bank(BANK_DATA_A)
        device.erase_bank(BANK_CONFIG)

        echoed = device.write_config(bytes(payload))
        device.set_clock(prog_utc)
        device.commit()

        # Weryfikacja round-trip — wymóg GxP, tak samo jak w moście 184 T3
        readback = device.read_config_payload()
        intended = describe_config(payload)
        actual = describe_config(readback)

        mismatches = []
        for key in ("count", "channelMask", "intervalSeconds", "startDelaySeconds"):
            if intended[key] != actual[key]:
                mismatches.append(
                    f"{key}: zapisano {actual[key]}, oczekiwano {intended[key]}")
        for want, got in zip(intended["channels"], actual["channels"]):
            if want["enabled"] != got["enabled"]:
                mismatches.append(
                    f"kanał {want['channel']}: aktywny={got['enabled']}, "
                    f"oczekiwano {want['enabled']}")
            elif want["enabled"] and (want["rangeUpperC"] != got["rangeUpperC"]
                                      or want["rangeLowerC"] != got["rangeLowerC"]):
                mismatches.append(
                    f"kanał {want['channel']}: zakres "
                    f"{got['rangeLowerC']}…{got['rangeUpperC']} °C, oczekiwano "
                    f"{want['rangeLowerC']}…{want['rangeUpperC']} °C")

        if len(echoed) != len(payload):
            mismatches.append(
                f"echo zapisu ma {len(echoed)} B, wysłano {len(payload)} B")

        if mismatches:
            result = {
                "status": "ERROR",
                "message": "Weryfikacja round-trip NIE powiodła się: "
                           + "; ".join(mismatches),
                "device": identity,
            }
        else:
            result = {
                "status": "SUCCESS",
                "message": "Rejestrator zaprogramowany i zweryfikowany.",
                "device": identity,
                "session": {
                    "programmingTimeUtc": prog_utc.isoformat(),
                    "firstMeasurementTimeUtc": start_utc.isoformat(),
                    "firstMeasurementTimeLocal": start_local.isoformat(),
                    "startDelaySeconds": delay,
                    "intervalMinutes": args.interval_minutes,
                    "measurementsCount": args.count,
                    "channelMask": channel_mask,
                    "channels": actual["channels"],
                    "channelNames": channel_names,
                    "operator": args.operator,
                },
                "warnings": [
                    "Typ termopary NIE jest zapisywany do urządzenia — do bloku "
                    "trafia wyłącznie zakres pomiarowy kanału. Typ czujnika musi "
                    "być prowadzony w kartotece rejestratora.",
                    "Pola B1 [8:10] i [12:16] (szacunki 'energycalc' liczone przez "
                    "ComSoft) są przepisywane z szablonu bez przeliczenia. "
                    "Wpływają na szacunek żywotności baterii pokazywany przez "
                    "oprogramowanie producenta, nie na przebieg pomiaru.",
                ],
            }

    except TestoError as exc:
        result = {"status": "ERROR", "message": str(exc)}
    except Exception as exc:  # noqa: BLE001 — most zawsze zwraca JSON
        result = {"status": "ERROR",
                  "message": f"Błąd krytyczny mostu: {type(exc).__name__}: {exc}"}
    finally:
        if device:
            device.close()

    print(json.dumps(result, ensure_ascii=False, indent=2))
    sys.exit(0)


if __name__ == "__main__":
    main()
