#!/usr/bin/env python3
"""Validate diagnostic physical structural transform profiles."""

from __future__ import annotations

import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / "src/main/resources/data/geostrata/geology/structural_transform_profiles.json"
PROVINCES = {
    "sedimentary_basin",
    "cratonic_shield",
    "orogenic_belt",
    "volcanic_arc",
    "rift_province",
}


def fail(message: str) -> None:
    print(f"structural transform profile validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def number(value, label: str) -> float:
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        fail(f"{label} must be numeric")
    parsed = float(value)
    if not math.isfinite(parsed):
        fail(f"{label} must be finite")
    return parsed


def bounded(value, minimum: float, maximum: float, label: str) -> float:
    parsed = number(value, label)
    if not minimum <= parsed <= maximum:
        fail(f"{label} must be within {minimum}..{maximum}")
    return parsed


def main() -> None:
    try:
        root = json.loads(RESOURCE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {RESOURCE.relative_to(ROOT)}: {exc}")

    if root.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if root.get("model") != "geostrata:structural_transform_profiles":
        fail("unexpected model identifier")
    if root.get("runtimeStatus") != "metadata_only":
        fail("structural transform profiles must remain metadata_only")

    profiles = root.get("profiles")
    if not isinstance(profiles, list) or not profiles:
        fail("profiles must be a non-empty array")

    seen: set[str] = set()
    for profile in profiles:
        if not isinstance(profile, dict):
            fail("profile entries must be objects")
        province = profile.get("province")
        if province not in PROVINCES:
            fail(f"unsupported province {province!r}")
        if province in seen:
            fail(f"duplicate transform profile for {province}")
        seen.add(province)

        dip = bounded(profile.get("maxDipDegrees"), 0.0, 75.0, f"{province}.maxDipDegrees")
        amplitude = bounded(
            profile.get("maxFoldAmplitudeBlocks"),
            0.0,
            96.0,
            f"{province}.maxFoldAmplitudeBlocks",
        )
        wavelength = bounded(
            profile.get("foldWavelengthBlocks"),
            64.0,
            2048.0,
            f"{province}.foldWavelengthBlocks",
        )
        bounded(
            profile.get("maxFaultDisplacementBlocks"),
            0.0,
            128.0,
            f"{province}.maxFaultDisplacementBlocks",
        )
        bounded(
            profile.get("faultPlaneOffsetRangeBlocks"),
            32.0,
            384.0,
            f"{province}.faultPlaneOffsetRangeBlocks",
        )
        if wavelength < amplitude * 4.0:
            fail(f"{province} fold wavelength must be at least four times amplitude")
        if dip >= 75.0 and amplitude > 0.0:
            fail(f"{province} cannot combine the absolute dip ceiling with folded staging")

    if seen != PROVINCES:
        fail(
            "profiles must exactly cover province IDs; "
            f"missing={sorted(PROVINCES - seen)}, extra={sorted(seen - PROVINCES)}"
        )

    print(
        "structural transform profile validation OK: "
        f"{len(profiles)} provinces, metadata-only dip/fold/fault scales"
    )


if __name__ == "__main__":
    main()
