#!/usr/bin/env python3
"""Validate the checked-in GeoStrata development-pack contract."""

from __future__ import annotations

import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "pack"
MANIFEST_PATH = PACK / "manifest.json"
INVENTORY_PATH = PACK / "dependencies.json"
ARTIFACT_LOCKS_PATH = PACK / "artifact-locks.json"

CONQUEST_PROJECT_ID = 250077
CONQUEST_170_FILE_ID = 8702617
FORGE_SKYBOXES_PROJECT_ID = 918052
CONQUEST_FABRIC_SUPPORT = {
    568563: "Entity Texture Features (ETF)",
    844662: "Entity Model Features (EMF)",
    531351: "Continuity",
    563977: "Puzzle",
    958094: "Polytone",
    408209: "Nuit",
    835546: "ArdaGrass",
}


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


def validate_artifact_locks(locks, manifest_entries, expected_mc: str) -> int:
    if locks.get("schemaVersion") != 1:
        fail("pack/artifact-locks.json schemaVersion must be 1")
    entries = locks.get("entries")
    if not isinstance(entries, list):
        fail("pack/artifact-locks.json entries must be an array")

    project_ids = set()
    mod_ids = set()
    filenames = set()
    for entry in entries:
        if not isinstance(entry, dict):
            fail(f"artifact lock entry must be an object: {entry!r}")
        required = {
            "name", "slug", "role", "source", "projectID", "fileID", "status",
            "filename", "size", "sha256", "modID", "version", "minecraftVersion", "loader"
        }
        missing = sorted(required - set(entry))
        if missing:
            fail(f"artifact lock is missing fields {missing}: {entry}")

        project_id = entry["projectID"]
        mod_id = entry["modID"]
        filename = entry["filename"]
        if not isinstance(project_id, int) or isinstance(project_id, bool) or project_id <= 0:
            fail(f"artifact lock projectID must be a positive integer: {entry}")
        if project_id in project_ids:
            fail(f"duplicate projectID {project_id} in artifact locks")
        project_ids.add(project_id)
        if not isinstance(mod_id, str) or not re.fullmatch(r"[a-z0-9_-]+", mod_id):
            fail(f"artifact lock has invalid modID {mod_id!r}")
        if mod_id in mod_ids:
            fail(f"duplicate modID {mod_id} in artifact locks")
        mod_ids.add(mod_id)
        if not isinstance(filename, str) or Path(filename).name != filename or not filename.endswith(".jar"):
            fail(f"artifact lock filename must be a bare .jar filename: {filename!r}")
        if filename in filenames:
            fail(f"duplicate filename {filename} in artifact locks")
        filenames.add(filename)

        if entry["source"] != "curseforge":
            fail(f"unsupported artifact-lock source {entry['source']!r}")
        if entry["loader"] != "fabric":
            fail(f"artifact lock {filename} must target Fabric")
        if entry["minecraftVersion"] != expected_mc:
            fail(f"artifact lock {filename} targets Minecraft {entry['minecraftVersion']} instead of {expected_mc}")
        if not isinstance(entry["size"], int) or isinstance(entry["size"], bool) or entry["size"] <= 0:
            fail(f"artifact lock {filename} has invalid size")
        if not isinstance(entry["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]):
            fail(f"artifact lock {filename} must contain a lowercase SHA-256 hash")

        status = entry["status"]
        file_id = entry["fileID"]
        if status == "verified-pending-manifest-pin":
            if file_id is not None:
                fail(f"pending artifact lock {filename} must keep fileID null until independently verified")
            if project_id in manifest_entries:
                fail(f"pending artifact project {project_id} is already present in the active CurseForge manifest")
        elif status == "manifest-pinned":
            if not isinstance(file_id, int) or isinstance(file_id, bool) or file_id <= 0:
                fail(f"manifest-pinned artifact {filename} requires a positive fileID")
            manifest_entry = manifest_entries.get(project_id)
            if not manifest_entry or manifest_entry.get("fileID") != file_id:
                fail(f"manifest-pinned artifact {filename} does not match pack/manifest.json")
        else:
            fail(f"artifact lock {filename} has unsupported status {status!r}")

    return len(entries)


def validate_conquest_fabric_stack(inventory_entries) -> None:
    conquest = inventory_entries.get(CONQUEST_PROJECT_ID)
    if not conquest or conquest.get("role") != "integration-content":
        fail("Conquest Reforged must remain classified as optional integration content, not a GeoStrata core dependency")

    if FORGE_SKYBOXES_PROJECT_ID in inventory_entries:
        fail("ForgeSkyboxes must not be present in the Fabric development pack; use Nuit/FabricSkyBoxes")

    if conquest.get("fileID") == CONQUEST_170_FILE_ID:
        missing = [
            f"{project_id} ({name})"
            for project_id, name in CONQUEST_FABRIC_SUPPORT.items()
            if project_id not in inventory_entries
        ]
        if missing:
            fail("Conquest 1.7.0 Fabric baseline is missing support projects: " + ", ".join(missing))

        expected_pins = {
            531351: 5962874,
            563977: 7394086,
        }
        for project_id, file_id in expected_pins.items():
            if inventory_entries[project_id].get("fileID") != file_id:
                fail(f"Conquest 1.7.0 support project {project_id} must remain pinned to verified file {file_id}")


def main() -> None:
    manifest = load_json(MANIFEST_PATH)
    inventory = load_json(INVENTORY_PATH)
    artifact_locks = load_json(ARTIFACT_LOCKS_PATH)

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

    validate_conquest_fabric_stack(inventory_entries)

    fabric_api = inventory_entries.get(306612)
    if not fabric_api or fabric_api.get("role") != "geostrata-core-dependency":
        fail("Fabric API must remain identified as the pack dependency required by GeoStrata core")

    lock_count = validate_artifact_locks(artifact_locks, manifest_entries, expected_mc)

    print(
        f"pack validation OK: Minecraft {expected_mc}, {primary_loaders[0]}, "
        f"{len(manifest_entries)} CurseForge projects, {lock_count} verified pending/pinned artifact locks"
    )


if __name__ == "__main__":
    main()
