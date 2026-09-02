#!/usr/bin/env python3
"""Validate GeoStrata's semantic geology and material contracts."""

from __future__ import annotations

import json
from pathlib import Path
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src" / "main" / "resources" / "data" / "geostrata"
CATALOG = DATA / "geology" / "lithologies.json"
MATERIAL_CATALOG = DATA / "materials" / "material_profiles.json"
ORE_CATALOG = DATA / "geology" / "ore_occurrences.json"
ORE_TEXTURE_MATRIX = DATA / "materials" / "ore_texture_matrix.json"
BLOCK_TAGS = DATA / "tags" / "blocks"
BIOME_TAGS = DATA / "tags" / "worldgen" / "biome"
CONFIGURED = DATA / "worldgen" / "configured_feature"
PLACED = DATA / "worldgen" / "placed_feature"
MINECRAFT_BLOCK_TAGS = ROOT / "src" / "main" / "resources" / "data" / "minecraft" / "tags" / "blocks"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "geostrata"
BLOCKS_SOURCE = ROOT / "src" / "main" / "java" / "com" / "geostrata" / "block" / "GeoStrataBlocks.java"
ORE_HOST_SOURCE = ROOT / "src" / "main" / "java" / "com" / "geostrata" / "block" / "OreHost.java"
CONTINUITY_ROOT = ASSETS / "optifine" / "ctm" / "host"
CONTINUITY_TEXTURES = ASSETS / "textures" / "optifine" / "ctm" / "host"
CONTINUITY_VARIANTS = 4

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
    "plutonic_body",
    "contact_aureole",
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
ALLOWED_MATERIAL_FAMILIES = {"rock", "soil", "mud", "clay", "ore"}
SEMANTIC_BLOCK_TAGS = (
    "rocks",
    "sedimentary_rocks",
    "igneous_rocks",
    "metamorphic_rocks",
    "soft_earth",
    "clays",
    "ores",
)
IDENTIFIER = re.compile(r"[a-z0-9_.-]+:[a-z0-9/._-]+")
SIMPLE_ID = re.compile(r"[a-z0-9_]+")

BLOCK_REGISTRATION = re.compile(
    r'register(?P<registration>Rock|Earth|RockVariant)\("(?P<name>[a-z0-9_]+)"'
    r'.*?(?P<builder>rock|earth)\(Blocks\.(?P<copy_from>[A-Z0-9_]+), '
    r'(?P<hardness>[0-9.]+)F, BlockSoundGroup\.(?P<sound_group>[A-Z0-9_]+)\)'
)
ORE_BLOCK_REGISTRATION = re.compile(
    r'registerOre\("(?P<name>[a-z0-9_]+)", "(?P<material>[a-z0-9_]+)", '
    r'OreGrade\.(?P<grade>[A-Z_]+), Blocks\.(?P<copy_from>[A-Z0-9_]+), '
    r'(?P<hardness>[0-9.]+)F, BlockSoundGroup\.(?P<sound_group>[A-Z0-9_]+)\)'
)


def fail(message: str) -> None:
    print(f"geology validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    try:
        with path.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")


def png_size(path: Path) -> tuple[int, int]:
    try:
        with path.open("rb") as handle:
            header = handle.read(24)
    except OSError as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        fail(f"{path.relative_to(ROOT)} must be a PNG image")
    return struct.unpack(">II", header[16:24])


def validate_continuity_hosts(hosts: list[str]) -> None:
    expected_properties = {f"{host}.properties" for host in hosts}
    actual_properties = {path.name for path in CONTINUITY_ROOT.glob("*.properties")}
    if actual_properties != expected_properties:
        fail(
            "Continuity host definitions must exactly cover the artist matrix; "
            f"missing={sorted(expected_properties - actual_properties)}, "
            f"extra={sorted(actual_properties - expected_properties)}"
        )
    for host in hosts:
        tiles = " ".join(
            f"geostrata:textures/optifine/ctm/host/{host}/{index}"
            for index in range(CONTINUITY_VARIANTS)
        )
        expected = (
            "method=random\n"
            f"matchTiles=geostrata:block/host/{host}\n"
            "prioritize=false\n"
            f"tiles={tiles}\n"
        )
        path = CONTINUITY_ROOT / f"{host}.properties"
        try:
            actual = path.read_text(encoding="utf-8")
        except OSError as exc:
            fail(f"cannot read {path.relative_to(ROOT)}: {exc}")
        if actual != expected:
            fail(f"{path.relative_to(ROOT)} has drifted from the generated Continuity contract")

        texture_root = CONTINUITY_TEXTURES / host
        expected_textures = {f"{index}.png" for index in range(CONTINUITY_VARIANTS)}
        actual_textures = {path.name for path in texture_root.glob("*.png")}
        if actual_textures != expected_textures:
            fail(f"Continuity host {host} must contain exactly {CONTINUITY_VARIANTS} variants")
        for texture in expected_textures:
            path = texture_root / texture
            if png_size(path) != (16, 16):
                fail(f"{path.relative_to(ROOT)} must be exactly 16x16")


def tag_values(path: Path) -> set[str]:
    data = load_json(path)
    values = data.get("values")
    if not isinstance(values, list):
        fail(f"{path.relative_to(ROOT)} must contain a values array")
    direct = {value for value in values if isinstance(value, str) and not value.startswith("#")}
    if len(direct) != len(values):
        fail(f"{path.relative_to(ROOT)} must use direct block IDs for catalog validation")
    return direct


def source_block_profiles() -> dict[str, dict[str, object]]:
    try:
        source = BLOCKS_SOURCE.read_text(encoding="utf-8")
    except OSError as exc:
        fail(f"cannot read {BLOCKS_SOURCE.relative_to(ROOT)}: {exc}")

    profiles: dict[str, dict[str, object]] = {}
    for line in source.splitlines():
        match = BLOCK_REGISTRATION.search(line)
        if match:
            block = f"geostrata:{match.group('name')}"
            is_rock = match.group("builder") == "rock"
            hardness = float(match.group("hardness"))
            profile = {
                "family": "rock" if is_rock else "earth",
                "copyFrom": f"minecraft:{match.group('copy_from').lower()}",
                "hardness": hardness,
                "blastResistance": 6.0 if is_rock else hardness,
                "requiresTool": is_rock,
                "soundGroup": match.group("sound_group").lower(),
            }
        else:
            match = ORE_BLOCK_REGISTRATION.search(line)
            if not match:
                continue
            block = f"geostrata:{match.group('name')}"
            profile = {
                "family": "ore",
                "material": match.group("material"),
                "grade": match.group("grade").lower(),
                "copyFrom": f"minecraft:{match.group('copy_from').lower()}",
                "hardness": float(match.group("hardness")),
                "blastResistance": 3.0,
                "requiresTool": True,
                "soundGroup": match.group("sound_group").lower(),
            }

        if block in profiles:
            fail(f"duplicate Java block registration: {block}")
        profiles[block] = profile

    declared_blocks = {
        f"geostrata:{name.lower()}"
        for name in re.findall(r"public static final Block ([A-Z0-9_]+) =", source)
    }
    if set(profiles) != declared_blocks:
        fail(
            "material validator could not parse every public block declaration; "
            f"missing={sorted(declared_blocks - set(profiles))}, "
            f"unexpected={sorted(set(profiles) - declared_blocks)}"
        )
    return profiles


def nested_model_ids(value: object) -> set[str]:
    models: set[str] = set()
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "model" and isinstance(child, str):
                models.add(child)
            else:
                models.update(nested_model_ids(child))
    elif isinstance(value, list):
        for child in value:
            models.update(nested_model_ids(child))
    return models


def block_textures(block: str) -> set[str]:
    namespace, path = block.split(":", 1)
    if namespace != "geostrata":
        fail(f"material catalog may only own GeoStrata blocks, found {block}")

    blockstate_path = ASSETS / "blockstates" / f"{path}.json"
    blockstate = load_json(blockstate_path)
    pending = list(nested_model_ids(blockstate))
    visited: set[str] = set()
    textures: set[str] = set()

    while pending:
        model = pending.pop()
        if model in visited or not model.startswith("geostrata:block/"):
            continue
        visited.add(model)

        model_path = ASSETS / "models" / "block" / f"{model.removeprefix('geostrata:block/')}.json"
        model_data = load_json(model_path)
        parent = model_data.get("parent")
        if isinstance(parent, str) and parent.startswith("geostrata:block/"):
            pending.append(parent)

        declared = model_data.get("textures", {})
        if not isinstance(declared, dict):
            fail(f"{model_path.relative_to(ROOT)} textures must be an object")
        textures.update(
            texture
            for texture in declared.values()
            if isinstance(texture, str) and texture.startswith("geostrata:")
        )

    if not visited:
        fail(f"{blockstate_path.relative_to(ROOT)} does not reference a GeoStrata block model")
    return textures


def validate_ore_loot(block: str, output_item: str, base_yield: int) -> None:
    block_path = block.split(":", 1)[1]
    path = DATA / "loot_tables" / "blocks" / f"{block_path}.json"
    expected = {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{
                "type": "minecraft:alternatives",
                "children": [
                    {
                        "type": "minecraft:item",
                        "name": block,
                        "conditions": [{
                            "condition": "minecraft:match_tool",
                            "predicate": {"enchantments": [{
                                "enchantment": "minecraft:silk_touch",
                                "levels": {"min": 1},
                            }]},
                        }],
                        "functions": [{
                            "function": "minecraft:copy_state",
                            "block": block,
                            "properties": ["host"],
                        }],
                    },
                    {
                        "type": "minecraft:item",
                        "name": output_item,
                        "functions": [
                            {"function": "minecraft:set_count", "count": base_yield},
                            {
                                "function": "minecraft:apply_bonus",
                                "enchantment": "minecraft:fortune",
                                "formula": "minecraft:ore_drops",
                            },
                            {"function": "minecraft:explosion_decay"},
                        ],
                    },
                ],
            }],
        }],
    }
    if load_json(path) != expected:
        fail(
            f"{path.relative_to(ROOT)} must drop itself with Silk Touch or "
            f"{base_yield}x {output_item} with the standard Fortune ore formula"
        )


def validate_ore_material(
    material_id: str,
    blocks: list[str],
    gameplay: dict[str, object],
    source_profiles: dict[str, dict[str, object]],
    occurrences: dict[str, dict[str, object]],
    grade_order: list[str],
    economics: dict[str, dict[str, object]],
) -> None:
    ore = gameplay.get("oreEconomy")
    if not isinstance(ore, dict):
        fail(f"{material_id} must declare gameplay.oreEconomy")
    material = ore.get("material")
    if material_id != f"{material}_ore":
        fail(f"ore material profile {material_id} must be named {material}_ore")
    occurrence = occurrences.get(material)
    if occurrence is None:
        fail(f"{material_id} has no matching ore occurrence")
    if ore.get("source") != "geostrata:geology/ore_occurrences":
        fail(f"{material_id} must use the ore occurrence catalog as its economy source")
    if ore.get("gradeOrder") != grade_order:
        fail(f"{material_id} gradeOrder does not match the ore catalog")
    if ore.get("outputItem") != occurrence.get("outputItem"):
        fail(f"{material_id} outputItem does not match its ore occurrence")

    yield_multiplier = occurrence.get("baseYieldMultiplier", 1)
    if not isinstance(yield_multiplier, int) or isinstance(yield_multiplier, bool) or yield_multiplier < 1:
        fail(f"{material_id} baseYieldMultiplier must be a positive integer")

    grade_blocks = occurrence.get("gradeBlocks")
    if not isinstance(grade_blocks, dict):
        fail(f"{material_id} occurrence is missing gradeBlocks")
    expected_blocks = [grade_blocks.get(grade) for grade in grade_order]
    if blocks != expected_blocks:
        fail(f"{material_id} blocks do not match its ordered occurrence gradeBlocks")

    for grade, block in zip(grade_order, blocks, strict=True):
        profile = source_profiles[block]
        if profile.get("material") != material or profile.get("grade") != grade:
            fail(f"{block} Java registration does not match {material} {grade}")
        economy = economics.get(grade)
        if not isinstance(economy, dict) or not isinstance(economy.get("baseYield"), int):
            fail(f"ore catalog has invalid {grade} economics")
        validate_ore_loot(
            block,
            occurrence["outputItem"],
            economy["baseYield"] * yield_multiplier,
        )


def validate_material_catalog() -> int:
    catalog = load_json(MATERIAL_CATALOG)
    ore_catalog = load_json(ORE_CATALOG)
    texture_matrix = load_json(ORE_TEXTURE_MATRIX)
    if catalog.get("schemaVersion") != 1:
        fail("material profile catalog schemaVersion must be 1")
    if catalog.get("model") != "geostrata:material_profile_catalog":
        fail("unexpected material profile catalog model identifier")
    if catalog.get("runtimeStatus") != "validated_metadata_only":
        fail("material profiles must remain explicit that JSON values are not runtime-loaded settings")
    if ore_catalog.get("schemaVersion") != 3 or ore_catalog.get("runtimeStatus") != "grade_economy_active":
        fail("ore occurrence catalog must expose the active schema-3 generation and grade economy")
    if texture_matrix.get("schemaVersion") != 1 or texture_matrix.get("resolution") != 16:
        fail("ore texture matrix must use schema 1 and native 16x16 textures")
    grade_model = ore_catalog.get("gradeModel")
    if not isinstance(grade_model, dict):
        fail("ore occurrence catalog must declare gradeModel")
    grade_order = grade_model.get("economicGrades")
    economics = grade_model.get("economics")
    if grade_order != ["poor", "medium", "rich", "massive"] or not isinstance(economics, dict):
        fail("ore grade model must define ordered grade economics")
    raw_occurrences = ore_catalog.get("occurrences")
    if not isinstance(raw_occurrences, list):
        fail("ore occurrence catalog must contain occurrences")
    occurrences = {
        occurrence.get("id"): occurrence
        for occurrence in raw_occurrences
        if isinstance(occurrence, dict) and isinstance(occurrence.get("id"), str)
    }
    if len(occurrences) != len(raw_occurrences):
        fail("ore occurrences must have unique string ids")
    matrix_hosts = texture_matrix.get("hosts")
    matrix_ores = texture_matrix.get("ores")
    matrix_grades = texture_matrix.get("grades")
    if not isinstance(matrix_hosts, list) or len(matrix_hosts) != len(set(matrix_hosts)):
        fail("ore texture matrix hosts must be a unique array")
    if not isinstance(matrix_ores, dict) or set(matrix_ores) != set(occurrences):
        fail("ore texture matrix materials must exactly match ore occurrences")
    if not isinstance(matrix_grades, dict) or list(matrix_grades) != grade_order:
        fail("ore texture matrix grades must match the economic grade order")
    grade_pixels = [matrix_grades[grade].get("targetPixels") for grade in grade_order]
    if any(not isinstance(value, int) for value in grade_pixels) or grade_pixels != sorted(set(grade_pixels)):
        fail("ore texture matrix targetPixels must increase strictly by grade")
    for material, occurrence in occurrences.items():
        matrix_ore = matrix_ores[material]
        if not isinstance(matrix_ore, dict):
            fail(f"ore texture matrix entry {material} must be an object")
        if matrix_ore.get("validHosts") != occurrence.get("hostLithologies"):
            fail(f"ore texture matrix validHosts for {material} must match its occurrence")
        if matrix_ore.get("defaultHost") not in matrix_ore["validHosts"]:
            fail(f"ore texture matrix defaultHost for {material} must be geologically valid")

    texture_sets = catalog.get("textureSets")
    if not isinstance(texture_sets, dict) or not texture_sets:
        fail("material profile catalog must declare textureSets")
    for name, texture_set in texture_sets.items():
        if not isinstance(texture_set, dict) or texture_set.get("status") not in {"placeholder", "production"}:
            fail(f"texture set {name} must declare placeholder or production status")
        textures = texture_set.get("textures")
        if not isinstance(textures, list) or not textures:
            fail(f"texture set {name} must contain textures")
        for texture in textures:
            if not isinstance(texture, str) or not texture.startswith("geostrata:block/"):
                fail(f"texture set {name} contains invalid texture {texture!r}")
            texture_path = ASSETS / "textures" / "block" / f"{texture.removeprefix('geostrata:block/')}.png"
            if not texture_path.is_file():
                fail(f"texture set {name} references missing {texture_path.relative_to(ROOT)}")
            if png_size(texture_path) != (16, 16):
                fail(f"{texture_path.relative_to(ROOT)} must be exactly 16x16")

    materials = catalog.get("materials")
    if not isinstance(materials, list) or not materials:
        fail("material profile catalog must contain materials")

    source_profiles = source_block_profiles()
    mineable_tags = {
        "pickaxe": tag_values(MINECRAFT_BLOCK_TAGS / "mineable" / "pickaxe.json"),
        "shovel": tag_values(MINECRAFT_BLOCK_TAGS / "mineable" / "shovel.json"),
    }
    tier_tags = {
        "stone": tag_values(MINECRAFT_BLOCK_TAGS / "needs_stone_tool.json"),
        "iron": tag_values(MINECRAFT_BLOCK_TAGS / "needs_iron_tool.json"),
    }
    semantic_tags = {
        f"geostrata:{name}": tag_values(BLOCK_TAGS / f"{name}.json")
        for name in SEMANTIC_BLOCK_TAGS
    }

    ids: set[str] = set()
    roles: set[str] = set()
    catalog_blocks: set[str] = set()
    required_fields = {
        "id",
        "primaryBlock",
        "derivedBlocks",
        "family",
        "compatibilityRole",
        "semanticTags",
        "gameplay",
        "assets",
    }

    for entry in materials:
        if not isinstance(entry, dict):
            fail(f"material profile must be an object: {entry!r}")
        missing = sorted(required_fields - set(entry))
        if missing:
            fail(f"material profile is missing fields {missing}: {entry}")

        material_id = entry["id"]
        primary = entry["primaryBlock"]
        derived = entry["derivedBlocks"]
        family = entry["family"]
        role = entry["compatibilityRole"]

        if not isinstance(material_id, str):
            fail(f"material id must be a string, found {material_id!r}")
        if not isinstance(role, str) or not role.startswith("geostrata:"):
            fail(f"{material_id} has invalid compatibilityRole {role!r}")
        if material_id in ids:
            fail(f"duplicate material id: {material_id}")
        if role in roles:
            fail(f"duplicate material compatibilityRole: {role}")
        ids.add(material_id)
        roles.add(role)

        if family != "ore" and primary != f"geostrata:{material_id}":
            fail(f"{material_id} must use geostrata:{material_id} as its primaryBlock")
        if not isinstance(derived, list) or any(not isinstance(block, str) for block in derived):
            fail(f"{material_id} derivedBlocks must be an array of block IDs")
        blocks = [primary, *derived]
        if len(blocks) != len(set(blocks)):
            fail(f"{material_id} lists a block more than once")
        duplicates = catalog_blocks.intersection(blocks)
        if duplicates:
            fail(f"blocks occur in more than one material profile: {sorted(duplicates)}")
        catalog_blocks.update(blocks)

        if family not in ALLOWED_MATERIAL_FAMILIES:
            fail(f"{material_id} has unsupported family {family}")
        if family == "ore" and any(block not in semantic_tags["geostrata:ores"] for block in blocks):
            fail(f"every {material_id} grade block must be present in geostrata:ores")

        declared_tags = entry["semanticTags"]
        if not isinstance(declared_tags, list) or any(tag not in semantic_tags for tag in declared_tags):
            fail(f"{material_id} has unsupported semanticTags {declared_tags!r}")
        actual_tags = {tag for tag, values in semantic_tags.items() if primary in values}
        if set(declared_tags) != actual_tags:
            fail(
                f"{material_id} semanticTags do not match live tags; "
                f"declared={sorted(declared_tags)}, actual={sorted(actual_tags)}"
            )

        gameplay = entry["gameplay"]
        breaking = gameplay.get("breaking") if isinstance(gameplay, dict) else None
        cultivation = gameplay.get("cultivation") if isinstance(gameplay, dict) else None
        if not isinstance(breaking, dict):
            fail(f"{material_id} must declare gameplay.breaking")
        expected_cultivation = "not_applicable" if family in {"rock", "ore"} else "not_implemented"
        if cultivation != {"status": expected_cultivation}:
            fail(f"{material_id} cultivation must be marked {expected_cultivation}")

        expected_tool = "pickaxe" if family in {"rock", "ore"} else "shovel"
        expected_tier = breaking.get("minimumToolTier") if family == "ore" else (
            "stone" if family == "rock" else "none"
        )
        if expected_tier not in {"none", "stone", "iron"}:
            fail(f"{material_id} has unsupported minimumToolTier {expected_tier}")
        if breaking.get("mineableWith") != expected_tool:
            fail(f"{material_id} must be mineableWith {expected_tool}")
        if breaking.get("minimumToolTier") != expected_tier:
            fail(f"{material_id} minimumToolTier must be {expected_tier}")

        for block in blocks:
            source = source_profiles.get(block)
            if source is None:
                fail(f"material profile references unregistered block {block}")
            expected_family = family if family in {"rock", "ore"} else "earth"
            if source["family"] != expected_family:
                fail(f"{block} Java registration does not match material family {family}")
            for trait in ("copyFrom", "hardness", "blastResistance", "requiresTool", "soundGroup"):
                if breaking.get(trait) != source[trait]:
                    fail(
                        f"{block} {trait} differs from GeoStrataBlocks.java; "
                        f"catalog={breaking.get(trait)!r}, source={source[trait]!r}"
                    )
            if block not in mineable_tags[expected_tool]:
                fail(f"{block} is absent from minecraft:mineable/{expected_tool}")
            for tier, tagged_blocks in tier_tags.items():
                if (block in tagged_blocks) != (expected_tier == tier):
                    fail(f"{block} does not match its declared minimumToolTier {expected_tier}")

        if family == "ore":
            validate_ore_material(
                material_id,
                blocks,
                gameplay,
                source_profiles,
                occurrences,
                grade_order,
                economics,
            )

        assets = entry["assets"]
        if family == "ore":
            ore_material = gameplay["oreEconomy"]["material"]
            expected_assets = {
                "textureMatrix": "geostrata:materials/ore_texture_matrix",
                "material": ore_material,
            }
            if assets != expected_assets:
                fail(f"{material_id} must reference its ore texture matrix material")
            for grade, block in zip(grade_order, blocks, strict=True):
                expected_textures = {
                    f"geostrata:block/ore/{ore_material}/{host}/{grade}"
                    for host in matrix_hosts
                }
                actual_textures = block_textures(block)
                if actual_textures != expected_textures:
                    fail(f"{block} host-aware model matrix is incomplete or contains drift")
                for texture in expected_textures:
                    texture_path = ASSETS / "textures" / "block" / f"{texture.removeprefix('geostrata:block/')}.png"
                    if png_size(texture_path) != (16, 16):
                        fail(f"{texture_path.relative_to(ROOT)} must be exactly 16x16")
        else:
            texture_set_name = assets.get("textureSet") if isinstance(assets, dict) else None
            if texture_set_name not in texture_sets:
                fail(f"{material_id} references unknown textureSet {texture_set_name!r}")
            declared_textures = set(texture_sets[texture_set_name]["textures"])
            actual_textures = set().union(*(block_textures(block) for block in blocks))
            if declared_textures != actual_textures:
                fail(
                    f"{material_id} textureSet does not match live block models; "
                    f"declared={sorted(declared_textures)}, actual={sorted(actual_textures)}"
                )

    if catalog_blocks != set(source_profiles):
        fail(
            "material profiles must exactly cover registered blocks; "
            f"missing={sorted(set(source_profiles) - catalog_blocks)}, "
            f"extra={sorted(catalog_blocks - set(source_profiles))}"
        )
    rock_materials = {entry["id"] for entry in materials if entry["family"] == "rock"}
    lithology_hosts = {entry["id"] for entry in load_json(CATALOG)["lithologies"]}
    matrix_host_set = set(matrix_hosts)
    required_ore_hosts = {
        host
        for occurrence in occurrences.values()
        for host in occurrence.get("hostLithologies", [])
    }
    if not required_ore_hosts.issubset(matrix_host_set):
        fail(
            "ore texture matrix must cover every lithology used by an ore occurrence; "
            f"missing={sorted(required_ore_hosts - matrix_host_set)}"
        )
    if not matrix_host_set.issubset(rock_materials | lithology_hosts):
        fail(
            "ore texture matrix may only contain registered rock materials or lithologies; "
            f"unknown={sorted(matrix_host_set - rock_materials - lithology_hosts)}"
        )
    try:
        host_source = ORE_HOST_SOURCE.read_text(encoding="utf-8")
    except OSError as exc:
        fail(f"cannot read {ORE_HOST_SOURCE.relative_to(ROOT)}: {exc}")
    source_hosts = set(re.findall(r'\b[A-Z_]+\("([a-z_]+)"\)', host_source))
    if source_hosts != matrix_host_set:
        fail("OreHost.java must exactly match the artist texture-matrix hosts")
    validate_continuity_hosts(matrix_hosts)
    return len(materials)


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
        runtime_authority = entry.get("runtimeAuthority")

        if not isinstance(lithology_id, str) or not SIMPLE_ID.fullmatch(lithology_id):
            fail(f"invalid lithology id: {lithology_id!r}")
        if not isinstance(block, str) or not IDENTIFIER.fullmatch(block):
            fail(f"{lithology_id} has invalid block identifier {block!r}")
        if feature is not None and (not isinstance(feature, str) or not SIMPLE_ID.fullmatch(feature)):
            fail(f"{lithology_id} has invalid baselineFeature {feature!r}")
        if runtime_authority is not None and (
            not isinstance(runtime_authority, str) or not SIMPLE_ID.fullmatch(runtime_authority)
        ):
            fail(f"{lithology_id} has invalid runtimeAuthority {runtime_authority!r}")
        if (feature is None) == (runtime_authority is None):
            fail(f"{lithology_id} must declare exactly one of baselineFeature or runtimeAuthority")

        if lithology_id in ids:
            fail(f"duplicate lithology id: {lithology_id}")
        if block in blocks:
            fail(f"duplicate lithology block: {block}")
        if feature is not None and feature in features:
            fail(f"duplicate baseline feature: {feature}")
        ids.add(lithology_id)
        blocks.add(block)
        if feature is not None:
            features.add(feature)

        geo_strata_owned = block.startswith("geostrata:")
        if geo_strata_owned and block != f"geostrata:{lithology_id}":
            fail(f"{lithology_id} must map to geostrata:{lithology_id}, found {block}")
        if not geo_strata_owned and feature is not None:
            fail(f"{lithology_id} provider-owned lithology must not declare GeoStrata baselineFeature")

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

        if feature is not None:
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

    material_count = validate_material_catalog()
    print(
        "geology validation OK: "
        f"{len(entries)} lithologies, {len(ROCK_CLASSES)} rock classes, {material_count} material profiles"
    )


if __name__ == "__main__":
    main()
