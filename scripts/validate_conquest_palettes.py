#!/usr/bin/env python3
"""Validate the optional Conquest Reforged material-palette contract."""

from __future__ import annotations

import csv
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
LITHOLOGIES = ROOT / "src/main/resources/data/geostrata/geology/lithologies.json"
PALETTES = ROOT / "compat/conquest/material-palettes.json"
CATALOG = ROOT / "compat/conquest/reference/conquest_wp.csv"
JAVA_ROOT = ROOT / "src/main/java"
ROLES = ("geology", "weathered", "rubble", "construction")
STATUSES = {"exact", "family", "partial", "unmapped"}


def fail(message: str) -> None:
    print(f"Conquest palette validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")


def load_catalog() -> dict[str, dict[str, str]]:
    try:
        with CATALOG.open("r", encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle))
    except (OSError, csv.Error) as exc:
        fail(f"cannot read {CATALOG.relative_to(ROOT)}: {exc}")

    if not rows or "name" not in rows[0]:
        fail("Conquest reference CSV does not contain the expected name column")

    result: dict[str, dict[str, str]] = {}
    for row in rows:
        name = row.get("name", "")
        if not name:
            continue
        if name in result:
            fail(f"duplicate Conquest registry name in reference catalog: {name}")
        result[name] = row
    return result


def validate_core_isolation() -> None:
    leaks = []
    for path in JAVA_ROOT.rglob("*.java"):
        text = path.read_text(encoding="utf-8").lower()
        if "conquest" in text:
            leaks.append(str(path.relative_to(ROOT)))
    if leaks:
        fail("Conquest references leaked into standalone core Java: " + ", ".join(sorted(leaks)))


def main() -> None:
    lithology_data = load_json(LITHOLOGIES)
    palette_data = load_json(PALETTES)
    catalog = load_catalog()

    if palette_data.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if palette_data.get("model") != "geostrata:conquest_material_palettes":
        fail("unexpected palette model identifier")
    if palette_data.get("runtimeStatus") != "compatibility_metadata":
        fail("Conquest palette contract must remain compatibility_metadata until a guarded optional consumer is added")

    target = palette_data.get("targetMod") or {}
    if target.get("id") != "conquest" or target.get("baselineVersion") != "1.7.0":
        fail("palette contract must target Conquest Reforged 1.7.0 baseline")
    if target.get("referenceCatalog") != "compat/conquest/reference/conquest_wp.csv":
        fail("palette contract must reference the checked-in Conquest registry export")

    lithology_ids = {
        entry.get("id")
        for entry in lithology_data.get("lithologies", [])
        if isinstance(entry, dict) and isinstance(entry.get("id"), str)
    }
    entries = palette_data.get("lithologies")
    if not isinstance(entries, list):
        fail("lithologies must be an array")

    seen_ids: set[str] = set()
    mapped_blocks: set[str] = set()
    status_counts = {status: 0 for status in STATUSES}

    for entry in entries:
        if not isinstance(entry, dict):
            fail(f"palette lithology entry must be an object: {entry!r}")
        lithology_id = entry.get("id")
        status = entry.get("status")
        reason = entry.get("reason")
        palette = entry.get("palette")

        if lithology_id not in lithology_ids:
            fail(f"unknown GeoStrata lithology in Conquest palette: {lithology_id!r}")
        if lithology_id in seen_ids:
            fail(f"duplicate Conquest palette lithology: {lithology_id}")
        seen_ids.add(lithology_id)
        if status not in STATUSES:
            fail(f"{lithology_id} has unsupported status {status!r}")
        status_counts[status] += 1
        if not isinstance(reason, str) or not reason.strip():
            fail(f"{lithology_id} must explain its mapping decision")
        if not isinstance(palette, dict) or set(palette) != set(ROLES):
            fail(f"{lithology_id} palette must contain exactly {ROLES}")

        role_values: dict[str, list[str]] = {}
        for role in ROLES:
            values = palette.get(role)
            if not isinstance(values, list) or not all(isinstance(value, str) for value in values):
                fail(f"{lithology_id}.{role} must be an array of registry IDs")
            if len(values) != len(set(values)):
                fail(f"{lithology_id}.{role} contains duplicate registry IDs")
            role_values[role] = values

            for block_id in values:
                if not re.fullmatch(r"conquest:[a-z0-9_]+", block_id):
                    fail(f"{lithology_id}.{role} has invalid/non-Conquest block ID {block_id!r}")
                row = catalog.get(block_id)
                if row is None:
                    fail(f"{lithology_id}.{role} references missing Conquest block {block_id}")
                if role in {"geology", "weathered", "construction"} and row.get("properties", "").strip():
                    fail(f"{lithology_id}.{role} must use a base material block with no required block-state properties: {block_id}")
                mapped_blocks.add(block_id)

        all_values = [value for role in ROLES for value in role_values[role]]
        if len(all_values) != len(set(all_values)):
            fail(f"{lithology_id} maps the same Conquest block into multiple semantic roles")

        if status in {"exact", "family"} and not role_values["geology"]:
            fail(f"{lithology_id} status {status} requires at least one geology material")
        if status == "partial" and not any(role_values[role] for role in ("weathered", "rubble", "construction")):
            fail(f"{lithology_id} partial mapping must provide a non-geology compatibility role")
        if status == "partial" and role_values["geology"]:
            fail(f"{lithology_id} partial mapping must not claim a solid geology substitute")
        if status == "unmapped" and all_values:
            fail(f"{lithology_id} unmapped status must have empty palettes")

    if seen_ids != lithology_ids:
        fail(
            "Conquest palette must explicitly classify every live GeoStrata lithology; "
            f"missing={sorted(lithology_ids - seen_ids)}, extra={sorted(seen_ids - lithology_ids)}"
        )

    validate_core_isolation()
    print(
        "Conquest palette validation OK: "
        f"{len(seen_ids)} lithologies, {len(mapped_blocks)} registry IDs, "
        + ", ".join(f"{status}={status_counts[status]}" for status in sorted(STATUSES))
    )


if __name__ == "__main__":
    main()
