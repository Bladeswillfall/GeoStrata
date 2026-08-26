#!/usr/bin/env python3
"""Validate the archived first-pass GeoStrata data-contract prototype."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent / "data" / "geostrata"


def load(path: Path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def main():
    grade_schema = load(ROOT / "ores" / "ore_grade_schema.json")
    allowed_grades = {g["id"] for g in grade_schema["grades"]}
    if allowed_grades != {"poor", "medium", "rich", "massive"}:
        raise SystemExit(f"Unexpected grade set: {sorted(allowed_grades)}")

    materials = load(ROOT / "materials" / "material_authority.json")
    shared_materials = set(materials["shared_materials"])

    controlled = load(ROOT / "ores" / "controlled_materials.json")
    controlled_materials = {e["material"] for e in controlled["authoritative_overworld_materials"]}
    if controlled_materials != shared_materials:
        raise SystemExit(
            f"Controlled materials {sorted(controlled_materials)} do not match shared materials {sorted(shared_materials)}"
        )

    deposit_dir = ROOT / "ores" / "deposits"
    deposit_files = sorted(deposit_dir.glob("*.json"))
    if not deposit_files:
        raise SystemExit("No deposit profile files found")

    for deposit_file in deposit_files:
        profile = load(deposit_file)
        mat = profile["material"]
        if mat not in shared_materials:
            raise SystemExit(f"Deposit profile {deposit_file.name} uses unknown material: {mat}")

        weights = profile["grade_weights"]
        if set(weights.keys()) != allowed_grades:
            raise SystemExit(f"Deposit profile {deposit_file.name} grades mismatch: {sorted(weights.keys())}")

        total_weight = sum(weights.values())
        if total_weight != 100:
            raise SystemExit(f"Deposit profile {deposit_file.name} grade weights must sum to 100, got {total_weight}")

    layer_profile = load(ROOT / "geology" / "layers" / "overworld_layers.json")
    layers = layer_profile["layers"]
    if not layers:
        raise SystemExit("No geology layers found")

    layer_ids = set()
    for layer in layers:
        layer_id = layer["id"]
        layer_ids.add(layer_id)
        y_min = layer["y_min"]
        y_max = layer["y_max"]
        if y_min > y_max:
            raise SystemExit(f"Layer {layer_id} has invalid range: y_min({y_min}) > y_max({y_max})")
        if not layer["allowed_blocks"]:
            raise SystemExit(f"Layer {layer_id} has empty allowed_blocks")

    block_policy = load(ROOT / "blocks" / "block_usage_policy.json")
    for zone in block_policy["zones"]:
        if not zone["preferred_blocks"]:
            raise SystemExit(f"Zone {zone['zone']} has empty preferred_blocks")
        for layer_ref in zone["layers"]:
            if layer_ref not in layer_ids:
                raise SystemExit(f"Zone {zone['zone']} references unknown layer {layer_ref}")

    host_rules = load(ROOT / "ores" / "hosts" / "host_rock_rules.json")
    if host_rules.get("cave_generation_interaction") != "none":
        raise SystemExit("cave_generation_interaction must remain 'none' for v0.1")

    host_materials = set()
    for entry in host_rules["materials"]:
        mat = entry["material"]
        host_materials.add(mat)
        if mat not in shared_materials:
            raise SystemExit(f"Host rules contain unknown material: {mat}")
        if not entry["host_blocks"]:
            raise SystemExit(f"Host rules for {mat} have empty host_blocks")
        for layer_ref in entry["host_layers"]:
            if layer_ref not in layer_ids:
                raise SystemExit(f"Host rules for {mat} reference unknown layer {layer_ref}")

    if host_materials != shared_materials:
        raise SystemExit(
            f"Host rule materials {sorted(host_materials)} do not match shared materials {sorted(shared_materials)}"
        )

    print("Archived GeoStrata prototype data validation passed")


if __name__ == "__main__":
    main()
