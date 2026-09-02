#!/usr/bin/env python3
"""Validate data-only optional-mod bridges and compatibility planning metadata."""

from __future__ import annotations

import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BLOCKS_JAVA = ROOT / "src/main/java/com/geostrata/block/GeoStrataBlocks.java"
STREAMS_STYLES = ROOT / "src/main/resources/data/geostrata/streamsreflowing/bank_style"
STREAMS_FLOOR_TAG = ROOT / "src/main/resources/data/streamsreflowing/tags/blocks/underwater_vegetation_floor.json"
STREAMS_ROCKY_BANKS_TAG = ROOT / "src/main/resources/data/streamsreflowing/tags/worldgen/biome/rocky_banks.json"
EXTERNAL_MATERIALS = ROOT / "src/main/resources/data/geostrata/compatibility/external_materials.json"
LITHOLOGIES = ROOT / "src/main/resources/data/geostrata/geology/lithologies.json"
IDENTIFIER = re.compile(r"[a-z0-9_.-]+:[a-z0-9/._-]+")
SIMPLE_ID = re.compile(r"[a-z0-9_]+")
ALLOWED_CANONICAL_KINDS = {"ore", "mineral_deposit"}
ALLOWED_PROVIDER_STATUSES = {"catalogued", "catalogued_alias"}
ALLOWED_BACKLOG_KINDS = {"lithology", "formation_context"}
ALLOWED_BACKLOG_STATUSES = {"implemented", "missing", "deferred"}
ALLOWED_PROVINCES = {
    "sedimentary_basin",
    "cratonic_shield",
    "orogenic_belt",
    "volcanic_arc",
    "rift_province",
}
ALLOWED_FORMATION_STYLES = {
    "coal_seam",
    "vein",
    "micro_vein",
    "stratiform",
    "disseminated",
    "massive_lens_or_pocket",
}


def fail(message: str) -> None:
    print(f"optional integration validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def block_registrations() -> tuple[set[str], set[str]]:
    try:
        source = BLOCKS_JAVA.read_text(encoding="utf-8")
    except OSError as exc:
        fail(f"cannot read {BLOCKS_JAVA.relative_to(ROOT)}: {exc}")

    all_names = set(re.findall(r'register(?:Rock|RockVariant|Earth)\("([a-z0-9_]+)"', source))
    earth_names = set(re.findall(r'registerEarth\("([a-z0-9_]+)"', source))
    if not all_names or not earth_names:
        fail("could not discover GeoStrata block registrations")
    return (
        {f"geostrata:{name}" for name in all_names},
        {f"geostrata:{name}" for name in earth_names},
    )


def load_object(path: Path) -> dict:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")
    if not isinstance(data, dict):
        fail(f"{path.relative_to(ROOT)} must contain a JSON object")
    return data


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

    blocks, earth_blocks = block_registrations()
    paths = sorted(STREAMS_STYLES.glob("*.json"))
    expected = {"fluvial_sediments.json", "swamp_sediments.json", "jungle_sediments.json"}
    if {path.name for path in paths} != expected:
        fail(f"Streams Reflowing bridge files must be exactly {sorted(expected)}")

    seen_geostrata_blocks: set[str] = set()
    for path in paths:
        data = load_object(path)
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

    floor = load_object(STREAMS_FLOOR_TAG)
    if floor.get("replace") is not False:
        fail("Streams Reflowing underwater vegetation floor extension must use replace=false")
    floor_values = floor.get("values")
    if not isinstance(floor_values, list) or not all(isinstance(value, str) for value in floor_values):
        fail("Streams Reflowing underwater vegetation floor extension must contain direct block IDs")
    if set(floor_values) != earth_blocks:
        fail(
            "underwater vegetation floor integration must explicitly classify every GeoStrata earth block; "
            f"missing={sorted(earth_blocks - set(floor_values))}, extra={sorted(set(floor_values) - earth_blocks)}"
        )

    rocky = load_object(STREAMS_ROCKY_BANKS_TAG)
    if rocky.get("replace") is not False:
        fail("Streams Reflowing rocky_banks extension must use replace=false")
    if rocky.get("values") != ["#geostrata:has_mountain_rocks"]:
        fail("Streams Reflowing rocky_banks must inherit exactly #geostrata:has_mountain_rocks")

    if not seen_geostrata_blocks:
        fail("Streams Reflowing bridge does not actually use any GeoStrata material")
    print(
        f"optional integration validation OK: {len(paths)} Streams Reflowing bank styles, "
        f"{len(seen_geostrata_blocks)} styled GeoStrata materials, {len(earth_blocks)} vegetation-floor blocks"
    )


def _string_list(value, field: str, allow_empty: bool = False) -> list[str]:
    if not isinstance(value, list) or (not allow_empty and not value) or not all(isinstance(item, str) for item in value):
        requirement = "a string list" if allow_empty else "a non-empty string list"
        fail(f"external material field {field} must be {requirement}")
    if len(value) != len(set(value)):
        fail(f"external material field {field} must not contain duplicates")
    return value


def validate_external_materials() -> None:
    data = load_object(EXTERNAL_MATERIALS)
    if data.get("schemaVersion") != 2 or data.get("model") != "geostrata:external_material_catalog":
        fail("external material catalogue must use schema 2 canonical formation routes")
    if data.get("runtimeStatus") != "planning_metadata_only":
        fail("external material catalogue must remain planning metadata only")

    lithology_data = load_object(LITHOLOGIES)
    current_lithologies = {
        entry.get("id") for entry in lithology_data.get("lithologies", []) if isinstance(entry, dict)
    }

    canonical = data.get("canonicalMaterials")
    if not isinstance(canonical, list) or not canonical:
        fail("external material catalogue must contain canonicalMaterials")
    canonical_ids: set[str] = set()
    canonical_roles: set[str] = set()
    required_geology_refs: list[tuple[str, str, set[str]]] = []
    route_count = 0
    for material in canonical:
        if not isinstance(material, dict):
            fail("canonical material entries must be objects")
        material_id = material.get("id")
        if not isinstance(material_id, str) or not SIMPLE_ID.fullmatch(material_id) or material_id in canonical_ids:
            fail(f"invalid or duplicate canonical material id {material_id!r}")
        canonical_ids.add(material_id)
        if material.get("kind") not in ALLOWED_CANONICAL_KINDS:
            fail(f"{material_id} has unsupported canonical material kind {material.get('kind')!r}")
        role = material.get("canonicalRole")
        if not isinstance(role, str) or not IDENTIFIER.fullmatch(role) or not role.startswith("geostrata:"):
            fail(f"{material_id} has invalid canonicalRole {role!r}")
        if role in canonical_roles:
            fail(f"canonical role {role} is assigned more than once")
        canonical_roles.add(role)

        routes = material.get("formationRoutes")
        if not isinstance(routes, list) or not routes:
            fail(f"{material_id} must declare at least one formation route")
        route_ids: set[str] = set()
        for route in routes:
            route_count += 1
            if not isinstance(route, dict):
                fail(f"{material_id} formation routes must be objects")
            route_id = route.get("id")
            if not isinstance(route_id, str) or not SIMPLE_ID.fullmatch(route_id) or route_id in route_ids:
                fail(f"{material_id} has invalid or duplicate formation route id {route_id!r}")
            route_ids.add(route_id)
            hosts = _string_list(route.get("hostLithologies"), f"{material_id}.{route_id}.hostLithologies", allow_empty=True)
            future_hosts = _string_list(route.get("futureHostRoles"), f"{material_id}.{route_id}.futureHostRoles", allow_empty=True)
            if not hosts and not future_hosts:
                fail(f"{material_id}.{route_id} must declare a current or future host")
            unknown_hosts = set(hosts) - current_lithologies
            if unknown_hosts:
                fail(f"{material_id}.{route_id} references missing current hosts {sorted(unknown_hosts)}")
            if not all(SIMPLE_ID.fullmatch(host) for host in future_hosts):
                fail(f"{material_id}.{route_id} has invalid futureHostRoles")
            contexts = _string_list(route.get("provinceContexts"), f"{material_id}.{route_id}.provinceContexts")
            unknown_contexts = set(contexts) - ALLOWED_PROVINCES
            if unknown_contexts:
                fail(f"{material_id}.{route_id} uses unknown province contexts {sorted(unknown_contexts)}")
            styles = _string_list(route.get("depositStyles"), f"{material_id}.{route_id}.depositStyles")
            unknown_styles = set(styles) - ALLOWED_FORMATION_STYLES
            if unknown_styles:
                fail(f"{material_id}.{route_id} uses unsupported deposit styles {sorted(unknown_styles)}")
            body_styles = _string_list(
                route.get("bodyStyles", []),
                f"{material_id}.{route_id}.bodyStyles",
                allow_empty=True,
            )
            if not all(SIMPLE_ID.fullmatch(body_style) for body_style in body_styles):
                fail(f"{material_id}.{route_id} has invalid bodyStyles")
            required = set(_string_list(
                route.get("requiredGeology"),
                f"{material_id}.{route_id}.requiredGeology",
                allow_empty=True,
            ))
            required_geology_refs.append((material_id, route_id, required))

    providers = data.get("providers")
    if not isinstance(providers, list) or not providers:
        fail("external material catalogue must contain providers")
    provider_ids: set[str] = set()
    mapped_materials: set[str] = set()
    mapping_count = 0
    for provider in providers:
        if not isinstance(provider, dict):
            fail("external material provider entries must be objects")
        provider_id = provider.get("id")
        if not isinstance(provider_id, str) or not SIMPLE_ID.fullmatch(provider_id) or provider_id in provider_ids:
            fail(f"invalid or duplicate external provider id {provider_id!r}")
        provider_ids.add(provider_id)
        mod_ids = _string_list(provider.get("modIds"), f"{provider_id}.modIds")
        if not all(SIMPLE_ID.fullmatch(mod_id) for mod_id in mod_ids):
            fail(f"{provider_id} has invalid mod IDs")
        mappings = provider.get("materials")
        if not isinstance(mappings, list) or not mappings:
            fail(f"{provider_id} must map at least one canonical material")
        seen_provider_materials: set[str] = set()
        for mapping in mappings:
            mapping_count += 1
            if not isinstance(mapping, dict):
                fail(f"{provider_id} material mappings must be objects")
            material_id = mapping.get("canonicalMaterial")
            if material_id not in canonical_ids:
                fail(f"{provider_id} references unknown canonical material {material_id!r}")
            if material_id in seen_provider_materials:
                fail(f"{provider_id} maps canonical material {material_id} more than once")
            seen_provider_materials.add(material_id)
            mapped_materials.add(material_id)
            if mapping.get("status") not in ALLOWED_PROVIDER_STATUSES:
                fail(f"{provider_id}:{material_id} has unsupported mapping status {mapping.get('status')!r}")
            blocks = _string_list(mapping.get("providerBlocks"), f"{provider_id}:{material_id}.providerBlocks")
            if not all(IDENTIFIER.fullmatch(block) for block in blocks):
                fail(f"{provider_id}:{material_id} has invalid provider block IDs")
            output = mapping.get("providerOutput")
            if output is not None and (not isinstance(output, str) or not IDENTIFIER.fullmatch(output)):
                fail(f"{provider_id}:{material_id} has invalid providerOutput {output!r}")
            tags = _string_list(mapping.get("commonTags"), f"{provider_id}:{material_id}.commonTags", allow_empty=True)
            if not all(IDENTIFIER.fullmatch(tag) for tag in tags):
                fail(f"{provider_id}:{material_id} has invalid common tag IDs")
    if mapped_materials != canonical_ids:
        fail(
            "every canonical external material must have at least one provider mapping; "
            f"missing={sorted(canonical_ids - mapped_materials)}"
        )

    backlog = data.get("coreGeologyBacklog")
    if not isinstance(backlog, list) or not backlog:
        fail("external material catalogue must contain coreGeologyBacklog")
    backlog_ids: set[str] = set()
    formation_context_ids: set[str] = set()
    for entry in backlog:
        if not isinstance(entry, dict):
            fail("core geology backlog entries must be objects")
        entry_id = entry.get("id")
        if not isinstance(entry_id, str) or not SIMPLE_ID.fullmatch(entry_id) or entry_id in backlog_ids:
            fail(f"invalid or duplicate core geology backlog id {entry_id!r}")
        backlog_ids.add(entry_id)
        kind = entry.get("kind")
        status = entry.get("status")
        if kind not in ALLOWED_BACKLOG_KINDS or status not in ALLOWED_BACKLOG_STATUSES:
            fail(f"{entry_id} has unsupported backlog kind/status {kind!r}/{status!r}")
        if entry.get("scope") != "core_geology":
            fail(f"{entry_id} must remain unconditional core_geology work")

        if kind == "lithology":
            role = entry.get("canonicalRole")
            block = entry.get("minecraftBlock")
            if not isinstance(role, str) or not IDENTIFIER.fullmatch(role) or not role.startswith("geostrata:rock/"):
                fail(f"{entry_id} has invalid lithology canonicalRole")
            if not isinstance(block, str) or not IDENTIFIER.fullmatch(block):
                fail(f"{entry_id} has invalid minecraftBlock")
            consumers = _string_list(entry.get("priorityConsumers"), f"{entry_id}.priorityConsumers")
            unknown_consumers = set(consumers) - provider_ids
            if unknown_consumers:
                fail(f"{entry_id} references unknown priority consumers {sorted(unknown_consumers)}")
            if status == "implemented" and entry_id not in current_lithologies:
                fail(f"{entry_id} is marked implemented but is not a current GeoStrata lithology")
            if status == "missing" and entry_id in current_lithologies:
                fail(f"{entry_id} is already a GeoStrata lithology and cannot remain marked missing")
        else:
            formation_context_ids.add(entry_id)
            styles = _string_list(entry.get("reusesDepositStyles"), f"{entry_id}.reusesDepositStyles")
            unknown_styles = set(styles) - ALLOWED_FORMATION_STYLES
            if unknown_styles:
                fail(f"{entry_id} reuses unsupported deposit styles {sorted(unknown_styles)}")
            future = _string_list(entry.get("futureLithologies"), f"{entry_id}.futureLithologies", allow_empty=True)
            if not all(SIMPLE_ID.fullmatch(host) for host in future):
                fail(f"{entry_id} has invalid futureLithologies")
            unlocks = _string_list(entry.get("unlocks"), f"{entry_id}.unlocks")
            unknown_unlocks = set(unlocks) - canonical_ids
            if unknown_unlocks:
                fail(f"{entry_id} unlocks unknown canonical materials {sorted(unknown_unlocks)}")
            if not isinstance(entry.get("newOreGeometryRequired"), bool):
                fail(f"{entry_id} must explicitly declare newOreGeometryRequired")

    for material_id, route_id, required in required_geology_refs:
        unknown = required - formation_context_ids
        if unknown:
            fail(f"{material_id}.{route_id} requires unknown formation contexts {sorted(unknown)}")

    print(
        f"external material catalogue validation OK: {len(canonical_ids)} canonical materials, "
        f"{route_count} formation routes, {len(provider_ids)} providers, {mapping_count} provider mappings, "
        f"{len(backlog_ids)} core geology backlog entries"
    )


if __name__ == "__main__":
    validate_streams_reflowing()
    validate_external_materials()
