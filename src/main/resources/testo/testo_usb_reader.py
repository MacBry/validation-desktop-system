"""
Program do bezpośredniego odczytu danych z rejestratora Testo 174T przez interfejs USB (D2XX).

Automatycznie ładuje oficjalny sterownik FTDI D2XX dostarczony z oprogramowaniem Testo,
wykonuje sekwencję komunikacyjną, pobiera pełną serię pomiarów bezpośrednio z rejestratora,
drukuje je w konsoli i generuje profesjonalny wykres temperatury w czasie.
"""

import ctypes
import os
import sys
import time
import json
import argparse
from datetime import datetime, timedelta, timezone


def get_polish_local_time(utc_dt: datetime) -> datetime:
    """Konwertuje UTC na czas lokalny w Polsce (CET/CEST), uwzględniając DST."""
    year = utc_dt.year
    # Ostatnia niedziela marca
    last_sun_march = max(d for d in range(25, 32) if datetime(year, 3, d).weekday() == 6)
    dst_start = datetime(year, 3, last_sun_march, 1, 0, 0, tzinfo=timezone.utc)
    
    # Ostatnia niedziela października
    last_sun_oct = max(d for d in range(25, 32) if datetime(year, 10, d).weekday() == 6)
    dst_end = datetime(year, 10, last_sun_oct, 1, 0, 0, tzinfo=timezone.utc)
    
    utc_dt_tz = utc_dt.replace(tzinfo=timezone.utc) if utc_dt.tzinfo is None else utc_dt
    
    if dst_start <= utc_dt_tz < dst_end:
        return utc_dt_tz + timedelta(hours=2)
    else:
        return utc_dt_tz + timedelta(hours=1)


def load_config(config_path: str) -> dict:
    """Natywny, lekki parser plików YAML do wczytania konfiguracji bez zależności (PyYAML)."""
    # Domyślna bezpieczna konfiguracja
    config = {
        'system': {
            'driver_paths': [
                r'C:\Windows\System32\ftd2xx64.dll',
                r'C:\Program Files (x86)\Testo\Comfort Software Basic\USBDriver\FTDIBus\AMD64\ftd2xx64.dll',
                'ftd2xx64.dll'
            ],
            'log_level': 'INFO'
        },
        'serial': {
            'baud_rate': 57600,
            'data_bits': 8,
            'stop_bits': 1,
            'parity': 'NONE',
            'timeout_ms': 2000,
            'device_index': 0
        },
        'protocol': {
            'wait_ms_default': 50,
            'stream_idle_timeout_s': 2.0
        },
        'chart': {
            'generate_png': True,
            'theme': 'seaborn-v0_8-whitegrid',
            'background_color': '#1E1E2F',
            'line_color': '#00F2FE',
            'glow_color': '#4FACFE',
            'output_filename': 'testo_wykres.png',
            'auto_open': True
        }
    }
    
    if not os.path.exists(config_path):
        return config

    try:
        with open(config_path, 'r', encoding='utf-8') as f:
            current_section = None
            paths_reset = False
            for line in f:
                # Usunięcie komentarzy i spacji
                line = line.split('#')[0].strip()
                if not line:
                    continue
                
                # Wykrywanie sekcji głównej
                if line.endswith(':'):
                    current_section = line[:-1].strip()
                    continue
                
                # Wykrywanie elementów listy w sekcji system
                if line.startswith('-') and current_section == 'system':
                    path_val = line[1:].strip().strip('"').strip("'")
                    if not paths_reset:
                        config['system']['driver_paths'] = []
                        paths_reset = True
                    config['system']['driver_paths'].append(path_val)
                    continue
                
                if ':' in line:
                    key, val = line.split(':', 1)
                    key = key.strip()
                    val = val.strip().strip('"').strip("'")
                    
                    # Konwersja typów danych
                    if val.lower() == 'true':
                        val = True
                    elif val.lower() == 'false':
                        val = False
                    elif val.isdigit():
                        val = int(val)
                    else:
                        try:
                            val = float(val)
                        except ValueError:
                            pass
                    
                    if current_section and current_section in config:
                        config[current_section][key] = val
                    else:
                        config[key] = val
    except Exception as e:
        # Cichy powrót do domyślnych w przypadku problemów
        pass
    
    return config


class TestoD2XXReader:
    def __init__(self, config: dict):
        self.config = config
        self.dll = None
        self.handle = ctypes.c_void_p(0)
        self._load_dll()

    def _load_dll(self):
        """Ładuje oficjalną bibliotekę FTDI D2XX z systemu lub folderu Testo."""
        paths = self.config['system']['driver_paths']
        for path in paths:
            if os.path.exists(path) or path == 'ftd2xx64.dll':
                try:
                    self.dll = ctypes.windll.LoadLibrary(path)
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
        
        dev_idx = self.config['serial']['device_index']
        self.dll.FT_GetDeviceInfoDetail(
            dev_idx,
            ctypes.byref(flags),
            ctypes.byref(dev_type),
            ctypes.byref(dev_id),
            ctypes.byref(loc_id),
            serial,
            desc,
            ctypes.byref(temp_handle)
        )
        
        res = self.dll.FT_Open(dev_idx, ctypes.byref(self.handle))
        if res != 0:
            raise RuntimeError(f"Błąd otwierania urządzenia Testo USB (kod błędu FTDI: {res})")
            
        dev_desc = desc.value.decode('latin-1')
        return dev_desc

    def configure(self):
        """Konfiguruje parametry transmisji szeregowej FTDI."""
        baud = self.config['serial']['baud_rate']
        self.dll.FT_SetBaudRate(self.handle, ctypes.c_ulong(baud))
        
        # Data bits, stop bits, parity
        db = self.config['serial']['data_bits']
        self.dll.FT_SetDataCharacteristics(self.handle, ctypes.c_byte(db), ctypes.c_byte(0), ctypes.c_byte(0))
        
        # Timeouts
        t_ms = self.config['serial']['timeout_ms']
        self.dll.FT_SetTimeouts(self.handle, ctypes.c_ulong(t_ms), ctypes.c_ulong(t_ms))
        
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

    def send_cmd(self, cmd_bytes: bytes, wait_ms: int = 50) -> bytes:
        """Wysyła polecenie i czeka na odpowiedź."""
        self.write(cmd_bytes)
        time.sleep(wait_ms / 1000.0)
        return self.read_all()

    def download(self) -> bytes:
        """Pobiera pełen strumień danych (handshake + odczyt flash)."""
        cmd_delay = self.config['protocol']['wait_ms_default']
        
        # 1. Reset
        self.send_cmd(b'\xf0', wait_ms=cmd_delay)
        
        # 2. Inicjalizacja (HELLO)
        res_init = self.send_cmd(b'\xab\x01\x0d\x00\x00\x02', wait_ms=cmd_delay)
        if not res_init:
            raise RuntimeError("Brak odpowiedzi z rejestratora. Upewnij się, że urządzenie leży prawidłowo w kołysce.")
            
        # 3. Get Device Info (ab30)
        self.send_cmd(b'\xf0', wait_ms=cmd_delay)
        res_info = self.send_cmd(b'\xab\x30\x00\x02\x0b\x37', wait_ms=cmd_delay)
        
        # 4. Get Status (ab31)
        self.send_cmd(b'\xf0', wait_ms=cmd_delay)
        res_status = self.send_cmd(b'\xab\x31\x00\x42\x1b\x66', wait_ms=cmd_delay)
        
        # 5. Odczyt metadanych (ab33)
        self.send_cmd(b'\xf0', wait_ms=cmd_delay)
        res_meta = bytearray()
        meta_cmds = [
            b'\xab\x33\x01\x00\x20\x1c',
            b'\xab\x33\x01\x20\x20\x3c',
            b'\xab\x33\x01\x40\x20\x5c',
            b'\xab\x33\x01\x60\x20\x7c',
            b'\xab\x33\x01\x80\x20\x9c',
            b'\xab\x33\x01\xa0\x20\xbc',
            b'\xab\x33\x01\xc0\x20\xdc',
            b'\xab\x33\x01\xe0\x20\xfc'
        ]
        for cmd in meta_cmds:
            res_meta.extend(self.send_cmd(cmd, wait_ms=cmd_delay))
            
        # 6. Wyzwolenie strumieniowania pomiarów (START_DUMP)
        self.send_cmd(b'\xf0', wait_ms=cmd_delay)
        self.write(b'\xab\x01\x09\x00\x00\x06')
        
        # Czytanie strumienia
        stream = bytearray()
        idle_start = time.time()
        idle_timeout = self.config['protocol']['stream_idle_timeout_s']
        while time.time() - idle_start < idle_timeout:
            rx = self.read_all()
            if rx:
                stream.extend(rx)
                idle_start = time.time()
            time.sleep(0.05)
            
        self.send_cmd(b'\xf0', wait_ms=cmd_delay)
        
        return bytes(res_info + res_status + bytes(res_meta) + stream)

    def close(self):
        if self.handle and self.handle.value:
            self.dll.FT_Close(self.handle)


def generate_temperature_chart(measurements, start_date_utc, interval, serial, config):
    """Generuje i zapisuje estetyczny, nowoczesny wykres temperatury w czasie."""
    if not config['chart']['generate_png']:
        return
        
    try:
        import matplotlib.pyplot as plt
        import matplotlib.dates as mdates
        
        # Obliczenie osi czasu (czas lokalny w Polsce)
        times = []
        for i in range(len(measurements)):
            meas_time_utc = start_date_utc + timedelta(minutes=interval * i)
            meas_time_local = get_polish_local_time(meas_time_utc)
            times.append(meas_time_local)
            
        # Konfiguracja stylu wykresu (ciemny, nowoczesny motyw)
        plt.style.use(config['chart']['theme'])
        fig, ax = plt.subplots(figsize=(11, 6), dpi=150)
        
        bg_col = config['chart']['background_color']
        line_col = config['chart']['line_color']
        glow_col = config['chart']['glow_color']
        
        fig.patch.set_facecolor(bg_col)
        ax.set_facecolor(bg_col)
        
        # Rysowanie linii
        ax.plot(times, measurements, color=line_col, linewidth=2.5, marker='o', 
                markersize=4, markerfacecolor=glow_col, markeredgecolor=line_col, 
                label='Temperatura (°C)')
        
        # Gradientowe wypełnienie pod wykresem
        ax.fill_between(times, measurements, color=line_col, alpha=0.12)
        
        ax.set_title(f'Wykres Temperatury - Testo 174T (S/N {serial})', 
                     fontsize=14, fontweight='bold', color='#FFFFFF', pad=15)
        ax.set_xlabel('Data i godzina (Czas Lokalny)', fontsize=11, color='#A0A0C0', labelpad=10)
        ax.set_ylabel('Temperatura (°C)', fontsize=11, color='#A0A0C0', labelpad=10)
        
        ax.xaxis.set_major_formatter(mdates.DateFormatter('%Y-%m-%d\n%H:%M'))
        ax.xaxis.set_major_locator(mdates.AutoDateLocator())
        
        ax.grid(True, color='#2D2D44', linestyle='--', linewidth=0.8)
        ax.tick_params(colors='#A0A0C0', labelsize=9)
        
        for spine in ax.spines.values():
            spine.set_color('#2D2D44')
            
        output_filename = config['chart']['output_filename']
        plt.tight_layout()
        plt.savefig(output_filename, facecolor=bg_col, edgecolor='none')
        plt.close()
        
        if config['chart']['auto_open']:
            try:
                os.startfile(output_filename)
            except Exception:
                pass
            
    except Exception as e:
        # Cichy fail dla wykresów matplotlib
        pass


def print_ascii_chart(measurements):
    """Drukuje prosty, czytelny wykres ASCII temperatury bezpośrednio w konsoli."""
    if not measurements:
        return
        
    min_t = min(measurements)
    max_t = max(measurements)
    span = max_t - min_t
    if span == 0:
        span = 1.0
        
    height = 10
    width = len(measurements)
    
    print("\n" + "="*60)
    print("          TEKSTOWY WYKRES TEMPERATURY W KONSOLI (ASCII)")
    print("="*60)
    
    for r in range(height, -1, -1):
        val = min_t + (r / height) * span
        line = f"  {val:5.1f} °C | "
        
        for temp in measurements:
            pos = int((temp - min_t) / span * height)
            if pos == r:
                line += "*"
            else:
                line += " "
        print(line)
        
    print("          +" + "-" * width)
    print("          ^" + " " * (width - 2) + "^")
    print("        Start" + " " * (width - 10) + "Koniec")
    print("="*60)


def parse_results_json(raw_stream: bytes) -> str:
    """Dekoduje surowy strumień pomiarów i zwraca go jako czysty obiekt JSON."""
    try:
        # 1. Dekodowanie ab30
        pos_30 = raw_stream.find(b'\xab\x30')
        if pos_30 == -1:
            return json.dumps({"status": "ERROR", "message": "Nie znaleziono ramki ab30."})
            
        length_30 = raw_stream[pos_30+4]
        payload_30 = raw_stream[pos_30+5:pos_30+5+length_30]
        
        rok_prod = (payload_30[0] << 8) | payload_30[1]
        serial = int.from_bytes(payload_30[4:8], 'big')
        m_date = f"{rok_prod:04d}-{payload_30[2]:02d}-{payload_30[3]:02d}"
        
        # 2. Dekodowanie ab31
        pos_31 = raw_stream.find(b'\xab\x31')
        if pos_31 == -1:
            return json.dumps({"status": "ERROR", "message": "Nie znaleziono ramki ab31."})
            
        length_31 = raw_stream[pos_31+4]
        payload_31 = raw_stream[pos_31+5:pos_31+5+length_31]
        
        interval = (payload_31[2] << 8) | payload_31[3]
        count = (payload_31[4] << 8) | payload_31[5]
        start_delay = int.from_bytes(payload_31[6:9], 'big')
        
        rok_prog = (payload_31[9] << 8) | payload_31[10]
        prog_date_utc = datetime(
            year=rok_prog,
            month=payload_31[11],
            day=payload_31[12],
            hour=payload_31[13],
            minute=payload_31[14],
            second=payload_31[15],
            tzinfo=timezone.utc
        )
        
        start_date_utc = prog_date_utc + timedelta(minutes=start_delay)
        start_date_local = get_polish_local_time(start_date_utc)
        battery = payload_31[20]
        
        # 3. Zbieranie temperatur (ab32)
        data_blocks = {}
        offset = 0
        while True:
            pos_32 = raw_stream.find(b'\xab\x32', offset)
            if pos_32 == -1:
                break
            offset = pos_32 + 2
            if pos_32 + 38 > len(raw_stream):
                continue
            
            length_32 = raw_stream[pos_32+4]
            if length_32 != 0x20:
                continue
                
            addr = (raw_stream[pos_32+2] << 8) | raw_stream[pos_32+3]
            if addr >= 0x0200:
                data_blocks[addr] = raw_stream[pos_32+5:pos_32+5+32]
                
        flash = b''
        for addr in sorted(data_blocks.keys()):
            flash += data_blocks[addr]
            
        measurements = []
        for i in range(count):
            idx = i * 2
            if idx + 2 > len(flash):
                break
            temp_raw = int.from_bytes(flash[idx:idx+2], 'big', signed=True)
            if temp_raw == -32752:
                break
            measurements.append(temp_raw / 10.0)

        # Generowanie punktów w formacie JSON
        json_points = []
        for i, temp in enumerate(measurements):
            meas_time_utc = start_date_utc + timedelta(minutes=interval * i)
            meas_time_local = get_polish_local_time(meas_time_utc)
            json_points.append({
                "index": i + 1,
                "timestampLocal": meas_time_local.isoformat(),
                "valueCelsius": temp
            })

        output = {
            "status": "SUCCESS",
            "device": {
                "model": "Testo 174T",
                "serialNumber": str(serial),
                "manufacturingDate": m_date
            },
            "session": {
                "batteryLevelPercent": battery,
                "intervalMinutes": interval,
                "measurementsCount": len(measurements),
                "programmingTimeUtc": prog_date_utc.isoformat(),
                "startDelayMinutes": start_delay,
                "firstMeasurementTimeUtc": start_date_utc.isoformat(),
                "firstMeasurementTimeLocal": start_date_local.isoformat()
            },
            "measurements": json_points
        }
        return json.dumps(output, indent=2)
    except Exception as e:
        return json.dumps({"status": "ERROR", "message": f"Błąd parsowania binarnego: {str(e)}"})


def parse_and_print_results(raw_stream: bytes, config: dict):
    """Wersja standardowa - drukowanie tekstowe w konsoli i generowanie wykresu Matplotlib."""
    pos_30 = raw_stream.find(b'\xab\x30')
    if pos_30 == -1:
        print("Błąd: Nie znaleziono ramki ab30.")
        return
        
    length_30 = raw_stream[pos_30+4]
    payload_30 = raw_stream[pos_30+5:pos_30+5+length_30]
    
    rok_prod = (payload_30[0] << 8) | payload_30[1]
    serial = int.from_bytes(payload_30[4:8], 'big')
    
    print("\n" + "="*60)
    print("                WYNIKI IMPORTU Z URZĄDZENIA")
    print("="*60)
    print(f"Rejestrator:")
    print(f"  Model:            Testo 174T")
    print(f"  Numer Seryjny:    {serial}")
    print(f"  Data Produkcji:   {rok_prod:04d}-{payload_30[2]:02d}-{payload_30[3]:02d}")
    
    pos_31 = raw_stream.find(b'\xab\x31')
    if pos_31 == -1:
        print("Błąd: Nie znaleziono ramki ab31.")
        return
        
    length_31 = raw_stream[pos_31+4]
    payload_31 = raw_stream[pos_31+5:pos_31+5+length_31]
    
    interval = (payload_31[2] << 8) | payload_31[3]
    count = (payload_31[4] << 8) | payload_31[5]
    start_delay = int.from_bytes(payload_31[6:9], 'big')
    
    rok_prog = (payload_31[9] << 8) | payload_31[10]
    prog_date_utc = datetime(
        year=rok_prog, month=payload_31[11], day=payload_31[12],
        hour=payload_31[13], minute=payload_31[14], second=payload_31[15],
        tzinfo=timezone.utc
    )
    
    start_date_utc = prog_date_utc + timedelta(minutes=start_delay)
    start_date_local = get_polish_local_time(start_date_utc)
    battery = payload_31[20]
    
    print(f"\nParametry Sesji:")
    print(f"  Stan Baterii:     {battery}%")
    print(f"  Interwał Zapisu:  {interval} minut")
    print(f"  Liczba Pomiarów:  {count}")
    
    data_blocks = {}
    offset = 0
    while True:
        pos_32 = raw_stream.find(b'\xab\x32', offset)
        if pos_32 == -1:
            break
        offset = pos_32 + 2
        if pos_32 + 38 > len(raw_stream):
            continue
        
        length_32 = raw_stream[pos_32+4]
        if length_32 != 0x20:
            continue
            
        addr = (raw_stream[pos_32+2] << 8) | raw_stream[pos_32+3]
        if addr >= 0x0200:
            data_blocks[addr] = raw_stream[pos_32+5:pos_32+5+32]
            
    flash = b''
    for addr in sorted(data_blocks.keys()):
        flash += data_blocks[addr]
        
    measurements = []
    for i in range(count):
        idx = i * 2
        if idx + 2 > len(flash):
            break
        temp_raw = int.from_bytes(flash[idx:idx+2], 'big', signed=True)
        if temp_raw == -32752:
            break
        measurements.append(temp_raw / 10.0)
        
    print(f"\nLista Zarejestrowanych Pomiarów ({len(measurements)} odczytów):")
    print("-" * 60)
    for i, temp in enumerate(measurements):
        meas_time_utc = start_date_utc + timedelta(minutes=interval * i)
        meas_time_local = get_polish_local_time(meas_time_utc)
        print(f"  {i+1:4d}  |        {meas_time_local.strftime('%Y-%m-%d %H:%M:%S')}        |     {temp:5.1f} °C")
        
    print("-" * 60)
    print_ascii_chart(measurements)
    generate_temperature_chart(measurements, start_date_utc, interval, serial, config)


def main():
    parser = argparse.ArgumentParser(description="Program do odczytu USB Testo 174T.")
    parser.add_argument('--json', action='store_true', help="Zwróć wyniki w surowym formacie JSON (tryb cichy)")
    args = parser.parse_args()

    # Wczytanie konfiguracji YAML z katalogu pliku (testo/)
    script_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(script_dir, 'testo_config.yml')
    config = load_config(config_path)

    if not args.json:
        print("="*60)
        print("    BEZPOŚREDNI ODCZYT USB TESTO 174T (STEROWNIK D2XX)")
        print("="*60)
    
    reader = None
    try:
        reader = TestoD2XXReader(config)
        reader.open_device()
        reader.configure()
        raw_data = reader.download()
        
        if args.json:
            # Wypisz tylko JSON na standardowe wyjście w trybie cichym
            sys.stdout.write(parse_results_json(raw_data))
        else:
            parse_and_print_results(raw_data, config)
            
    except Exception as e:
        if args.json:
            sys.stdout.write(json.dumps({"status": "ERROR", "message": str(e)}))
        else:
            print(f"\nWystąpił błąd podczas odczytu: {e}")
    finally:
        if reader:
            try:
                reader.close()
            except Exception:
                pass


if __name__ == "__main__":
    main()
