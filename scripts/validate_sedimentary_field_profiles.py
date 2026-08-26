#!/usr/bin/env python3
"""Validate diagnostic sedimentary spatial-field profiles against succession metadata."""

from __future__ import annotations

import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
GEOLOGY = ROOT / "src/main/resources/data/geostrata/geology"
SUCCESSIONS = GEOLOGY / "sedimentary_successions.json"
FIELD_PROFILES = GEOLOGY / "sedimentary_field_profiles.json"


def fail(message: str) -> None:
    print(f"sedimentary field profile validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")


def number(value, label: str) -> float:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        fail(f"{label} must be numeric")
    parsed = float(value)
    if not math.isfinite(parsed):
        fail(f"{label} must be finite")
    return parsed


def main() -> None:
    successions_root = load_json(SUCCESSIONS)
    if successions_root.get("schemaVersion") != 1 or successions_root.get("model") != "geostrata:sedimentary_successions":
        fail("unexpected sedimentary succession schema")
    successions = successions_root.get("successions")
    if not isinstance(successions, list) or not successions:
        fail("sedimentary succession metadata must contain successions")

    continuity_values: set[str] = set()
    for succession in successions:
        if not isinstance(succession, dict):
            fail("succession entries must be objects")
        continuity = succession.get("continuity")
        if not isinstance(continuity, str) or not continuity:
            fail("every succession must declare continuity")
        continuity_values.add(continuity)

    root = load_json(FIELD_PROFILES)
    if root.get("schemaVersion") != 1:
        fail("field profile schemaVersion must be 1")
    if root.get("model") != "geostrata:sedimentary_field_profiles":
        fail("unexpected field profile model identifier")
    if root.get("runtimeStatus") != "metadata_only":
        fail("field profiles must remain metadata_only until a reviewed runtime consumer is enabled")

    raw_profiles = root.get("profiles")
    if not isinstance(raw_profiles, list) or not raw_profiles:
        fail("field profiles must contain a non-empty profiles array")

    profiles: dict[str, dict] = {}
    required = {
        "continuity",
        "cycleThicknessBlocks",
        "maxDip",
        "warpAmplitudeBlocks",
        "warpWavelengthBlocks",
    }
    for profile in raw_profiles:
        if not isinstance(profile, dict):
            fail("field profile entry must be an object")
        missing = sorted(required - set(profile))
        if missing:
            fail(f"field profile is missing fields {missing}: {profile}")

        continuity = profile["continuity"]
        if not isinstance(continuity, str) or not continuity:
            fail("field profile continuity must be a non-empty string")
        if continuity in profiles:
            fail(f"duplicate field profile continuity: {continuity}")
        if continuity not in continuity_values:
            fail(f"field profile references unused succession continuity: {continuity}")

        cycle = number(profile["cycleThicknessBlocks"], f"{continuity}.cycleThicknessBlocks")
        dip = number(profile["maxDip"], f"{continuity}.maxDip")
        warp = number(profile["warpAmplitudeBlocks"], f"{continuity}.warpAmplitudeBlocks")
        wavelength = number(profile["warpWavelengthBlocks"], f"{continuity}.warpWavelengthBlocks")

        if not 8.0 <= cycle <= 256.0:
            fail(f"{continuity} cycleThicknessBlocks must be within 8..256")
        if not 0.0 <= dip <= 0.35:
            fail(f"{continuity} maxDip must be within 0..0.35")
        if not 0.0 <= warp <= cycle * 0.25:
            fail(f"{continuity} warpAmplitudeBlocks must be within 0..25% of cycle thickness")
        if not cycle * 2.0 <= wavelength <= 2048.0:
            fail(f"{continuity} warpWavelengthBlocks must be at least two cycles and at most 2048 blocks")

        profiles[continuity] = profile

    if set(profiles) != continuity_values:
        fail(
            "field profiles must exactly cover succession continuity values; "
            f"missing={sorted(continuity_values - set(profiles))}, "
            f"extra={sorted(set(profiles) - continuity_values)}"
        )

    for succession in successions:
        succession_id = succession.get("id", "<unknown>")
        continuity = succession["continuity"]
        beds = succession.get("beds")
        if not isinstance(beds, list) or not beds:
            fail(f"{succession_id} must contain beds")
        thicknesses = [
            number(bed.get("relativeThickness") if isinstance(bed, dict) else None,
                   f"{succession_id}.relativeThickness")
            for bed in beds
        ]
        if any(value <= 0.0 for value in thicknesses):
            fail(f"{succession_id} relative thicknesses must be positive")
        total = sum(thicknesses)
        cycle = float(profiles[continuity]["cycleThicknessBlocks"])
        thinnest_virtual_bed = min(value / total * cycle for value in thicknesses)
        if thinnest_virtual_bed < 2.0:
            fail(
                f"{succession_id} would compress its thinnest bed to "
                f"{thinnest_virtual_bed:.2f} blocks under the {continuity} profile"
            )

    print(
        "sedimentary field profile validation OK: "
        f"{len(profiles)} continuity profile(s), {len(successions)} succession(s)"
    )


if __name__ == "__main__":
    main()
