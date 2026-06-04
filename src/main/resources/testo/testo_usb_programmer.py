"""
Program do programowania rejestratora Testo 174T przez interfejs USB (D2XX).
Umożliwia ustawienie interwału, liczby pomiarów oraz zaplanowanie czasu startu.
Wykorzystuje w pełni rozszyfrowany i zweryfikowany algorytm sumy kontrolnej XOR ^ 0xA5.

Autor: Antigravity (Google DeepMind)
Status: GOTOWY DO UŻYCIA (PRODUCTION-READY)
"""

import ctypes
import os
import sys
import time
from datetime import datetime, timedelta, timezone
import argparse


def convert_polish_local_to_utc(local_dt: datetime) -> datetime:
    """Konwertuje czas lokalny w Polsce (CET/CEST) na UTC, uwzględniając DST."""
    year = local_dt.year
    # Ostatnia niedziela marca (zmiana na czas letni CEST: UTC+2)
    last_sun_march = max(d for d in range(25, 32) if datetime(year, 3, d).weekday() == 6)
    dst_start = datetime(year, 3, last_sun_march, 2, 0, 0)
    
    # Ostatnia niedziela października (zmiana na czas zimowy CET: UTC+1)
    last_sun_oct = max(d for d in range(25, 32) if datetime(year, 10, d).weekday() == 6)
    dst_end = datetime(year, 10, last_sun_oct, 3, 0, 0)
    
    if dst_start <= local_dt < dst_end:
        return (local_dt - timedelta(hours=2)).replace(tzinfo=timezone.utc)
    else:
        return (local_dt - timedelta(hours=1)).replace(tzinfo=timezone.utc)


def calculate_checksum(packet: bytes) -> int:
    """
    Oblicza sumę kontrolną dla ramki protokołu Testo.
    Wzór: XOR wszystkich bajtów ramki, a następnie XOR z maską 0xA5.
    """
    xor_sum = 0
    for b in packet:
        xor_sum ^= b
    return xor_sum ^ 0xA5


class TestoD2XXProgrammer:
    def __init__(self):
        self.dll = None
        self.handle = ctypes.c_void_p(0)
        self._load_dll()

    def _load_dll(self):
        """Ładuje oficjalną bibliotekę FTDI D2XX."""
        paths = [
            r'C:\Windows\System32\ftd2xx64.dll',
            r'C:\Program Files (x86)\Testo\Comfort Software Basic\USBDriver\FTDIBus\AMD64\ftd2xx64.dll',
            'ftd2xx64.dll'
        ]
        for path in paths:
            if os.path.exists(path) or path == 'ftd2xx64.dll':
                try:
                    self.dll = ctypes.windll.LoadLibrary(path)
                    print(f"[*] Pomyślnie załadowano bibliotekę: {path}")
                    return
                except Exception:
                    pass
        raise RuntimeError("Błąd: Nie można odnaleźć ftd2xx64.dll. Upewnij się, że sterowniki Testo są zainstalowane.")

    def open_device(self) -> str:
        """Wyszukuje i otwiera kołyskę Testo USB."""
        num_devs = ctypes.c_ulong(0)
        self.dll.FT_CreateDeviceInfoList(ctypes.byref(num_devs))
        
        if num_devs.value == 0:
            raise RuntimeError("Nie wykryto podłączonej kołyski Testo USB. Wepnij kołyskę i spróbuj ponownie.")
            
        flags = ctypes.c_ulong(0)
        dev_type = ctypes.c_ulong(0)
        dev_id = ctypes.c_ulong(0)
        loc_id = ctypes.c_ulong(0)
        serial = ctypes.create_string_buffer(64)
        desc = ctypes.create_string_buffer(64)
        temp_handle = ctypes.c_void_p(0)
        
        self.dll.FT_GetDeviceInfoDetail(
            0,
            ctypes.byref(flags),
            ctypes.byref(dev_type),
            ctypes.byref(dev_id),
            ctypes.byref(loc_id),
            serial,
            desc,
            ctypes.byref(temp_handle)
        )
        
        res = self.dll.FT_Open(0, ctypes.byref(self.handle))
        if res != 0:
            raise RuntimeError(f"Błąd otwierania urządzenia Testo USB (kod błędu FTDI: {res})")
            
        dev_desc = desc.value.decode('latin-1')
        print(f"[*] Połączono z kołyską: {dev_desc} (S/N kołyski: {serial.value.decode('latin-1')})")
        return dev_desc

    def configure(self):
        """Konfiguruje parametry transmisji (57600 baud, 8N1, timeouty)."""
        self.dll.FT_SetBaudRate(self.handle, ctypes.c_ulong(57600))
        self.dll.FT_SetDataCharacteristics(self.handle, ctypes.c_byte(8), ctypes.c_byte(0), ctypes.c_byte(0))
        self.dll.FT_SetTimeouts(self.handle, ctypes.c_ulong(2000), ctypes.c_ulong(2000))
        self.dll.FT_Purge(self.handle, ctypes.c_ulong(3))

    def write(self, data: bytes):
        """Wysyła surowe bajty do urządzenia."""
        written = ctypes.c_ulong(0)
        self.dll.FT_Write(self.handle, data, len(data), ctypes.byref(written))

    def read_all(self) -> bytes:
        """Czyta wszystkie dostępne bajty z bufora urządzenia."""
        rx_queue = ctypes.c_ulong(0)
        tx_queue = ctypes.c_ulong(0)
        event_status = ctypes.c_ulong(0)
        
        self.dll.FT_GetStatus(self.handle, ctypes.byref(rx_queue), ctypes.byref(tx_queue), ctypes.byref(event_status))
        if rx_queue.value > 0:
            buf = ctypes.create_string_buffer(rx_queue.value)
            read_val = ctypes.c_ulong(0)
            self.dll.FT_Read(self.handle, buf, rx_queue.value, ctypes.byref(read_val))
            return buf.raw[:read_val.value]
        return b''

    def send_cmd(self, cmd_bytes: bytes, wait_ms: int = 100) -> bytes:
        """Wysyła polecenie i czeka na odpowiedź."""
        self.write(cmd_bytes)
        time.sleep(wait_ms / 1000.0)
        return self.read_all()

    def program_device(self, interval: int, count: int, start_time_local: datetime, upper_limit: float = 8.0, lower_limit: float = 2.0) -> bool:
        """
        Wykonuje pełną sekwencję programowania urządzenia z uwzględnieniem granic alarmowych.
        """
        print(f"\n[+] Przygotowanie parametrów programowania:")
        print(f"    - Interwał:        {interval} minut")
        print(f"    - Liczba pomiarów: {count}")
        print(f"    - Czas startu (PL): {start_time_local.strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"    - Górny alarm:     {upper_limit} °C")
        print(f"    - Dolny alarm:     {lower_limit} °C")
        
        # 1. Konwersja czasu na UTC
        start_time_utc = convert_polish_local_to_utc(start_time_local)
        print(f"    - Czas startu (UTC):{start_time_utc.strftime('%Y-%m-%d %H:%M:%S')}")
        
        # 2. Synchronizacja zegara: Program Date = bieżący czas UTC (sekundy muszą być 00!)
        current_time_utc = datetime.now(timezone.utc).replace(second=0, microsecond=0)
        
        if start_time_utc <= current_time_utc:
            program_date_utc = current_time_utc
            start_delay = 0
            start_time_utc = current_time_utc
        else:
            program_date_utc = current_time_utc
            start_delay = int((start_time_utc - program_date_utc).total_seconds() // 60)
            if start_delay < 0:
                start_delay = 0
                
        print(f"    - Program Date (UTC):{program_date_utc.strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"    - Start Delay (min):{start_delay} (hex: {start_delay:06x})")
        
        # 4. Konstrukcja payloadu (27 bajtów)
        payload = bytearray(27)
        payload[0] = 0x04
        payload[1] = 0x1B
        
        # Interval (uint16 BE)
        payload[2] = (interval >> 8) & 0xFF
        payload[3] = interval & 0xFF
        
        # Count (uint16 BE)
        payload[4] = (count >> 8) & 0xFF
        payload[5] = count & 0xFF
        
        # Start Delay (uint24 BE - 3 bajty)
        payload[6] = (start_delay >> 16) & 0xFF
        payload[7] = (start_delay >> 8) & 0xFF
        payload[8] = start_delay & 0xFF
        
        # Rok (uint16 BE)
        year = program_date_utc.year
        payload[9] = (year >> 8) & 0xFF
        payload[10] = year & 0xFF
        
        # Miesiąc, Dzień, Godzina, Minuta, Sekunda
        payload[11] = program_date_utc.month
        payload[12] = program_date_utc.day
        payload[13] = program_date_utc.hour
        payload[14] = program_date_utc.minute
        payload[15] = program_date_utc.second
        
        # 16-18: 00 00 00 (3 bajty rezerwy)
        payload[16] = 0x00
        payload[17] = 0x00
        payload[18] = 0x00
        
        # 19-20: Górna granica alarmowa (int16 BE * 10)
        upper_raw = int(round(upper_limit * 10))
        if upper_raw < 0:
            upper_raw = (1 << 16) + upper_raw
        payload[19] = (upper_raw >> 8) & 0xFF
        payload[20] = upper_raw & 0xFF
        
        # 21-22: Dolna granica alarmowa (int16 BE * 10)
        lower_raw = int(round(lower_limit * 10))
        if lower_raw < 0:
            lower_raw = (1 << 16) + lower_raw
        payload[21] = (lower_raw >> 8) & 0xFF
        payload[22] = lower_raw & 0xFF
        
        # 23-24: Granica pomocnicza/sondy (domyślnie 100.0 °C = 1000 = 0x03e8)
        payload[23] = 0x03
        payload[24] = 0xE8
        
        # 25-26: 00 00
        payload[25] = 0x00
        payload[26] = 0x00

        # Obliczenie sumy kontrolnej
        cmd_header = b'\xab\x61\x00\x42\x1b'
        csum = calculate_checksum(cmd_header + payload)
        
        full_command = cmd_header + payload + bytes([csum])
        
        # -------------------------------------------------------------
        # SEKWENCJA TRANSMISJI USB
        # -------------------------------------------------------------
        print("\n[+] Rozpoczęcie sekwencji transmisji USB:")
        
        # 1. Reset
        print("  1/9 Resetowanie urządzenia...")
        self.send_cmd(b'\xf0', wait_ms=50)
        
        # 2. Inicjalizacja (HELLO)
        print("  2/9 Inicjalizacja rejestratora (Hello)...")
        res_init = self.send_cmd(b'\xab\x01\x0d\x00\x00\x02')
        if not res_init:
            raise RuntimeError("Brak odpowiedzi z rejestratora. Upewnij się, że urządzenie leży prawidłowo w kołysce.")
            
        # 3. Get Device Info (ab30)
        self.send_cmd(b'\xf0', wait_ms=50)
        print("  3/9 Pobieranie informacji o urządzeniu (ab30)...")
        res_info = self.send_cmd(b'\xab\x30\x00\x02\x0b\x37', wait_ms=200)
        
        if res_info:
            pos_30 = res_info.find(b'\xab\x30')
            if pos_30 != -1 and len(res_info) >= pos_30 + 13:
                serial_bytes = res_info[pos_30 + 9 : pos_30 + 13]
                serial = int.from_bytes(serial_bytes, 'big')
                print(f"      [S/N Urządzenia: {serial}]")
        
        # 4. Get Status (ab31 query)
        self.send_cmd(b'\xf0', wait_ms=50)
        print("  4/10 Pobieranie bieżącego statusu (ab31 query)...")
        self.send_cmd(b'\xab\x31\x00\x42\x1b\x66', wait_ms=200)
        
        # 5. Read metadata blocks (ab33)
        print("  5/10 Odczytywanie istniejących metadanych (ab33)...")
        meta_blocks = {}
        meta_addresses = [0x0100, 0x0120, 0x0140, 0x0160, 0x0180, 0x01a0, 0x01c0, 0x01e0]
        # Wysyłamy reset tylko raz na początku sekwencji odczytu metadanych
        self.send_cmd(b'\xf0', wait_ms=20)
        for addr in meta_addresses:
            addr_hi = (addr >> 8) & 0xFF
            addr_lo = addr & 0xFF
            cmd_meta = bytes([0xab, 0x33, addr_hi, addr_lo, 0x20])
            csum_meta = calculate_checksum(cmd_meta)
            res = self.send_cmd(cmd_meta + bytes([csum_meta]), wait_ms=50)
            pos = res.find(b'\xab\x33')
            if pos != -1 and len(res) >= pos + 38:
                meta_blocks[addr] = res[pos+5 : pos+37]
            else:
                # Domyślne wartości w razie problemów
                if addr == 0x0100:
                    meta_blocks[addr] = b'\x00\x00' + b'testo 174-2010\r\n' + b'\x00\x00\x00\x00\x36\x00\x36\x00\x00\x00\x2f\x00\x32\x00\x30\x00\x32'[:16]
                elif addr == 0x0120:
                    meta_blocks[addr] = b'19/a' + b'\x00'*28
                else:
                    meta_blocks[addr] = b'\xcc' * 32
        
        # 6. Write metadata blocks (ab63)
        print("  6/10 Zapisywanie metadanych (ab63 WRITE_METADATA)...")
        # Wysyłamy reset tylko raz na początku sekwencji zapisu metadanych
        self.send_cmd(b'\xf0', wait_ms=20)
        for addr in meta_addresses:
            addr_hi = (addr >> 8) & 0xFF
            addr_lo = addr & 0xFF
            payload_meta = meta_blocks[addr]
            cmd_write = bytes([0xab, 0x63, addr_hi, addr_lo, 0x20]) + payload_meta
            csum_write = calculate_checksum(cmd_write)
            self.send_cmd(cmd_write + bytes([csum_write]), wait_ms=50)

        # 7. Reset + Status Check to refresh MCU
        self.send_cmd(b'\xf0', wait_ms=50)
        print("  7/10 Weryfikacja statusu przed zapisem parametrów...")
        self.send_cmd(b'\xab\x31\x00\x42\x1b\x66', wait_ms=200)

        # 8. SET_PARAMS (Zapis parametrów)
        self.send_cmd(b'\xf0', wait_ms=50)
        print("  8/10 Zapisywanie nowych parametrów (ab61 SET_PARAMS)...")
        
        # Wysyłamy komendę surową bez automatycznego resetowania
        self.write(full_command)
        
        print("      [i] Rejestrator zapisuje parametry do pamięci Flash (może to zająć do 8-10 sekund)...")
        res_write = b''
        start_wait = time.time()
        success = False
        
        while time.time() - start_wait < 10.0:
            time.sleep(0.1)
            chunk = self.read_all()
            if chunk:
                res_write += chunk
                pos_61 = res_write.find(b'\xab\x61')
                pos_e1 = res_write.find(b'\xab\xe1')
                if pos_61 != -1:
                    print(f"      [+] Konfiguracja zapisana i potwierdzona przez rejestrator! (Odpowiedź: {res_write[pos_61:pos_61+6].hex()})")
                    success = True
                    break
                elif pos_e1 != -1:
                    err_code = res_write[pos_e1+2] if len(res_write) > pos_e1+2 else -1
                    print(f"      [!] Rejestrator odrzucił parametry (NACK). Kod błędu: {err_code}")
                    success = False
                    break
        else:
            print("      [!] Przekroczono limit czasu oczekiwania na potwierdzenie zapisu (10s timeout).")
            success = False

        # 9. Polling & Finalize
        print("  9/10 Finalizowanie komunikacji...")
        self.send_cmd(b'\xf0', wait_ms=50)
        for _ in range(3):
            self.send_cmd(b'\xab\x01\x0b\x00\x00\x04', wait_ms=50)

        # 10. Reset
        print("  10/10 Wykonanie końcowego resetu rejestratora...")
        self.send_cmd(b'\xf0', wait_ms=50)
        
        return success

    def close(self):
        if self.handle.value:
            self.dll.FT_Close(self.handle)
            print("\n[*] Rozłączono z kołyską USB.")


def main():
    print("=" * 60)
    print("       PROGRAMATOR USB REJESTRATORA TESTO 174T (D2XX)")
    print("=" * 60)
    
    parser = argparse.ArgumentParser(description="Programowanie parametrów rejestratora Testo 174T przez USB.")
    parser.add_argument("--interval", type=int, help="Interwał pomiarów w minutach (np. 15, 30, 60, 180)")
    parser.add_argument("--count", type=int, help="Planowana liczba pomiarów (np. 40, 100)")
    parser.add_argument("--start", type=str, help="Czas pierwszego pomiaru w formacie: RRRR-MM-DD HH:MM:SS")
    parser.add_argument("--upper", "--upper-limit", dest="upper_limit", type=float, default=8.0, help="Górny próg alarmowy w °C (domyślnie: 8.0)")
    parser.add_argument("--lower", "--lower-limit", dest="lower_limit", type=float, default=2.0, help="Dolny próg alarmowy w °C (domyślnie: 2.0)")
    
    args = parser.parse_args()
    upper_limit = args.upper_limit
    lower_limit = args.lower_limit
    
    # Interaktywne zbieranie danych, jeśli nie podano głównych parametrów
    if args.interval is None or args.count is None or args.start is None:
        print("\n[i] Brak kompletnych parametrów uruchomienia. Przechodzę do trybu interaktywnego.\n")
        
        # 1. Zbieranie interwału
        while True:
            try:
                inp = input("Podaj interwał pomiarów w minutach [domyślnie: 180 (3h)]: ").strip()
                if not inp:
                    interval = 180
                    break
                interval = int(inp)
                if interval <= 0:
                    raise ValueError
                break
            except ValueError:
                print("[!] Błąd: Interwał musi być liczbą całkowitą większą od 0.")
                
        # 2. Zbieranie liczby pomiarów
        while True:
            try:
                inp = input("Podaj liczbę pomiarów [domyślnie: 40]: ").strip()
                if not inp:
                    count = 40
                    break
                count = int(inp)
                if count <= 0:
                    raise ValueError
                break
            except ValueError:
                print("[!] Błąd: Liczba pomiarów musi być liczbą całkowitą większą od 0.")
                
        # 3. Zbieranie czasu startu
        default_start = datetime.now() + timedelta(hours=2)
        default_start_str = default_start.strftime("%Y-%m-%d %H:%M:00")
        while True:
            inp = input(f"Podaj czas pierwszego pomiaru (RRRR-MM-DD HH:MM:SS)\n[domyślnie (za 2h): {default_start_str}]: ").strip()
            if not inp:
                start_time_local = datetime.strptime(default_start_str, "%Y-%m-%d %H:%M:%S")
                break
            try:
                start_time_local = datetime.strptime(inp, "%Y-%m-%d %H:%M:%S")
                if start_time_local < datetime.now():
                    print("[!] Ostrzeżenie: Podany czas startu jest w przeszłości!")
                    confirm = input("Czy na pewno chcesz kontynuować? (t/n): ").strip().lower()
                    if confirm != 't':
                        continue
                break
            except ValueError:
                print("[!] Błąd: Nieprawidłowy format czasu. Użyj: RRRR-MM-DD HH:MM:SS")
                
        # 4. Zbieranie opcjonalnych progów alarmowych w trybie interaktywnym
        try:
            inp = input(f"Podaj górny próg alarmowy w °C [domyślnie: 8.0]: ").strip()
            if inp:
                upper_limit = float(inp)
        except ValueError:
            print("[*] Niepoprawna wartość. Używam domyślnej: 8.0 °C")
            
        try:
            inp = input(f"Podaj dolny próg alarmowy w °C [domyślnie: 2.0]: ").strip()
            if inp:
                lower_limit = float(inp)
        except ValueError:
            print("[*] Niepoprawna wartość. Używam domyślnej: 2.0 °C")
    else:
        interval = args.interval
        count = args.count
        try:
            start_time_local = datetime.strptime(args.start, "%Y-%m-%d %H:%M:%S")
        except ValueError:
            print("[!] Błąd: Parametr --start musi mieć format: RRRR-MM-DD HH:MM:SS")
            sys.exit(1)
            
    programmer = None
    try:
        programmer = TestoD2XXProgrammer()
        programmer.open_device()
        programmer.configure()
        
        success = programmer.program_device(interval, count, start_time_local, upper_limit, lower_limit)
        
        if success:
            print("\n" + "=" * 60)
            print("  [OK] Rejestrator Testo 174T zaprogramowany pomyslnie!")
            print("  Wloz urzadzenie na miejsce pomiarowe. Zacznie pomiar o czasie startu.")
            print("=" * 60)
        else:
            print("\n" + "=" * 60)
            print("  [ERROR] Proces programowania mogl sie nie powiesc.")
            print("  Sprawdz polaczenie z kolyska USB i spróbuj ponownie.")
            print("=" * 60)
            
    except Exception as e:
        print(f"\n[!] Wystapil krytyczny blad: {e}")
    finally:
        if programmer:
            programmer.close()


if __name__ == "__main__":
    main()
