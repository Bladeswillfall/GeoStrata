#!/usr/bin/env python3
"""Validate GeoStrata strata-lens geometry and placement data before Minecraft decodes it."""

from __future__ import annotations

import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CONFIGURED = ROOT / "src/main/resources/data/geostrata/worldgen/configured_feature"
PLACED = ROOT / "src/main/resources/data/geostrata/worldgen/placed_feature"

FIELDS = {
    "long_radius",
    "short_radius_ratio",
    "short_radius_variation",
    "half_thickness",
    "edge_half_thickness",
    "max_slope",
    "warp_amplitude",
    "warp_variation",
    "warp_wavelength",
}


def fail(message: str) -> None:
    print(f"strata lens validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")


def finite_number(value, name: str, path: Path) -> float:
    if not isinstance(value, (int, float)) or isinstance(value, bool) or not math.isfinite(float(value)):
        fail(f"{path.relative_to(ROOT)} field {name} must be a finite number")
    return float(value)


def validate_placement(configured_path: Path) -> None:
    placed_path = PLACED / configured_path.name
    if not placed_path.is_file():
        fail(f"{configured_path.relative_to(ROOT)} has no matching placed feature {placed_path.relative_to(ROOT)}")

    data = load_json(placed_path)
    expected_feature = f"geostrata:{configured_path.stem}"
    if data.get("feature") != expected_feature:
        fail(f"{placed_path.relative_to(ROOT)} must reference {expected_feature}")

    placement = data.get("placement")
    if not isinstance(placement, list) or not placement:
        fail(f"{placed_path.relative_to(ROOT)} placement must be a non-empty array")
    if not all(isinstance(entry, dict) for entry in placement):
        fail(f"{placed_path.relative_to(ROOT)} placement entries must be objects")

    types = [entry.get("type") for entry in placement]
    for required in ("minecraft:count", "minecraft:in_square", "minecraft:height_range", "minecraft:biome"):
        if types.count(required) != 1:
            fail(f"{placed_path.relative_to(ROOT)} must contain exactly one {required} placement modifier")

    count_entry = placement[types.index("minecraft:count")]
    count = count_entry.get("count")
    if not isinstance(count, int) or isinstance(count, bool) or not 1 <= count <= 8:
        fail(f"{placed_path.relative_to(ROOT)} count must be an integer from 1 to 8")

    height_entry = placement[types.index("minecraft:height_range")]
    height = height_entry.get("height")
    if not isinstance(height, dict) or not isinstance(height.get("type"), str):
        fail(f"{placed_path.relative_to(ROOT)} height_range must contain a typed height provider")


def main() -> None:
    found = 0
    for path in sorted(CONFIGURED.glob("*.json")):
        data = load_json(path)
        if data.get("type") != "geostrata:strata_lens":
            continue
        found += 1
        config = data.get("config")
        if not isinstance(config, dict):
            fail(f"{path.relative_to(ROOT)} config must be an object")

        missing = sorted(FIELDS - set(config))
        if missing:
            fail(f"{path.relative_to(ROOT)} is missing geometry fields {missing}")
        if "size" in config:
            fail(f"{path.relative_to(ROOT)} must not encode lens geometry through legacy ore size")

        targets = config.get("targets")
        if not isinstance(targets, list) or not targets:
            fail(f"{path.relative_to(ROOT)} must contain at least one replacement target")
        discard = finite_number(config.get("discard_chance_on_air_exposure"), "discard_chance_on_air_exposure", path)
        if not 0.0 <= discard <= 1.0:
            fail(f"{path.relative_to(ROOT)} discard chance must be between 0 and 1")

        radius = config.get("long_radius")
        if not isinstance(radius, int) or isinstance(radius, bool) or not 4 <= radius <= 64:
            fail(f"{path.relative_to(ROOT)} long_radius must be an integer from 4 to 64")

        ratio = finite_number(config.get("short_radius_ratio"), "short_radius_ratio", path)
        ratio_var = finite_number(config.get("short_radius_variation"), "short_radius_variation", path)
        if not 0.2 <= ratio <= 1.0 or not 0.0 <= ratio_var <= 0.4:
            fail(f"{path.relative_to(ROOT)} has invalid short-radius ratio/variation")
        if ratio - ratio_var < 0.2 or ratio + ratio_var > 1.0:
            fail(f"{path.relative_to(ROOT)} short-radius variation leaves the supported 0.2..1.0 range")

        thickness = finite_number(config.get("half_thickness"), "half_thickness", path)
        edge = finite_number(config.get("edge_half_thickness"), "edge_half_thickness", path)
        if not 0.5 <= thickness <= 12.0 or not 0.25 <= edge <= thickness:
            fail(f"{path.relative_to(ROOT)} has invalid thickness values")

        slope = finite_number(config.get("max_slope"), "max_slope", path)
        if not 0.0 <= slope <= 0.5:
            fail(f"{path.relative_to(ROOT)} max_slope must be between 0 and 0.5")

        warp = finite_number(config.get("warp_amplitude"), "warp_amplitude", path)
        warp_var = finite_number(config.get("warp_variation"), "warp_variation", path)
        wavelength = finite_number(config.get("warp_wavelength"), "warp_wavelength", path)
        if not 0.0 <= warp <= 4.0 or not 0.0 <= warp_var <= warp:
            fail(f"{path.relative_to(ROOT)} has invalid warp amplitude/variation")
        if not 2.0 <= wavelength <= 64.0:
            fail(f"{path.relative_to(ROOT)} warp_wavelength must be between 2 and 64")

        validate_placement(path)

    if found == 0:
        fail("no geostrata:strata_lens configured features were found")
    print(f"strata lens validation OK: {found} data-driven lens feature(s) with matching placed features")


if __name__ == "__main__":
    main()
