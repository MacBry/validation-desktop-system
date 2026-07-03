import sys
import os
import difflib

# Add paths to sys.path
sys.path.insert(0, r"C:\Users\macie\Desktop\VCC Desktop APP\Analiza Programowania testo z84 T\Dotychczasowa Praca")
import testo_config as proto_config

sys.path.insert(0, r"C:\Users\macie\Desktop\VCC Desktop APP\validation-desktop\src\main\resources\testo")
import testo_184_config as app_config

def generate_proto_xml():
    # Mimicking the parameters passed by the working prototype when interval is 15 and count is 40
    # under Manual Start mode
    payload = {
        "drive": r"C:\Users\macie\.gemini\antigravity-ide\scratch",
        "interval": "15",
        "count": "40",
        "start_mode": "1", # Button / Manual
        "start_date": "2026-05-26",
        "start_time": "18:30",
        "stop_date": "2026-05-27", # 15 min * 40 = 600 min = 10 hours later
        "stop_time": "04:30",
        "configured_by": "Maciej Bryja",
        "comment": "Walidacja okresowa komory",
        "alarm_max_enabled": True,
        "alarm_max_limit": "8.0",
        "alarm_max_minutes": "60",
        "alarm_min_enabled": True,
        "alarm_min_limit": "2.0",
        "alarm_min_minutes": "60"
    }
    
    interval = int(payload["interval"])
    if interval < 10:
        interval = 10
    count = int(payload["count"])
    
    start_mode = 1 # Button
    stop_check1 = 0
    stop_check3 = 1
    
    # Obiekt konfiguracji
    config = proto_config.TestoConfig(
        device_model=3,
        meas_interval_minutes=interval,
        start_mode=start_mode,
        start_date=payload["start_date"],
        start_time=payload["start_time"],
        stop_check1=stop_check1,
        stop_check2=0,
        stop_check3=stop_check3,
        stop_date=payload["stop_date"],
        stop_time=payload["stop_time"],
        configuration_by=payload["configured_by"],
        user_comment=payload["comment"],
        alarm_temp_1=proto_config.AlarmLimit(
            enabled=1,
            direction=1,
            limit=float(payload["alarm_max_limit"]),
            alarm_type=1,
            allowed_minutes=int(payload["alarm_max_minutes"])
        ),
        alarm_temp_2=proto_config.AlarmLimit(
            enabled=1,
            direction=2,
            limit=float(payload["alarm_min_limit"]),
            alarm_type=1,
            allowed_minutes=int(payload["alarm_min_minutes"])
        ),
        alarm_temp_3=proto_config.AlarmLimit(enabled=0),
        alarm_temp_4=proto_config.AlarmLimit(enabled=0),
        alarm_mkt=proto_config.AlarmLimit(enabled=0),
        alarm_hum_1=proto_config.AlarmLimit(enabled=0),
        alarm_hum_2=proto_config.AlarmLimit(enabled=0),
        alarm_shock=proto_config.AlarmLimit(enabled=0)
    )
    
    path = r"C:\Users\macie\.gemini\antigravity-ide\scratch\proto_config.xml"
    proto_config.XdpIO.write(config, path, now=app_config.datetime(2026, 5, 26, 16, 30))
    return path

def generate_app_xml():
    # Mimicking current desktop app behavior when interval is 15, count is 40, upper limit is 8.0, lower is 2.0
    # operator is "Maciej Bryja", comment is "Programowanie przez aplikację Validation Desktop"
    import argparse
    
    interval = 15
    start_mode = 3 # wait! In Java, it passes startModeManual? "3" : "1". And startModeManual is hardcoded to true, so it passes 3.
    # But in testo_184_programmer.py, start_mode argument is choices=[1,3], but then it checks:
    # if args.start_mode == 1:
    #     start_mode = 1, stop_check1 = 1, stop_check3 = 1
    # else:
    #     start_mode = 3, stop_check1 = 0, stop_check3 = 0
    #
    # Wait, start_mode is 3, so:
    start_mode_mapped = 3
    stop_check1 = 0
    stop_check3 = 0
    
    # Start time in format YYYY-MM-DD HH:MM passed from java is:
    # LocalDateTime defaultStart = LocalDateTime.now().plusHours(2)
    # let's say "2026-05-26 18:30"
    start_time_arg = "2026-05-26 18:30"
    dt = app_config.datetime.strptime(start_time_arg, "%Y-%m-%d %H:%M")
    start_date = dt.strftime("%Y-%m-%d")
    start_time_val = dt.strftime("%H:%M")
    
    # In current python script:
    now_dt = app_config.datetime(2026, 5, 26, 16, 30) # fixed now for comparison
    stop_dt = now_dt.replace(year=now_dt.year + 1)
    stop_date = stop_dt.strftime("%Y-%m-%d")
    stop_time_val = stop_dt.strftime("%H:%M")
    
    config = app_config.TestoConfig(
        device_model=3,
        meas_interval_minutes=interval,
        start_mode=start_mode_mapped,
        start_date=start_date,
        start_time=start_time_val,
        stop_check1=stop_check1,
        stop_check2=0,
        stop_check3=stop_check3,
        stop_date=stop_date,
        stop_time=stop_time_val,
        configuration_by="Maciej Bryja",
        user_comment="Programowanie przez aplikację Validation Desktop",
        alarm_temp_1=app_config.AlarmLimit(
            enabled=1,
            direction=1,
            limit=8.0,
            alarm_type=1,
            allowed_minutes=60
        ),
        alarm_temp_2=app_config.AlarmLimit(
            enabled=1,
            direction=2,
            limit=2.0,
            alarm_type=1,
            allowed_minutes=60
        )
        # Note: alarm_temp_3, alarm_temp_4, alarm_mkt, alarm_hum_1, alarm_hum_2, alarm_shock are not provided
    )
    
    path = r"C:\Users\macie\.gemini\antigravity-ide\scratch\app_config.xml"
    app_config.XdpIO.write(config, path, now=now_dt)
    return path

def main():
    p1 = generate_proto_xml()
    p2 = generate_app_xml()
    
    with open(p1, 'r', encoding='utf-8') as f:
        proto_lines = f.readlines()
    with open(p2, 'r', encoding='utf-8') as f:
        app_lines = f.readlines()
        
    print("=== XML DIFF ===")
    diff = difflib.unified_diff(proto_lines, app_lines, fromfile="proto_config.xml", tofile="app_config.xml")
    sys.stdout.writelines(diff)

if __name__ == "__main__":
    main()
