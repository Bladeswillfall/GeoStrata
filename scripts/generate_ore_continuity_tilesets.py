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


def mineral_palette(master: Image.Image) -> list[tuple[int, int, int]]:
    palette = [pixel[:3] for pixel in master.getdata() if pixel[3] >= 32]
    if not palette:
        raise SystemExit("ore master must contain opaque mineral pixels")
    return palette


def periodic_score(material: str, x: int, y: int) -> float:
    """Smooth, seamless 64x64 field with material-specific phases."""
    waves = (
        (1, 0, 1.00),
        (0, 1, 0.92),
        (1, 1, 0.78),
        (2, -1, 0.56),
        (1, 3, 0.42),
        (3, 2, 0.34),
        (5, -2, 0.20),
    )
    score = 0.0
    for index, (fx, fy, weight) in enumerate(waves):
        phase = (stable_value(material, "phase", index) % 1_000_000) / 1_000_000.0 * math.tau
        angle = math.tau * (fx * x / FIELD_WIDTH + fy * y / FIELD_HEIGHT) + phase
        score += weight * math.cos(angle)
    slope = ((stable_value(material, "ridge-slope") % 7) - 3) or 1
    ridge_phase = (stable_value(material, "ridge-phase") % FIELD_WIDTH) / FIELD_WIDTH * math.tau
    ridge = math.cos(math.tau * (x + slope * y) / FIELD_WIDTH + ridge_phase)
    return score + 0.38 * ridge


def select_pixels(material: str, target_per_tile: int) -> set[tuple[int, int]]:
    target = target_per_tile * REPEAT_TILE_COUNT
    ranked = sorted(
        (
            (periodic_score(material, x, y), stable_value(material, "pixel", x, y), x, y)
            for y in range(FIELD_HEIGHT)
            for x in range(FIELD_WIDTH)
        ),
        reverse=True,
    )
    selected = {(x, y) for _, _, x, y in ranked[:target]}

    minimum = max(2, min(12, target_per_tile // 3))
    by_tile: dict[tuple[int, int], list[tuple[float, int, int, int]]] = {}
    for row in ranked:
        _, _, x, y = row
        by_tile.setdefault((x // TILE_SIZE, y // TILE_SIZE), []).append(row)

    counts = {(tx, ty): 0 for ty in range(REPEAT_HEIGHT) for tx in range(REPEAT_WIDTH)}
    for x, y in selected:
        counts[(x // TILE_SIZE, y // TILE_SIZE)] += 1

    for tile, rows in by_tile.items():
        need = minimum - counts[tile]
        if need <= 0:
            continue
        for _, _, x, y in rows:
            if (x, y) not in selected:
                selected.add((x, y))
                counts[tile] += 1
                need -= 1
                if need == 0:
                    break

    if len(selected) > target:
        removal = sorted(
            (
                (periodic_score(material, x, y), stable_value(material, "trim", x, y), x, y)
                for x, y in selected
            )
        )
        for _, _, x, y in removal:
            tile = (x // TILE_SIZE, y // TILE_SIZE)
            if counts[tile] <= minimum:
                continue
            selected.remove((x, y))
            counts[tile] -= 1
            if len(selected) == target:
                break
    return selected


def build_overlay(master: Image.Image, material: str, selected: set[tuple[int, int]]) -> Image.Image:
    palette = mineral_palette(master)
    overlay = Image.new("RGBA", (FIELD_WIDTH, FIELD_HEIGHT), (0, 0, 0, 0))
    for x, y in selected:
        source = master.getpixel((x % TILE_SIZE, y % TILE_SIZE))
        if source[3] >= 32:
            red, green, blue = source[:3]
        else:
            red, green, blue = palette[stable_value(material, "colour", x, y) % len(palette)]
        overlay.putpixel((x, y), (red, green, blue, 255))
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
        if overlay.getpixel((x, y))[3] > 0
    }
    rim: set[tuple[int, int]] = set()
    for x, y in ore_pixels:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            point = ((x + dx) % FIELD_WIDTH, (y + dy) % FIELD_HEIGHT)
            if point not in ore_pixels:
                rim.add(point)
    for point in rim:
        red, green, blue, alpha = field.getpixel(point)
        field.putpixel(point, (int(red * 0.88), int(green * 0.88), int(blue * 0.88), alpha))
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
        for grade in GRADES:
            source_path = ORE_SOURCE_ROOT / material / f"{grade}.png"
            inputs.add(source_path)
            load_rgba(source_path, (TILE_SIZE, TILE_SIZE))
            selected = select_pixels(material, int(matrix["grades"][grade]["targetPixels"]))
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
