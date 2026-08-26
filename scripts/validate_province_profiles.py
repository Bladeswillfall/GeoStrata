#!/usr/bin/env python3
"""Validate GeoStrata's province-to-lithology suitability contract."""

from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
GEOLOGY = ROOT / "src" / "main" / "resources" / "data" / "geostrata" / "geology"
CATALOG = GEOLOGY / "lithologies.json"
PROFILES = GEOLOGY / "province_profiles.json"

PROVINCES = {
    "sedimentary_basin",
    "cratonic_shield",
    "orogenic_belt",
    "volcanic_arc",
    "rift_province",
}


def fail(message: str) -> None:
    print(f"province profile validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    try:
        with path.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")


def main() -> None:
    catalog = load_json(CATALOG)
    lithologies = catalog.get("lithologies")
    if not isinstance(lithologies, list) or not lithologies:
        fail("lithology catalog has no entries")
    lithology_ids = {entry.get("id") for entry in lithologies if isinstance(entry, dict)}
    if None in lithology_ids or len(lithology_ids) != len(lithologies):
        fail("lithology catalog IDs are missing or duplicated")

    data = load_json(PROFILES)
    if data.get("schemaVersion") != 1:
        fail("province profile schemaVersion must be 1")
    if data.get("model") != "geostrata:province_profiles":
        fail("unexpected province profile model identifier")
    if data.get("runtimeStatus") != "metadata_only":
        fail("province profiles must remain metadata-only until a reviewed runtime consumer is added")

    blend_width = data.get("blendWidthBlocks")
    if not isinstance(blend_width, int) or isinstance(blend_width, bool) or not 1 <= blend_width <= 384:
        fail("blendWidthBlocks must be an integer from 1 to 384")

    profiles = data.get("profiles")
    if not isinstance(profiles, list):
        fail("profiles must be an array")

    seen: set[str] = set()
    max_weight = {lithology: 0.0 for lithology in lithology_ids}

    for profile in profiles:
        if not isinstance(profile, dict):
            fail(f"profile must be an object: {profile!r}")
        province = profile.get("province")
        if province not in PROVINCES:
            fail(f"unsupported province {province!r}")
        if province in seen:
            fail(f"duplicate province profile {province}")
        seen.add(province)

        weights = profile.get("lithologyWeights")
        if not isinstance(weights, dict):
            fail(f"{province} lithologyWeights must be an object")
        weight_ids = set(weights)
        if weight_ids != lithology_ids:
            fail(
                f"{province} must cover every live lithology exactly; "
                f"missing={sorted(lithology_ids - weight_ids)}, extra={sorted(weight_ids - lithology_ids)}"
            )

        characteristic = 0
        for lithology, weight in weights.items():
            if isinstance(weight, bool) or not isinstance(weight, (int, float)):
                fail(f"{province}/{lithology} weight must be numeric")
            if not 0.0 < float(weight) <= 1.0:
                fail(f"{province}/{lithology} weight must be > 0 and <= 1; zero would create a hard province wall")
            max_weight[lithology] = max(max_weight[lithology], float(weight))
            if float(weight) >= 0.65:
                characteristic += 1

        if characteristic < 3:
            fail(f"{province} must have at least three characteristic lithologies with weight >= 0.65")

    if seen != PROVINCES:
        fail(f"profiles must exactly cover province IDs; missing={sorted(PROVINCES - seen)}, extra={sorted(seen - PROVINCES)}")

    never_characteristic = sorted(lithology for lithology, weight in max_weight.items() if weight < 0.65)
    if never_characteristic:
        fail(f"every lithology must be characteristic of at least one province: {never_characteristic}")

    print(
        f"province profile validation OK: {len(profiles)} provinces, "
        f"{len(lithology_ids)} lithologies, {blend_width}-block blend width"
    )


if __name__ == "__main__":
    main()
