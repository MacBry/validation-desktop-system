#!/usr/bin/env python3
import os
import sys
import json
import argparse
from datetime import datetime, timedelta

# Add local path to import libraries
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

try:
    import testo_184_pdf_reader
except ImportError as e:
    print(json.dumps({
        "status": "ERROR",
        "message": f"Could not import PDF reader library: {e}"
    }, ensure_ascii=False))
    sys.exit(0)

def main():
    parser = argparse.ArgumentParser(description="Testo 184 PDF Reader Bridge")
    parser.add_argument("pdf_path", help="Path to Testo 184 measurement report PDF")
    args = parser.parse_args()
    
    if not os.path.exists(args.pdf_path):
        print(json.dumps({
            "status": "ERROR",
            "message": f"Plik '{args.pdf_path}' nie istnieje."
        }, ensure_ascii=False))
        sys.exit(0)
        
    try:
        with open(args.pdf_path, 'rb') as f:
            pdf_bytes = f.read()

        pdf_data = testo_184_pdf_reader.extract_pdf_structure(pdf_bytes)
        meta = testo_184_pdf_reader.parse_metadata_from_text(pdf_data["text"])
        count = meta.get("count", 0)

        try:
            measurements = testo_184_pdf_reader.decode_measurements(pdf_data["private_stream"], count)
        except ValueError as decode_error:
            print(json.dumps({
                "status": "ERROR",
                "message": f"Import przerwany: {decode_error}"
            }, ensure_ascii=False))
            sys.exit(0)

        # Parse intervals and timestamps
        interval_mins = meta.get("interval_value", 1)
        interval_unit = meta.get("interval_unit", "min")
        if interval_unit in ("sek", "sec", "s"):
            delta_mins = interval_mins / 60.0
        elif interval_unit == "h":
            delta_mins = interval_mins * 60.0
        else:
            delta_mins = interval_mins

        # Czas startu jest OBOWIĄZKOWY — bez niego nie fabrykujemy osi czasu.
        # Wcześniejszy fallback przypisywał wszystkim punktom datetime.now(),
        # co w systemie GxP oznaczałoby ciche sfałszowanie znaczników czasu.
        start_date_str = meta.get("start_date_str")
        if not start_date_str:
            print(json.dumps({
                "status": "ERROR",
                "message": "Nie odczytano czasu startu (Start Date/Time) z tekstu raportu PDF — "
                           "import przerwany, aby nie generować fikcyjnych znaczników czasu."
            }, ensure_ascii=False))
            sys.exit(0)

        try:
            start_dt = datetime.strptime(start_date_str, "%d.%m.%Y %H:%M")
        except ValueError:
            print(json.dumps({
                "status": "ERROR",
                "message": f"Nieoczekiwany format czasu startu w raporcie: '{start_date_str}' "
                           f"(oczekiwano DD.MM.RRRR HH:MM) — import przerwany."
            }, ensure_ascii=False))
            sys.exit(0)

        first_meas_local_str = start_dt.strftime("%Y-%m-%d %H:%M:%S")

        # Czas UTC pierwszego pomiaru: 7 bajtów BINARNYCH na początku strumienia
        # prywatnego (rok jako uint16 LE, potem miesiąc/dzień/godz/min/sek po bajcie).
        # Założenie zweryfikowane na pliku referencyjnym; przy niespójnej wartości
        # spadamy na czas lokalny z tekstu raportu (jawnie, bez zgadywania).
        first_meas_utc_str = first_meas_local_str
        private_stream = pdf_data["private_stream"]
        if private_stream and len(private_stream) >= 7:
            try:
                year = private_stream[0] + (private_stream[1] << 8)
                utc_start_dt = datetime(year, private_stream[2], private_stream[3],
                                        private_stream[4], private_stream[5], private_stream[6])
                first_meas_utc_str = utc_start_dt.strftime("%Y-%m-%d %H:%M:%S")
            except ValueError:
                pass  # nagłówek nie jest datą — zostaje czas lokalny z tekstu

        points = []
        for i, temp in enumerate(measurements):
            meas_time = start_dt + timedelta(minutes=delta_mins * i)
            points.append({
                "index": i + 1,
                "timestampLocal": meas_time.strftime("%Y-%m-%dT%H:%M:%S"),
                "valueCelsius": round(temp, 4)
            })

        output_data = {
            "status": "SUCCESS",
            "message": "Pomyślnie zaimportowano dane pomiarowe z pliku PDF.",
            "device": {
                "model": meta.get("model", "Testo 184"),
                "serialNumber": meta.get("serial_number", "Nieznany"),
                "manufacturingDate": "Nieznana"
            },
            "session": {
                # -1 = N/D: raport PDF nie zawiera stanu baterii
                # (konwencja spójna z TestoRevalidationService)
                "batteryLevelPercent": -1,
                "intervalMinutes": max(1, int(round(delta_mins))),
                "measurementsCount": len(measurements),
                "programmingTimeUtc": first_meas_utc_str,
                "startDelayMinutes": 0,
                "firstMeasurementTimeUtc": first_meas_utc_str,
                "firstMeasurementTimeLocal": first_meas_local_str
            },
            "measurements": points
        }

        print(json.dumps(output_data, ensure_ascii=False))
        sys.exit(0)
        
    except Exception as e:
        print(json.dumps({
            "status": "ERROR",
            "message": f"Krytyczny błąd podczas analizy pliku PDF: {str(e)}"
        }, ensure_ascii=False))
        sys.exit(0)

if __name__ == "__main__":
    main()
