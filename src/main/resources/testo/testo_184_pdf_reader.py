#!/usr/bin/env python3
"""
read_testo_pdf.py
=================
Skrypt do odczytu i dekodowania surowych pomiarów oraz metadanych z raportów PDF
generowanych przez rejestratory Testo 184 (np. T3).

Skrypt parsuje strukturę PDF, wyciąga tekst raportu oraz dekoduje prywatny strumień
binarny (Object 13 / PieceInfo) zawierający precyzyjne pomiary zakodowane w formacie
stałoprzecinkowym Q16.16 (z offsetem 100 i skalowaniem 100).

Wzór dekodowania temperatury:
  T = (V_uint32_LE / 6,553,600) - 100.0

Autor: Antigravity AI
Data: 2026-05-22
"""

import os
import re
import sys
import zlib
import struct
import json
import argparse
from datetime import datetime, timedelta

def get_polish_local_time(utc_dt: datetime) -> datetime:
    """Konwertuje UTC na czas lokalny w Polsce (CET/CEST) uwzględniając DST."""
    year = utc_dt.year
    # Ostatnia niedziela marca (start CEST = UTC+2)
    last_sun_march = max(d for d in range(25, 32) if datetime(year, 3, d).weekday() == 6)
    dst_start = datetime(year, 3, last_sun_march, 1, 0, 0)
    
    # Ostatnia niedziela października (koniec CEST -> CET = UTC+1)
    last_sun_oct = max(d for d in range(25, 32) if datetime(year, 10, d).weekday() == 6)
    dst_end = datetime(year, 10, last_sun_oct, 1, 0, 0)
    
    if dst_start <= utc_dt < dst_end:
        return utc_dt + timedelta(hours=2)
    else:
        return utc_dt + timedelta(hours=1)

def extract_pdf_structure(pdf_bytes):
    """
    Parsuje surowe bajty PDF w poszukiwaniu obiektów tekstowych i prywatnego strumienia Testo.
    Zwraca słownik z surowym tekstem i bajtami strumienia prywatnego.
    """
    results = {
        "text": "",
        "private_stream": None,
        "private_obj_id": None
    }
    
    # 1. Wyciągnięcie tekstu ze wszystkich strumieni zawartości (Content Streams)
    # Szukamy obiektów z /Contents lub po prostu wszystkich strumieni tekstowych
    content_matches = re.findall(rb'(\d+)\s+\d+\s+obj.*?stream\r?\n(.*?)\r?\nendstream', pdf_bytes, re.DOTALL)
    
    full_text_parts = []
    for obj_id, stream_data in content_matches:
        # Próbujemy dekompresować jeśli strumień jest skompresowany
        decompressed = None
        # Znajdź słownik obiektu przed słowem kluczowym stream
        obj_start_pos = pdf_bytes.find(obj_id + b' 0 obj')
        if obj_start_pos != -1:
            dict_part = pdf_bytes[obj_start_pos:pdf_bytes.find(b'stream', obj_start_pos)]
            if b'/FlateDecode' in dict_part:
                try:
                    decompressed = zlib.decompress(stream_data)
                except Exception:
                    pass
            else:
                decompressed = stream_data
                
        if decompressed:
            # Wyciągamy teksty w nawiasach (...) Tj lub TJ
            strings = re.findall(rb'\((.*?)\)', decompressed)
            for s in strings:
                try:
                    decoded = s.decode('latin-1')
                    # Zamiana oktalnych kodów znaków PDF (np. \260 dla stopnia Celcjusza)
                    decoded = re.sub(r'\\(\d{3})', lambda m: chr(int(m.group(1), 8)), decoded)
                    # Zamiana innych specjalnych znaków ucieczki w PDF
                    decoded = decoded.replace(r'\/', '/').replace(r'\(', '(').replace(r'\)', ')')
                    full_text_parts.append(decoded)
                except Exception:
                    pass
                    
    results["text"] = "\n".join(full_text_parts)
    
    # 2. Wykrywanie ID obiektu z surowymi danymi binarnymi poprzez Catalog -> PieceInfo
    # W strukturze Testo: /PieceInfo << /Testo-Logger << /Private << /Data XX 0 R >> >> >> lub /Private XX 0 R
    private_match = re.search(rb'/Private\s+<<\s*/Data\s+(\d+)\s+\d+\s+R', pdf_bytes)
    if not private_match:
        private_match = re.search(rb'/Data\s+(\d+)\s+\d+\s+R', pdf_bytes)
    if not private_match:
        private_match = re.search(rb'/Private\s+(\d+)\s+\d+\s+R', pdf_bytes)
        
    if private_match:
        private_obj_id = int(private_match.group(1))
        results["private_obj_id"] = private_obj_id
        
        # Szukamy strumienia dla tego konkretnego obiektu
        pattern = re.compile(rf'{private_obj_id}\s+0\s+obj.*?stream\r?\n(.*?)\r?\nendstream'.encode('utf-8'), re.DOTALL)
        stream_match = pattern.search(pdf_bytes)
        if stream_match:
            raw_stream = stream_match.group(1)
            # Sprawdzenie kompresji
            obj_pos = pdf_bytes.find(f'{private_obj_id} 0 obj'.encode('utf-8'))
            dict_part = pdf_bytes[obj_pos:pdf_bytes.find(b'stream', obj_pos)]
            if b'/FlateDecode' in dict_part:
                try:
                    results["private_stream"] = zlib.decompress(raw_stream)
                except Exception as e:
                    print(f"Ostrzeżenie: Błąd dekompresji strumienia prywatnego: {e}", file=sys.stderr)
            else:
                results["private_stream"] = raw_stream
                
    return results

def parse_metadata_from_text(text):
    """Parsuje metadane nagłówkowe z wyciągniętego tekstu PDF za pomocą wyrażeń regularnych."""
    meta = {}
    
    # Model
    model_match = re.search(r'created by\s+(testo\s+\d+\s+[A-Za-z0-9]+)', text, re.IGNORECASE)
    if not model_match:
        model_match = re.search(r'Type\s+(testo\s+\d+\s+[A-Za-z0-9]+)', text, re.IGNORECASE)
    meta["model"] = model_match.group(1).strip() if model_match else "Testo 184"
    
    # Numer seryjny
    sn_match = re.search(r'SN\.:\s*(\d+)', text)
    meta["serial_number"] = sn_match.group(1).strip() if sn_match else "Nieznany"
    
    # Wersja firmware
    fw_match = re.search(r'(V\d+\.\d+)', text)
    meta["firmware"] = fw_match.group(1).strip() if fw_match else "Nieznana"
    
    # Interwał (jednostki: min, sek/sec/s — raporty PL/EN, h, d)
    int_match = re.search(r'Meas\.\s+Interval\s*\n\s*(\d+)\s*(min|sek|sec|s|h|d)', text, re.IGNORECASE)
    if not int_match:
        int_match = re.search(r'Interval\s*\n\s*(\d+)\s*(min|sek|sec|s|h|d)', text, re.IGNORECASE)
    meta["interval_value"] = int(int_match.group(1)) if int_match else 1
    meta["interval_unit"] = int_match.group(2).strip().lower() if int_match else "min"
    
    # Liczba wartości
    count_match = re.search(r'No\.\s+of\s+Values\s*\n\s*(\d+)', text, re.IGNORECASE)
    meta["count"] = int(count_match.group(1)) if count_match else 0
    
    # Daty startu/stopu
    start_match = re.search(r'Start Date\\057Time\s*\n\s*([\d\.]+)\s+([\d:]+)', text)
    if not start_match:
        start_match = re.search(r'Start Date/Time\s*\n\s*([\d\.]+)\s+([\d:]+)', text)
    meta["start_date_str"] = f"{start_match.group(1)} {start_match.group(2)}" if start_match else None
    
    stop_match = re.search(r'Stop Date\\057Time\s*\n\s*([\d\.]+)\s+([\d:]+)', text)
    if not stop_match:
        stop_match = re.search(r'Stop Date/Time\s*\n\s*([\d\.]+)\s+([\d:]+)', text)
    meta["stop_date_str"] = f"{stop_match.group(1)} {stop_match.group(2)}" if stop_match else None
    
    def safe_float(val):
        if not val:
            return None
        try:
            return float(val.replace(',', '.').strip())
        except ValueError:
            return None

    # Statystyki podsumowania z tekstu
    # 1. Próba dopasowania całego bloku statystyk (najbardziej niezawodna metoda dla tabeli statystyk Testo)
    stats_pattern = r'Maximum(?:/|\\057)Time\s*Minimum(?:/|\\057)Time\s*Average\s*MKT\s*Temperature\s*([\d\.,-]+|---)[°\xb0\?]?C\s*(?:[\d\.]+)?\s*(?:[\d:]+)?\s*([\d\.,-]+|---)[°\xb0\?]?C\s*(?:[\d\.]+)?\s*(?:[\d:]+)?\s*([\d\.,-]+|---)[°\xb0\?]?C\s*([\d\.,-]+|---)[°\xb0\?]?C'
    stats_match = re.search(stats_pattern, text, re.IGNORECASE)
    if stats_match:
        meta["text_max_temp"] = safe_float(stats_match.group(1))
        meta["text_min_temp"] = safe_float(stats_match.group(2))
        meta["text_avg_temp"] = safe_float(stats_match.group(3))
        meta["text_mkt_temp"] = safe_float(stats_match.group(4))
    else:
        # Fallback na indywidualne dopasowania
        max_match = re.search(r'Maximum\\057Time.*?([\d\.,-]+)[°\xb0\?]?C', text, re.DOTALL | re.IGNORECASE)
        if not max_match:
            max_match = re.search(r'Maximum/Time.*?([\d\.,-]+)[°\xb0\?]?C', text, re.DOTALL | re.IGNORECASE)
        meta["text_max_temp"] = safe_float(max_match.group(1)) if max_match else None
        
        min_match = re.search(r'Minimum\\057Time.*?([\d\.,-]+)[°\xb0\?]?C', text, re.DOTALL | re.IGNORECASE)
        if not min_match:
            min_match = re.search(r'Minimum/Time.*?([\d\.,-]+)[°\xb0\?]?C', text, re.DOTALL | re.IGNORECASE)
        meta["text_min_temp"] = safe_float(min_match.group(1)) if min_match else None
        
        avg_match = re.search(r'Average\s*\n\s*([\d\.,-]+)[°\xb0\?]?C', text, re.IGNORECASE)
        meta["text_avg_temp"] = safe_float(avg_match.group(1)) if avg_match else None
        
        mkt_match = re.search(r'MKT\s*\n\s*([\d\.,-]+)[°\xb0\?]?C', text, re.IGNORECASE)
        meta["text_mkt_temp"] = safe_float(mkt_match.group(1)) if mkt_match else None
    
    # Suma kontrolna z tekstu (SHA-256 / MD5 urządzenia)
    cs_match = re.search(r'Check\s+Sum\s*\n\s*([0-9A-Fa-f]{32})', text)
    meta["checksum"] = cs_match.group(1).upper() if cs_match else "Brak"
    
    return meta

# Koniec bufora pomiarowego w strumieniu prywatnym — wartość zreverse-engineerowana
# z referencyjnego raportu (SN 44373263, 11 pomiarów). Struktura NIE została
# potwierdzona dla dłuższych sesji; stąd twardy limit i jawny błąd poniżej.
MEASUREMENT_BUFFER_END_OFFSET = 1476
MAX_SUPPORTED_COUNT = MEASUREMENT_BUFFER_END_OFFSET // 4  # 369 pomiarów


def decode_measurements(private_stream, count):
    """
    Dekoduje precyzyjne pomiary z bufora binarnego streamu.
    Każdy pomiar to uint32 Little-Endian w formacie Q16.16:
    T = (V / 6553600) - 100.0

    Rzuca ValueError zamiast zwracać częściowe/błędne dane — dla systemu GxP
    ciche obcięcie pomiarów jest gorsze niż odmowa importu.
    """
    if not private_stream:
        raise ValueError("Brak strumienia prywatnego w pliku PDF (PieceInfo/Private).")
    if len(private_stream) < MEASUREMENT_BUFFER_END_OFFSET:
        raise ValueError(
            f"Strumień prywatny za krótki ({len(private_stream)} B < "
            f"{MEASUREMENT_BUFFER_END_OFFSET} B) — nieznany wariant pliku PDF.")
    if count <= 0:
        raise ValueError("Nie odczytano liczby pomiarów (No. of Values) z tekstu raportu.")
    if count > MAX_SUPPORTED_COUNT:
        raise ValueError(
            f"Raport zawiera {count} pomiarów — dekoder obsługuje maksymalnie "
            f"{MAX_SUPPORTED_COUNT} (struktura strumienia dla dłuższych sesji "
            f"nie została zweryfikowana). Import przerwany, aby nie zwrócić "
            f"błędnych danych.")

    measurements = []
    start_offset = MEASUREMENT_BUFFER_END_OFFSET - (count * 4)
    for i in range(count):
        offset = start_offset + (i * 4)
        val = struct.unpack('<I', private_stream[offset:offset + 4])[0]
        temp = (val / 6553600.0) - 100.0
        measurements.append(temp)

    return measurements

def main():
    parser = argparse.ArgumentParser(
        description="Dekoduje dane pomiarowe i metadane bezpośrednio z raportu PDF Testo 184."
    )
    parser.add_argument("pdf_path", help="Ścieżka do pliku PDF z raportem Testo 184")
    parser.add_argument("--json", action="store_true", help="Wypisz wynik w surowym formacie JSON")
    parser.add_argument("--csv", help="Ścieżka zapisu odczytanych pomiarów do pliku CSV")
    args = parser.parse_args()
    
    if not os.path.exists(args.pdf_path):
        print(f"Błąd: Plik '{args.pdf_path}' nie istnieje.", file=sys.stderr)
        sys.exit(1)
        
    try:
        with open(args.pdf_path, 'rb') as f:
            pdf_bytes = f.read()
    except Exception as e:
        print(f"Błąd odczytu pliku: {e}", file=sys.stderr)
        sys.exit(1)
        
    # Wyciągnięcie struktury
    pdf_data = extract_pdf_structure(pdf_bytes)
    
    # Parsowanie metadanych z tekstu
    meta = parse_metadata_from_text(pdf_data["text"])
    
    # Dekodowanie surowych pomiarów
    count = meta.get("count", 0)
    try:
        measurements = decode_measurements(pdf_data["private_stream"], count)
    except ValueError as e:
        print(f"Błąd dekodowania pomiarów: {e}", file=sys.stderr)
        sys.exit(1)
    
    # Obliczanie osi czasu pomiarów
    points = []
    if meta.get("start_date_str") and measurements:
        try:
            # Format daty w PDF: 22.05.2026 15:16
            start_dt = datetime.strptime(meta["start_date_str"], "%d.%m.%Y %H:%M")
            interval_mins = meta.get("interval_value", 1)
            # Jeśli jednostka interwału to sekundy lub godziny, przelicz odpowiednio
            if meta.get("interval_unit") in ("sek", "sec", "s"):
                delta_type = "seconds"
            elif meta.get("interval_unit") == "h":
                delta_type = "hours"
            else:
                delta_type = "minutes"
                
            for i, temp in enumerate(measurements):
                kwargs = {delta_type: interval_mins * i}
                meas_time = start_dt + timedelta(**kwargs)
                points.append({
                    "index": i + 1,
                    "timestamp": meas_time.strftime("%Y-%m-%d %H:%M:%S"),
                    "temperature_celsius": round(temp, 4)
                })
        except Exception as e:
            print(f"Ostrzeżenie przy generowaniu osi czasu: {e}", file=sys.stderr)
            
    # Zabezpieczenie na wypadek braku wygenerowanej osi czasu
    if not points and measurements:
        for i, temp in enumerate(measurements):
            points.append({
                "index": i + 1,
                "timestamp": f"Punkt {i+1}",
                "temperature_celsius": round(temp, 4)
            })
            
    # Obliczanie statystyk z surowych danych do weryfikacji
    stats = {}
    if measurements:
        stats["max"] = round(max(measurements), 2)
        stats["min"] = round(min(measurements), 2)
        stats["avg"] = round(sum(measurements) / len(measurements), 2)
        
    # Wynik zbiorczy
    output_data = {
        "device": {
            "model": meta.get("model"),
            "serial_number": meta.get("serial_number"),
            "firmware_version": meta.get("firmware"),
            "checksum": meta.get("checksum")
        },
        "config": {
            "interval": f"{meta.get('interval_value')} {meta.get('interval_unit')}",
            "start_time": meta.get("start_date_str"),
            "stop_time": meta.get("stop_date_str"),
            "expected_count": count,
            "decoded_count": len(measurements)
        },
        "statistics": {
            "report_limits": {
                "max": meta.get("text_max_temp"),
                "min": meta.get("text_min_temp"),
                "avg": meta.get("text_avg_temp"),
                "mkt": meta.get("text_mkt_temp")
            },
            "decoded_limits": stats
        },
        "measurements": points
    }
    
    # Zapis do pliku CSV
    if args.csv and points:
        try:
            import csv
            with open(args.csv, 'w', newline='', encoding='utf-8') as csvfile:
                writer = csv.writer(csvfile, delimiter=';')
                writer.writerow(["Lp.", "Czas Lokalny", "Temperatura (C)"])
                for pt in points:
                    writer.writerow([pt["index"], pt["timestamp"], str(pt["temperature_celsius"]).replace('.', ',')])
            if not args.json:
                print(f"Pomyślnie wyeksportowano {len(points)} pomiarów do pliku CSV: {args.csv}\n")
        except Exception as e:
            print(f"Błąd zapisu pliku CSV: {e}", file=sys.stderr)
            
    # Wyświetlanie wyników
    if args.json:
        print(json.dumps(output_data, indent=2, ensure_ascii=False))
    else:
        print("=" * 65)
        print(f"       RAPORT ODCZYTU REJESTRATORA TESTO: {output_data['device']['model']}")
        print("=" * 65)
        print(f"Numer Seryjny:    {output_data['device']['serial_number']}")
        print(f"Wersja Firmware:  {output_data['device']['firmware_version']}")
        print(f"Suma Kontrolna:   {output_data['device']['checksum']}")
        print("-" * 65)
        print(f"Interwał Zapisu:  {output_data['config']['interval']}")
        print(f"Czas Startu:      {output_data['config']['start_time']}")
        print(f"Czas Stopu:       {output_data['config']['stop_time']}")
        print(f"Liczba próbek:    {output_data['config']['decoded_count']} / {output_data['config']['expected_count']}")
        print("-" * 65)
        print("Statystyki Temperatury:")
        print(f"  Maksimum:       {output_data['statistics']['decoded_limits'].get('max')} °C (W raporcie: {output_data['statistics']['report_limits'].get('max')} °C)")
        print(f"  Minimum:        {output_data['statistics']['decoded_limits'].get('min')} °C (W raporcie: {output_data['statistics']['report_limits'].get('min')} °C)")
        print(f"  Średnia:        {output_data['statistics']['decoded_limits'].get('avg')} °C (W raporcie: {output_data['statistics']['report_limits'].get('avg')} °C)")
        print(f"  MKT (skalowane): (W raporcie: {output_data['statistics']['report_limits'].get('mkt')} °C)")
        print("-" * 65)
        
        # Wyświetlamy pierwsze 5 i ostatnie 5 pomiarów w celach czytelności
        print("Próbka pomiarów (Wszystkich: {}):".format(len(points)))
        if len(points) <= 12:
            for pt in points:
                print(f"  {pt['index']:4d} | {pt['timestamp']} | {pt['temperature_celsius']:7.2f} °C")
        else:
            for pt in points[:5]:
                print(f"  {pt['index']:4d} | {pt['timestamp']} | {pt['temperature_celsius']:7.2f} °C")
            print("  .... | ...                 | ...")
            for pt in points[-5:]:
                print(f"  {pt['index']:4d} | {pt['timestamp']} | {pt['temperature_celsius']:7.2f} °C")
        print("=" * 65)

if __name__ == "__main__":
    main()
