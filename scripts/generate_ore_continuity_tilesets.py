#!/usr/bin/env python3
"""Generate compact Continuity tiles from the normal 16x16 graded ore overlays.

The old generator built each compact sprite from repeated 8x8 mini-tiles. That
made the ore itself repeat on an 8-pixel lattice, which became a visible grid
across deposits. Compact CTM only needs five 16x16 sprites, so use the existing
full-size ore artwork as the shared field and derive only the edge/corner
termination masks required by the five CTM states.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:
    raise SystemExit("Pillow is required: python -m pip install Pillow") from exc

from generate_ore_texture_matrix import ASSETS, GRADES, ROOT, load_matrix, load_rgba

COMPACT_TILE_COUNT = 5
EDGE_DEPTH = 2
CORNER_DEPTH = 4
MANIFEST_PATH = ROOT / "src" / "main" / "resources" / "data" / "geostrata" / "materials" / "ore_ctm_manifest.json"
PROPERTIES_ROOT = ASSETS / "optifine" / "ctm" / "ore"
TEXTURE_ROOT = ASSETS / "textures" / "optifine" / "ctm" / "ore"
ORE_SOURCE_ROOT = ASSETS / "textures" / "block" / "ore_source"
HOST_ROOT = ASSETS / "textures" / "block" / "host"
PREVIEW_PATH = ROOT / "docs" / "images" / "ore-ctm-tileset-preview.png"


def stable_value(*parts: object) -> int:
    digest = hashlib.blake2s("|".join(map(str, parts)).encode("utf-8"), digest_size=8).digest()
    return int.from_bytes(digest, "big")


def master_palette(master: Image.Image) -> list[tuple[int, int, int]]:
    palette = [pixel[:3] for pixel in master.getdata() if pixel[3] >= 32]
    if not palette:
        raise SystemExit("dense ore master must contain opaque mineral pixels")
    return palette


def mineral_color(master: Image.Image, palette: list[tuple[int, int, int]], material: str, x: int, y: int) -> tuple[int, int, int]:
    pixel = master.getpixel((x, y))
    if pixel[3] >= 32:
        return pixel[:3]
    return palette[stable_value(material, "ctm-port", x, y) % len(palette)]


def ensure_wrap_ports(
    master: Image.Image,
    overlay: Image.Image,
    material: str,
    grade: str,
    target_pixels: int,
) -> Image.Image:
    """Keep the normal ore artwork and only add a tiny wrap port when an axis has none."""
    result = overlay.copy()
    alpha = result.getchannel("A")
    horizontal = any(alpha.getpixel((0, y)) >= 32 and alpha.getpixel((15, y)) >= 32 for y in range(16))
    vertical = any(alpha.getpixel((x, 0)) >= 32 and alpha.getpixel((x, 15)) >= 32 for x in range(16))
    mandatory: set[tuple[int, int]] = set()
    palette = master_palette(master)

    if not horizontal:
        y = min(range(3, 13), key=lambda value: stable_value(material, grade, "horizontal-port", value))
        for x in (0, 15):
            red, green, blue = mineral_color(master, palette, material, x, y)
            result.putpixel((x, y), (red, green, blue, 255))
            mandatory.add((x, y))

    if not vertical:
        x = min(range(3, 13), key=lambda value: stable_value(material, grade, "vertical-port", value))
        for y in (0, 15):
            red, green, blue = mineral_color(master, palette, material, x, y)
            result.putpixel((x, y), (red, green, blue, 255))
            mandatory.add((x, y))

    selected = {
        (x, y)
        for y in range(16)
        for x in range(16)
        if result.getpixel((x, y))[3] >= 32
    }
    overflow = max(0, len(selected) - target_pixels)
    removable = [
        point
        for point in selected
        if point not in mandatory
        and point[0] not in (0, 15)
        and point[1] not in (0, 15)
    ]
    removable.sort(key=lambda point: stable_value(material, grade, "port-trim", *point))
    for point in removable[:overflow]:
        result.putpixel(point, (0, 0, 0, 0))

    return result


def clear_sides(overlay: Image.Image, sides: str) -> Image.Image:
    result = overlay.copy()
    for y in range(16):
        for x in range(16):
            if (
                ("w" in sides and x < EDGE_DEPTH)
                or ("e" in sides and x >= 16 - EDGE_DEPTH)
                or ("n" in sides and y < EDGE_DEPTH)
                or ("s" in sides and y >= 16 - EDGE_DEPTH)
            ):
                result.putpixel((x, y), (0, 0, 0, 0))
    return result


def clear_inner_corners(overlay: Image.Image) -> Image.Image:
    result = overlay.copy()
    for y in range(CORNER_DEPTH):
        for x in range(CORNER_DEPTH):
            if x + y > CORNER_DEPTH - 1:
                continue
            for point in (
                (x, y),
                (15 - x, y),
                (x, 15 - y),
                (15 - x, 15 - y),
            ):
                result.putpixel(point, (0, 0, 0, 0))
    return result


def compact_overlays(connected: Image.Image) -> list[Image.Image]:
    # Continuity compact indices:
    # 0 unconnected, 1 fully connected, 2 vertical, 3 horizontal, 4 missing diagonal.
    return [
        clear_sides(connected, "nesw"),
        connected.copy(),
        clear_sides(connected, "we"),
        clear_sides(connected, "ns"),
        clear_inner_corners(connected),
    ]


def composite_ctm(host: Image.Image, overlay: Image.Image) -> Image.Image:
    result = host.copy()
    ore_pixels = {
        (x, y)
        for y in range(16)
        for x in range(16)
        if overlay.getpixel((x, y))[3] > 0
    }
    rim = {
        (x + dx, y + dy)
        for x, y in ore_pixels
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))
        if 0 <= x + dx < 16 and 0 <= y + dy < 16
    } - ore_pixels
    for point in rim:
        red, green, blue, alpha = result.getpixel(point)
        result.putpixel(point, (int(red * 0.88), int(green * 0.88), int(blue * 0.88), alpha))
    result.alpha_composite(overlay)
    return result


def properties_text(material: str, grade: str, host: str) -> str:
    # Prefixing the namespaced path with textures/ makes Continuity resolve the
    # normal texture resource directly instead of creating continuity_reserved redirects.
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


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_preview(matrix: dict[str, object]) -> None:
    scale = 2
    sprite = 16 * scale
    label_width = 92
    group_gap = 10
    material_width = COMPACT_TILE_COUNT * sprite + group_gap
    width = label_width + len(matrix["ores"]) * material_width + 8
    height = 34 + len(GRADES) * (sprite + 8) + 24
    preview = Image.new("RGB", (width, height), "#17191d")
    draw = ImageDraw.Draw(preview)
    font = ImageFont.load_default()

    for material_index, (material, ore) in enumerate(matrix["ores"].items()):
        left = label_width + material_index * material_width
        draw.text((left, 6), material.upper(), font=font, fill="#eeeeea")
        host = ore["defaultHost"]
        for grade_index, grade in enumerate(GRADES):
            top = 30 + grade_index * (sprite + 8)
            if material_index == 0:
                draw.text((6, top + 10), grade.upper(), font=font, fill="#c8c9cc")
            for tile_index in range(COMPACT_TILE_COUNT):
                path = TEXTURE_ROOT / material / host / grade / f"{tile_index}.png"
                texture = Image.open(path).convert("RGB").resize((sprite, sprite), Image.Resampling.NEAREST)
                preview.paste(texture, (left + tile_index * sprite, top))

    draw.text(
        (6, height - 17),
        "Compact order: isolated | connected | vertical | horizontal | missing diagonal",
        font=font,
        fill="#8f9298",
    )
    PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW_PATH, optimize=True)


def generate() -> tuple[int, int]:
    matrix = load_matrix()
    hosts = {host: load_rgba(HOST_ROOT / f"{host}.png", 16) for host in matrix["hosts"]}
    generated_properties: set[Path] = set()
    generated_textures: set[Path] = set()
    inputs: set[Path] = {HOST_ROOT / f"{host}.png" for host in matrix["hosts"]}

    for material, ore in matrix["ores"].items():
        master_path = ORE_SOURCE_ROOT / "master" / f"{material}.png"
        master = load_rgba(master_path, 16)
        inputs.add(master_path)
        for grade in GRADES:
            source_path = ORE_SOURCE_ROOT / material / f"{grade}.png"
            source = load_rgba(source_path, 16)
            inputs.add(source_path)
            target_pixels = int(matrix["grades"][grade]["targetPixels"])
            connected = ensure_wrap_ports(master, source, material, grade, target_pixels)
            overlays = compact_overlays(connected)

            for host in ore["validHosts"]:
                property_path = PROPERTIES_ROOT / material / grade / f"{host}.properties"
                property_path.parent.mkdir(parents=True, exist_ok=True)
                property_path.write_text(properties_text(material, grade, host), encoding="utf-8")
                generated_properties.add(property_path)

                texture_dir = TEXTURE_ROOT / material / host / grade
                texture_dir.mkdir(parents=True, exist_ok=True)
                for index, overlay in enumerate(overlays):
                    texture_path = texture_dir / f"{index}.png"
                    composite_ctm(hosts[host], overlay).save(texture_path, optimize=True)
                    generated_textures.add(texture_path)

    write_preview(matrix)

    actual_properties = set(PROPERTIES_ROOT.rglob("*.properties"))
    actual_textures = set(TEXTURE_ROOT.rglob("*.png"))
    for stale in actual_properties - generated_properties:
        stale.unlink()
    for stale in actual_textures - generated_textures:
        stale.unlink()

    tracked_files = generated_properties | generated_textures | {PREVIEW_PATH}
    manifest = {
        "schemaVersion": 2,
        "method": "ctm_compact",
        "source": "graded-16x16-ore-overlays",
        "runtimeTilesPerCombination": COMPACT_TILE_COUNT,
        "edgeTerminationDepth": EDGE_DEPTH,
        "generatedCombinations": len(generated_properties),
        "inputs": {
            path.relative_to(ROOT).as_posix(): sha256(path)
            for path in sorted(inputs)
        },
        "files": {
            path.relative_to(ROOT).as_posix(): sha256(path)
            for path in sorted(tracked_files)
        },
    }
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return len(generated_properties), len(generated_textures)


if __name__ == "__main__":
    combinations, sprites = generate()
    print(f"generated {combinations} compact CTM combinations and {sprites} sprites")
