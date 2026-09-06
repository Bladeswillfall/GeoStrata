#!/usr/bin/env python3
"""Validate GeoStrata's dithered Continuity host-transition assets without Pillow."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "geostrata"
HOST_ROOT = ASSETS / "textures" / "block" / "host"
PROPERTIES_ROOT = ASSETS / "optifine" / "ctm" / "_host_transitions"
TEXTURE_ROOT = ASSETS / "textures" / "optifine" / "ctm" / "host_transition"
LEGACY_TILESET_ROOT = ASSETS / "textures" / "block" / "ore_source" / "tileset"
MATRIX_PATH = RESOURCES / "data" / "geostrata" / "materials" / "ore_texture_matrix.json"
TILE_COUNT = 17
GRADES = ("poor", "medium", "rich", "massive")
VANILLA_HOST_BLOCKS = {"granite": "minecraft:granite"}
TRANSITION_EXCLUDED_HOSTS = {"sandstone"}


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


def load_matrix() -> dict[str, object]:
    try:
        value = json.loads(MATRIX_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"cannot read ore texture matrix: {exc}")
    if not isinstance(value, dict):
        fail("ore texture matrix must contain an object")
    return value


def ore_states_by_host(hosts: tuple[str, ...]) -> dict[str, tuple[str, ...]]:
    matrix = load_matrix()
    ores = matrix.get("ores")
    if not isinstance(ores, dict):
        fail("ore texture matrix must contain ores")
    states: dict[str, list[str]] = {host: [] for host in hosts}
    for material, ore in ores.items():
        if not isinstance(material, str) or not isinstance(ore, dict):
            fail("ore texture matrix contains an invalid ore entry")
        valid_hosts = ore.get("validHosts")
        if not isinstance(valid_hosts, list) or not all(isinstance(host, str) for host in valid_hosts):
            fail(f"{material}.validHosts must be a list of host ids")
        for host in valid_hosts:
            if host not in states:
                continue
            states[host].extend(
                f"geostrata:{grade}_{material}_ore:host={host}"
                for grade in GRADES
            )
    return {host: tuple(values) for host, values in states.items()}


def host_block(host: str) -> str:
    return VANILLA_HOST_BLOCKS.get(host, f"geostrata:{host}")


def geological_states(hosts: tuple[str, ...], ore_states: dict[str, tuple[str, ...]]) -> tuple[str, ...]:
    return tuple(
        state
        for host in hosts
        for state in (host_block(host), *ore_states[host])
    )


def property_text(
    host: str,
    hosts: tuple[str, ...],
    ore_states: dict[str, tuple[str, ...]],
) -> str:
    own_states = (host_block(host), *ore_states[host])
    own_set = set(own_states)
    match_blocks = " ".join(
        state
        for state in geological_states(hosts, ore_states)
        if state not in own_set
    )
    connect_blocks = " ".join(own_states)
    tiles = " ".join(
        f"geostrata:textures/optifine/ctm/host_transition/{host}/{index}"
        for index in range(TILE_COUNT)
    )
    return (
        "method=overlay\n"
        f"matchBlocks={match_blocks}\n"
        f"connectBlocks={connect_blocks}\n"
        "connect=block\n"
        f"tiles={tiles}\n"
        "layer=cutout\n"
    )


def main() -> None:
    hosts = tuple(sorted(
        path.stem
        for path in HOST_ROOT.glob("*.png")
        if path.stem not in TRANSITION_EXCLUDED_HOSTS
    ))
    if not hosts:
        fail("no GeoStrata transition host textures found")
    ore_states = ore_states_by_host(hosts)

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
        fail("host transition property coverage does not exactly match transition-enabled host textures")
    if set(TEXTURE_ROOT.rglob("*.png")) != expected_textures:
        fail("host transition sprite coverage does not exactly match transition-enabled host textures")

    for host in hosts:
        property_path = PROPERTIES_ROOT / f"{host}.properties"
        try:
            actual = property_path.read_text(encoding="utf-8")
        except OSError as exc:
            fail(f"cannot read {property_path.relative_to(ROOT)}: {exc}")
        if actual != property_text(host, hosts, ore_states):
            fail(f"{property_path.relative_to(ROOT)} has drifted from the host transition contract")

        own_states = {host_block(host), *ore_states[host]}
        if ore_states[host] and not all(f":host={host}" in state for state in ore_states[host]):
            fail(f"{host} ore transition states must preserve the host property")
        if any(state in property_text(host, hosts, ore_states).split("\n")[1] for state in own_states):
            fail(f"{host} transition must not overlay between blocks with the same geological host")

        hashes = []
        for index in range(TILE_COUNT):
            texture = TEXTURE_ROOT / host / f"{index}.png"
            if png_size(texture) != (16, 16):
                fail(f"{texture.relative_to(ROOT)} must be exactly 16x16")
            hashes.append(digest(texture))
        if len(set(hashes)) != TILE_COUNT:
            fail(f"{host} transition sprites must remain distinct")

    print(
        f"validated {len(hosts)} host transition definitions, "
        f"{sum(len(states) for states in ore_states.values())} host-aware ore states and "
        f"{len(expected_textures)} dither sprites"
    )


if __name__ == "__main__":
    main()
