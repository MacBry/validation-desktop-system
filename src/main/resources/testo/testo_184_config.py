"""
testo_config.py
================
Konfiguracja loggerow testo 184 (T1/T2/T3/H1/G1) - kodowanie/dekodowanie
pliku XDP (XFA dataset) ktory urzadzenie czyta jako konfiguracje wejsciowa.

Format <alldata> zostal zrekonstruowany 1:1 ze skryptow JavaScript wewnatrz
oryginalnego pliku 'testo 184 configuration.pdf' (XFA template). Wszystkie
funkcje pomocnicze (StringToHex, valueAdd200, kolejnosc pol) odpowiadaja
oficjalnej implementacji producenta.

Architektura:
- Codec   : niskopoziomowe kodowanie wartosci do pola <alldata>
- Config  : model danych (dataclass) - kompletna konfiguracja loggera
- XdpIO   : odczyt/zapis plikow XDP z modelu Config
- presets : gotowe konfiguracje (np. KKCz, KKP, FFP, lodowka 2-8 C)

Mapowanie na Spring Boot:
- Config       -> @Entity / @ConfigurationProperties bean
- Codec        -> @Component statyczne metody pomocnicze
- XdpIO        -> @Service (TestoConfigService)
- presets      -> wartosci domyslne w application.yml lub strategia enum

Autor: Maciej Bryja, RCKiK Poznan, 2026
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass, field, asdict
from datetime import datetime, timedelta
from typing import Optional
from xml.sax.saxutils import escape


# ---------------------------------------------------------------------------
# CODEC - niskopoziomowe kodowanie pojedynczych wartosci do <alldata>
# ---------------------------------------------------------------------------

class Codec:
    """
    Odpowiednik funkcji JavaScript z oryginalnego XFA: StringToHex,
    valueAdd200, StringToHexOnlyForEnterString. Wszystkie pola w <alldata>
    sa rozdzielone literalnym ciagiem '%%' i zakodowane jako hex ASCII
    (kazda litera -> dwa znaki hex char codu).
    """

    SEPARATOR = "%%"

    @staticmethod
    def string_to_hex(s: str) -> str:
        """Kazdy znak -> jego charCode w hex. Pusty string traktowany jako spacja."""
        if s is None or s == "":
            s = " "
        # Transliterate Polish characters to ASCII equivalents to prevent multi-byte characters
        # from generating 3-character hex sequences (e.g. ord('ę') = 281 = 0x119)
        polish_map = {
            'ą': 'a', 'ć': 'c', 'ę': 'e', 'ł': 'l', 'ń': 'n', 'ó': 'o', 'ś': 's', 'ź': 'z', 'ż': 'z',
            'Ą': 'A', 'Ć': 'C', 'Ę': 'E', 'Ł': 'L', 'Ń': 'N', 'Ó': 'O', 'Ś': 'S', 'Ź': 'Z', 'Ż': 'Z'
        }
        s_clean = "".join(polish_map.get(c, c) for c in str(s))
        return "".join(f"{ord(c):02x}" for c in s_clean)

    @staticmethod
    def hex_to_string(h: str) -> str:
        """Odwrotnosc string_to_hex; toleruje nieparzysta dlugosc i 'NaN'/'----'."""
        if not h:
            return ""
        # Specjalne wartosci - alarmy wylaczone
        if h in ("NaN", "----", "-"):
            return h
        # Czysty hex
        if re.fullmatch(r"[0-9a-fA-F]+", h) and len(h) % 2 == 0:
            try:
                return bytes.fromhex(h).decode("utf-8", errors="replace")
            except ValueError:
                pass
        return h  # raw fallback (np. liczby zapisane bez kodowania)

    @staticmethod
    def value_add_200(v) -> str:
        """
        valueAdd200(str) z JS: parseFloat(str)+200, zawsze w formacie X.Y (gdy
        ulamek = 0, dodaj '.0'). Sluzy do zapisu limitow alarmow (offset 200
        kompensuje wartosci ujemne temperatury).
        """
        if v is None or v in ("", "----", "NaN"):
            return "NaN"
        try:
            f = float(v) + 200.0
        except (TypeError, ValueError):
            return "NaN"
        if (f * 10) % 10 == 0:
            return f"{int(f)}.0"
        return f"{f:g}"

    @staticmethod
    def parse_value_minus_200(s: str) -> Optional[float]:
        """Odwrotnosc value_add_200."""
        if s in ("NaN", "----", "-", "", None):
            return None
        try:
            return float(s) - 200.0
        except ValueError:
            return None


# ---------------------------------------------------------------------------
# MODEL - dataclass'y opisujace kompletna konfiguracje
# ---------------------------------------------------------------------------

@dataclass
class AlarmLimit:
    """
    Pojedyncza granica alarmu. Konwencja Testo:
      enabled    : 1 = wlaczony, 0 = wylaczony
      direction  : 1 = gorna granica (limit MAX), 2 = dolna granica (limit MIN)
      limit      : wartosc graniczna w jednostce fizycznej (degC, %RH, g)
                   None = alarm nieaktywny (zakodowane jako 'NaN')
      alarm_type : 1 = kumulatywny, 2 = ciagly  (z oficjalnej dokumentacji)
      allowed_minutes : maksymalny czas naruszenia przed wyzwoleniem
    """
    enabled: int = 0
    direction: int = 1
    limit: Optional[float] = None
    alarm_type: int = 1
    allowed_minutes: int = 60


@dataclass
class TestoConfig:
    """
    Kompletna konfiguracja loggera testo 184. Pola odpowiadaja 1:1 polom
    formularza XDP. Nazwy zachowuja konwencje producenta dla latwosci
    kontroli (porownanie z plikiem otwartym w Adobe Reader).
    """

    # --- naglowek raportu / general -----------------------------------------
    device_model: int = 3                  # 1=T1, 2=T2, 3=T3, 4=H1, 5=G1
    report_name: str = "testo 184 measurement report"
    report_language: int = 2               # 1=DE, 2=EN, ... (z UI)
    title: str = ""
    mail_to: str = ""
    sender: str = ""
    receiver: str = ""
    user_comment: str = ""

    # --- pomiar / device ---------------------------------------------------
    meas_interval_minutes: int = 10        # interwal pomiaru
    start_mode: int = 1                    # 1=date/time (czasowy), 3=button (ręczny)
    start_date: str = "2026-01-01"         # YYYY-MM-DD (ISO, jak w XDP)
    start_time: str = "08:00"              # HH:MM
    start_delay_minutes: int = 0
    stop_check1: int = 1                   # stop after date/time (1=active, 0=inactive)
    stop_check2: int = 0                   # stop on PC
    stop_check3: int = 1                   # stop on button (1=active, 0=inactive)
    stop_date: str = "2026-12-31"
    stop_time: str = "23:59"
    configuration_by: str = ""             # imie/nazwisko konfigurujacego
    mkt_activation_energy: float = 83.144  # kJ/mol (domyslnie z dokumentacji)
    # UWAGA (do weryfikacji sprzętowej): indeks strefy jest zafiksowany na 53
    # (UTC+02:00, czas letni PL). Zimą Polska ma UTC+1 — flaga DST w ogonie
    # alldata (isxls 990/991) jest ustawiana z zegara maszyny, ale sam indeks
    # strefy się nie zmienia; możliwe przesunięcie czasu urządzenia o 1h zimą.
    time_zone: int = 53                    # 53 = UTC+02:00 (Europe/Warsaw, DST)
    date_format: int = 3                   # 1=US, 2=EU, 3=DD-MM-YYYY (Domyślnie 3 dla zgodności z firmware)

    # --- wyswietlanie / display flags ---------------------------------------
    select_temp: int = 1                   # pokaz temp na LCD
    select_hum: int = 1                    # pokaz wilgotnosc
    select_acc: int = 1                    # pokaz wstrzasy
    select_lcd: int = 1                    # LCD wlaczony
    select_led: int = 1                    # LED wlaczony
    select_time_mark: int = 1              # pozwol na time marks
    select_nfc: int = 0                    # NFC

    # --- jednostki ----------------------------------------------------------
    limit_temp_unit: int = 1               # 1=Celsius, 2=Fahrenheit
    limit_shock_unit: int = 1              # 1=g

    # --- alarmy ------------------------------------------------------------
    alarm_temp_1: AlarmLimit = field(default_factory=lambda: AlarmLimit(enabled=0, direction=1, limit=None, alarm_type=1, allowed_minutes=60))
    alarm_temp_2: AlarmLimit = field(default_factory=lambda: AlarmLimit(enabled=0, direction=2, limit=None, alarm_type=1, allowed_minutes=60))
    alarm_temp_3: AlarmLimit = field(default_factory=lambda: AlarmLimit(enabled=0, direction=1, limit=None, alarm_type=1, allowed_minutes=60))
    alarm_temp_4: AlarmLimit = field(default_factory=lambda: AlarmLimit(enabled=0, direction=2, limit=None, alarm_type=1, allowed_minutes=60))
    alarm_mkt: AlarmLimit = field(default_factory=lambda: AlarmLimit(enabled=0, direction=1, limit=None, alarm_type=1, allowed_minutes=60))
    alarm_hum_1: AlarmLimit = field(default_factory=lambda: AlarmLimit(enabled=0, direction=1, limit=40.0, alarm_type=1, allowed_minutes=60))
    alarm_hum_2: AlarmLimit = field(default_factory=lambda: AlarmLimit(enabled=0, direction=2, limit=20.0, alarm_type=1, allowed_minutes=60))
    alarm_shock: AlarmLimit = field(default_factory=lambda: AlarmLimit(enabled=0, direction=1, limit=4.0, alarm_type=1, allowed_minutes=60))

    # --- dodatkowe pola UI XDP (zwykle nie zmieniane) -----------------------
    language: int = 2                      # jezyk interfejsu (gorna belka XDP)
    experten_select: int = 2               # tryb ekspercki: 1=NO, 2=YES
    temp_upper_limit: float = 70           # zakres widoczny w UI (lewa kolumna)
    temp_lower_limit: float = -20
    temp_radio: int = 1
    hum_upper_limit: int = 100
    hum_lower_limit: int = 1
    hum_answer: int = 1
    shock_answer: int = 1
    shock_lower_limit: int = 16
    transport_answer: int = 10

    # ----------------------------------------------------------------------
    def to_dict(self) -> dict:
        return asdict(self)


# ---------------------------------------------------------------------------
# ENCODER <alldata>
# ---------------------------------------------------------------------------

class AlldataEncoder:
    """
    Buduje string <alldata> dokladnie wedlug kolejnosci pol z oryginalnego
    JavaScriptu w testo configuration.pdf. Wynik zaczyna sie od '%%SHENZHEN%%'
    i konczy '%%%%%%%%' (sekwencja terminujaca z 4 separatorow).
    """

    @staticmethod
    def _alarm_block(a: AlarmLimit) -> str:
        """
        Kazdy alarm temp/hum -> 5 pol:
        enabled, direction, limit+200, alarm_type, allowed_minutes
        (rozdzielone separatorem %%; tu zwracamy gotowy fragment)
        """
        sep = Codec.SEPARATOR
        parts = [
            Codec.string_to_hex(str(a.enabled)),
            Codec.string_to_hex(str(a.direction)),
            Codec.string_to_hex(Codec.value_add_200(a.limit)),
            Codec.string_to_hex(str(a.alarm_type)),
            Codec.string_to_hex(str(a.allowed_minutes)),
        ]
        return sep.join(parts) + sep

    @staticmethod
    def _short_alarm_block(a: AlarmLimit) -> str:
        """
        MKT i shock maja krotki format: enabled, direction, limit+200, "2d", "2d"
        (zakodowane '----' jako "2d"="-" zamiast type/time).
        """
        sep = Codec.SEPARATOR
        parts = [
            Codec.string_to_hex(str(a.enabled)),
            Codec.string_to_hex(str(a.direction)),
            Codec.string_to_hex(Codec.value_add_200(a.limit)),
        ]
        return sep.join(parts) + sep + "2d" + sep + "2d" + sep

    @classmethod
    def encode(cls, c: TestoConfig, now: Optional[datetime] = None) -> str:
        """Glowna funkcja - buduje pelen string <alldata>."""
        if now is None:
            now = datetime.now()

        h = Codec.string_to_hex
        sep = Codec.SEPARATOR

        # Calkowity start delay w minutach (XFA liczy start*60 + startMin)
        # Tutaj uznajemy ze 'start_delay_minutes' to juz minuty calkowite.
        start_delay_total = c.start_delay_minutes

        # stop_check1/2/3 zlaczone bez separatorow (jak w JS):
        # getAlldata.StringToHex(stopCheck1) + ...Hex(stopCheck2) + ...Hex(stopCheck3) + "%%"
        stop_checks = (
            h(str(c.stop_check1))
            + h(str(c.stop_check2))
            + h(str(c.stop_check3))
        )

        # Display flags: 7 hex'ow zlaczonych bez separatorow + %%
        display_flags = (
            h(str(c.select_temp))
            + h(str(c.select_hum))
            + h(str(c.select_acc))
            + h(str(c.select_lcd))
            + h(str(c.select_led))
            + h(str(c.select_time_mark))
            + h(str(c.select_nfc))
        )

        parts: list[str] = [
            "",                                             # leading %% -> empty before first sep
            "SHENZHEN",                                     # 1 marker producenta
            h(str(c.device_model)),                         # 2 model
            h(c.report_name),                               # 3 reportName
            h(str(c.report_language)),                      # 4 reportLanguage
            h(c.title),                                     # 5 title
            h(c.mail_to),                                   # 6 mailTo
            h(c.sender),                                    # 7 sender
            h(c.receiver),                                  # 8 receiver
            h(c.user_comment),                              # 9 userComment
            h(str(c.meas_interval_minutes)),                # 10 meas interval
            h(str(c.start_mode)),                           # 11 RadioButtonList (1=date/time, 3=button)
            h(c.start_date),                                # 12 startDate (ISO YYYY-MM-DD)
            h(c.start_time),                                # 13 startTime
            h(str(start_delay_total)),                      # 14 start delay
            stop_checks,                                    # 15 stopCheck1+2+3 (concat)
            h(c.stop_date),                                 # 16 stopDate
            h(c.stop_time),                                 # 17 stopTime
            h(c.configuration_by),                          # 18 configuredBy
            h(f"{c.mkt_activation_energy:g}"),              # 19 MKT
            h(str(c.time_zone)),                            # 20 timezone
            h(str(c.date_format)),                          # 21 dateFormat
            display_flags,                                  # 22 display flags concat
            h(str(c.limit_temp_unit)),                      # 23 limitTempUnit
            h(str(c.limit_shock_unit)),                     # 24 limitShockUnit
        ]
        head = sep.join(parts) + sep  # konczymy separatorem przed alarmami

        # alarmy: temp1-4 (5 pol kazdy), MKT (krotki), hum1-2 (5 pol), shock (krotki)
        alarms = (
            cls._alarm_block(c.alarm_temp_1)
            + cls._alarm_block(c.alarm_temp_2)
            + cls._alarm_block(c.alarm_temp_3)
            + cls._alarm_block(c.alarm_temp_4)
            + cls._short_alarm_block(c.alarm_mkt)
            + cls._alarm_block(c.alarm_hum_1)
            + cls._alarm_block(c.alarm_hum_2)
            + cls._short_alarm_block(c.alarm_shock)
        )

        # ogon: data konfiguracji, godzina, timezone, isxls, 999999, 8 separatorow
        import time
        is_dst = time.localtime().tm_isdst > 0
        isxls = "991" if is_dst else "990"

        tail = (
            h(now.strftime("%Y-%m-%d")) + sep
            + h(now.strftime("%H:%M:%S")) + sep
            + h(str(c.time_zone)) + sep
            + isxls + sep
            + "999999"
            + sep * 4                                       # "%%%%%%%%"
        )

        return head + alarms + tail


# ---------------------------------------------------------------------------
# DECODER <alldata>  (do weryfikacji / round-tripu)
# ---------------------------------------------------------------------------

class AlldataDecoder:
    """
    Parsuje pole <alldata> z powrotem do slownika. Nie odtwarza wszystkich
    pol modelu (czesc to derywaty), ale wystarcza do weryfikacji round-tripu.
    """

    @staticmethod
    def decode(alldata: str) -> dict:
        # rozdziel po %% i odfiltruj puste
        raw = [p for p in alldata.split("%%") if p]

        def hd(s):
            return Codec.hex_to_string(s)

        out = {}
        try:
            out["marker"] = raw[0]
            out["device_model"] = int(hd(raw[1]))
            out["report_name"] = hd(raw[2])
            out["report_language"] = int(hd(raw[3]))
            out["title"] = hd(raw[4])
            out["mail_to"] = hd(raw[5])
            out["sender"] = hd(raw[6])
            out["receiver"] = hd(raw[7])
            out["user_comment"] = hd(raw[8])
            out["meas_interval_minutes"] = int(hd(raw[9]))
            out["start_mode"] = int(hd(raw[10]))
            out["start_date"] = hd(raw[11])
            out["start_time"] = hd(raw[12])
            out["start_delay_minutes"] = int(hd(raw[13]))
            # raw[14] = 3 znaki '001' = stopCheck1/2/3 polaczone, dekoduj parami
            sc = hd(raw[14])
            out["stop_check1"], out["stop_check2"], out["stop_check3"] = \
                int(sc[0]), int(sc[1]), int(sc[2])
            out["stop_date"] = hd(raw[15])
            out["stop_time"] = hd(raw[16])
            out["configuration_by"] = hd(raw[17])
            out["mkt_activation_energy"] = float(hd(raw[18]))
            out["time_zone"] = int(hd(raw[19]))
            out["date_format"] = int(hd(raw[20]))
            # display flags - 7 znakow zlaczonych
            df = hd(raw[21])
            for i, name in enumerate(
                ["select_temp", "select_hum", "select_acc", "select_lcd",
                 "select_led", "select_time_mark", "select_nfc"]
            ):
                out[name] = int(df[i]) if i < len(df) else 0
            out["limit_temp_unit"] = int(hd(raw[22]))
            out["limit_shock_unit"] = int(hd(raw[23]))

            # alarmy: 4 x 5pol temp + 5pol MKT (krotki) + 2 x 5pol hum + 5pol shock (krotki)
            idx = 24

            def read_alarm(idx, short=False):
                a = {
                    "enabled": int(hd(raw[idx])),
                    "direction": int(hd(raw[idx + 1])),
                    "limit": Codec.parse_value_minus_200(hd(raw[idx + 2])),
                }
                if short:
                    a["alarm_type"] = None
                    a["allowed_minutes"] = None
                    return a, idx + 5
                try:
                    a["alarm_type"] = int(hd(raw[idx + 3]))
                except ValueError:
                    a["alarm_type"] = hd(raw[idx + 3])
                try:
                    a["allowed_minutes"] = int(hd(raw[idx + 4]))
                except ValueError:
                    a["allowed_minutes"] = hd(raw[idx + 4])
                return a, idx + 5

            for n in ("alarm_temp_1", "alarm_temp_2", "alarm_temp_3", "alarm_temp_4"):
                out[n], idx = read_alarm(idx)
            out["alarm_mkt"], idx = read_alarm(idx, short=True)
            for n in ("alarm_hum_1", "alarm_hum_2"):
                out[n], idx = read_alarm(idx)
            out["alarm_shock"], idx = read_alarm(idx, short=True)

            out["config_date"] = hd(raw[idx]) if idx < len(raw) else None
            out["config_time"] = hd(raw[idx + 1]) if idx + 1 < len(raw) else None
        except (IndexError, ValueError) as e:
            out["_parse_error"] = str(e)
            out["_parsed_fields"] = len(out)

        return out


# ---------------------------------------------------------------------------
# I/O - odczyt/zapis plikow XDP
# ---------------------------------------------------------------------------

class XdpIO:
    """
    Odczyt/zapis plikow .xdp ktore loggery testo 184 czytaja z dysku USB.
    Plik XDP to XML (XFA dataset) zawierajacy <form1> z polami formularza
    i polem <alldata> (kanoniczny payload odczytywany przez urzadzenie).

    UWAGA: testo dystrybuuje pelen plik testo_184_configuration.pdf zawierajacy
    template XFA + datasets. Na potrzeby PoC operujemy na samym XDP (datasets),
    co wystarczy do USB. Pozniej mozna podmieniac datasets w PDF.
    """

    XDP_TEMPLATE = """<?xml version="1.0" encoding="UTF-8"?>
<form1
><alldata
>{alldata}</alldata
><pdfversion
>20150331v1.10</pdfversion
><Useless
> </Useless
><language
>{language}</language
><expertenSelect
>{experten_select}</expertenSelect
><deviceModell
>{device_model}</deviceModell
><colorUseFunction
/><leftTempSubform
><tempUpperLimit
>{temp_upper_limit}</tempUpperLimit
><tmpLowerLimit
>{temp_lower_limit}</tmpLowerLimit
><tempRadio
>{temp_radio}</tempRadio
></leftTempSubform
><leftHumSubform
><humUpperLimit
>{hum_upper_limit}</humUpperLimit
><humLowerLimit
>{hum_lower_limit}</humLowerLimit
><humAnswer
>{hum_answer}</humAnswer
></leftHumSubform
><LeftShockSubform
><shockAnswer
>{shock_answer}</shockAnswer
><shockLowerLimit
>{shock_lower_limit}</shockLowerLimit
></LeftShockSubform
><leftTransSubform
><transportAnswer
>{transport_answer}</transportAnswer
></leftTransSubform
><leftZoneSubform
><timeZoneAnswer
>{time_zone}</timeZoneAnswer
></leftZoneSubform
><rightAlarmSubform
><alarmTemp1
>{at1_en}</alarmTemp1
><alarmTemp2
>{at2_en}</alarmTemp2
><alarmTemp3
>{at3_en}</alarmTemp3
><alarmTemp4
>{at4_en}</alarmTemp4
><alarmMKT
>{amkt_en}</alarmMKT
><alarmhum1
>{ah1_en}</alarmhum1
><alarmhum2
>{ah2_en}</alarmhum2
><alarmshock
>{ash_en}</alarmshock
><directionTemp1
>{at1_dir}</directionTemp1
><directionTemp2
>{at2_dir}</directionTemp2
><directionTemp3
>{at3_dir}</directionTemp3
><directionTemp4
>{at4_dir}</directionTemp4
><directionMKT
>{amkt_dir}</directionMKT
><directionHum1
>{ah1_dir}</directionHum1
><directionHum2
>{ah2_dir}</directionHum2
><directionShock
>{ash_dir}</directionShock
><limitTemp1
>{at1_lim}</limitTemp1
><limitTemp2
>{at2_lim}</limitTemp2
><limitTemp3
>{at3_lim}</limitTemp3
><limitTemp4
>{at4_lim}</limitTemp4
><limitMKT
>{amkt_lim}</limitMKT
><limitHum1
>{ah1_lim}</limitHum1
><limitHum2
>{ah2_lim}</limitHum2
><limitShock
>{ash_lim}</limitShock
><limitTempU
>{limit_temp_unit}</limitTempU
><limitShockU
>{limit_shock_unit}</limitShockU
><alarmTypeTemp1
>{at1_typ}</alarmTypeTemp1
><alarmTypeTemp2
>{at2_typ}</alarmTypeTemp2
><alarmTypeTemp3
>{at3_typ}</alarmTypeTemp3
><alarmTypeTemp4
>{at4_typ}</alarmTypeTemp4
><alarmTypeHum1
>{ah1_typ}</alarmTypeHum1
><alarmTypeHum2
>{ah2_typ}</alarmTypeHum2
><allowTimeTemp1
>{at1_min}</allowTimeTemp1
><allowTimeTemp2
>{at2_min}</allowTimeTemp2
><allowTimeTemp3
>{at3_min}</allowTimeTemp3
><allowTimeTemp4
>{at4_min}</allowTimeTemp4
><allowTimeHum1
>{ah1_min}</allowTimeHum1
><allowTimeHum2
>{ah2_min}</allowTimeHum2
><limitTempUnit
>{limit_temp_unit}</limitTempUnit
><limitShockUnit
>{limit_shock_unit}</limitShockUnit
></rightAlarmSubform
><rightGeneralSubform
><reportLanguage
>{report_language}</reportLanguage
><title
>{title}</title
><sender
>{sender}</sender
><reciever
>{receiver}</reciever
><userComment
>{user_comment}</userComment
><mailTo
>{mail_to}</mailTo
><reportName
>{report_name}</reportName
><formatTimeZone
>{time_zone}</formatTimeZone
></rightGeneralSubform
><rightDeviceSubform
><measInterval
>{meas_interval_minutes}</measInterval
><measIntervalHour
>0</measIntervalHour
><RadioButtonList
>{start_mode}</RadioButtonList
><startDateTime1
>{start_date_formatted}</startDateTime1
><startDateTime2
>{start_time}</startDateTime2
><startDelay
>0</startDelay
><startDelayMin
>{start_delay_minutes}</startDelayMin
><stopCheck2
>{stop_check2}</stopCheck2
><stopCheck3
>{stop_check3}</stopCheck3
><stopCheck1
>{stop_check1}</stopCheck1
><stopDateTime1
>{stop_date_formatted}</stopDateTime1
><stopDateTime2
>{stop_time}</stopDateTime2
><battery
>500</battery
><MKTAct
>{mkt_activation_energy}</MKTAct
><dateFormat
>{date_format}</dateFormat
><selectTemp
>{select_temp}</selectTemp
><selectHum
>{select_hum}</selectHum
><selectAcc
>{select_acc}</selectAcc
><selectLCD
>{select_lcd}</selectLCD
><selectLED
>{select_led}</selectLED
><selectTimeMark
>{select_time_mark}</selectTimeMark
><configurationBy
>{configuration_by}</configurationBy
><selectNFC
>{select_nfc}</selectNFC
></rightDeviceSubform
></form1
>
"""

    @staticmethod
    def _fmt_alarm_limit(v: Optional[float]) -> str:
        """Limit dla widoku XDP - jak w oryginale: '24.0' lub '----'."""
        if v is None:
            return "----"
        # zachowaj jedna cyfre po przecinku gdy potrzeba
        if float(v).is_integer():
            return f"{int(v)}.0"
        return f"{v:g}"

    @staticmethod
    def _format_date(iso: str, date_format: int) -> str:
        """Formatuje date YYYY-MM-DD zgodnie z c.date_format (1=US, 2=EU, 3=DD-MM-YYYY)."""
        try:
            y, m, d = iso.split("-")
            if date_format == 1:
                return f"{m}/{d}/{y}"
            elif date_format == 2:
                return f"{d}.{m}.{y}"
            elif date_format == 3:
                return f"{d}-{m}-{y}"
            else:
                return f"{y}-{m}-{d}"
        except Exception:
            return iso

    @classmethod
    def write(cls, c: TestoConfig, path: str, now: Optional[datetime] = None) -> str:
        """Zapisuje konfiguracje jako plik XDP gotowy do skopiowania na logger."""
        alldata = AlldataEncoder.encode(c, now=now)

        def esc(s):
            if not s:
                return " "
            polish_map = {
                'ą': 'a', 'ć': 'c', 'ę': 'e', 'ł': 'l', 'ń': 'n', 'ó': 'o', 'ś': 's', 'ź': 'z', 'ż': 'z',
                'Ą': 'A', 'Ć': 'C', 'Ę': 'E', 'Ł': 'L', 'Ń': 'N', 'Ó': 'O', 'Ś': 'S', 'Ź': 'Z', 'Ż': 'Z'
            }
            s_clean = "".join(polish_map.get(c, c) for c in str(s))
            return escape(s_clean)

        xml = cls.XDP_TEMPLATE.format(
            alldata=alldata,
            language=c.language,
            experten_select=c.experten_select,
            device_model=c.device_model,
            temp_upper_limit=c.temp_upper_limit,
            temp_lower_limit=c.temp_lower_limit,
            temp_radio=c.temp_radio,
            hum_upper_limit=c.hum_upper_limit,
            hum_lower_limit=c.hum_lower_limit,
            hum_answer=c.hum_answer,
            shock_answer=c.shock_answer,
            shock_lower_limit=c.shock_lower_limit,
            transport_answer=c.transport_answer,
            time_zone=c.time_zone,
            # alarmy: enable/direction/limit/type/allowedTime
            at1_en=c.alarm_temp_1.enabled, at1_dir=c.alarm_temp_1.direction,
            at1_lim=cls._fmt_alarm_limit(c.alarm_temp_1.limit),
            at1_typ=c.alarm_temp_1.alarm_type, at1_min=c.alarm_temp_1.allowed_minutes,
            at2_en=c.alarm_temp_2.enabled, at2_dir=c.alarm_temp_2.direction,
            at2_lim=cls._fmt_alarm_limit(c.alarm_temp_2.limit),
            at2_typ=c.alarm_temp_2.alarm_type, at2_min=c.alarm_temp_2.allowed_minutes,
            at3_en=c.alarm_temp_3.enabled, at3_dir=c.alarm_temp_3.direction,
            at3_lim=cls._fmt_alarm_limit(c.alarm_temp_3.limit),
            at3_typ=c.alarm_temp_3.alarm_type, at3_min=c.alarm_temp_3.allowed_minutes,
            at4_en=c.alarm_temp_4.enabled, at4_dir=c.alarm_temp_4.direction,
            at4_lim=cls._fmt_alarm_limit(c.alarm_temp_4.limit),
            at4_typ=c.alarm_temp_4.alarm_type, at4_min=c.alarm_temp_4.allowed_minutes,
            amkt_en=c.alarm_mkt.enabled, amkt_dir=c.alarm_mkt.direction,
            amkt_lim=cls._fmt_alarm_limit(c.alarm_mkt.limit),
            ah1_en=c.alarm_hum_1.enabled, ah1_dir=c.alarm_hum_1.direction,
            ah1_lim=cls._fmt_alarm_limit(c.alarm_hum_1.limit),
            ah1_typ=c.alarm_hum_1.alarm_type, ah1_min=c.alarm_hum_1.allowed_minutes,
            ah2_en=c.alarm_hum_2.enabled, ah2_dir=c.alarm_hum_2.direction,
            ah2_lim=cls._fmt_alarm_limit(c.alarm_hum_2.limit),
            ah2_typ=c.alarm_hum_2.alarm_type, ah2_min=c.alarm_hum_2.allowed_minutes,
            ash_en=c.alarm_shock.enabled, ash_dir=c.alarm_shock.direction,
            ash_lim=cls._fmt_alarm_limit(c.alarm_shock.limit),
            limit_temp_unit=c.limit_temp_unit,
            limit_shock_unit=c.limit_shock_unit,
            report_language=c.report_language,
            title=esc(c.title), sender=esc(c.sender),
            receiver=esc(c.receiver), user_comment=esc(c.user_comment),
            mail_to=esc(c.mail_to), report_name=esc(c.report_name),
            meas_interval_minutes=c.meas_interval_minutes,
            start_mode=c.start_mode,
            start_date_formatted=cls._format_date(c.start_date, c.date_format),
            start_time=c.start_time,
            start_delay_minutes=c.start_delay_minutes,
            stop_check1=c.stop_check1,
            stop_check2=c.stop_check2,
            stop_check3=c.stop_check3,
            stop_date_formatted=cls._format_date(c.stop_date, c.date_format),
            stop_time=c.stop_time,
            mkt_activation_energy=c.mkt_activation_energy,
            date_format=c.date_format,
            select_temp=c.select_temp, select_hum=c.select_hum,
            select_acc=c.select_acc, select_lcd=c.select_lcd,
            select_led=c.select_led, select_time_mark=c.select_time_mark,
            configuration_by=esc(c.configuration_by),
            select_nfc=c.select_nfc,
        )
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(xml.strip())
        return alldata

    @classmethod
    def read_alldata(cls, path: str) -> str:
        """Wyciaga sam string <alldata> z istniejacego pliku XDP."""
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        m = re.search(r"<alldata\s*>([^<]*)</alldata\s*>", content)
        if not m:
            raise ValueError("Plik nie zawiera <alldata>")
        return m.group(1)


# ---------------------------------------------------------------------------
# PRESETY - typowe scenariusze RCKiK / GMP
# ---------------------------------------------------------------------------

class Presets:
    """Gotowe konfiguracje dla typowych scenariuszy walidacji."""

    @staticmethod
    def kkcz_storage(configured_by: str = "") -> TestoConfig:
        """
        Przechowywanie KKCz w lodowce 2-6 stC z marginesem alarmowym 1.0-7.0.
        Interwal 1 min, alarmy kumulatywne 30 min.
        """
        c = TestoConfig()
        c.report_name = "RCKiK Walidacja - KKCz 2-6 stC"
        c.configuration_by = configured_by
        c.meas_interval_minutes = 1
        c.alarm_temp_1 = AlarmLimit(enabled=1, direction=1, limit=7.0,
                                    alarm_type=1, allowed_minutes=30)
        c.alarm_temp_2 = AlarmLimit(enabled=1, direction=2, limit=1.0,
                                    alarm_type=1, allowed_minutes=30)
        return c

    @staticmethod
    def kkp_storage(configured_by: str = "") -> TestoConfig:
        """
        Koncentrat krwinek plytkowych: 20-24 stC, interwal 5 min.
        """
        c = TestoConfig()
        c.report_name = "RCKiK Walidacja - KKP 20-24 stC"
        c.configuration_by = configured_by
        c.meas_interval_minutes = 5
        c.alarm_temp_1 = AlarmLimit(enabled=1, direction=1, limit=24.0,
                                    alarm_type=1, allowed_minutes=60)
        c.alarm_temp_2 = AlarmLimit(enabled=1, direction=2, limit=20.0,
                                    alarm_type=1, allowed_minutes=60)
        return c

    @staticmethod
    def ffp_storage(configured_by: str = "") -> TestoConfig:
        """
        Osocze swiezo mrozone: ponizej -25 stC, interwal 10 min.
        """
        c = TestoConfig()
        c.report_name = "RCKiK Walidacja - FFP <-25 stC"
        c.configuration_by = configured_by
        c.meas_interval_minutes = 10
        c.alarm_temp_1 = AlarmLimit(enabled=1, direction=1, limit=-25.0,
                                    alarm_type=1, allowed_minutes=60)
        return c


if __name__ == "__main__":
    import sys
    import argparse

    parser = argparse.ArgumentParser(description="Programowanie rejestratora Testo 184 (T1/T2/T3/H1/G1).")
    parser.add_argument("--interval", type=int, default=1, help="Interwal pomiarow w minutach")
    parser.add_argument("--count", type=int, default=10, help="Planowana liczba pomiarow")
    parser.add_argument("--start", type=str, default=None, help="Czas startu (format HH:MM lub RRRR-MM-DD HH:MM, domyslnie za 5 minut)")
    parser.add_argument("--output", type=str, default="testo 184 configuration_data.xml", help="Sciezka zapisu pliku (np. E:\\testo 184 configuration_data.xml)")
    # Mapowanie spójne z modelem TestoConfig i mostem testo_184_programmer.py:
    # 1 = date/time (czasowy), 3 = button (przycisk)
    parser.add_argument("--start-mode", type=int, default=3, choices=[1, 3], help="Tryb startu: 1=czas (date/time), 3=przycisk (button)")
    parser.add_argument("--model", type=int, default=3, help="Model urzadzenia: 1=T1, 2=T2, 3=T3, 4=H1, 5=G1 (domyslnie: 3)")

    args = parser.parse_args()

    now = datetime.utcnow()
    if args.start is None:
        start_dt = now + timedelta(minutes=5)
        start_date_str = start_dt.strftime("%Y-%m-%d")
        start_time_str = start_dt.strftime("%H:%M")
    else:
        start_date_str = now.strftime("%Y-%m-%d")
        start_time_str = args.start
        if " " in args.start:
            parts = args.start.split(" ", 1)
            start_date_str = parts[0]
            start_time_str = parts[1]
        try:
            start_dt = datetime.strptime(f"{start_date_str} {start_time_str}", "%Y-%m-%d %H:%M")
        except ValueError:
            print("Blad: Nieprawidlowy format czasu. Uzyj HH:MM lub RRRR-MM-DD HH:MM")
            sys.exit(1)

    # Obliczanie czasu zakonczenia
    stop_dt = start_dt + timedelta(minutes=args.interval * args.count)
    stop_date_str = stop_dt.strftime("%Y-%m-%d")
    stop_time_str = stop_dt.strftime("%H:%M")

    # Utworzenie konfiguracji
    config = TestoConfig(
        device_model=args.model,
        meas_interval_minutes=args.interval,
        start_mode=args.start_mode,
        start_date=start_date_str,
        start_time=start_time_str,
        stop_check1=1 if args.start_mode == 1 else 0,  # stop na date/time dla trybu czasowego (1)
        stop_check2=0,
        stop_check3=1 if args.start_mode in (1, 3) else 0,  # stop przyciskiem (1 dla czasowego i przycisku)
        stop_date=stop_date_str,
        stop_time=stop_time_str
    )

    print(f"Generowanie konfiguracji dla Testo 184 (T{args.model}):")
    print(f"  Czas startu:  {start_date_str} {start_time_str}")
    print(f"  Interwal:     {args.interval} min")
    print(f"  Liczba pom.:  {args.count}")
    print(f"  Czas stopu:   {stop_date_str} {stop_time_str}")
    print(f"  Zapis do:     {args.output}")

    try:
        XdpIO.write(config, args.output, now=now)
        print(f"Plik '{args.output}' zostal wygenerowany pomyslnie!")
    except Exception as e:
        print(f"Blad podczas zapisu pliku: {e}")

