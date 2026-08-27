#!/usr/bin/env python3
"""Validate committed GeoStrata ore CTM assets without requiring Pillow."""

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
SOURCE_ROOT = ASSETS / "textures" / "block" / "ore_source" / "tileset"
PROPERTIES_ROOT = ASSETS / "optifine" / "ctm" / "ore"
TEXTURE_ROOT = ASSETS / "textures" / "optifine" / "ctm" / "ore"
PREVIEW_PATH = ROOT / "docs" / "images" / "ore-ctm-tileset-preview.png"
GRADES = ("poor", "medium", "rich", "massive")
COMPACT_TILE_COUNT = 5


def fail(message: str) -> None:
    print(f"ore CTM validation failed: {message}", file=sys.stderr)
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


def properties_text(material: str, grade: str, host: str) -> str:
    tiles = " ".join(
        f"geostrata:optifine/ctm/ore/{material}/{host}/{grade}/{index}"
        for index in range(COMPACT_TILE_COUNT)
    )
    return (
        "method=ctm_compact\n"
        f"matchBlocks=geostrata:{grade}_{material}_ore:host={host}\n"
        "connect=block\n"
        f"tiles={tiles}\n"
        "innerSeams=false\n"
    )


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    matrix = load_json(MATRIX_PATH)
    manifest = load_json(MANIFEST_PATH)
    if matrix.get("schemaVersion") != 1 or matrix.get("resolution") != 16:
        fail("ore texture matrix must remain schema 1 at 16x16")
    if manifest.get("schemaVersion") != 1 or manifest.get("method") != "ctm_compact":
        fail("ore CTM manifest must use schema 1 and ctm_compact")
    if manifest.get("sourceSubtiles") != 13 or manifest.get("subtileResolution") != 8:
        fail("ore CTM authoring contract must remain thirteen 8x8 subtiles")
    if manifest.get("runtimeTilesPerCombination") != COMPACT_TILE_COUNT:
        fail("ctm_compact must contain exactly five runtime tiles per combination")

    ores = matrix.get("ores")
    if not isinstance(ores, dict):
        fail("ore texture matrix must contain ores")

    expected_properties: set[Path] = set()
    expected_textures: set[Path] = set()
    expected_sources: set[Path] = set()
    for material, ore in ores.items():
        if not isinstance(material, str) or not isinstance(ore, dict):
            fail("ore texture matrix contains an invalid ore entry")
        source = SOURCE_ROOT / f"{material}.png"
        expected_sources.add(source)
        if png_size(source) != (40, 24):
            fail(f"{source.relative_to(ROOT)} must be exactly 40x24")

        valid_hosts = ore.get("validHosts")
        if not isinstance(valid_hosts, list) or not all(isinstance(host, str) for host in valid_hosts):
            fail(f"{material}.validHosts must be a list of host ids")
        for grade in GRADES:
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
                    if png_size(texture) != (16, 16):
                        fail(f"{texture.relative_to(ROOT)} must be exactly 16x16")

    actual_properties = set(PROPERTIES_ROOT.rglob("*.properties"))
    actual_textures = set(TEXTURE_ROOT.rglob("*.png"))
    actual_sources = set(SOURCE_ROOT.glob("*.png"))
    if actual_properties != expected_properties:
        fail("ore CTM property coverage does not exactly match valid geological host combinations")
    if actual_textures != expected_textures:
        fail("ore CTM sprite coverage does not exactly match valid geological host combinations")
    if actual_sources != expected_sources:
        fail("ore CTM source sheets must exactly cover the artist ore matrix")

    expected_combinations = len(expected_properties)
    if manifest.get("generatedCombinations") != expected_combinations:
        fail(f"manifest must declare {expected_combinations} generated combinations")

    files = manifest.get("files")
    if not isinstance(files, dict):
        fail("ore CTM manifest files must be an object")
    tracked = expected_sources | expected_properties | expected_textures | {PREVIEW_PATH}
    expected_keys = {path.relative_to(ROOT).as_posix() for path in tracked}
    if set(files) != expected_keys:
        fail("ore CTM manifest file set has drifted; regenerate the tilesets")
    for relative, expected_hash in files.items():
        if not isinstance(relative, str) or not isinstance(expected_hash, str):
            fail("ore CTM manifest hashes must be strings")
        path = ROOT / relative
        if digest(path) != expected_hash:
            fail(f"{relative} does not match the generated CTM manifest; regenerate the tilesets")

    print(
        f"validated {len(expected_sources)} ore source sheets, {expected_combinations} compact CTM combinations "
        f"and {len(expected_textures)} sprites"
    )


if __name__ == "__main__":
    main()
