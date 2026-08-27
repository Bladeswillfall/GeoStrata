#!/usr/bin/env python3
"""Validate diagnostic terrain-aware province deformation profiles."""

from __future__ import annotations

import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
PROFILES = ROOT / "src/main/resources/data/geostrata/geology/province_deformation_profiles.json"
PROVINCES = {
    "sedimentary_basin",
    "cratonic_shield",
    "orogenic_belt",
    "volcanic_arc",
    "rift_province",
}


def fail(message: str) -> None:
    print(f"province deformation profile validation failed: {message}", file=sys.stderr)
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


def unit(value, label: str) -> float:
    parsed = number(value, label)
    if not 0.0 <= parsed <= 1.0:
        fail(f"{label} must be within 0..1")
    return parsed


def main() -> None:
    root = load_json(PROFILES)
    if root.get("schemaVersion") != 1:
        fail("schemaVersion must be 1")
    if root.get("model") != "geostrata:province_deformation_profiles":
        fail("unexpected model identifier")
    if root.get("runtimeStatus") != "metadata_only":
        fail("province deformation profiles must remain metadata_only until runtime deformation is reviewed")

    normalization = root.get("morphologyNormalization")
    if not isinstance(normalization, dict):
        fail("morphologyNormalization must be an object")

    relief_scale = number(normalization.get("reliefScaleBlocks"), "reliefScaleBlocks")
    slope_scale = number(normalization.get("slopeScale"), "slopeScale")
    ridge_scale = number(normalization.get("ridgeProminenceScaleBlocks"), "ridgeProminenceScaleBlocks")
    if relief_scale <= 0.0 or slope_scale <= 0.0 or ridge_scale <= 0.0:
        fail("morphology normalization scales must be positive")

    relief_weight = unit(normalization.get("reliefWeight"), "reliefWeight")
    slope_weight = unit(normalization.get("slopeWeight"), "slopeWeight")
    ridge_weight = unit(normalization.get("ridgeWeight"), "ridgeWeight")
    if not math.isclose(relief_weight + slope_weight + ridge_weight, 1.0, abs_tol=1.0e-9):
        fail("morphology normalization weights must sum to 1.0")

    raw_profiles = root.get("profiles")
    if not isinstance(raw_profiles, list) or not raw_profiles:
        fail("profiles must be a non-empty array")

    seen: set[str] = set()
    fields = {
        "baselineIntensity",
        "terrainCoupling",
        "dipPotential",
        "foldPotential",
        "faultPotential",
    }
    for profile in raw_profiles:
        if not isinstance(profile, dict):
            fail("profile entries must be objects")
        province = profile.get("province")
        if province not in PROVINCES:
            fail(f"unsupported province {province!r}")
        if province in seen:
            fail(f"duplicate deformation profile for {province}")
        seen.add(province)

        values = {field: unit(profile.get(field), f"{province}.{field}") for field in fields}
        if values["baselineIntensity"] + values["terrainCoupling"] > 1.0 + 1.0e-9:
            fail(f"{province} baselineIntensity + terrainCoupling must not exceed 1.0")

    if seen != PROVINCES:
        fail(
            "profiles must exactly cover province IDs; "
            f"missing={sorted(PROVINCES - seen)}, extra={sorted(seen - PROVINCES)}"
        )

    print(
        "province deformation profile validation OK: "
        f"{len(raw_profiles)} provinces, metadata-only normalized response contract"
    )


if __name__ == "__main__":
    main()
