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
        measurements = testo_184_pdf_reader.decode_measurements(pdf_data["private_stream"], count)
        
        if not measurements:
            print(json.dumps({
                "status": "ERROR",
                "message": "Nie odnaleziono punktów pomiarowych w strumieniu prywatnym pliku PDF."
            }, ensure_ascii=False))
            sys.exit(0)
            
        # Parse intervals and timestamps
        interval_mins = meta.get("interval_value", 1)
        if meta.get("interval_unit") == "sek":
            delta_mins = interval_mins / 60.0
        elif meta.get("interval_unit") == "h":
            delta_mins = interval_mins * 60.0
        else:
            delta_mins = interval_mins
            
        start_date_str = meta.get("start_date_str")
        first_meas_local_str = ""
        first_meas_utc_str = ""
        
        points = []
        if start_date_str:
            # Format in PDF text: "22.05.2026 15:16" (usually local time format of the generator PC)
            # But the private stream is UTC. Let's calculate local time based on start_date_str
            try:
                start_dt = datetime.strptime(start_date_str, "%d.%m.%Y %H:%M")
                first_meas_local_str = start_dt.strftime("%Y-%m-%d %H:%M:%S")
                
                # Assume UTC time of first measurement is stored at offset 0 of private stream (7 BCD bytes)
                private_stream = pdf_data["private_stream"]
                if private_stream and len(private_stream) >= 7:
                    year = private_stream[0] + (private_stream[1] << 8)
                    month = private_stream[2]
                    day = private_stream[3]
                    hour = private_stream[4]
                    minute = private_stream[5]
                    second = private_stream[6]
                    utc_start_dt = datetime(year, month, day, hour, minute, second)
                    first_meas_utc_str = utc_start_dt.strftime("%Y-%m-%d %H:%M:%S")
                else:
                    first_meas_utc_str = first_meas_local_str # fallback
                
                for i, temp in enumerate(measurements):
                    meas_time = start_dt + timedelta(minutes=delta_mins * i)
                    points.append({
                        "index": i + 1,
                        "timestampLocal": meas_time.strftime("%Y-%m-%dT%H:%M:%S"),
                        "valueCelsius": round(temp, 4)
                    })
            except Exception as time_ex:
                # Fallback if time parsing fails
                for i, temp in enumerate(measurements):
                    points.append({
                        "index": i + 1,
                        "timestampLocal": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
                        "valueCelsius": round(temp, 4)
                    })
        else:
            # Absolute fallback
            for i, temp in enumerate(measurements):
                points.append({
                    "index": i + 1,
                    "timestampLocal": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
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
                "batteryLevelPercent": 100,
                "intervalMinutes": int(delta_mins),
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
