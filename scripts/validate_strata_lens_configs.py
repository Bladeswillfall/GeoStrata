#!/usr/bin/env python3
"""Validate GeoStrata strata-lens geometry data before Minecraft decodes it."""

from __future__ import annotations

import json
import math
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
CONFIGURED = ROOT / "src/main/resources/data/geostrata/worldgen/configured_feature"

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


def finite_number(value, name: str, path: Path) -> float:
    if not isinstance(value, (int, float)) or isinstance(value, bool) or not math.isfinite(float(value)):
        fail(f"{path.relative_to(ROOT)} field {name} must be a finite number")
    return float(value)


def main() -> None:
    found = 0
    for path in sorted(CONFIGURED.glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            fail(f"cannot read {path.relative_to(ROOT)}: {exc}")
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

    if found == 0:
        fail("no geostrata:strata_lens configured features were found")
    print(f"strata lens validation OK: {found} data-driven lens feature(s)")


if __name__ == "__main__":
    main()
