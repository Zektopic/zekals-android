#!/usr/bin/env python3
"""Validate bundled language assets without requiring Android or network access."""
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / "app/src/main/assets/languages"
reference = json.loads((root / "en.json").read_text(encoding="utf-8"))
# Words that are legitimately spelled the same as English in a given language.
SAME_AS_ENGLISH = {"fr": {"standard"}, "it": {"standard"}}
for file in root.glob("*.json"):
    assert file.stat().st_size <= 65536, file
    pack = json.loads(file.read_text(encoding="utf-8"))
    assert pack["schemaVersion"] == 1 and re.fullmatch(r"[a-z]{2,3}(-[A-Za-z0-9]+)*", pack["code"]), file
    assert file.name == pack["code"] + ".json", file
    assert pack["direction"] in ("ltr", "rtl"), file
    assert pack["speech"]["locale"] == pack["locale"], file
    assert set(reference["ui"]) <= set(pack["ui"]), file
    assert all(isinstance(value, str) and value.strip() and len(value) <= 500 for value in pack["ui"].values()), file
    for key, english in reference["ui"].items():
        for placeholder in re.findall(r"\{[a-z]+\}", english):
            assert placeholder in pack["ui"][key], f"{file.name}: {key} lost {placeholder}"
    if pack["code"] != "en":
        untranslated = sorted(key for key, english in reference["ui"].items()
                              if pack["ui"][key] == english and key not in SAME_AS_ENGLISH.get(pack["code"], set()))
        assert not untranslated, f"{file.name}: untranslated UI strings {untranslated}"
    assert 1 <= len(pack["phrases"]) <= 24 and all(isinstance(value, str) and 0 < len(value) <= 200 for value in pack["phrases"]), file
    pages = [sum(pack["keyboard"], [])] + pack["keyboardPages"]
    assert 1 <= len(pages) <= 11, file
    assert all(0 < len(page) <= 160 and all(isinstance(key, str) and 0 < len(key) <= 16 for key in page) for page in pages), file
print("Validated", len(list(root.glob("*.json"))), "language packs")
