#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ekstrakcja tekstów UI z plików FXML do kluczy i18n (docs/i18n.md).

Dla każdego pliku ui/*.fxml:
  - znajduje atrybuty text="...", promptText="...", title="..."
  - pomija wartości już skonwertowane (%klucz), puste i bez liter
  - generuje klucz: <ekran>.<slug> (string w >=2 plikach -> common.<slug>)
  - podmienia wartość na %klucz w FXML (in place)
  - wypisuje fragment properties (PL) do stdout / pliku

Użycie:  python scripts/i18n_extract.py [--dry-run]
"""
import io
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

UI_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/ui"
OUT_PROPS = Path(__file__).resolve().parent / "i18n_extracted_pl.properties"

ATTR_RE = re.compile(r'\b(text|promptText|title)="([^"]*)"')
PL_MAP = str.maketrans("ąćęłńóśźżĄĆĘŁŃÓŚŹŻ", "acelnoszzACELNOSZZ")

# Ekrany już skonwertowane ręcznie — pomijamy
SKIP_FILES = {"login.fxml", "main.fxml"}


def slugify(text: str, max_words: int = 4) -> str:
    t = text.translate(PL_MAP)
    t = unicodedata.normalize("NFKD", t).encode("ascii", "ignore").decode()
    words = re.findall(r"[A-Za-z0-9]+", t)[:max_words]
    slug = "_".join(w.lower() for w in words)
    return slug or "txt"


def screen_prefix(fxml_name: str) -> str:
    return fxml_name.replace(".fxml", "").replace("_", "")


def is_translatable(value: str) -> bool:
    if not value or value.startswith("%"):
        return False
    # bez liter (separatory, liczby, myślniki) — nie tłumaczymy
    return bool(re.search(r"[A-Za-ząćęłńóśźżĄĆĘŁŃÓŚŹŻ]", value))


def main() -> None:
    dry_run = "--dry-run" in sys.argv

    # Przebieg 1: zbierz wystąpienia string -> pliki
    occurrences: dict[str, set[str]] = defaultdict(set)
    for fxml in sorted(UI_DIR.glob("*.fxml")):
        if fxml.name in SKIP_FILES:
            continue
        content = fxml.read_text(encoding="utf-8")
        for _, value in ATTR_RE.findall(content):
            if is_translatable(value):
                occurrences[value].add(fxml.name)

    # Przydział kluczy: wspólne stringi -> common.*, reszta per ekran
    assigned: dict[str, str] = {}   # string -> key
    used_keys: set[str] = set()

    def unique_key(base: str) -> str:
        key, n = base, 2
        while key in used_keys:
            key = f"{base}{n}"
            n += 1
        used_keys.add(key)
        return key

    for value, files in sorted(occurrences.items()):
        if len(files) >= 2:
            assigned[value] = unique_key(f"common.{slugify(value)}")

    for fxml in sorted(UI_DIR.glob("*.fxml")):
        if fxml.name in SKIP_FILES:
            continue
        prefix = screen_prefix(fxml.name)
        content = fxml.read_text(encoding="utf-8")
        for _, value in ATTR_RE.findall(content):
            if is_translatable(value) and value not in assigned:
                assigned[value] = unique_key(f"{prefix}.{slugify(value)}")

    # Przebieg 2: podmiana w FXML
    changed = 0
    for fxml in sorted(UI_DIR.glob("*.fxml")):
        if fxml.name in SKIP_FILES:
            continue
        content = fxml.read_text(encoding="utf-8")

        def repl(m: re.Match) -> str:
            attr, value = m.group(1), m.group(2)
            if is_translatable(value):
                return f'{attr}="%{assigned[value]}"'
            return m.group(0)

        new_content = ATTR_RE.sub(repl, content)
        if new_content != content:
            changed += 1
            if not dry_run:
                fxml.write_text(new_content, encoding="utf-8", newline="\n")

    # Emisja properties PL (klucz=oryginalny tekst), posortowane po kluczu
    lines = [f"{key}={value}" for value, key in
             sorted(assigned.items(), key=lambda kv: kv[1])]
    out = "\n".join(lines) + "\n"
    if not dry_run:
        OUT_PROPS.write_text(out, encoding="utf-8", newline="\n")

    print(f"Strings: {len(assigned)} | common.*: "
          f"{sum(1 for k in assigned.values() if k.startswith('common.'))} "
          f"| FXML changed: {changed} | props: {OUT_PROPS.name}"
          f"{' (dry-run)' if dry_run else ''}")


if __name__ == "__main__":
    main()
