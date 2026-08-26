#!/usr/bin/env python3
"""Validate data-only optional-mod bridges shipped by GeoStrata core."""

from __future__ import annotations

import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCKS_JAVA = ROOT / "src/main/java/com/geostrata/block/GeoStrataBlocks.java"
STREAMS_STYLES = ROOT / "src/main/resources/data/geostrata/streamsreflowing/bank_style"


def fail(message: str) -> None:
    print(f"optional integration validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def registered_blocks() -> set[str]:
    try:
        source = BLOCKS_JAVA.read_text(encoding="utf-8")
    except OSError as exc:
        fail(f"cannot read {BLOCKS_JAVA.relative_to(ROOT)}: {exc}")

    names = set(re.findall(r'register(?:Rock|RockVariant|Earth)\("([a-z0-9_]+)"', source))
    if not names:
        fail("could not discover GeoStrata block registrations")
    return {f"geostrata:{name}" for name in names}


def block_values(value, field: str, path: Path) -> list[str]:
    if isinstance(value, str):
        values = [value]
    elif isinstance(value, list) and value and all(isinstance(item, str) for item in value):
        values = value
    else:
        fail(f"{path.relative_to(ROOT)} field {field} must be a block id or non-empty block-id list")

    for block in values:
        if ":" not in block:
            fail(f"{path.relative_to(ROOT)} field {field} has non-namespaced block id {block!r}")
    return values


def validate_streams_reflowing() -> None:
    if not STREAMS_STYLES.is_dir():
        fail("Streams Reflowing bridge directory is missing")

    blocks = registered_blocks()
    paths = sorted(STREAMS_STYLES.glob("*.json"))
    expected = {"fluvial_sediments.json", "swamp_sediments.json", "jungle_sediments.json"}
    if {path.name for path in paths} != expected:
        fail(f"Streams Reflowing bridge files must be exactly {sorted(expected)}")

    seen_geostrata_blocks: set[str] = set()
    for path in paths:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            fail(f"cannot read {path.relative_to(ROOT)}: {exc}")

        if not isinstance(data, dict):
            fail(f"{path.relative_to(ROOT)} must contain a JSON object")
        if "biomes" in data:
            fail(f"{path.relative_to(ROOT)} must not contest Streams Reflowing exact-biome styles")
        if data.get("bank_enabled") is not False:
            fail(f"{path.relative_to(ROOT)} must leave cut banks natural (bank_enabled=false)")
        if "bank" in data:
            fail(f"{path.relative_to(ROOT)} must not paint a bank material over generated geology")

        tags = data.get("tags")
        if not isinstance(tags, list) or len(tags) < 2 or not all(isinstance(tag, str) for tag in tags):
            fail(f"{path.relative_to(ROOT)} must use at least two biome tags for selector specificity")
        if "minecraft:is_overworld" not in tags:
            fail(f"{path.relative_to(ROOT)} must explicitly stay within overworld biomes")
        geostrata_tags = [tag for tag in tags if tag.startswith("geostrata:has_")]
        if len(geostrata_tags) != 1:
            fail(f"{path.relative_to(ROOT)} must use exactly one GeoStrata biome extension tag")

        for field in ("bed", "waterline"):
            for block in block_values(data.get(field), field, path):
                if block.startswith("geostrata:"):
                    if block not in blocks:
                        fail(f"{path.relative_to(ROOT)} references unknown GeoStrata block {block}")
                    seen_geostrata_blocks.add(block)
                elif not block.startswith("minecraft:"):
                    fail(f"{path.relative_to(ROOT)} must not hard-reference another optional mod block {block}")

        for field in ("waterline_below", "waterline_above"):
            value = data.get(field)
            if not isinstance(value, int) or isinstance(value, bool) or not 0 <= value <= 4:
                fail(f"{path.relative_to(ROOT)} field {field} must be an integer from 0 to 4")
        for field in ("underwater_noise", "above_water_noise"):
            value = data.get(field)
            if not isinstance(value, (int, float)) or isinstance(value, bool) or not 0.0 <= float(value) <= 0.5:
                fail(f"{path.relative_to(ROOT)} field {field} must be between 0.0 and 0.5")

    if not seen_geostrata_blocks:
        fail("Streams Reflowing bridge does not actually use any GeoStrata material")
    print(
        f"optional integration validation OK: {len(paths)} Streams Reflowing bank styles, "
        f"{len(seen_geostrata_blocks)} GeoStrata sediment/soil blocks referenced"
    )


if __name__ == "__main__":
    validate_streams_reflowing()
