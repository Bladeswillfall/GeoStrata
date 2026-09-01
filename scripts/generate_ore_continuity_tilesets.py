#!/usr/bin/env python3
"""Generate spatially continuous 4x4 ore texture fields for Continuity.

Continuity's compact CTM splits a block face into quadrants. That is useful for
edge topology, but it cannot make several adjacent ore blocks read as one
larger mineral body without repeating a block-local motif. GeoStrata instead
uses Continuity's native ``repeat`` method: a deterministic 64x64 mineral field
is cropped into sixteen 16x16 tiles and composited against each valid host.
Adjacent ore blocks therefore sample adjacent pieces of the same field.
"""

from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "geostrata"
MATRIX_PATH = RESOURCES / "data" / "geostrata" / "materials" / "ore_texture_matrix.json"
MANIFEST_PATH = RESOURCES / "data" / "geostrata" / "materials" / "ore_ctm_manifest.json"
HOST_ROOT = ASSETS / "textures" / "block" / "host"
ORE_SOURCE_ROOT = ASSETS / "textures" / "block" / "ore_source"
PROPERTIES_ROOT = ASSETS / "optifine" / "ctm" / "ore"
TEXTURE_ROOT = ASSETS / "textures" / "optifine" / "ctm" / "ore"
PREVIEW_PATH = ROOT / "docs" / "images" / "ore-ctm-tileset-preview.png"

TILE_SIZE = 16
REPEAT_WIDTH = 4
REPEAT_HEIGHT = 4
FIELD_WIDTH = TILE_SIZE * REPEAT_WIDTH
FIELD_HEIGHT = TILE_SIZE * REPEAT_HEIGHT
REPEAT_TILE_COUNT = REPEAT_WIDTH * REPEAT_HEIGHT
GRADES = ("poor", "medium", "rich", "massive")

# Integer-frequency ridges stay seamless across the 64x64 repeat boundary.
# Each tuple is fx, fy, warp_fx, warp_fy, warp_amount, priority. The first
# ridges dominate Poor ore; lower-priority branches appear as grade density rises.
RIDGE_PROFILES: dict[str, tuple[tuple[int, int, int, int, float, float], ...]] = {
    "coal": (
        (0, 1, 1, 0, 0.48, 1.00),
        (0, 2, 1, 0, 0.34, 0.78),
        (1, 0, 0, 1, 0.18, 0.48),
    ),
    "iron": (
        (1, -1, 1, 1, 0.42, 1.00),
        (2, 1, 1, -1, 0.34, 0.82),
        (1, 2, 2, -1, 0.28, 0.68),
    ),
    "copper": (
        (1, 1, 1, -1, 0.48, 1.00),
        (1, -2, 2, 1, 0.34, 0.82),
        (3, 1, 1, 2, 0.24, 0.64),
    ),
    "gold": (
        (1, 2, 2, -1, 0.50, 1.00),
        (2, -1, 1, 2, 0.36, 0.76),
        (3, 1, 1, -2, 0.22, 0.55),
    ),
    "emerald": (
        (2, 1, 1, -2, 0.44, 1.00),
        (1, -3, 2, 1, 0.28, 0.70),
    ),
}


def stable_value(*parts: object) -> int:
    digest = hashlib.blake2s("|".join(map(str, parts)).encode("utf-8"), digest_size=8).digest()
    return int.from_bytes(digest, "big")


def load_json(path: Path) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise SystemExit(f"{path.relative_to(ROOT)} must contain an object")
    return value


def load_rgba(path: Path, size: tuple[int, int]) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    if image.size != size:
        raise SystemExit(f"{path.relative_to(ROOT)} must be exactly {size[0]}x{size[1]}")
    return image


def mineral_palette(master: Image.Image) -> list[tuple[int, int, int, int]]:
    palette = [pixel for pixel in master.getdata() if pixel[3] >= 32]
    if not palette:
        raise SystemExit("ore master must contain visible mineral pixels")
    return palette


def ridge_profile(material: str) -> tuple[tuple[int, int, int, int, float, float], ...]:
    profile = RIDGE_PROFILES.get(material)
    if profile is not None:
        return profile
    return (
        (1, 1, 1, -1, 0.42, 1.00),
        (1, -2, 2, 1, 0.30, 0.72),
    )


def periodic_score(material: str, x: int, y: int) -> float:
    """Seamless mineral ridge network instead of thresholded round noise blobs."""
    best = -10.0
    for index, (fx, fy, warp_fx, warp_fy, warp_amount, priority) in enumerate(ridge_profile(material)):
        phase = (stable_value(material, "ridge-phase", index) % 1_000_000) / 1_000_000.0 * math.tau
        warp_phase = (stable_value(material, "warp-phase", index) % 1_000_000) / 1_000_000.0 * math.tau
        theta = math.tau * (fx * x / FIELD_WIDTH + fy * y / FIELD_HEIGHT) + phase
        theta += warp_amount * math.sin(
            math.tau * (warp_fx * x / FIELD_WIDTH + warp_fy * y / FIELD_HEIGHT) + warp_phase
        )
        ridge = 1.0 - abs(math.sin(theta / 2.0))

        along_phase = (stable_value(material, "along-phase", index) % 1_000_000) / 1_000_000.0 * math.tau
        along = 0.08 * math.cos(
            math.tau * ((fy or 1) * x / FIELD_WIDTH - (fx or 1) * y / FIELD_HEIGHT) + along_phase
        )
        best = max(best, priority + ridge + along)
    return best


def tile_of(x: int, y: int) -> tuple[int, int]:
    return x // TILE_SIZE, y // TILE_SIZE


def adjacent_in_tile(
    point: tuple[int, int],
    selected: set[tuple[int, int]],
    tile: tuple[int, int],
) -> bool:
    x, y = point
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            if dx == 0 and dy == 0:
                continue
            neighbour = (x + dx, y + dy)
            if neighbour in selected and tile_of(*neighbour) == tile:
                return True
    return False


def select_pixels(
    material: str,
    target_per_tile: int,
    required: set[tuple[int, int]] | None = None,
) -> set[tuple[int, int]]:
    target = target_per_tile * REPEAT_TILE_COUNT
    ranked = sorted(
        (
            (periodic_score(material, x, y), stable_value(material, "pixel", x, y), x, y)
            for y in range(FIELD_HEIGHT)
            for x in range(FIELD_WIDTH)
        ),
        reverse=True,
    )
    selected = set(required or ())
    for _, _, x, y in ranked:
        if len(selected) >= target:
            break
        selected.add((x, y))

    minimum = 2 if target_per_tile <= 18 else 3 if target_per_tile <= 38 else 4
    by_tile: dict[tuple[int, int], list[tuple[float, int, int, int]]] = {
        (tx, ty): []
        for ty in range(REPEAT_HEIGHT)
        for tx in range(REPEAT_WIDTH)
    }
    for row in ranked:
        _, _, x, y = row
        by_tile[tile_of(x, y)].append(row)

    counts = {tile: 0 for tile in by_tile}
    for x, y in selected:
        counts[tile_of(x, y)] += 1

    # An ore block must never look like plain host rock. If a global vein misses a
    # repeat tile, add one small local stringer instead of scattered salt-and-pepper.
    for tile, rows in by_tile.items():
        while counts[tile] < minimum:
            candidates = [
                row for row in rows
                if (row[2], row[3]) not in selected
                and (counts[tile] == 0 or adjacent_in_tile((row[2], row[3]), selected, tile))
            ]
            if not candidates:
                candidates = [row for row in rows if (row[2], row[3]) not in selected]
            if not candidates:
                break
            _, _, x, y = candidates[0]
            selected.add((x, y))
            counts[tile] += 1

    required_pixels = set(required or ())
    if len(selected) > target:
        removal = sorted(
            (
                (periodic_score(material, x, y), stable_value(material, "trim", x, y), x, y)
                for x, y in selected
                if (x, y) not in required_pixels
            )
        )
        for _, _, x, y in removal:
            tile = tile_of(x, y)
            if counts[tile] <= minimum:
                continue
            selected.remove((x, y))
            counts[tile] -= 1
            if len(selected) == target:
                break

    if len(selected) != target:
        raise SystemExit(f"could not satisfy {material} spatial density target {target}")
    return selected


def build_overlay(master: Image.Image, material: str, selected: set[tuple[int, int]]) -> Image.Image:
    palette = mineral_palette(master)
    overlay = Image.new("RGBA", (FIELD_WIDTH, FIELD_HEIGHT), (0, 0, 0, 0))
    for x, y in selected:
        source = master.getpixel((x % TILE_SIZE, y % TILE_SIZE))
        if source[3] >= 32:
            red, green, blue, alpha = source
        else:
            red, green, blue, alpha = palette[stable_value(material, "colour", x, y) % len(palette)]
        overlay.putpixel((x, y), (red, green, blue, alpha))
    return overlay


def composite_field(host: Image.Image, overlay: Image.Image) -> Image.Image:
    field = Image.new("RGBA", (FIELD_WIDTH, FIELD_HEIGHT))
    for ty in range(REPEAT_HEIGHT):
        for tx in range(REPEAT_WIDTH):
            field.alpha_composite(host, (tx * TILE_SIZE, ty * TILE_SIZE))

    ore_pixels = {
        (x, y)
        for y in range(FIELD_HEIGHT)
        for x in range(FIELD_WIDTH)
        if overlay.getpixel((x, y))[3] >= 32
    }
    rim: set[tuple[int, int]] = set()
    for x, y in ore_pixels:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            point = ((x + dx) % FIELD_WIDTH, (y + dy) % FIELD_HEIGHT)
            if point not in ore_pixels:
                rim.add(point)
    for point in rim:
        red, green, blue, alpha = field.getpixel(point)
        field.putpixel(point, (int(red * 0.94), int(green * 0.94), int(blue * 0.94), alpha))
    field.alpha_composite(overlay)
    return field


def properties_text(material: str, grade: str, host: str) -> str:
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


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_preview(matrix: dict[str, object]) -> None:
    field_px = FIELD_WIDTH
    label_width = 76
    gap = 12
    width = label_width + len(matrix["ores"]) * (field_px + gap) + 8
    height = 30 + len(GRADES) * (field_px + 10) + 22
    preview = Image.new("RGB", (width, height), "#17191d")
    draw = ImageDraw.Draw(preview)
    font = ImageFont.load_default()
    for material_index, (material, ore) in enumerate(matrix["ores"].items()):
        left = label_width + material_index * (field_px + gap)
        draw.text((left, 6), material.upper(), font=font, fill="#eeeeea")
        host = ore["defaultHost"]
        for grade_index, grade in enumerate(GRADES):
            top = 26 + grade_index * (field_px + 10)
            if material_index == 0:
                draw.text((5, top + field_px // 2 - 4), grade.upper(), font=font, fill="#c8c9cc")
            for tile_y in range(REPEAT_HEIGHT):
                for tile_x in range(REPEAT_WIDTH):
                    index = tile_y * REPEAT_WIDTH + tile_x
                    tile = Image.open(TEXTURE_ROOT / material / host / grade / f"{index}.png").convert("RGB")
                    preview.paste(tile, (left + tile_x * TILE_SIZE, top + tile_y * TILE_SIZE))
    draw.text(
        (5, height - 16),
        "Each panel is the actual 4x4 block repeat field.",
        font=font,
        fill="#8f9298",
    )
    PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW_PATH, optimize=True)


def generate() -> tuple[int, int]:
    matrix = load_json(MATRIX_PATH)
    generated_properties: set[Path] = set()
    generated_textures: set[Path] = set()
    inputs: set[Path] = {MATRIX_PATH}

    hosts = {
        host: load_rgba(HOST_ROOT / f"{host}.png", (TILE_SIZE, TILE_SIZE))
        for host in matrix["hosts"]
    }
    inputs.update(HOST_ROOT / f"{host}.png" for host in matrix["hosts"])

    for material, ore in matrix["ores"].items():
        master_path = ORE_SOURCE_ROOT / "master" / f"{material}.png"
        master = load_rgba(master_path, (TILE_SIZE, TILE_SIZE))
        inputs.add(master_path)
        overlays: dict[str, Image.Image] = {}
        selected: set[tuple[int, int]] = set()
        for grade in GRADES:
            source_path = ORE_SOURCE_ROOT / material / f"{grade}.png"
            inputs.add(source_path)
            load_rgba(source_path, (TILE_SIZE, TILE_SIZE))
            selected = select_pixels(
                material,
                int(matrix["grades"][grade]["targetPixels"]),
                selected,
            )
            overlays[grade] = build_overlay(master, material, selected)

        for host in ore["validHosts"]:
            for grade in GRADES:
                property_path = PROPERTIES_ROOT / material / grade / f"{host}.properties"
                property_path.parent.mkdir(parents=True, exist_ok=True)
                property_path.write_text(properties_text(material, grade, host), encoding="utf-8")
                generated_properties.add(property_path)

                field = composite_field(hosts[host], overlays[grade])
                texture_dir = TEXTURE_ROOT / material / host / grade
                texture_dir.mkdir(parents=True, exist_ok=True)
                for tile_y in range(REPEAT_HEIGHT):
                    for tile_x in range(REPEAT_WIDTH):
                        index = tile_y * REPEAT_WIDTH + tile_x
                        tile = field.crop(
                            (
                                tile_x * TILE_SIZE,
                                tile_y * TILE_SIZE,
                                (tile_x + 1) * TILE_SIZE,
                                (tile_y + 1) * TILE_SIZE,
                            )
                        )
                        path = texture_dir / f"{index}.png"
                        tile.save(path, optimize=True)
                        generated_textures.add(path)

    for stale in set(PROPERTIES_ROOT.rglob("*.properties")) - generated_properties:
        stale.unlink()
    for stale in set(TEXTURE_ROOT.rglob("*.png")) - generated_textures:
        stale.unlink()

    write_preview(matrix)
    tracked = generated_properties | generated_textures | {PREVIEW_PATH}
    manifest = {
        "schemaVersion": 3,
        "method": "repeat",
        "source": "spatial-64x64-mineral-field",
        "repeatWidth": REPEAT_WIDTH,
        "repeatHeight": REPEAT_HEIGHT,
        "runtimeTilesPerCombination": REPEAT_TILE_COUNT,
        "generatedCombinations": len(generated_properties),
        "inputs": {
            path.relative_to(ROOT).as_posix(): sha256(path)
            for path in sorted(inputs)
        },
        "files": {
            path.relative_to(ROOT).as_posix(): sha256(path)
            for path in sorted(tracked)
        },
    }
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return len(generated_properties), len(generated_textures)


if __name__ == "__main__":
    combinations, sprites = generate()
    print(f"generated {combinations} spatial repeat combinations and {sprites} sprites")
