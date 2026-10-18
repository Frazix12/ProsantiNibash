#!/usr/bin/env python3
"""Check complete resource coverage and matching format arguments in English/Bangla."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "app/src/main/res"
def strings(folder):
    entries = ET.parse(root / folder / "strings.xml").getroot().findall("string")
    values = {entry.attrib["name"]: "".join(entry.itertext()) for entry in entries}
    assert len(entries) == len(values), f"Duplicate keys in {folder}"
    return values

english, bangla = strings("values"), strings("values-bn")
assert english.keys() == bangla.keys(), "Missing language resources"
for key in english:
    arguments = lambda value: sorted(re.findall(r"%\d+\$[sd]", value))
    assert arguments(english[key]) == arguments(bangla[key]), f"Format mismatch: {key}"
    assert bangla[key].strip(), f"Empty Bangla text: {key}"
print(f"English/Bangla coverage and placeholders match: {len(english)} resources")
