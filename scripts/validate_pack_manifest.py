#!/usr/bin/env python3
"""Validate the checked-in GeoStrata development-pack contract."""

from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "pack"
MANIFEST_PATH = PACK / "manifest.json"
INVENTORY_PATH = PACK / "dependencies.json"


def fail(message: str) -> None:
    print(f"pack validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    try:
        with path.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")


def unique_by(items, key, label: str):
    result = {}
    for item in items:
        value = item.get(key)
        if value is None:
            fail(f"{label} entry is missing {key}: {item}")
        if value in result:
            fail(f"duplicate {key} {value} in {label}")
        result[value] = item
    return result


def main() -> None:
    manifest = load_json(MANIFEST_PATH)
    inventory = load_json(INVENTORY_PATH)

    if manifest.get("manifestType") != "minecraftModpack" or manifest.get("manifestVersion") != 1:
        fail("pack/manifest.json is not a CurseForge manifest v1")
    if manifest.get("name") != "GeoStrata Development Pack":
        fail("manifest name must remain 'GeoStrata Development Pack'")

    minecraft = manifest.get("minecraft", {})
    expected_mc = inventory.get("minecraftVersion")
    if minecraft.get("version") != expected_mc:
        fail(f"Minecraft version differs between manifest and inventory ({minecraft.get('version')} != {expected_mc})")

    loaders = minecraft.get("modLoaders") or []
    primary_loaders = [entry.get("id", "") for entry in loaders if entry.get("primary")]
    if len(primary_loaders) != 1 or not primary_loaders[0].startswith("fabric-"):
        fail(f"expected exactly one primary Fabric loader, found {primary_loaders}")
    if inventory.get("loader") != "fabric":
        fail("dependency inventory loader must be 'fabric'")

    overrides = manifest.get("overrides")
    if overrides != "overrides":
        fail("manifest overrides path must be 'overrides'")
    if not (PACK / overrides).is_dir():
        fail("manifest overrides directory does not exist")
    if (PACK / overrides / "mods").exists():
        fail("do not commit mod jars under pack/overrides/mods; dependencies belong in the manifest")

    manifest_entries = unique_by(manifest.get("files") or [], "projectID", "manifest")
    inventory_entries = unique_by(inventory.get("entries") or [], "projectID", "inventory")

    if set(manifest_entries) != set(inventory_entries):
        missing_inventory = sorted(set(manifest_entries) - set(inventory_entries))
        missing_manifest = sorted(set(inventory_entries) - set(manifest_entries))
        fail(
            "manifest/inventory project sets differ; "
            f"missing from inventory={missing_inventory}, missing from manifest={missing_manifest}"
        )

    seen_names = set()
    seen_slugs = set()
    for project_id, manifest_entry in sorted(manifest_entries.items()):
        inventory_entry = inventory_entries[project_id]
        for field in ("fileID", "required"):
            if manifest_entry.get(field) != inventory_entry.get(field):
                fail(
                    f"project {project_id} differs on {field}: "
                    f"manifest={manifest_entry.get(field)!r}, inventory={inventory_entry.get(field)!r}"
                )

        name = inventory_entry.get("name")
        slug = inventory_entry.get("slug")
        role = inventory_entry.get("role")
        if not name or not slug or not role:
            fail(f"project {project_id} must have non-empty name, slug and role")
        if name in seen_names:
            fail(f"duplicate dependency name: {name}")
        if slug in seen_slugs:
            fail(f"duplicate dependency slug: {slug}")
        seen_names.add(name)
        seen_slugs.add(slug)

    conquest = inventory_entries.get(250077)
    if not conquest or conquest.get("role") != "integration-content":
        fail("Conquest Reforged must remain classified as optional integration content, not a GeoStrata core dependency")

    fabric_api = inventory_entries.get(306612)
    if not fabric_api or fabric_api.get("role") != "geostrata-core-dependency":
        fail("Fabric API must remain identified as the pack dependency required by GeoStrata core")

    print(
        f"pack validation OK: Minecraft {expected_mc}, {primary_loaders[0]}, "
        f"{len(manifest_entries)} CurseForge projects"
    )


if __name__ == "__main__":
    main()
