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
ALLOWED_EXTERNAL_KINDS = {"ore", "ore_alias", "mineral_deposit"}
ALLOWED_EXTERNAL_STATUSES = {"catalogued", "catalogued_alias"}
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
        fail(f"external material field {field} must be {'a' if allow_empty else 'a non-empty'} string list")
    return value


def validate_external_materials() -> None:
    data = load_object(EXTERNAL_MATERIALS)
    if data.get("schemaVersion") != 1 or data.get("model") != "geostrata:external_material_catalog":
        fail("external material catalogue schema/model is unsupported")
    if data.get("runtimeStatus") != "planning_metadata_only":
        fail("external material catalogue must remain planning metadata only")

    lithology_data = load_object(LITHOLOGIES)
    current_lithologies = {
        entry.get("id") for entry in lithology_data.get("lithologies", []) if isinstance(entry, dict)
    }

    providers = data.get("providers")
    if not isinstance(providers, list) or not providers:
        fail("external material catalogue must contain providers")

    provider_ids: set[str] = set()
    canonical_roles: dict[str, set[str]] = {}
    material_count = 0
    for provider in providers:
        if not isinstance(provider, dict):
            fail("external material provider entries must be objects")
        provider_id = provider.get("id")
        if not isinstance(provider_id, str) or not SIMPLE_ID.fullmatch(provider_id) or provider_id in provider_ids:
            fail(f"invalid or duplicate external provider id {provider_id!r}")
        provider_ids.add(provider_id)
        mod_ids = _string_list(provider.get("modIds"), f"{provider_id}.modIds")
        if len(mod_ids) != len(set(mod_ids)) or not all(SIMPLE_ID.fullmatch(mod_id) for mod_id in mod_ids):
            fail(f"{provider_id} has invalid or duplicate mod IDs")

        materials = provider.get("materials")
        if not isinstance(materials, list) or not materials:
            fail(f"{provider_id} must catalogue at least one material")
        seen_material_ids: set[str] = set()
        for material in materials:
            material_count += 1
            if not isinstance(material, dict):
                fail(f"{provider_id} material entries must be objects")
            material_id = material.get("id")
            if not isinstance(material_id, str) or not SIMPLE_ID.fullmatch(material_id) or material_id in seen_material_ids:
                fail(f"{provider_id} has invalid or duplicate material id {material_id!r}")
            seen_material_ids.add(material_id)
            kind = material.get("kind")
            status = material.get("status")
            if kind not in ALLOWED_EXTERNAL_KINDS or status not in ALLOWED_EXTERNAL_STATUSES:
                fail(f"{provider_id}:{material_id} has unsupported kind/status {kind!r}/{status!r}")
            if (kind == "ore_alias") != (status == "catalogued_alias"):
                fail(f"{provider_id}:{material_id} alias kind/status must agree")

            role = material.get("canonicalRole")
            if not isinstance(role, str) or not IDENTIFIER.fullmatch(role) or not role.startswith("geostrata:"):
                fail(f"{provider_id}:{material_id} has invalid canonicalRole {role!r}")
            canonical_roles.setdefault(role, set()).add(kind)

            provider_blocks = _string_list(material.get("providerBlocks"), f"{provider_id}:{material_id}.providerBlocks")
            if not all(IDENTIFIER.fullmatch(block) for block in provider_blocks):
                fail(f"{provider_id}:{material_id} has invalid provider block IDs")
            output = material.get("providerOutput")
            if output is not None and (not isinstance(output, str) or not IDENTIFIER.fullmatch(output)):
                fail(f"{provider_id}:{material_id} has invalid providerOutput {output!r}")
            tags = _string_list(material.get("commonTags"), f"{provider_id}:{material_id}.commonTags", allow_empty=True)
            if not all(IDENTIFIER.fullmatch(tag) for tag in tags):
                fail(f"{provider_id}:{material_id} has invalid common tag IDs")

            styles = _string_list(material.get("preferredFormationStyles"), f"{provider_id}:{material_id}.preferredFormationStyles")
            unknown_styles = set(styles) - ALLOWED_FORMATION_STYLES
            if unknown_styles:
                fail(f"{provider_id}:{material_id} uses unsupported occurrence styles {sorted(unknown_styles)}")
            hosts = _string_list(material.get("currentGeoStrataHosts"), f"{provider_id}:{material_id}.currentGeoStrataHosts")
            unknown_hosts = set(hosts) - current_lithologies
            if unknown_hosts:
                fail(f"{provider_id}:{material_id} references missing current GeoStrata hosts {sorted(unknown_hosts)}")
            future_hosts = _string_list(material.get("futureHostRoles"), f"{provider_id}:{material_id}.futureHostRoles")
            if set(hosts) & set(future_hosts):
                fail(f"{provider_id}:{material_id} mixes current lithology IDs into futureHostRoles")

    for role, kinds in canonical_roles.items():
        if len(kinds) > 1 and kinds != {"ore", "ore_alias"}:
            fail(f"canonical role {role} is reused by incompatible material kinds {sorted(kinds)}")

    priority_hosts = data.get("priorityHostRocks")
    if not isinstance(priority_hosts, list) or not priority_hosts:
        fail("external material catalogue must contain priorityHostRocks")
    seen_hosts: set[str] = set()
    for host in priority_hosts:
        if not isinstance(host, dict):
            fail("priority host entries must be objects")
        host_id = host.get("id")
        if not isinstance(host_id, str) or not SIMPLE_ID.fullmatch(host_id) or host_id in seen_hosts:
            fail(f"invalid or duplicate priority host id {host_id!r}")
        seen_hosts.add(host_id)
        block = host.get("providerBlock")
        role = host.get("canonicalRole")
        if not isinstance(block, str) or not IDENTIFIER.fullmatch(block):
            fail(f"priority host {host_id} has invalid providerBlock")
        if not isinstance(role, str) or not IDENTIFIER.fullmatch(role) or not role.startswith("geostrata:rock/"):
            fail(f"priority host {host_id} has invalid canonicalRole")
        consumers = _string_list(host.get("priorityConsumers"), f"priorityHostRocks.{host_id}.priorityConsumers")
        if not set(consumers).issubset(provider_ids):
            fail(f"priority host {host_id} references unknown consumers {sorted(set(consumers) - provider_ids)}")
        if host.get("status") != "missing_geo_strata_lithology":
            fail(f"priority host {host_id} must remain explicit that its GeoStrata lithology is missing")
        if host_id in current_lithologies:
            fail(f"priority host {host_id} is already a GeoStrata lithology; update its catalogue status")

    print(
        f"external material catalogue validation OK: {len(providers)} providers, "
        f"{material_count} provider material entries, {len(priority_hosts)} priority host rocks"
    )


if __name__ == "__main__":
    validate_streams_reflowing()
    validate_external_materials()
