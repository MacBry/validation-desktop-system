#!/usr/bin/env python3
import os
import sys
import argparse
from datetime import datetime, timedelta

# Add local path to import libraries
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

try:
    import testo_184_config
except ImportError as e:
    print(f"Error: Could not import testo_184_config: {e}", file=sys.stderr)
    sys.exit(1)

def main():
    parser = argparse.ArgumentParser(description="Testo 184 Programmer Bridge")
    parser.add_argument("--drive", required=True, help="Target drive/path (e.g. E:\\)")
    parser.add_argument("--interval", type=int, default=10, help="Interval in minutes")
    parser.add_argument("--count", type=int, default=100, help="Number of measurements")
    parser.add_argument("--start-mode", type=int, choices=[1, 3], default=1, help="Start mode (1=Auto/Temporal, 3=Button/Manual)")
    parser.add_argument("--start-delay", type=int, default=0, help="Start delay in minutes")
    parser.add_argument("--start-time", help="Start time in format YYYY-MM-DD HH:MM")
    parser.add_argument("--upper-limit", type=float, help="Max temperature limit in Celsius")
    parser.add_argument("--upper-minutes", type=int, default=60, help="Max allowed minutes for upper alarm")
    parser.add_argument("--lower-limit", type=float, help="Min temperature limit in Celsius")
    parser.add_argument("--lower-minutes", type=int, default=60, help="Min allowed minutes for lower alarm")
    parser.add_argument("--operator", default="Validation System", help="Configured by username")
    parser.add_argument("--comment", default="", help="User comment")
    
    args = parser.parse_args()
    
    try:
        drive_path = args.drive
        if not drive_path.endswith("\\") and not drive_path.endswith("/"):
            drive_path += os.sep
            
        xml_path = os.path.join(drive_path, "testo 184 configuration_data.xml")
        
        # Determine start/stop parameters
        # 1 = Czasowy / Temporal (automatic/date-time)
        # 3 = Ręczny / Button (manual)
        if args.start_mode == 1:
            start_mode = 1
            stop_check1 = 1
            stop_check3 = 1
        else:
            start_mode = 3
            stop_check1 = 0
            stop_check3 = 1
            
        now_dt = datetime.now()
        
        if args.start_time:
            try:
                start_dt = datetime.strptime(args.start_time, "%Y-%m-%d %H:%M")
            except Exception as ex:
                print(f"Error parsing start time: {ex}", file=sys.stderr)
                sys.exit(1)
        else:
            start_dt = now_dt
            
        start_date = start_dt.strftime("%Y-%m-%d")
        start_time_val = start_dt.strftime("%H:%M")
        
        # Calculate stop time dynamically: stop_dt = start_dt + start_delay + (interval * count) minutes
        total_minutes = args.start_delay + (args.interval * args.count)
        stop_dt = start_dt + timedelta(minutes=total_minutes)
        stop_date = stop_dt.strftime("%Y-%m-%d")
        stop_time_val = stop_dt.strftime("%H:%M")
        
        # Configure Alarms
        alarm_max_enabled = 1 if args.upper_limit is not None else 0
        alarm_max_limit = args.upper_limit
        
        alarm_min_enabled = 1 if args.lower_limit is not None else 0
        alarm_min_limit = args.lower_limit
        
        config = testo_184_config.TestoConfig(
            device_model=3,  # Testo 184 T3
            meas_interval_minutes=args.interval,
            start_mode=start_mode,
            start_date=start_date,
            start_time=start_time_val,
            start_delay_minutes=args.start_delay,
            stop_check1=stop_check1,
            stop_check2=0,
            stop_check3=stop_check3,
            stop_date=stop_date,
            stop_time=stop_time_val,
            configuration_by=args.operator,
            user_comment=args.comment,
            alarm_temp_1=testo_184_config.AlarmLimit(
                enabled=alarm_max_enabled,
                direction=1,
                limit=alarm_max_limit,
                alarm_type=1,
                allowed_minutes=args.upper_minutes
            ),
            alarm_temp_2=testo_184_config.AlarmLimit(
                enabled=alarm_min_enabled,
                direction=2,
                limit=alarm_min_limit,
                alarm_type=1,
                allowed_minutes=args.lower_minutes
            ),
            alarm_temp_3=testo_184_config.AlarmLimit(enabled=0),
            alarm_temp_4=testo_184_config.AlarmLimit(enabled=0),
            alarm_mkt=testo_184_config.AlarmLimit(enabled=0),
            alarm_hum_1=testo_184_config.AlarmLimit(enabled=0),
            alarm_hum_2=testo_184_config.AlarmLimit(enabled=0),
            alarm_shock=testo_184_config.AlarmLimit(enabled=0)
        )
        
        testo_184_config.XdpIO.write(config, xml_path, now=now_dt)
        print("[OK]")
        sys.exit(0)
        
    except Exception as e:
        print(f"Error writing configuration file: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
