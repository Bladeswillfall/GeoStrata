#!/usr/bin/env python3
"""Validate GeoStrata's semantic lithology contract against live resources."""

from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src" / "main" / "resources" / "data" / "geostrata"
CATALOG = DATA / "geology" / "lithologies.json"
BLOCK_TAGS = DATA / "tags" / "blocks"
BIOME_TAGS = DATA / "tags" / "worldgen" / "biome"
CONFIGURED = DATA / "worldgen" / "configured_feature"
PLACED = DATA / "worldgen" / "placed_feature"

ROCK_CLASSES = ("sedimentary", "igneous", "metamorphic")
ALLOWED_BODY_STYLES = {
    "bedded",
    "channel_or_bed",
    "lens_or_bed",
    "metamorphic_band",
    "lens_or_band",
    "basement_massif",
    "flow_or_sheet",
    "volcanic_body",
}
ALLOWED_DEPTH_AFFINITIES = {
    "shallow",
    "shallow_to_mid",
    "mid",
    "mid_to_deep",
    "deep",
    "broad",
}
ALLOWED_CONTINUITY = {"local", "regional"}


def fail(message: str) -> None:
    print(f"geology validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    try:
        with path.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")


def tag_values(path: Path) -> set[str]:
    data = load_json(path)
    values = data.get("values")
    if not isinstance(values, list):
        fail(f"{path.relative_to(ROOT)} must contain a values array")
    direct = {value for value in values if isinstance(value, str) and not value.startswith("#")}
    if len(direct) != len(values):
        fail(f"{path.relative_to(ROOT)} must use direct GeoStrata block IDs for catalog validation")
    return direct


def main() -> None:
    catalog = load_json(CATALOG)
    if catalog.get("schemaVersion") != 1:
        fail("lithology catalog schemaVersion must be 1")
    if catalog.get("model") != "geostrata:lithology_catalog":
        fail("unexpected lithology catalog model identifier")
    if catalog.get("runtimeStatus") != "metadata_only":
        fail("catalog must remain explicit that formation hints are not yet runtime generation rules")

    entries = catalog.get("lithologies")
    if not isinstance(entries, list) or not entries:
        fail("lithology catalog must contain at least one entry")

    all_rocks = tag_values(BLOCK_TAGS / "rocks.json")
    class_blocks = {
        rock_class: tag_values(BLOCK_TAGS / f"{rock_class}_rocks.json")
        for rock_class in ROCK_CLASSES
    }

    union = set().union(*class_blocks.values())
    if union != all_rocks:
        fail(
            "rock class tags must exactly cover geostrata:rocks; "
            f"missing={sorted(all_rocks - union)}, extra={sorted(union - all_rocks)}"
        )

    for rock in all_rocks:
        memberships = [name for name, values in class_blocks.items() if rock in values]
        if len(memberships) != 1:
            fail(f"{rock} must belong to exactly one rock class tag, found {memberships}")

    ids: set[str] = set()
    blocks: set[str] = set()
    features: set[str] = set()
    required_fields = {
        "id", "block", "rockClass", "genesis", "bodyStyle", "depthAffinity",
        "continuity", "biomeTag", "baselineFeature"
    }

    for entry in entries:
        if not isinstance(entry, dict):
            fail(f"catalog entry must be an object: {entry!r}")
        missing = sorted(required_fields - set(entry))
        if missing:
            fail(f"catalog entry is missing fields {missing}: {entry}")

        lithology_id = entry["id"]
        block = entry["block"]
        rock_class = entry["rockClass"]
        feature = entry["baselineFeature"]

        if lithology_id in ids:
            fail(f"duplicate lithology id: {lithology_id}")
        if block in blocks:
            fail(f"duplicate lithology block: {block}")
        if feature in features:
            fail(f"duplicate baseline feature: {feature}")
        ids.add(lithology_id)
        blocks.add(block)
        features.add(feature)

        if block != f"geostrata:{lithology_id}":
            fail(f"{lithology_id} must map to geostrata:{lithology_id}, found {block}")
        if rock_class not in ROCK_CLASSES:
            fail(f"{lithology_id} has unsupported rockClass {rock_class}")
        if block not in class_blocks[rock_class]:
            fail(f"{block} is not present in the {rock_class}_rocks block tag")
        if entry["bodyStyle"] not in ALLOWED_BODY_STYLES:
            fail(f"{lithology_id} has unsupported bodyStyle {entry['bodyStyle']}")
        if entry["depthAffinity"] not in ALLOWED_DEPTH_AFFINITIES:
            fail(f"{lithology_id} has unsupported depthAffinity {entry['depthAffinity']}")
        if entry["continuity"] not in ALLOWED_CONTINUITY:
            fail(f"{lithology_id} has unsupported continuity {entry['continuity']}")

        biome_tag = entry["biomeTag"]
        if not isinstance(biome_tag, str) or not biome_tag.startswith("geostrata:has_"):
            fail(f"{lithology_id} has invalid biomeTag {biome_tag!r}")
        biome_path = BIOME_TAGS / f"{biome_tag.split(':', 1)[1]}.json"
        if not biome_path.is_file():
            fail(f"{lithology_id} references missing biome tag {biome_tag}")

        configured_path = CONFIGURED / f"{feature}.json"
        placed_path = PLACED / f"{feature}.json"
        if not configured_path.is_file() or not placed_path.is_file():
            fail(f"{lithology_id} baseline feature {feature} is missing configured or placed data")

        configured = load_json(configured_path)
        target_states = {
            target.get("state", {}).get("Name")
            for target in configured.get("config", {}).get("targets", [])
            if isinstance(target, dict)
        }
        if target_states != {block}:
            fail(f"{feature} must generate exactly {block}, found {sorted(str(v) for v in target_states)}")

        placed = load_json(placed_path)
        if placed.get("feature") != f"geostrata:{feature}":
            fail(f"placed feature {feature} does not point to geostrata:{feature}")

    if blocks != all_rocks:
        fail(
            "lithology catalog must exactly cover geostrata:rocks; "
            f"missing={sorted(all_rocks - blocks)}, extra={sorted(blocks - all_rocks)}"
        )

    print(f"geology validation OK: {len(entries)} lithologies, {len(ROCK_CLASSES)} rock classes")


if __name__ == "__main__":
    main()
