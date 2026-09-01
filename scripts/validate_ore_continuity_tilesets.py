#!/usr/bin/env python3
"""Validate committed GeoStrata texture assets without requiring Pillow."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "geostrata"
MATRIX_PATH = RESOURCES / "data" / "geostrata" / "materials" / "ore_texture_matrix.json"
MANIFEST_PATH = RESOURCES / "data" / "geostrata" / "materials" / "ore_ctm_manifest.json"
HOST_ROOT = ASSETS / "textures" / "block" / "host"
MASTER_ROOT = ASSETS / "textures" / "block" / "ore_source" / "master"
OVERLAY_ROOT = ASSETS / "textures" / "block" / "ore_source"
COMPOSITE_ROOT = ASSETS / "textures" / "block" / "ore"
MODELS_ROOT = ASSETS / "models"
HOST_PROPERTIES_ROOT = ASSETS / "optifine" / "ctm" / "host"
HOST_TEXTURE_ROOT = ASSETS / "textures" / "optifine" / "ctm" / "host"
PROPERTIES_ROOT = ASSETS / "optifine" / "ctm" / "ore"
TEXTURE_ROOT = ASSETS / "textures" / "optifine" / "ctm" / "ore"
PREVIEW_PATH = ROOT / "docs" / "images" / "ore-ctm-tileset-preview.png"
GRADES = ("poor", "medium", "rich", "massive")
HOST_VARIANT_COUNT = 4
COMPACT_TILE_COUNT = 5


def fail(message: str) -> None:
    print(f"texture validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> dict[str, object]:
    try:
        with path.open("r", encoding="utf-8") as handle:
            value = json.load(handle)
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")
    if not isinstance(value, dict):
        fail(f"{path.relative_to(ROOT)} must contain an object")
    return value


def png_size(path: Path) -> tuple[int, int]:
    try:
        header = path.read_bytes()[:24]
    except OSError as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        fail(f"{path.relative_to(ROOT)} must be a PNG image")
    return struct.unpack(">II", header[16:24])


def host_properties_text(host: str) -> str:
    tiles = " ".join(
        f"geostrata:optifine/ctm/host/{host}/{index}"
        for index in range(HOST_VARIANT_COUNT)
    )
    return (
        "method=random\n"
        f"matchTiles=geostrata:block/host/{host}\n"
        f"tiles={tiles}\n"
    )


def properties_text(material: str, grade: str, host: str) -> str:
    tiles = " ".join(
        f"geostrata:textures/optifine/ctm/ore/{material}/{host}/{grade}/{index}"
        for index in range(COMPACT_TILE_COUNT)
    )
    return (
        "method=ctm_compact\n"
        f"matchBlocks=geostrata:{grade}_{material}_ore:host={host}\n"
        "connect=state\n"
        f"tiles={tiles}\n"
        "innerSeams=false\n"
    )


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require_png(path: Path, size: tuple[int, int]) -> None:
    if png_size(path) != size:
        fail(f"{path.relative_to(ROOT)} must be exactly {size[0]}x{size[1]}")


def validate_hashes(section: object, expected_paths: set[Path], label: str) -> None:
    if not isinstance(section, dict):
        fail(f"ore CTM manifest {label} must be an object")
    expected_keys = {path.relative_to(ROOT).as_posix() for path in expected_paths}
    if set(section) != expected_keys:
        fail(f"ore CTM manifest {label} set has drifted; regenerate the tilesets")
    for relative, expected_hash in section.items():
        if not isinstance(relative, str) or not isinstance(expected_hash, str):
            fail(f"ore CTM manifest {label} hashes must be strings")
        path = ROOT / relative
        if digest(path) != expected_hash:
            fail(f"{relative} does not match the generated CTM manifest; regenerate the tilesets")


def validate_model_texture_references() -> int:
    checked = 0
    for model_path in MODELS_ROOT.rglob("*.json"):
        model = load_json(model_path)
        textures = model.get("textures")
        if textures is None:
            continue
        if not isinstance(textures, dict):
            fail(f"{model_path.relative_to(ROOT)} textures must be an object")
        for texture in textures.values():
            if not isinstance(texture, str):
                fail(f"{model_path.relative_to(ROOT)} texture references must be strings")
            if texture.startswith("#") or not texture.startswith("geostrata:"):
                continue
            relative = texture.removeprefix("geostrata:")
            path = ASSETS / "textures" / f"{relative}.png"
            png_size(path)
            checked += 1
    return checked


def main() -> None:
    matrix = load_json(MATRIX_PATH)
    manifest = load_json(MANIFEST_PATH)
    if matrix.get("schemaVersion") != 1 or matrix.get("resolution") != 16:
        fail("ore texture matrix must remain schema 1 at 16x16")
    if tuple(matrix.get("grades", {})) != GRADES:
        fail(f"ore texture matrix grade order must remain {GRADES}")
    if manifest.get("schemaVersion") != 2 or manifest.get("method") != "ctm_compact":
        fail("ore CTM manifest must use schema 2 and ctm_compact")
    if manifest.get("source") != "graded-16x16-ore-overlays":
        fail("ore CTM must derive from the normal full-size graded ore artwork")
    if manifest.get("runtimeTilesPerCombination") != COMPACT_TILE_COUNT:
        fail("ctm_compact must contain exactly five runtime tiles per combination")

    hosts = matrix.get("hosts")
    if not isinstance(hosts, list) or not hosts or not all(isinstance(host, str) for host in hosts):
        fail("ore texture matrix must contain host ids")
    if len(set(hosts)) != len(hosts):
        fail("ore texture matrix host ids must be unique")

    ores = matrix.get("ores")
    if not isinstance(ores, dict) or not ores:
        fail("ore texture matrix must contain ores")

    host_textures = set(HOST_ROOT.glob("*.png"))
    for texture in host_textures:
        require_png(texture, (16, 16))
    missing_hosts = {HOST_ROOT / f"{host}.png" for host in hosts} - host_textures
    if missing_hosts:
        fail("ore texture matrix references missing host textures")

    expected_masters: set[Path] = set()
    expected_overlays: set[Path] = set()
    expected_composites: set[Path] = set()
    expected_inputs: set[Path] = {HOST_ROOT / f"{host}.png" for host in hosts}
    expected_host_properties = {HOST_PROPERTIES_ROOT / f"{host}.properties" for host in hosts}
    expected_host_textures = {
        HOST_TEXTURE_ROOT / host / f"{index}.png"
        for host in hosts
        for index in range(HOST_VARIANT_COUNT)
    }

    for host in hosts:
        property_path = HOST_PROPERTIES_ROOT / f"{host}.properties"
        try:
            actual = property_path.read_text(encoding="utf-8")
        except OSError as exc:
            fail(f"cannot read {property_path.relative_to(ROOT)}: {exc}")
        if actual != host_properties_text(host):
            fail(f"{property_path.relative_to(ROOT)} has drifted from the host Continuity contract")
        variants = []
        for index in range(HOST_VARIANT_COUNT):
            texture = HOST_TEXTURE_ROOT / host / f"{index}.png"
            require_png(texture, (16, 16))
            variants.append(digest(texture))
        if len(set(variants)) != HOST_VARIANT_COUNT:
            fail(f"{host} Continuity variants must remain distinct")

    if set(HOST_PROPERTIES_ROOT.glob("*.properties")) != expected_host_properties:
        fail("host Continuity property coverage does not exactly match the texture matrix")
    if set(HOST_TEXTURE_ROOT.rglob("*.png")) != expected_host_textures:
        fail("host Continuity sprite coverage does not exactly match the texture matrix")

    expected_properties: set[Path] = set()
    expected_textures: set[Path] = set()
    for material, ore in ores.items():
        if not isinstance(material, str) or not isinstance(ore, dict):
            fail("ore texture matrix contains an invalid ore entry")
        master = MASTER_ROOT / f"{material}.png"
        expected_masters.add(master)
        expected_inputs.add(master)
        for grade in GRADES:
            overlay = OVERLAY_ROOT / material / f"{grade}.png"
            expected_overlays.add(overlay)
            expected_inputs.add(overlay)
            for host in hosts:
                expected_composites.add(COMPOSITE_ROOT / material / host / f"{grade}.png")

        valid_hosts = ore.get("validHosts")
        if not isinstance(valid_hosts, list) or not all(isinstance(host, str) for host in valid_hosts):
            fail(f"{material}.validHosts must be a list of host ids")
        if not set(valid_hosts) <= set(hosts):
            fail(f"{material}.validHosts contains an unknown host")
        for grade in GRADES:
            source = OVERLAY_ROOT / material / f"{grade}.png"
            require_png(source, (16, 16))
            for host in valid_hosts:
                property_path = PROPERTIES_ROOT / material / grade / f"{host}.properties"
                expected_properties.add(property_path)
                try:
                    actual = property_path.read_text(encoding="utf-8")
                except OSError as exc:
                    fail(f"cannot read {property_path.relative_to(ROOT)}: {exc}")
                if actual != properties_text(material, grade, host):
                    fail(f"{property_path.relative_to(ROOT)} has drifted from the compact CTM contract")

                for index in range(COMPACT_TILE_COUNT):
                    texture = TEXTURE_ROOT / material / host / grade / f"{index}.png"
                    expected_textures.add(texture)
                    require_png(texture, (16, 16))

    actual_masters = set(MASTER_ROOT.glob("*.png"))
    actual_overlays = {
        path
        for path in OVERLAY_ROOT.glob("*/*.png")
        if path.parent.name not in {"master", "tileset"}
    }
    actual_composites = set(COMPOSITE_ROOT.rglob("*.png"))
    if actual_masters != expected_masters:
        fail("master ore texture coverage does not exactly match the texture matrix")
    if actual_overlays != expected_overlays:
        fail("generated ore overlay coverage does not exactly match the texture matrix")
    if actual_composites != expected_composites:
        fail("host-aware ore composite coverage does not exactly match the texture matrix")
    for texture in expected_masters | expected_overlays | expected_composites:
        require_png(texture, (16, 16))

    actual_properties = set(PROPERTIES_ROOT.rglob("*.properties"))
    actual_textures = set(TEXTURE_ROOT.rglob("*.png"))
    if actual_properties != expected_properties:
        fail("ore CTM property coverage does not exactly match valid geological host combinations")
    if actual_textures != expected_textures:
        fail("ore CTM sprite coverage does not exactly match valid geological host combinations")

    expected_combinations = len(expected_properties)
    if manifest.get("generatedCombinations") != expected_combinations:
        fail(f"manifest must declare {expected_combinations} generated combinations")

    validate_hashes(manifest.get("inputs"), expected_inputs, "inputs")
    validate_hashes(manifest.get("files"), expected_properties | expected_textures | {PREVIEW_PATH}, "files")

    model_texture_references = validate_model_texture_references()
    print(
        f"validated {model_texture_references} model texture references, "
        f"{len(host_textures)} host textures, {len(expected_composites)} host-aware ore composites, "
        f"{len(expected_host_textures)} host Continuity sprites, "
        f"{expected_combinations} compact CTM combinations and {len(expected_textures)} ore Continuity sprites"
    )


if __name__ == "__main__":
    main()
