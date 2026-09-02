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
PROVIDER_CATALOG = DATA / "compatibility/external_materials.json"
BLOCKS_SOURCE = ROOT / "src/main/java/com/geostrata/block/GeoStrataBlocks.java"
HOST_SOURCE = ROOT / "src/main/java/com/geostrata/block/OreHost.java"
COMPANION_SOURCE = ROOT / "experiment-companion/src/main/java/com/geostrata/experiment/CorrelatedExperimentCompanion.java"
GRADES = ("poor", "medium", "rich", "massive")
YIELDS = {"poor": 1, "medium": 2, "rich": 4, "massive": 8}
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


def png_size(path: Path) -> tuple[int, int]:
    try:
        header = path.read_bytes()[:24]
    except OSError as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        fail(f"{path.relative_to(ROOT)} must be a PNG")
    return struct.unpack(">II", header[16:24])


def direct_tag(path: Path) -> set[str]:
    values = load(path).get("values")
    if not isinstance(values, list) or not all(isinstance(value, str) for value in values):
        fail(f"{path.relative_to(ROOT)} must contain direct block ids")
    return set(values)


def external_registrations() -> dict[str, dict[str, str]]:
    source = BLOCKS_SOURCE.read_text(encoding="utf-8")
    found: dict[str, dict[str, str]] = {}
    for match in EXTERNAL_BLOCK.finditer(source):
        name = match.group("name")
        if name in found:
            fail(f"duplicate external ore registration {name}")
        found[name] = match.groupdict()
    if not found:
        fail("no registerExternalOre calls found")
    return found


def validate_loot(material: str, grade: str, block: str, output_tag: str) -> None:
    path = DATA / f"loot_tables/blocks/{grade}_{material}_ore.json"
    loot = load(path)
    try:
        children = loot["pools"][0]["entries"][0]["children"]
    except (KeyError, IndexError, TypeError):
        fail(f"{path.relative_to(ROOT)} has unexpected loot shape")
    if len(children) != 2:
        fail(f"{path.relative_to(ROOT)} must contain Silk Touch and provider-output alternatives")
    silk, output = children
    if silk.get("type") != "minecraft:item" or silk.get("name") != block:
        fail(f"{path.relative_to(ROOT)} Silk Touch entry must drop {block}")
    if output.get("type") != "minecraft:tag" or output.get("name") != output_tag or output.get("expand") is not True:
        fail(f"{path.relative_to(ROOT)} must resolve output through {output_tag}")
    functions = output.get("functions")
    if not isinstance(functions, list) or not functions:
        fail(f"{path.relative_to(ROOT)} provider output must have yield functions")
    count = next((fn.get("count") for fn in functions if fn.get("function") == "minecraft:set_count"), None)
    if count != YIELDS[grade]:
        fail(f"{path.relative_to(ROOT)} must use base yield {YIELDS[grade]}")
    if not any(fn.get("function") == "minecraft:apply_bonus" and fn.get("formula") == "minecraft:ore_drops" for fn in functions):
        fail(f"{path.relative_to(ROOT)} must use the standard Fortune ore formula")


def validate_provider_output_tag(material: str, output_item: str, output_tag: str) -> None:
    namespace, path_id = output_tag.split(":", 1)
    path = RESOURCES / "data" / namespace / "tags/items" / f"{path_id}.json"
    tag = load(path)
    expected = [{"id": output_item, "required": False}]
    if tag.get("replace") is not False or tag.get("values") != expected:
        fail(f"{path.relative_to(ROOT)} must contain exactly optional {output_item}")


def main() -> None:
    occurrence_root = load(OCCURRENCES)
    profile_root = load(PROFILES)
    matrix = load(MATRIX)
    if occurrence_root.get("schemaVersion") != 1 or occurrence_root.get("model") != "geostrata:external_ore_occurrence_catalog":
        fail("external ore occurrences must use schema 1")
    if occurrence_root.get("runtimeStatus") != "optional_provider_gated":
        fail("external ore occurrences must be provider gated")
    if profile_root.get("schemaVersion") != 1 or profile_root.get("model") != "geostrata:external_ore_profile_catalog":
        fail("external ore profiles must use schema 1")
    if matrix.get("schemaVersion") != 1 or matrix.get("model") != "geostrata:external_ore_texture_matrix" or matrix.get("resolution") != 16:
        fail("external ore texture matrix must use schema 1 at 16x16")
    if tuple(matrix.get("grades", {})) != GRADES:
        fail("external ore grade order must match core grades")

    occurrences = {entry.get("id"): entry for entry in occurrence_root.get("occurrences", []) if isinstance(entry, dict)}
    profiles = {
        entry.get("gameplay", {}).get("oreEconomy", {}).get("material"): entry
        for entry in profile_root.get("materials", [])
        if isinstance(entry, dict)
    }
    matrix_ores = matrix.get("ores")
    if not occurrences or set(occurrences) != set(profiles) or not isinstance(matrix_ores, dict) or set(matrix_ores) != set(occurrences):
        fail("external occurrence, profile and texture-matrix materials must match exactly")

    registrations = external_registrations()
    ore_hosts = set(HOST.findall(HOST_SOURCE.read_text(encoding="utf-8")))
    ores_tag = direct_tag(DATA / "tags/blocks/ores.json")
    pickaxe = direct_tag(RESOURCES / "data/minecraft/tags/blocks/mineable/pickaxe.json")
    stone = direct_tag(RESOURCES / "data/minecraft/tags/blocks/needs_stone_tool.json")
    provider_catalog = load(PROVIDER_CATALOG)
    provider_mappings = {
        (provider.get("id"), mapping.get("canonicalMaterial")): mapping
        for provider in provider_catalog.get("providers", [])
        if isinstance(provider, dict)
        for mapping in provider.get("materials", [])
        if isinstance(mapping, dict)
    }

    total_blocks = 0
    for material, occurrence in occurrences.items():
        provider = occurrence.get("providerMod")
        output_item = occurrence.get("outputItem")
        profile = profiles[material]
        ore = profile.get("gameplay", {}).get("oreEconomy", {})
        matrix_ore = matrix_ores[material]
        hosts = occurrence.get("hostLithologies")
        if hosts != matrix_ore.get("validHosts") or matrix_ore.get("defaultHost") not in hosts:
            fail(f"{material} texture hosts must exactly match runtime hosts")
        if not set(hosts) <= ore_hosts:
            fail(f"{material} contains unsupported OreHost values")
        if matrix_ore.get("continuity") is not False:
            fail(f"{material} optional prototype must not claim Continuity assets it does not ship")
        if ore.get("outputItem") != output_item or ore.get("gradeOrder") != list(GRADES):
            fail(f"{material} profile economy must match occurrence output and grade order")
        output_tag = ore.get("outputTag")
        if not isinstance(output_tag, str):
            fail(f"{material} must declare an outputTag")
        validate_provider_output_tag(material, output_item, output_tag)

        mapping = provider_mappings.get((provider, material))
        if mapping is None or mapping.get("providerOutput") != output_item:
            fail(f"{material} runtime provider/output does not match external material catalogue")

        grade_blocks = occurrence.get("gradeBlocks")
        expected_blocks = [f"geostrata:{grade}_{material}_ore" for grade in GRADES]
        if [grade_blocks.get(grade) for grade in GRADES] != expected_blocks:
            fail(f"{material} grade block ids must follow the shared naming contract")
        profile_blocks = [profile.get("primaryBlock"), *profile.get("derivedBlocks", [])]
        if profile_blocks != expected_blocks:
            fail(f"{material} external profile blocks must follow grade order")

        master = ASSETS / f"textures/block/ore_source/master/{material}.png"
        if png_size(master) != (16, 16):
            fail(f"{material} master texture must be 16x16")
        for grade, block in zip(GRADES, expected_blocks, strict=True):
            total_blocks += 1
            name = f"{grade}_{material}_ore"
            registration = registrations.get(name)
            if registration is None or registration["material"] != material or registration["grade"].lower() != grade:
                fail(f"missing Java external ore registration for {name}")
            if registration["base"] != "IRON_ORE" or registration["hardness"] != "3.0" or registration["sound"] != "STONE":
                fail(f"{name} must use the shared zinc breaking profile")
            if block not in ores_tag or block not in pickaxe or block not in stone:
                fail(f"{block} is missing ore/pickaxe/stone-tool classification")
            if png_size(ASSETS / f"textures/block/ore_source/{material}/{grade}.png") != (16, 16):
                fail(f"{material}/{grade} overlay must be 16x16")
            for host in hosts:
                texture = ASSETS / f"textures/block/ore/{material}/{host}/{grade}.png"
                model = ASSETS / f"models/block/ore/{material}/{host}/{grade}.json"
                if png_size(texture) != (16, 16):
                    fail(f"{texture.relative_to(ROOT)} must be 16x16")
                expected_texture = f"geostrata:block/ore/{material}/{host}/{grade}"
                if load(model) != {"parent": "minecraft:block/cube_all", "textures": {"all": expected_texture}}:
                    fail(f"{model.relative_to(ROOT)} does not match its host composite")

            default_host = matrix_ore["defaultHost"]
            item_model = load(ASSETS / f"models/item/{name}.json")
            if item_model != {"parent": f"geostrata:block/ore/{material}/{default_host}/{grade}"}:
                fail(f"{name} item model must use default host {default_host}")
            variants = load(ASSETS / f"blockstates/{name}.json").get("variants")
            if not isinstance(variants, dict) or set(variants) != {f"host={host}" for host in ore_hosts}:
                fail(f"{name} blockstate must cover every OreHost state")
            for host in ore_hosts:
                rendered_host = host if host in hosts else default_host
                expected_model = f"geostrata:block/ore/{material}/{rendered_host}/{grade}"
                if variants[f"host={host}"] != {"model": expected_model}:
                    fail(f"{name} host={host} must render {rendered_host}")
            validate_loot(material, grade, block, output_tag)

    common_zinc = direct_tag(RESOURCES / "data/c/tags/blocks/ores/zinc.json")
    expected_zinc = {f"geostrata:{grade}_zinc_ore" for grade in GRADES}
    if occurrences.keys() == {"zinc"} and common_zinc != expected_zinc:
        fail("c:ores/zinc must contain all four GeoStrata zinc grades")

    companion = COMPANION_SOURCE.read_text(encoding="utf-8")
    if 'new Identifier("create", "raw_zinc")' not in companion or 'new Identifier("create", "zinc_ore")' not in companion:
        fail("experimental companion must gate Create zinc suppression on the provider output and placed feature ids")

    print(f"external ore validation OK: {len(occurrences)} materials, {total_blocks} graded blocks")


if __name__ == "__main__":
    main()
