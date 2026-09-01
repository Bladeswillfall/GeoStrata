#!/usr/bin/env python3
"""Validate GeoStrata's dithered Continuity host-transition assets without Pillow."""

from __future__ import annotations

import hashlib
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "geostrata"
HOST_ROOT = ASSETS / "textures" / "block" / "host"
PROPERTIES_ROOT = ASSETS / "optifine" / "ctm" / "_host_transitions"
TEXTURE_ROOT = ASSETS / "textures" / "optifine" / "ctm" / "host_transition"
LEGACY_TILESET_ROOT = ASSETS / "textures" / "block" / "ore_source" / "tileset"
TILE_COUNT = 17


def fail(message: str) -> None:
    print(f"host Continuity validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def png_size(path: Path) -> tuple[int, int]:
    try:
        header = path.read_bytes()[:24]
    except OSError as exc:
        fail(f"cannot read {path.relative_to(ROOT)}: {exc}")
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        fail(f"{path.relative_to(ROOT)} must be a PNG image")
    return struct.unpack(">II", header[16:24])


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def property_text(host: str, hosts: tuple[str, ...]) -> str:
    match_blocks = " ".join(f"geostrata:{name}" for name in hosts)
    tiles = " ".join(
        f"geostrata:textures/optifine/ctm/host_transition/{host}/{index}"
        for index in range(TILE_COUNT)
    )
    return (
        "method=overlay\n"
        f"matchBlocks={match_blocks}\n"
        f"connectBlocks=geostrata:{host}\n"
        "connect=block\n"
        f"tiles={tiles}\n"
        "layer=cutout\n"
    )


def main() -> None:
    hosts = tuple(sorted(path.stem for path in HOST_ROOT.glob("*.png")))
    if not hosts:
        fail("no GeoStrata host textures found")

    legacy = list(LEGACY_TILESET_ROOT.glob("*.png"))
    if legacy:
        fail("obsolete 40x24 ore tileset sources must not ship inside the runtime texture atlas")

    expected_properties = {PROPERTIES_ROOT / f"{host}.properties" for host in hosts}
    expected_textures = {
        TEXTURE_ROOT / host / f"{index}.png"
        for host in hosts
        for index in range(TILE_COUNT)
    }

    if set(PROPERTIES_ROOT.glob("*.properties")) != expected_properties:
        fail("host transition property coverage does not exactly match GeoStrata host textures")
    if set(TEXTURE_ROOT.rglob("*.png")) != expected_textures:
        fail("host transition sprite coverage does not exactly match GeoStrata host textures")

    for host in hosts:
        property_path = PROPERTIES_ROOT / f"{host}.properties"
        try:
            actual = property_path.read_text(encoding="utf-8")
        except OSError as exc:
            fail(f"cannot read {property_path.relative_to(ROOT)}: {exc}")
        if actual != property_text(host, hosts):
            fail(f"{property_path.relative_to(ROOT)} has drifted from the host transition contract")

        hashes = []
        for index in range(TILE_COUNT):
            texture = TEXTURE_ROOT / host / f"{index}.png"
            if png_size(texture) != (16, 16):
                fail(f"{texture.relative_to(ROOT)} must be exactly 16x16")
            hashes.append(digest(texture))
        if len(set(hashes)) != TILE_COUNT:
            fail(f"{host} transition sprites must remain distinct")

    print(f"validated {len(hosts)} host transition definitions and {len(expected_textures)} dither sprites")


if __name__ == "__main__":
    main()
