#!/usr/bin/env python3
"""Generate Continuity overlay sprites for dithered GeoStrata rock transitions."""

from __future__ import annotations

from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:
    raise SystemExit("Pillow is required: python -m pip install Pillow") from exc

from generate_ore_texture_matrix import ASSETS, load_rgba

HOST_ROOT = ASSETS / "textures" / "block" / "host"
PROPERTIES_ROOT = ASSETS / "optifine" / "ctm" / "_host_transitions"
TEXTURE_ROOT = ASSETS / "textures" / "optifine" / "ctm" / "host_transition"
TILE_COUNT = 17

# Continuity's standard overlay tile order, using screen-space
# up/right/down/left orientation supplied by the renderer.
TILE_COMPONENTS: dict[int, tuple[str, ...]] = {
    0: ("corner_se",),
    1: ("south",),
    2: ("corner_sw",),
    3: ("south", "east"),
    4: ("west", "south"),
    5: ("west", "south", "east"),
    6: ("west", "south", "north"),
    7: ("east",),
    8: ("west", "south", "east", "north"),
    9: ("west",),
    10: ("east", "north"),
    11: ("west", "north"),
    12: ("south", "east", "north"),
    13: ("west", "east", "north"),
    14: ("corner_ne",),
    15: ("north",),
    16: ("corner_nw",),
}


def host_ids() -> tuple[str, ...]:
    return tuple(sorted(path.stem for path in HOST_ROOT.glob("*.png")))


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


def copy_pixel(source: Image.Image, output: Image.Image, x: int, y: int) -> None:
    red, green, blue, alpha = source.getpixel((x, y))
    output.putpixel((x, y), (red, green, blue, alpha))


def add_side(source: Image.Image, output: Image.Image, side: str) -> None:
    for offset in range(16):
        if side == "north":
            points = ((offset, 0, 2), (offset, 1, 4))
        elif side == "south":
            points = ((offset, 15, 2), (offset, 14, 4))
        elif side == "west":
            points = ((0, offset, 2), (1, offset, 4))
        elif side == "east":
            points = ((15, offset, 2), (14, offset, 4))
        else:
            raise ValueError(side)
        for x, y, modulus in points:
            if (x + y) % modulus == 0:
                copy_pixel(source, output, x, y)


def add_corner(source: Image.Image, output: Image.Image, corner: str) -> None:
    for dy in range(4):
        for dx in range(4):
            if dx + dy > 4:
                continue
            if (dx + dy) % 2:
                continue
            x = dx if "w" in corner else 15 - dx
            y = dy if "n" in corner else 15 - dy
            copy_pixel(source, output, x, y)


def overlay_tile(source: Image.Image, components: tuple[str, ...]) -> Image.Image:
    output = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for component in components:
        if component.startswith("corner_"):
            add_corner(source, output, component.removeprefix("corner_"))
        else:
            add_side(source, output, component)
    return output


def generate() -> tuple[int, int]:
    hosts = host_ids()
    if not hosts:
        raise SystemExit("no GeoStrata host textures found")

    expected_properties: set[Path] = set()
    expected_textures: set[Path] = set()
    for host in hosts:
        source = load_rgba(HOST_ROOT / f"{host}.png", 16)
        property_path = PROPERTIES_ROOT / f"{host}.properties"
        property_path.parent.mkdir(parents=True, exist_ok=True)
        property_path.write_text(property_text(host, hosts), encoding="utf-8")
        expected_properties.add(property_path)

        texture_dir = TEXTURE_ROOT / host
        texture_dir.mkdir(parents=True, exist_ok=True)
        for index in range(TILE_COUNT):
            texture_path = texture_dir / f"{index}.png"
            overlay_tile(source, TILE_COMPONENTS[index]).save(texture_path, optimize=True)
            expected_textures.add(texture_path)

    for stale in set(PROPERTIES_ROOT.glob("*.properties")) - expected_properties:
        stale.unlink()
    for stale in set(TEXTURE_ROOT.rglob("*.png")) - expected_textures:
        stale.unlink()

    return len(expected_properties), len(expected_textures)


if __name__ == "__main__":
    properties, sprites = generate()
    print(f"generated {properties} host transition definitions and {sprites} dither sprites")
