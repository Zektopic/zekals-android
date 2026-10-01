#!/usr/bin/env python3
"""Validate bundled language assets without requiring Android or network access."""
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / "app/src/main/assets/languages"
reference = json.loads((root / "en.json").read_text(encoding="utf-8"))
for file in root.glob("*.json"):
    assert file.stat().st_size <= 65536, file
    pack = json.loads(file.read_text(encoding="utf-8"))
    assert pack["schemaVersion"] == 1 and re.fullmatch(r"[a-z]{2,3}(-[A-Za-z0-9]+)*", pack["code"]), file
    assert file.name == pack["code"] + ".json", file
    assert pack["direction"] in ("ltr", "rtl"), file
    assert pack["speech"]["locale"] == pack["locale"], file
    assert set(reference["ui"]) <= set(pack["ui"]), file
    assert all(isinstance(value, str) and value.strip() and len(value) <= 500 for value in pack["ui"].values()), file
    assert 1 <= len(pack["phrases"]) <= 24 and all(isinstance(value, str) and 0 < len(value) <= 200 for value in pack["phrases"]), file
    pages = [sum(pack["keyboard"], [])] + pack["keyboardPages"]
    assert 1 <= len(pages) <= 11, file
    assert all(0 < len(page) <= 160 and all(isinstance(key, str) and 0 < len(key) <= 16 for key in page) for page in pages), file
print("Validated", len(list(root.glob("*.json"))), "language packs")
