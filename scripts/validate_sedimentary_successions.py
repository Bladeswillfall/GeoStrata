#!/usr/bin/env python3
"""Validate GeoStrata's metadata-only sedimentary succession contract."""

from __future__ import annotations

import json
import math
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
GEOLOGY = ROOT / "src/main/resources/data/geostrata/geology"
CATALOG = GEOLOGY / "lithologies.json"
PROFILES = GEOLOGY / "province_profiles.json"
SUCCESSIONS = GEOLOGY / "sedimentary_successions.json"


def fail(message: str) -> None:
    print(f"sedimentary succession validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")


def main() -> None:
    catalog = load_json(CATALOG)
    profiles = load_json(PROFILES)
    data = load_json(SUCCESSIONS)

    sedimentary = {
        entry.get("id")
        for entry in catalog.get("lithologies", [])
        if isinstance(entry, dict) and entry.get("rockClass") == "sedimentary"
    }
    all_lithologies = {
        entry.get("id")
        for entry in catalog.get("lithologies", [])
        if isinstance(entry, dict) and isinstance(entry.get("id"), str)
    }
    provinces = {
        entry.get("province")
        for entry in profiles.get("profiles", [])
        if isinstance(entry, dict) and isinstance(entry.get("province"), str)
    }
    if not sedimentary or not provinces:
        fail("catalog/province inputs must contain sedimentary lithologies and province IDs")

    if data.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if data.get("model") != "geostrata:sedimentary_successions":
        fail("unexpected model identifier")
    if data.get("runtimeStatus") != "metadata_only":
        fail("successions must remain metadata_only until a separately tested runtime consumer exists")
    if data.get("order") != "lower_to_upper":
        fail("order must be lower_to_upper")

    successions = data.get("successions")
    if not isinstance(successions, list) or not successions:
        fail("successions must be a non-empty array")

    seen_ids: set[str] = set()
    covered: set[str] = set()
    context_coverage: set[str] = set()

    for succession in successions:
        if not isinstance(succession, dict):
            fail(f"succession entry must be an object: {succession!r}")
        succession_id = succession.get("id")
        if not isinstance(succession_id, str) or not re.fullmatch(r"[a-z0-9_]+", succession_id):
            fail(f"invalid succession id {succession_id!r}")
        if succession_id in seen_ids:
            fail(f"duplicate succession id {succession_id}")
        seen_ids.add(succession_id)

        contexts = succession.get("contexts")
        if not isinstance(contexts, list) or not contexts or not all(isinstance(value, str) for value in contexts):
            fail(f"{succession_id}.contexts must be a non-empty string array")
        if len(contexts) != len(set(contexts)):
            fail(f"{succession_id}.contexts contains duplicates")
        unknown_contexts = set(contexts) - provinces
        if unknown_contexts:
            fail(f"{succession_id} references unknown province contexts {sorted(unknown_contexts)}")
        context_coverage.update(contexts)

        continuity = succession.get("continuity")
        if continuity not in {"local", "regional"}:
            fail(f"{succession_id}.continuity must be local or regional")

        beds = succession.get("beds")
        if not isinstance(beds, list) or len(beds) < 3:
            fail(f"{succession_id}.beds must contain at least three ordered beds")

        distinct: set[str] = set()
        for index, bed in enumerate(beds):
            if not isinstance(bed, dict) or set(bed) != {"lithology", "relativeThickness"}:
                fail(f"{succession_id}.beds[{index}] must contain exactly lithology and relativeThickness")
            lithology = bed.get("lithology")
            if lithology not in all_lithologies:
                fail(f"{succession_id}.beds[{index}] references unknown lithology {lithology!r}")
            if lithology not in sedimentary:
                fail(f"{succession_id}.beds[{index}] uses non-sedimentary lithology {lithology}")
            thickness = bed.get("relativeThickness")
            if not isinstance(thickness, (int, float)) or isinstance(thickness, bool) or not math.isfinite(float(thickness)):
                fail(f"{succession_id}.beds[{index}].relativeThickness must be finite numeric")
            if not 0.1 <= float(thickness) <= 4.0:
                fail(f"{succession_id}.beds[{index}].relativeThickness must be within 0.1..4.0")
            distinct.add(lithology)
            covered.add(lithology)

        if len(distinct) < 2:
            fail(f"{succession_id} must contain at least two distinct lithologies")

    if covered != sedimentary:
        fail(
            "successions must collectively cover every sedimentary lithology exactly as a supported member set; "
            f"missing={sorted(sedimentary - covered)}, extra={sorted(covered - sedimentary)}"
        )
    if "sedimentary_basin" not in context_coverage or "rift_province" not in context_coverage:
        fail("successions must include both sedimentary_basin and rift_province contexts")

    print(
        f"sedimentary succession validation OK: {len(successions)} successions, "
        f"{len(covered)} sedimentary lithologies, {len(context_coverage)} province contexts"
    )


if __name__ == "__main__":
    main()
