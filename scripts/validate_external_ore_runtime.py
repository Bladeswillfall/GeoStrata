#!/usr/bin/env python3
"""Validate provider-gated external ore runtime data and assets."""

from __future__ import annotations

import json
from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
DATA = RESOURCES / "data/geostrata"
ASSETS = RESOURCES / "assets/geostrata"
OCCURRENCES = DATA / "geology/external_ore_occurrences.json"
PROFILES = DATA / "materials/external_ore_profiles.json"
MATRIX = DATA / "materials/external_ore_texture_matrix.json"
PROVIDERS = DATA / "compatibility/external_materials.json"
BLOCKS_SOURCE = ROOT / "src/main/java/com/geostrata/block/GeoStrataBlocks.java"
HOST_SOURCE = ROOT / "src/main/java/com/geostrata/block/OreHost.java"
COMPANION_SOURCE = ROOT / "experiment-companion/src/main/java/com/geostrata/experiment/CorrelatedExperimentCompanion.java"
GRADES = ("poor", "medium", "rich", "massive")
YIELDS = dict(zip(GRADES, (1, 2, 4, 8), strict=True))
EXTERNAL_BLOCK = re.compile(
    r'registerExternalOre\("(?P<name>[a-z0-9_]+)", "(?P<material>[a-z0-9_]+)", '
    r'OreGrade\.(?P<grade>[A-Z_]+), Blocks\.(?P<base>[A-Z0-9_]+), '
    r'(?P<hardness>[0-9.]+)F, BlockSoundGroup\.(?P<sound>[A-Z0-9_]+)\)'
)
HOST = re.compile(r'\b[A-Z_]+\("([a-z_]+)"\)')


def fail(message: str) -> None:
    print(f"external ore validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")


def require_png(path: Path) -> None:
    try:
        header = path.read_bytes()[:24]
    except OSError as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        fail(f"{path.relative_to(ROOT)} must be a PNG")
    if struct.unpack(">II", header[16:24]) != (16, 16):
        fail(f"{path.relative_to(ROOT)} must be exactly 16x16")


def tag_values(path: Path) -> set[str]:
    values = load(path).get("values")
    if not isinstance(values, list) or not all(isinstance(value, str) for value in values):
        fail(f"{path.relative_to(ROOT)} must contain string values")
    return set(values)


def validate_loot(material: str, grade: str, block: str, output_tag: str) -> None:
    path = DATA / f"loot_tables/blocks/{grade}_{material}_ore.json"
    try:
        children = load(path)["pools"][0]["entries"][0]["children"]
        silk, output = children
    except (KeyError, IndexError, TypeError, ValueError):
        fail(f"{path.relative_to(ROOT)} has unexpected loot shape")
    if silk.get("type") != "minecraft:item" or silk.get("name") != block:
        fail(f"{path.relative_to(ROOT)} must preserve the block with Silk Touch")
    if output.get("type") != "minecraft:tag" or output.get("name") != output_tag or output.get("expand") is not True:
        fail(f"{path.relative_to(ROOT)} must resolve provider output through {output_tag}")
    functions = output.get("functions", [])
    if not any(fn.get("function") == "minecraft:set_count" and fn.get("count") == YIELDS[grade] for fn in functions):
        fail(f"{path.relative_to(ROOT)} must use base yield {YIELDS[grade]}")
    if not any(fn.get("function") == "minecraft:apply_bonus" and fn.get("formula") == "minecraft:ore_drops" for fn in functions):
        fail(f"{path.relative_to(ROOT)} must use the standard Fortune ore formula")


def main() -> None:
    occurrence_root = load(OCCURRENCES)
    profile_root = load(PROFILES)
    matrix = load(MATRIX)
    if (occurrence_root.get("schemaVersion"), occurrence_root.get("model"), occurrence_root.get("runtimeStatus")) != (
        1, "geostrata:external_ore_occurrence_catalog", "optional_provider_gated"
    ):
        fail("external ore occurrence wrapper has drifted")
    if (profile_root.get("schemaVersion"), profile_root.get("model")) != (1, "geostrata:external_ore_profile_catalog"):
        fail("external ore profile wrapper has drifted")
    if (matrix.get("schemaVersion"), matrix.get("model"), matrix.get("resolution")) != (
        1, "geostrata:external_ore_texture_matrix", 16
    ) or tuple(matrix.get("grades", {})) != GRADES:
        fail("external ore texture matrix has drifted")

    occurrences = {entry.get("id"): entry for entry in occurrence_root.get("occurrences", []) if isinstance(entry, dict)}
    profiles = {
        entry.get("gameplay", {}).get("oreEconomy", {}).get("material"): entry
        for entry in profile_root.get("materials", []) if isinstance(entry, dict)
    }
    matrix_ores = matrix.get("ores")
    if not occurrences or set(occurrences) != set(profiles) or not isinstance(matrix_ores, dict) or set(matrix_ores) != set(occurrences):
        fail("external occurrence, profile and texture-matrix materials must match exactly")

    registrations = {match.group("name"): match.groupdict() for match in EXTERNAL_BLOCK.finditer(BLOCKS_SOURCE.read_text(encoding="utf-8"))}
    hosts_supported = set(HOST.findall(HOST_SOURCE.read_text(encoding="utf-8")))
    ores_tag = tag_values(DATA / "tags/blocks/ores.json")
    pickaxe = tag_values(RESOURCES / "data/minecraft/tags/blocks/mineable/pickaxe.json")
    stone = tag_values(RESOURCES / "data/minecraft/tags/blocks/needs_stone_tool.json")
    providers = {
        (provider.get("id"), mapping.get("canonicalMaterial")): mapping
        for provider in load(PROVIDERS).get("providers", []) if isinstance(provider, dict)
        for mapping in provider.get("materials", []) if isinstance(mapping, dict)
    }

    total_blocks = 0
    for material, occurrence in occurrences.items():
        profile = profiles[material]
        economy = profile.get("gameplay", {}).get("oreEconomy", {})
        matrix_ore = matrix_ores[material]
        provider = occurrence.get("providerMod")
        output_item = occurrence.get("outputItem")
        hosts = occurrence.get("hostLithologies")
        if hosts != matrix_ore.get("validHosts") or not set(hosts or ()) <= hosts_supported:
            fail(f"{material} runtime and texture hosts must match supported OreHost values")
        if matrix_ore.get("defaultHost") not in hosts or matrix_ore.get("continuity") is not False:
            fail(f"{material} external prototype must have a valid fallback host and no Continuity claim")
        if economy.get("outputItem") != output_item or economy.get("gradeOrder") != list(GRADES):
            fail(f"{material} profile economy must match its occurrence")
        mapping = providers.get((provider, material))
        if mapping is None or mapping.get("providerOutput") != output_item:
            fail(f"{material} provider/output does not match external material catalogue")

        output_tag = economy.get("outputTag")
        if not isinstance(output_tag, str) or ":" not in output_tag:
            fail(f"{material} must declare a provider output tag")
        namespace, tag_path = output_tag.split(":", 1)
        provider_tag = load(RESOURCES / "data" / namespace / "tags/items" / f"{tag_path}.json")
        if provider_tag != {"replace": False, "values": [{"id": output_item, "required": False}]}:
            fail(f"{material} provider output tag must contain exactly optional {output_item}")

        expected_blocks = [f"geostrata:{grade}_{material}_ore" for grade in GRADES]
        grade_blocks = occurrence.get("gradeBlocks", {})
        if [grade_blocks.get(grade) for grade in GRADES] != expected_blocks:
            fail(f"{material} grade blocks must follow the shared naming contract")
        if [profile.get("primaryBlock"), *profile.get("derivedBlocks", [])] != expected_blocks:
            fail(f"{material} profile blocks must follow grade order")

        require_png(ASSETS / f"textures/block/external_ore_source/master/{material}.png")
        for grade, block in zip(GRADES, expected_blocks, strict=True):
            total_blocks += 1
            name = f"{grade}_{material}_ore"
            registration = registrations.get(name)
            if registration is None or registration["material"] != material or registration["grade"].lower() != grade:
                fail(f"missing Java external ore registration for {name}")
            if (registration["base"], registration["hardness"], registration["sound"]) != ("IRON_ORE", "3.0", "STONE"):
                fail(f"{name} must use the shared external-ore breaking profile")
            if block not in ores_tag or block not in pickaxe or block not in stone:
                fail(f"{block} is missing ore/pickaxe/stone-tool classification")
            require_png(ASSETS / f"textures/block/external_ore_source/{material}/{grade}.png")

            for host in hosts:
                texture = ASSETS / f"textures/block/external_ore/{material}/{host}/{grade}.png"
                model = ASSETS / f"models/block/ore/{material}/{host}/{grade}.json"
                require_png(texture)
                texture_id = f"geostrata:block/external_ore/{material}/{host}/{grade}"
                if load(model) != {"parent": "minecraft:block/cube_all", "textures": {"all": texture_id}}:
                    fail(f"{model.relative_to(ROOT)} does not match its external host composite")

            default_host = matrix_ore["defaultHost"]
            if load(ASSETS / f"models/item/{name}.json") != {"parent": f"geostrata:block/ore/{material}/{default_host}/{grade}"}:
                fail(f"{name} item model must use default host {default_host}")
            variants = load(ASSETS / f"blockstates/{name}.json").get("variants")
            if not isinstance(variants, dict) or set(variants) != {f"host={host}" for host in hosts_supported}:
                fail(f"{name} blockstate must cover every OreHost state")
            for host in hosts_supported:
                rendered = host if host in hosts else default_host
                if variants[f"host={host}"] != {"model": f"geostrata:block/ore/{material}/{rendered}/{grade}"}:
                    fail(f"{name} host={host} must fall back to {rendered}")
            validate_loot(material, grade, block, output_tag)

        if tag_values(RESOURCES / f"data/c/tags/blocks/ores/{material}.json") != set(expected_blocks):
            fail(f"c:ores/{material} must contain all four GeoStrata grades")

    companion = COMPANION_SOURCE.read_text(encoding="utf-8")
    suppression_contracts = {
        "zinc": ("create", "raw_zinc", "zinc_ore"),
        "tin": ("create_dd", "raw_tin", "tin_ore"),
    }
    for material, (namespace, output, placed_feature) in suppression_contracts.items():
        if material not in occurrences:
            continue
        if (f'new Identifier("{namespace}", "{output}")' not in companion
                or f'new Identifier("{namespace}", "{placed_feature}")' not in companion):
            fail(f"experimental companion must gate {material} suppression on provider output and placed feature ids")

    print(f"external ore validation OK: {len(occurrences)} materials, {total_blocks} graded blocks")


if __name__ == "__main__":
    main()
