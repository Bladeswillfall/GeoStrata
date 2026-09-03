#!/usr/bin/env python3
"""Validate provider-gated external ore runtime data and assets."""

from __future__ import annotations

import json
from pathlib import Path
import re
import struct
import sys
import zlib

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
IDENTIFIER = re.compile(r'new Identifier\(\s*"([a-z0-9_.-]+)"\s*,\s*"([a-z0-9/._-]+)"\s*\)')


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


def opaque_pixel_count(path: Path) -> int:
    data = path.read_bytes()
    offset = 8
    compressed = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        if kind == b"IHDR" and payload[8:10] != b"\x08\x06":
            fail(f"{path.relative_to(ROOT)} must use 8-bit RGBA pixels")
        if kind == b"IDAT":
            compressed.extend(payload)
        offset += length + 12
    raw = zlib.decompress(compressed)
    stride = 16 * 4
    previous = bytearray(stride)
    opaque = 0
    cursor = 0
    for _ in range(16):
        filter_type = raw[cursor]
        encoded = raw[cursor + 1:cursor + 1 + stride]
        cursor += stride + 1
        row = bytearray(stride)
        for index, value in enumerate(encoded):
            left = row[index - 4] if index >= 4 else 0
            above = previous[index]
            upper_left = previous[index - 4] if index >= 4 else 0
            if filter_type == 0:
                predictor = 0
            elif filter_type == 1:
                predictor = left
            elif filter_type == 2:
                predictor = above
            elif filter_type == 3:
                predictor = (left + above) // 2
            elif filter_type == 4:
                p = left + above - upper_left
                distances = (abs(p - left), abs(p - above), abs(p - upper_left))
                predictor = (left, above, upper_left)[distances.index(min(distances))]
            else:
                fail(f"{path.relative_to(ROOT)} uses unsupported PNG filter {filter_type}")
            row[index] = (value + predictor) & 255
        opaque += sum(row[index] > 0 for index in range(3, stride, 4))
        previous = row
    return opaque


def tag_values(path: Path) -> set[str]:
    values = load(path).get("values")
    if not isinstance(values, list) or not all(isinstance(value, str) for value in values):
        fail(f"{path.relative_to(ROOT)} must contain string values")
    return set(values)


def provider_candidates(material: str, occurrence: dict) -> list[tuple[str, str]]:
    ordered = occurrence.get("providers")
    legacy = "providerMod" in occurrence or "outputItem" in occurrence
    if ordered is not None:
        if legacy or not isinstance(ordered, list) or not ordered:
            fail(f"{material} must declare either one legacy provider or a non-empty ordered provider list")
        raw_candidates = ordered
    else:
        raw_candidates = [{
            "providerMod": occurrence.get("providerMod"),
            "outputItem": occurrence.get("outputItem"),
        }]

    candidates = []
    for candidate in raw_candidates:
        if not isinstance(candidate, dict):
            fail(f"{material} provider candidates must be objects")
        provider = candidate.get("providerMod")
        output = candidate.get("outputItem")
        if not isinstance(provider, str) or not provider or not isinstance(output, str) or ":" not in output:
            fail(f"{material} provider candidate must declare providerMod and outputItem")
        candidates.append((provider, output))
    return candidates


def validate_loot(material: str, grade: str, block: str) -> None:
    path = DATA / f"loot_tables/blocks/{grade}_{material}_ore.json"
    try:
        children = load(path)["pools"][0]["entries"][0]["children"]
        silk, output = children
    except (KeyError, IndexError, TypeError, ValueError):
        fail(f"{path.relative_to(ROOT)} has unexpected loot shape")
    if silk.get("type") != "minecraft:item" or silk.get("name") != block:
        fail(f"{path.relative_to(ROOT)} must preserve the block with Silk Touch")
    dynamic_output = f"geostrata:provider_output/{material}"
    if output.get("type") != "minecraft:dynamic" or output.get("name") != dynamic_output:
        fail(f"{path.relative_to(ROOT)} must resolve provider output through {dynamic_output}")
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
    common_ores = tag_values(RESOURCES / "data/c/tags/blocks/ores.json")
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
        candidates = provider_candidates(material, occurrence)
        preferred_output = candidates[0][1]
        hosts = occurrence.get("hostLithologies")
        if hosts != matrix_ore.get("validHosts") or not set(hosts or ()) <= hosts_supported:
            fail(f"{material} runtime and texture hosts must match supported OreHost values")
        if matrix_ore.get("defaultHost") not in hosts or matrix_ore.get("continuity") is not False:
            fail(f"{material} external prototype must have a valid fallback host and no Continuity claim")
        if economy.get("outputItem") != preferred_output or economy.get("gradeOrder") != list(GRADES):
            fail(f"{material} profile economy must match its preferred occurrence provider")
        for provider, output_item in candidates:
            mapping = providers.get((provider, material))
            if mapping is None or mapping.get("providerOutput") != output_item:
                fail(f"{material} provider/output does not match external material catalogue: {provider}/{output_item}")

        output_tag = economy.get("outputTag")
        if not isinstance(output_tag, str) or ":" not in output_tag:
            fail(f"{material} must declare a provider output tag")
        namespace, tag_path = output_tag.split(":", 1)
        provider_tag = load(RESOURCES / "data" / namespace / "tags/items" / f"{tag_path}.json")
        expected_tag = {
            "replace": False,
            "values": [{"id": output_item, "required": False} for _, output_item in candidates],
        }
        if provider_tag != expected_tag:
            fail(f"{material} provider output tag must contain exactly its ordered optional provider outputs")

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
            if opaque_pixel_count(ASSETS / f"textures/block/external_ore_source/{material}/{grade}.png") != matrix["grades"][grade]["targetPixels"]:
                fail(f"{material} {grade} overlay must match its declared target pixel count")

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
            validate_loot(material, grade, block)

        if tag_values(RESOURCES / f"data/c/tags/blocks/ores/{material}.json") != set(expected_blocks):
            fail(f"c:ores/{material} must contain all four GeoStrata grades")
        if f"#c:ores/{material}" not in common_ores:
            fail(f"c:ores must include #c:ores/{material}")

    companion_ids = set(IDENTIFIER.findall(COMPANION_SOURCE.read_text(encoding="utf-8")))
    suppression_contracts = {
        "zinc": [("create", "raw_zinc", ("zinc_ore",))],
        "tin": [
            ("create_dd", "raw_tin", ("tin_ore",)),
            (
                "modern_industrialization",
                "raw_tin",
                ("ore_generator_tin", "deepslate_ore_generator_tin"),
            ),
            ("techreborn", "raw_tin", ("tin_ore",)),
        ],
        "thorium": [("create_new_age", "thorium", ("thorium_ore",))],
        "uranium": [
            ("createnuclear", "raw_uranium", ("uranium_ore",)),
            (
                "modern_industrialization",
                "raw_uranium",
                ("ore_generator_uranium", "deepslate_ore_generator_uranium"),
            ),
        ],
        "lead": [
            ("tfmg", "raw_lead", ("lead_ore",)),
            ("createnuclear", "raw_lead", ("lead_ore",)),
        ],
        "nickel": [("tfmg", "raw_nickel", ("nickel_ore",))],
    }
    for material, contracts in suppression_contracts.items():
        if material not in occurrences:
            continue
        for namespace, output, placed_features in contracts:
            if (namespace, output) not in companion_ids:
                fail(f"experimental companion must gate {material} suppression on {namespace}:{output}")
            for placed_feature in placed_features:
                if (namespace, placed_feature) not in companion_ids:
                    fail(f"experimental companion must suppress {namespace}:{placed_feature} for {material}")

    print(f"external ore validation OK: {len(occurrences)} materials, {total_blocks} graded blocks")


if __name__ == "__main__":
    main()
