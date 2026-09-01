#!/usr/bin/env python3
"""Validate GeoStrata's committed Continuity texture assets without Pillow."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import struct
import sys

from validate_host_continuity_transitions import main as validate_host_transitions

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
REPEAT_WIDTH = 4
REPEAT_HEIGHT = 4
REPEAT_TILE_COUNT = REPEAT_WIDTH * REPEAT_HEIGHT


def fail(message: str) -> None:
    print(f"texture validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> dict[str, object]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
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


def require_png(path: Path, size: tuple[int, int]) -> None:
    if png_size(path) != size:
        fail(f"{path.relative_to(ROOT)} must be exactly {size[0]}x{size[1]}")


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def host_properties_text(host: str) -> str:
    tiles = " ".join(
        f"geostrata:textures/optifine/ctm/host/{host}/{index}"
        for index in range(HOST_VARIANT_COUNT)
    )
    return (
        "method=random\n"
        f"matchTiles=geostrata:block/host/{host}\n"
        "prioritize=false\n"
        f"tiles={tiles}\n"
    )


def ore_properties_text(material: str, grade: str, host: str) -> str:
    tiles = " ".join(
        f"geostrata:textures/optifine/ctm/ore/{material}/{host}/{grade}/{index}"
        for index in range(REPEAT_TILE_COUNT)
    )
    return (
        "method=repeat\n"
        f"matchBlocks=geostrata:{grade}_{material}_ore:host={host}\n"
        f"width={REPEAT_WIDTH}\n"
        f"height={REPEAT_HEIGHT}\n"
        f"tiles={tiles}\n"
    )


def validate_hashes(section: object, expected_paths: set[Path], label: str) -> None:
    if not isinstance(section, dict):
        fail(f"ore texture manifest {label} must be an object")
    expected_keys = {path.relative_to(ROOT).as_posix() for path in expected_paths}
    if set(section) != expected_keys:
        fail(f"ore texture manifest {label} set has drifted; regenerate the spatial tiles")
    for relative, expected_hash in section.items():
        if not isinstance(relative, str) or not isinstance(expected_hash, str):
            fail(f"ore texture manifest {label} hashes must be strings")
        if digest(ROOT / relative) != expected_hash:
            fail(f"{relative} does not match the generated manifest; regenerate the spatial tiles")


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
            png_size(ASSETS / "textures" / f"{relative}.png")
            checked += 1
    return checked


def main() -> None:
    matrix = load_json(MATRIX_PATH)
    manifest = load_json(MANIFEST_PATH)
    if matrix.get("schemaVersion") != 1 or matrix.get("resolution") != 16:
        fail("ore texture matrix must remain schema 1 at 16x16")
    if tuple(matrix.get("grades", {})) != GRADES:
        fail(f"ore texture matrix grade order must remain {GRADES}")
    if (
        manifest.get("schemaVersion") != 3
        or manifest.get("method") != "repeat"
        or manifest.get("source") != "spatial-64x64-mineral-field"
        or manifest.get("repeatWidth") != REPEAT_WIDTH
        or manifest.get("repeatHeight") != REPEAT_HEIGHT
        or manifest.get("runtimeTilesPerCombination") != REPEAT_TILE_COUNT
    ):
        fail("ore texture manifest must describe the 4x4 spatial repeat contract")

    hosts = matrix.get("hosts")
    ores = matrix.get("ores")
    if not isinstance(hosts, list) or not hosts or not all(isinstance(host, str) for host in hosts):
        fail("ore texture matrix must contain host ids")
    if len(set(hosts)) != len(hosts):
        fail("ore texture matrix host ids must be unique")
    if not isinstance(ores, dict) or not ores:
        fail("ore texture matrix must contain ores")

    host_textures = set(HOST_ROOT.glob("*.png"))
    for host in hosts:
        require_png(HOST_ROOT / f"{host}.png", (16, 16))
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

    expected_properties: set[Path] = set()
    expected_textures: set[Path] = set()
    expected_inputs: set[Path] = {MATRIX_PATH} | {
        HOST_ROOT / f"{host}.png" for host in hosts
    }
    expected_composites: set[Path] = set()
    expected_masters: set[Path] = set()
    expected_overlays: set[Path] = set()

    for material, ore in ores.items():
        if not isinstance(material, str) or not isinstance(ore, dict):
            fail("ore texture matrix contains an invalid ore entry")
        master = MASTER_ROOT / f"{material}.png"
        expected_masters.add(master)
        expected_inputs.add(master)
        require_png(master, (16, 16))
        valid_hosts = ore.get("validHosts")
        if not isinstance(valid_hosts, list) or not all(isinstance(host, str) for host in valid_hosts):
            fail(f"{material}.validHosts must be a list of host ids")
        if not set(valid_hosts) <= set(hosts):
            fail(f"{material}.validHosts contains an unknown host")

        for grade in GRADES:
            source = OVERLAY_ROOT / material / f"{grade}.png"
            expected_overlays.add(source)
            expected_inputs.add(source)
            require_png(source, (16, 16))
            for host in hosts:
                expected_composites.add(COMPOSITE_ROOT / material / host / f"{grade}.png")
            for host in valid_hosts:
                property_path = PROPERTIES_ROOT / material / grade / f"{host}.properties"
                expected_properties.add(property_path)
                try:
                    actual = property_path.read_text(encoding="utf-8")
                except OSError as exc:
                    fail(f"cannot read {property_path.relative_to(ROOT)}: {exc}")
                if actual != ore_properties_text(material, grade, host):
                    fail(f"{property_path.relative_to(ROOT)} has drifted from the spatial repeat contract")
                tile_hashes = []
                for index in range(REPEAT_TILE_COUNT):
                    texture = TEXTURE_ROOT / material / host / grade / f"{index}.png"
                    expected_textures.add(texture)
                    require_png(texture, (16, 16))
                    tile_hashes.append(digest(texture))
                if len(set(tile_hashes)) < REPEAT_TILE_COUNT // 2:
                    fail(
                        f"{material}/{grade}/{host} repeat field has collapsed into repeated block-local sprites"
                    )

    if set(PROPERTIES_ROOT.rglob("*.properties")) != expected_properties:
        fail("ore Continuity property coverage does not exactly match valid geological host combinations")
    if set(TEXTURE_ROOT.rglob("*.png")) != expected_textures:
        fail("ore Continuity sprite coverage does not exactly match the 4x4 repeat contract")
    if set(MASTER_ROOT.glob("*.png")) != expected_masters:
        fail("master ore texture coverage does not exactly match the texture matrix")
    actual_overlays = {
        path
        for path in OVERLAY_ROOT.glob("*/*.png")
        if path.parent.name not in {"master", "tileset"}
    }
    if actual_overlays != expected_overlays:
        fail("generated ore overlay coverage does not exactly match the texture matrix")
    if set(COMPOSITE_ROOT.rglob("*.png")) != expected_composites:
        fail("host-aware fallback ore composite coverage does not exactly match the texture matrix")
    for texture in expected_composites:
        require_png(texture, (16, 16))

    if manifest.get("generatedCombinations") != len(expected_properties):
        fail(f"manifest must declare {len(expected_properties)} generated combinations")
    validate_hashes(manifest.get("inputs"), expected_inputs, "inputs")
    validate_hashes(
        manifest.get("files"),
        expected_properties | expected_textures | {PREVIEW_PATH},
        "files",
    )

    model_refs = validate_model_texture_references()
    print(
        f"validated {model_refs} model texture references, {len(host_textures)} host textures, "
        f"{len(expected_properties)} 4x4 ore repeat combinations and {len(expected_textures)} ore sprites"
    )
    validate_host_transitions()


if __name__ == "__main__":
    main()
