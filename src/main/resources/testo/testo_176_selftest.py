#!/usr/bin/env python3
"""
Test regresyjny kodera konfiguracji Testo 176 T4.

Dane wzorcowe pochodza z dwoch przechwytow USBPcap rzeczywistych sesji
programowania z Testo ComfortSoftware Basic (2026-09-03). Test dowodzi, ze
nasz koder odtwarza blok B0 CO DO BAJTU, wlacznie z suma kontrolna.

Uruchomienie:  python testo_176_selftest.py
Kod wyjscia 0 = zgodnosc, 1 = regresja.
"""
import os
import sys
import struct
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import testo_176_programmer as P

H = lambda s: bytes.fromhex(s.replace(' ', ''))

A = H("00 00 01 01 01 00 00 00 28 00 00 00 50 5e 00 00 03 24 11 06 03 09 ea 07 "
      "88 af c0 ae 88 af c0 ae e0 b1 e0 b1 e0 b1 e0 b1 e0 b1 e0 b1 e0 b1 3e 31 "
      "e0 b1 3e 31 e0 b1 3e 31 e0 b1 3e 31 e0 b1 3e 31 80 10 03 03 a5 e4 "
      "00 00 00 00 00 00 01 01 01 01 30 2a 00 00 55 03 80 07 b1 1c 02 00 "
      "00 00 00 00 00 00 00 00 f3 fd 00 00")

B = H("00 00 01 01 01 00 00 00 0a 00 00 00 44 32 00 00 0f 20 19 06 03 09 ea 07 "
      "7c b1 d8 aa 7c b1 d8 aa 80 c1 10 aa 7c b1 d8 aa e0 b1 e0 b1 e0 b1 3e 31 "
      "e0 b1 3e 31 e0 b1 3e 31 e0 b1 3e 31 e0 b1 3e 31 80 10 0f 0f 59 e6 "
      "00 00 00 00 00 00 01 01 01 01 10 0e 00 00 65 03 80 07 19 34 02 00 "
      "00 00 00 00 00 00 00 00 9f fe 00 00")

print(f"  A: {len(A)} B, B: {len(B)} B, oczekiwane {P.CONFIG_PAYLOAD_LEN}")
assert len(A) == len(B) == P.CONFIG_PAYLOAD_LEN

print("\n  1. Suma kontrolna na blokach z przechwytow")
for label, blk in (("A", A), ("B", B)):
    for name, off, ln, csoff in (("B0", P.B0_OFFSET, P.B0_CHECKSUM, P.B0_OFFSET + P.B0_CHECKSUM),
                                 ("B1", P.B1_OFFSET, P.B1_CHECKSUM, P.B1_OFFSET + P.B1_CHECKSUM)):
        want = struct.unpack_from('<H', blk, csoff)[0]
        got = P.block_checksum(blk[off:off + ln])
        print(f"     {label}/{name}: obliczono {got:#06x}, w przechwycie {want:#06x}  "
              f"{'OK' if got == want else 'BLAD'}")
        assert got == want

print("\n  2. Kodowanie temperatur")
for c in (-60.0, -80.0, -10.0, -180.0, 400.0, -200.0, 0.0):
    raw = P.encode_temperature(c)
    back = P.decode_temperature(raw)
    print(f"     {c:8.1f} °C -> {raw:7d} ({struct.pack('<h', raw).hex(' ')}) -> {back:8.1f} °C  "
          f"{'OK' if abs(back - c) < 0.05 else 'BLAD'}")
    assert abs(back - c) < 0.05

print("\n  3. Odtworzenie bloku B z szablonu A")
prog = datetime(2026, 9, 3, 6, 25, 32, tzinfo=timezone.utc)
out = P.patch_config(
    bytearray(A),
    count=10,
    channel_mask=0x0F,
    interval_seconds=3600,
    start_delay_seconds=12868,
    ranges=[(-10.0, -180.0), (-10.0, -180.0), (400.0, -200.0), (-10.0, -180.0)],
    prog_time_utc=prog,
)

b0s, b0e = P.B0_OFFSET, P.B0_OFFSET + P.B0_LEN
if bytes(out[b0s:b0e]) == B[b0s:b0e]:
    print(f"     BLOK B0 [{b0s}:{b0e}] — IDENTYCZNY co do bajtu z przechwytem ComSoftu")
else:
    print("     BLOK B0 — ROZBIEZNOSC:")
    for i in range(b0s, b0e):
        if out[i] != B[i]:
            print(f"       offset {i} (B0+{i-b0s}): wyliczono {out[i]:02x}, ComSoft {B[i]:02x}")

b1s, b1e = P.B1_OFFSET, P.B1_OFFSET + P.B1_LEN
print(f"\n     BLOK B1 [{b1s}:{b1e}]:")
diffs = [i for i in range(b1s, b1e) if out[i] != B[i]]
if not diffs:
    print("       identyczny")
else:
    for i in diffs:
        print(f"       offset {i} (B1+{i-b1s}): wyliczono {out[i]:02x}, ComSoft {B[i]:02x}")
    print("       ^ oczekiwane: pola 'energycalc' (B1+8, B1+12..16) i wynikajaca z nich suma")

print("\n  4. Odczyt wynikowej konfiguracji")
d = P.describe_config(out)
print(f"     odczytow={d['count']}  interwal={d['intervalSeconds']} s  "
      f"opoznienie={d['startDelaySeconds']} s  maska={d['channelMask']:#04x}")
for ch in d['channels']:
    print(f"       kanal {ch['channel']}: aktywny={ch['enabled']}  "
          f"zakres {ch['rangeLowerC']}…{ch['rangeUpperC']} °C")
