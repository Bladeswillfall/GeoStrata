#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "geostrata_mod_src" / "data" / "geostrata"


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
    zone_names = set()
    for zone in block_policy["zones"]:
        zone_names.add(zone["zone"])
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

    remap = load(ROOT / "remap" / "conquest_reforged_remap_profile.json")
    if remap.get("enabled_when_mod_loaded") != "conquest_reforged":
        raise SystemExit("CR remap profile must target conquest_reforged")

    for entry in remap["layer_remaps"]:
        layer_ref = entry["layer"]
        if layer_ref not in layer_ids:
            raise SystemExit(f"Remap references unknown layer {layer_ref}")
        for zone_ref in entry["zone_bias"]:
            if zone_ref not in zone_names:
                raise SystemExit(f"Remap for {layer_ref} references unknown zone {zone_ref}")
        if not entry["fallback_blocks"]:
            raise SystemExit(f"Remap for {layer_ref} has empty fallback_blocks")

        preferred_tag = entry["preferred_tag"]
        if not preferred_tag.startswith("#geostrata:"):
            raise SystemExit(f"Remap for {layer_ref} has invalid preferred_tag namespace: {preferred_tag}")
        tag_name = preferred_tag.replace("#geostrata:", "")
        tag_file = ROOT / "tags" / "blocks" / f"{tag_name}.json"
        if not tag_file.exists():
            raise SystemExit(f"Remap for {layer_ref} references missing tag file {tag_file}")

    zone_palette = load(ROOT / "remap" / "conquest_zone_palette_map.json")
    if zone_palette.get("enabled_when_mod_loaded") != "conquest_reforged":
        raise SystemExit("Zone palette map must target conquest_reforged")
    for entry in zone_palette["zone_palettes"]:
        zone = entry["zone"]
        if zone not in zone_names:
            raise SystemExit(f"Zone palette references unknown zone {zone}")
        preferred_tag = entry["preferred_tag"]
        if not preferred_tag.startswith("#geostrata:"):
            raise SystemExit(f"Zone palette {zone} has invalid preferred_tag namespace: {preferred_tag}")
        tag_name = preferred_tag.replace("#geostrata:", "")
        tag_file = ROOT / "tags" / "blocks" / f"{tag_name}.json"
        if not tag_file.exists():
            raise SystemExit(f"Zone palette {zone} references missing tag file {tag_file}")
        if not entry["fallback_blocks"]:
            raise SystemExit(f"Zone palette {zone} has empty fallback_blocks")

    texture_channels = load(ROOT / "remap" / "conquest_material_texture_channels.json")
    if texture_channels.get("enabled_when_mod_loaded") != "conquest_reforged":
        raise SystemExit("Material texture channels must target conquest_reforged")

    texture_materials = set()
    for entry in texture_channels["materials"]:
        mat = entry["material"]
        texture_materials.add(mat)
        if mat not in shared_materials:
            raise SystemExit(f"Texture channel material {mat} is not a shared material")
        grades = set(entry["grade_texture_sets"].keys())
        if grades != allowed_grades:
            raise SystemExit(f"Texture channel grades for {mat} mismatch: {sorted(grades)}")

    if texture_materials != shared_materials:
        raise SystemExit(
            f"Texture channel materials {sorted(texture_materials)} do not match shared materials {sorted(shared_materials)}"
        )

    print("GeoStrata data validation passed")


if __name__ == "__main__":
    main()
