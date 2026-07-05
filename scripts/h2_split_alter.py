#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pomocnik migracji H2: zamienia MySQL-owe wielokrotne ADD COLUMN w jednym
ALTER TABLE na osobne instrukcje (H2 nie wspiera składni z przecinkami).

Użycie: python scripts/h2_split_alter.py <in.sql> <out.sql>
"""
import re
import sys
from pathlib import Path


def split_multi_add(sql: str) -> str:
    pattern = re.compile(
        r"ALTER\s+TABLE\s+(`?\w+`?)\s+((?:ADD\s+COLUMN\s+[^,;]+,\s*)+ADD\s+COLUMN\s+[^,;]+);",
        re.IGNORECASE | re.DOTALL)

    def repl(m: re.Match) -> str:
        table = m.group(1)
        adds = re.split(r",\s*(?=ADD\s+COLUMN)", m.group(2), flags=re.IGNORECASE)
        return "\n".join(f"ALTER TABLE {table} {add.strip()};" for add in adds)

    return pattern.sub(repl, sql)


if __name__ == "__main__":
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    content = src.read_text(encoding="utf-8")
    header = ("-- (wariant H2 — wygenerowany przez scripts/h2_split_alter.py:\n"
              "--  wielokrotne ADD COLUMN rozdzielone na osobne ALTER TABLE)\n")
    dst.write_text(header + split_multi_add(content), encoding="utf-8", newline="\n")
    print(f"OK: {dst}")
