#!/usr/bin/env python3
"""Generate the Java lang fragments from docs/design/STRINGS.md.

    python3 java/tools/gen_lang.py            # (re)write java/src/main/lang/<module>/{en_us,ru_ru}.json
    python3 java/tools/gen_lang.py --check    # verify only; exit 1 if a fragment is out of date (CI)
    python3 java/tools/gen_lang.py --prune    # also drop keys that are not in STRINGS.md

STRINGS.md is the single source of truth for player-facing text (LOCALIZATION.md). Every row
``| `key` | English | Russian |`` becomes one key in exactly one module's fragment:

* the key's owned name (text after ``burmaldaholic.``, e.g. ``blackjack.hit`` or
  ``slot_machine_copper``) is matched against java/config/namespaces.properties — the same
  rule as the Gradle ``mergeLang`` check and ``Namespaces#owns``;
* if exactly one feature module owns it, it goes there; if several do, the module of the
  STRINGS.md section wins; if none does, it goes to ``core`` (core may own anything).

Values are copied verbatim (they already use Java's positional ``%1$s`` placeholders and ``%%``).
The script validates what ``mergeLang`` would reject (placeholder mismatch EN/RU, ``%d`` etc.,
incomplete plural groups, gaps in ``.1…N`` variants, duplicate keys) and fails early.

Keys that exist in a fragment but not in STRINGS.md are KEPT (with a warning) unless ``--prune``
is given, so re-running never destroys work in progress. Values of spec keys are always
overwritten by the spec. Java-only keys that STRINGS.md cannot contain (e.g. the game-rule
category name) live in java/tools/lang_java_only.json and are merged the same way.

Pure Python 3 standard library; re-runnable and deterministic (keys keep STRINGS.md order).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import OrderedDict
from pathlib import Path

MOD_ID = "burmaldaholic"
LANGS = ("en_us", "ru_ru")
PLURALS = ("p1", "p21", "p2", "p5")

JAVA_DIR = Path(__file__).resolve().parent.parent
REPO = JAVA_DIR.parent
STRINGS = REPO / "docs" / "design" / "STRINGS.md"
NAMESPACES = JAVA_DIR / "config" / "namespaces.properties"
LANG_ROOT = JAVA_DIR / "src" / "main" / "lang"
JAVA_ONLY = Path(__file__).resolve().parent / "lang_java_only.json"

ROW = re.compile(r"^\|\s*`([^`]+)`\s*\|(.*)\|(.*)\|\s*$")
SECTION = re.compile(r"^##\s+([A-Za-z0-9_]+)")
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?([A-Za-z%]|$)")


def load_namespaces() -> "OrderedDict[str, list[str]]":
    result: "OrderedDict[str, list[str]]" = OrderedDict()
    for raw in NAMESPACES.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        module, _, extra = line.partition("=")
        result[module.strip()] = [e.strip() for e in extra.split(",") if e.strip()]
    return result


def matches(ns: str, name: str) -> bool:
    return name == ns or name.startswith(ns + "_") or name.startswith(ns + ".") or name.startswith(ns + "/")


def owners(namespaces, key: str) -> list[str]:
    """Feature modules (never core) that own the key, per namespaces.properties."""
    marker = MOD_ID + "."
    idx = key.find(marker)
    if idx < 0:
        return []
    name = key[idx + len(marker):]
    found = []
    for module, extra in namespaces.items():
        if "*" in extra:
            continue
        if any(matches(ns, name) for ns in [module] + extra):
            found.append(module)
    return found


def placeholders(value: str, where: str, errors: list[str]) -> list[str]:
    tokens = []
    for m in PLACEHOLDER.finditer(value):
        idx, conv = m.group(1), m.group(2)
        if conv == "%":
            continue
        if conv != "s":
            errors.append(f"{where}: unsupported format specifier '%{idx + '$' if idx else ''}{conv}'")
            continue
        tokens.append(f"%{idx}$s" if idx else "%s")
    return sorted(tokens)


def parse_strings(errors: list[str]):
    """Returns list of (section, key, en, ru) in file order."""
    rows = []
    section = "core"
    seen = {}
    for lineno, line in enumerate(STRINGS.read_text(encoding="utf-8").splitlines(), 1):
        sm = SECTION.match(line)
        if sm:
            section = sm.group(1).lower()
            continue
        m = ROW.match(line)
        if not m:
            continue
        key, en, ru = m.group(1).strip(), m.group(2).strip(), m.group(3).strip()
        if key in seen:
            errors.append(f"STRINGS.md:{lineno}: duplicate key '{key}' (first at line {seen[key]})")
            continue
        seen[key] = lineno
        if not en or not ru:
            errors.append(f"STRINGS.md:{lineno}: '{key}' has an empty value")
        pe = placeholders(en, f"STRINGS.md:{lineno} '{key}' EN", errors)
        pr = placeholders(ru, f"STRINGS.md:{lineno} '{key}' RU", errors)
        if pe != pr:
            errors.append(f"STRINGS.md:{lineno}: placeholder mismatch for '{key}': EN={pe} RU={pr}")
        rows.append((section, key, en, ru))
    return rows


def check_groups(keys: set[str], errors: list[str]) -> None:
    for key in keys:
        base, _, last = key.rpartition(".")
        if last in PLURALS:
            for sfx in PLURALS:
                if f"{base}.{sfx}" not in keys:
                    errors.append(f"plural group '{base}' lacks '.{sfx}'")
    # Random NPC variants (LOCALIZATION.md §1.2) are the dialog.* keys; other numeric suffixes
    # (card ranks, rules lines) are ordinary keys.
    variants: dict[str, list[int]] = {}
    for key in keys:
        base, _, last = key.rpartition(".")
        if last.isdigit() and key.startswith("dialog."):
            variants.setdefault(base, []).append(int(last))
    for base, nums in variants.items():
        nums.sort()
        if nums != list(range(1, len(nums) + 1)):
            errors.append(f"variant group '{base}' is not numbered 1..N without gaps: {nums}")


def assign(rows, namespaces, errors):
    """-> {module: OrderedDict(key -> (en, ru))}"""
    modules = sorted(p.name for p in LANG_ROOT.iterdir() if p.is_dir())
    out = {m: OrderedDict() for m in modules}
    for section, key, en, ru in rows:
        found = [m for m in owners(namespaces, key) if m in out]
        if len(found) == 1:
            module = found[0]
        elif len(found) > 1:
            module = section if section in found else found[0]
        else:
            module = "core"
        if module not in out:
            errors.append(f"'{key}': module '{module}' has no src/main/lang/{module}/ directory")
            continue
        out[module][key] = (en, ru)
    return out


def load_java_only(namespaces, errors):
    if not JAVA_ONLY.is_file():
        return {}
    data = json.loads(JAVA_ONLY.read_text(encoding="utf-8"))
    result = {}
    for module, entries in data.items():
        if module.startswith("_"):
            continue
        for key, pair in entries.items():
            if not (isinstance(pair, list) and len(pair) == 2):
                errors.append(f"lang_java_only.json '{key}': value must be [english, russian]")
                continue
            result.setdefault(module, OrderedDict())[key] = (pair[0], pair[1])
    return result


def render(entries: "OrderedDict[str, str]") -> str:
    return json.dumps(entries, ensure_ascii=False, indent="\t") + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--check", action="store_true", help="verify only, do not write")
    ap.add_argument("--prune", action="store_true", help="drop keys that are not in STRINGS.md")
    args = ap.parse_args()

    errors: list[str] = []
    namespaces = load_namespaces()
    rows = parse_strings(errors)
    per_module = assign(rows, namespaces, errors)
    for module, entries in load_java_only(namespaces, errors).items():
        for key, pair in entries.items():
            if any(key in e for e in per_module.values()):
                errors.append(f"lang_java_only.json '{key}' is also in STRINGS.md — remove it from the JSON")
            per_module.setdefault(module, OrderedDict())[key] = pair
    check_groups({k for e in per_module.values() for k in e}, errors)
    if errors:
        print("gen_lang: STRINGS.md problems:\n  - " + "\n  - ".join(errors), file=sys.stderr)
        return 2

    spec_keys = {k for e in per_module.values() for k in e}
    stale = []
    total = 0
    for module, entries in sorted(per_module.items()):
        for i, lang in enumerate(LANGS):
            path = LANG_ROOT / module / f"{lang}.json"
            existing = OrderedDict()
            if path.is_file():
                try:
                    existing = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=OrderedDict)
                except json.JSONDecodeError as e:
                    print(f"gen_lang: {path}: invalid JSON ({e}); rewriting", file=sys.stderr)
            merged = OrderedDict((k, v[i]) for k, v in entries.items())
            extras = [k for k in existing if k not in spec_keys]
            for k in extras:
                if args.prune:
                    continue
                merged[k] = existing[k]
                if i == 0:
                    print(f"gen_lang: warning: {module}: '{k}' is not in STRINGS.md (kept; --prune removes it)", file=sys.stderr)
            text = render(merged)
            current = path.read_text(encoding="utf-8") if path.is_file() else None
            if current != text:
                stale.append(str(path.relative_to(REPO)))
                if not args.check:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(text, encoding="utf-8")
        total += len(entries)

    if args.check:
        if stale:
            print("gen_lang: out of date (run python3 java/tools/gen_lang.py):\n  " + "\n  ".join(stale), file=sys.stderr)
            return 1
        print(f"gen_lang: OK — {total} keys in {len(per_module)} modules match STRINGS.md")
        return 0
    counts = ", ".join(f"{m}={len(e)}" for m, e in sorted(per_module.items()))
    print(f"gen_lang: {total} keys per language ({counts}); {len(stale)} file(s) written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
