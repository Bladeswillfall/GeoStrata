#!/usr/bin/env python3
"""Generate compact connected-texture tilesets for GeoStrata graded ores.

Artists edit one 40x24 RGBA sheet per mineral. Each sheet contains thirteen 8x8
subtiles (center, four borders, four outer corners and four inner corners).
Those subtiles are graded and assembled into the five sprites required by the
OptiFine/Continuity ``ctm_compact`` method, then composited against every host
that can actually occur for that mineral.

The first run seeds a missing sheet from the existing dense mineral master.
After that, the sheet is source art and is never overwritten.
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

SUBTILE = 8
SHEET_COLUMNS = 5
SHEET_ROWS = 3
SHEET_SIZE = (SHEET_COLUMNS * SUBTILE, SHEET_ROWS * SUBTILE)
COMPACT_TILE_COUNT = 5
MANIFEST_PATH = ROOT / "src" / "main" / "resources" / "data" / "geostrata" / "materials" / "ore_ctm_manifest.json"
SOURCE_ROOT = ASSETS / "textures" / "block" / "ore_source" / "tileset"
PROPERTIES_ROOT = ASSETS / "optifine" / "ctm" / "ore"
TEXTURE_ROOT = ASSETS / "textures" / "optifine" / "ctm" / "ore"
PREVIEW_PATH = ROOT / "docs" / "images" / "ore-ctm-tileset-preview.png"

# The first 3x3 block is spatial: outer corners/borders surround center.
# The remaining 2x2 block contains the four internal-corner cases.
SHEET_SLOTS = {
    "outer_nw": (0, 0),
    "border_n": (1, 0),
    "outer_ne": (2, 0),
    "inner_nw": (3, 0),
    "inner_ne": (4, 0),
    "border_w": (0, 1),
    "center": (1, 1),
    "border_e": (2, 1),
    "inner_sw": (3, 1),
    "inner_se": (4, 1),
    "outer_sw": (0, 2),
    "border_s": (1, 2),
    "outer_se": (2, 2),
}

OPEN_SIDES = {
    "center": frozenset("nesw"),
    "border_n": frozenset("esw"),
    "border_e": frozenset("nsw"),
    "border_s": frozenset("new"),
    "border_w": frozenset("nes"),
    "outer_nw": frozenset("es"),
    "outer_ne": frozenset("sw"),
    "outer_se": frozenset("nw"),
    "outer_sw": frozenset("ne"),
    # Inner corners are a mostly-connected field with a notch at one corner.
    "inner_nw": frozenset("nesw"),
    "inner_ne": frozenset("nesw"),
    "inner_se": frozenset("nesw"),
    "inner_sw": frozenset("nesw"),
}

INNER_NOTCH = {
    "inner_nw": "nw",
    "inner_ne": "ne",
    "inner_se": "se",
    "inner_sw": "sw",
}

# Full compact sprites, listed as NW, NE, SE, SW quadrants.
COMPACT_QUADRANTS = {
    0: ("outer_nw", "outer_ne", "outer_se", "outer_sw"),
    1: ("center", "center", "center", "center"),
    2: ("border_w", "border_e", "border_e", "border_w"),
    3: ("border_n", "border_n", "border_s", "border_s"),
    4: ("inner_nw", "inner_ne", "inner_se", "inner_sw"),
}


def stable_value(*parts: object) -> int:
    digest = hashlib.blake2s("|".join(map(str, parts)).encode("utf-8"), digest_size=8).digest()
    return int.from_bytes(digest, "big")


def master_palette(master: Image.Image) -> list[tuple[int, int, int]]:
    palette = [pixel[:3] for pixel in master.getdata() if pixel[3] >= 32]
    if not palette:
        raise SystemExit("dense ore master must contain opaque mineral pixels")
    return palette


def corner_pixels(corner: str) -> set[tuple[int, int]]:
    xs = range(0, 3) if "w" in corner else range(SUBTILE - 3, SUBTILE)
    ys = range(0, 3) if "n" in corner else range(SUBTILE - 3, SUBTILE)
    return {(x, y) for y in ys for x in xs if (x in (0, SUBTILE - 1) or y in (0, SUBTILE - 1) or (x + y) % 2 == 0)}


def port_path(side: str) -> set[tuple[int, int]]:
    center = SUBTILE // 2 - 1
    if side == "n":
        return {(center, y) for y in range(0, center + 1)}
    if side == "s":
        return {(center, y) for y in range(center, SUBTILE)}
    if side == "w":
        return {(x, center) for x in range(0, center + 1)}
    if side == "e":
        return {(x, center) for x in range(center, SUBTILE)}
    raise ValueError(side)


def seed_mask(material: str, tile_name: str) -> set[tuple[int, int]]:
    center = (SUBTILE - 1) / 2
    mask = {
        (x, y)
        for y in range(SUBTILE)
        for x in range(SUBTILE)
        if (x - center) ** 2 + (y - center) ** 2 <= 12.5
    }
    for side in OPEN_SIDES[tile_name]:
        mask.update(port_path(side))

    # Closed edges must remain host-only so terminated CTM sides do not look cut.
    if "n" not in OPEN_SIDES[tile_name]:
        mask = {point for point in mask if point[1] != 0}
    if "s" not in OPEN_SIDES[tile_name]:
        mask = {point for point in mask if point[1] != SUBTILE - 1}
    if "w" not in OPEN_SIDES[tile_name]:
        mask = {point for point in mask if point[0] != 0}
    if "e" not in OPEN_SIDES[tile_name]:
        mask = {point for point in mask if point[0] != SUBTILE - 1}

    notch = INNER_NOTCH.get(tile_name)
    if notch:
        mask.difference_update(corner_pixels(notch))

    # Add restrained material-specific irregularity while keeping at least 34 dense pixels.
    candidates = [
        (x, y)
        for y in range(SUBTILE)
        for x in range(SUBTILE)
        if (x, y) not in mask
    ]
    candidates.sort(
        key=lambda point: (
            (point[0] - center) ** 2 + (point[1] - center) ** 2,
            stable_value(material, tile_name, *point),
        )
    )
    for point in candidates:
        if len(mask) >= 36:
            break
        x, y = point
        if (y == 0 and "n" not in OPEN_SIDES[tile_name]) or (y == SUBTILE - 1 and "s" not in OPEN_SIDES[tile_name]):
            continue
        if (x == 0 and "w" not in OPEN_SIDES[tile_name]) or (x == SUBTILE - 1 and "e" not in OPEN_SIDES[tile_name]):
            continue
        if notch and point in corner_pixels(notch):
            continue
        mask.add(point)
    if len(mask) < 30:
        raise SystemExit(f"could not seed dense source tile {material}/{tile_name}")
    return mask


def seed_source_sheet(material: str, master: Image.Image) -> Image.Image:
    palette = master_palette(master)
    sheet = Image.new("RGBA", SHEET_SIZE, (0, 0, 0, 0))
    for tile_name, (column, row) in SHEET_SLOTS.items():
        mask = seed_mask(material, tile_name)
        for x, y in mask:
            red, green, blue = palette[stable_value(material, tile_name, x, y) % len(palette)]
            # Preserve vanilla-like value variation from the master palette, not flat recoloring.
            sheet.putpixel((column * SUBTILE + x, row * SUBTILE + y), (red, green, blue, 255))
    return sheet


def load_source_sheet(material: str, master: Image.Image) -> Image.Image:
    path = SOURCE_ROOT / f"{material}.png"
    if path.exists():
        image = Image.open(path).convert("RGBA")
        if image.size != SHEET_SIZE:
            raise SystemExit(f"{path.relative_to(ROOT)} must be exactly {SHEET_SIZE[0]}x{SHEET_SIZE[1]}")
        return image
    path.parent.mkdir(parents=True, exist_ok=True)
    image = seed_source_sheet(material, master)
    image.save(path, optimize=True)
    return image


def crop_source_tiles(sheet: Image.Image) -> dict[str, Image.Image]:
    result: dict[str, Image.Image] = {}
    for name, (column, row) in SHEET_SLOTS.items():
        left = column * SUBTILE
        top = row * SUBTILE
        result[name] = sheet.crop((left, top, left + SUBTILE, top + SUBTILE))
    return result


def ranked_tile_pixels(tile: Image.Image, material: str, tile_name: str) -> list[tuple[int, int]]:
    candidates = [
        (x, y)
        for y in range(SUBTILE)
        for x in range(SUBTILE)
        if tile.getpixel((x, y))[3] >= 32
    ]
    if len(candidates) < 30:
        raise SystemExit(f"{material} source subtile {tile_name} has only {len(candidates)} usable pixels; need 30")

    def score(point: tuple[int, int]) -> tuple[int, int, int]:
        x, y = point
        neighbourhood = sum(
            tile.getpixel((nx, ny))[3]
            for ny in range(max(0, y - 1), min(SUBTILE, y + 2))
            for nx in range(max(0, x - 1), min(SUBTILE, x + 2))
        )
        edge_bonus = 0
        sides = OPEN_SIDES[tile_name]
        if y == 0 and "n" in sides:
            edge_bonus += 1024
        if y == SUBTILE - 1 and "s" in sides:
            edge_bonus += 1024
        if x == 0 and "w" in sides:
            edge_bonus += 1024
        if x == SUBTILE - 1 and "e" in sides:
            edge_bonus += 1024
        return edge_bonus, neighbourhood, stable_value(material, tile_name, x, y)

    return sorted(candidates, key=score, reverse=True)


def grade_source_tiles(matrix: dict[str, object], material: str, source: dict[str, Image.Image]) -> dict[str, dict[str, Image.Image]]:
    rankings = {
        name: ranked_tile_pixels(tile, material, name)
        for name, tile in source.items()
    }
    graded: dict[str, dict[str, Image.Image]] = {}
    for grade in GRADES:
        target = max(1, round(matrix["grades"][grade]["targetPixels"] / 4))
        grade_tiles: dict[str, Image.Image] = {}
        for name, tile in source.items():
            selected = set(rankings[name][:target])
            output = Image.new("RGBA", (SUBTILE, SUBTILE), (0, 0, 0, 0))
            for point in selected:
                red, green, blue, _ = tile.getpixel(point)
                output.putpixel(point, (red, green, blue, 255))
            grade_tiles[name] = output
        graded[grade] = grade_tiles
    return graded


def assemble_compact_tiles(tiles: dict[str, Image.Image]) -> list[Image.Image]:
    output: list[Image.Image] = []
    placements = ((0, 0), (SUBTILE, 0), (SUBTILE, SUBTILE), (0, SUBTILE))
    for index in range(COMPACT_TILE_COUNT):
        image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for tile_name, position in zip(COMPACT_QUADRANTS[index], placements, strict=True):
            image.alpha_composite(tiles[tile_name], position)
        output.append(image)
    return output


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
                tile = Image.open(path).convert("RGB").resize((sprite, sprite), Image.Resampling.NEAREST)
                preview.paste(tile, (left + tile_index * sprite, top))
        draw.text((left, height - 17), f"host: {host}", font=font, fill="#8f9298")

    PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW_PATH, optimize=True)


def generate() -> tuple[int, int, int]:
    matrix = load_matrix()
    host_root = ASSETS / "textures" / "block" / "host"
    hosts = {
        host: load_rgba(host_root / f"{host}.png", 16)
        for host in matrix["hosts"]
    }

    expected_properties: set[Path] = set()
    expected_textures: set[Path] = set()
    source_sheets: set[Path] = set()
    for material, ore in matrix["ores"].items():
        master = load_rgba(ASSETS / "textures" / "block" / "ore_source" / "master" / f"{material}.png", 16)
        sheet = load_source_sheet(material, master)
        sheet_path = SOURCE_ROOT / f"{material}.png"
        source_sheets.add(sheet_path)
        source_tiles = crop_source_tiles(sheet)
        graded_tiles = grade_source_tiles(matrix, material, source_tiles)

        for grade in GRADES:
            compact_overlays = assemble_compact_tiles(graded_tiles[grade])
            for host in ore["validHosts"]:
                property_path = PROPERTIES_ROOT / material / grade / f"{host}.properties"
                property_path.parent.mkdir(parents=True, exist_ok=True)
                property_path.write_text(properties_text(material, grade, host), encoding="utf-8")
                expected_properties.add(property_path)

                texture_dir = TEXTURE_ROOT / material / host / grade
                texture_dir.mkdir(parents=True, exist_ok=True)
                for index, overlay in enumerate(compact_overlays):
                    path = texture_dir / f"{index}.png"
                    composite_ctm(hosts[host], overlay).save(path, optimize=True)
                    expected_textures.add(path)

    # Remove generated CTM files for combinations that are no longer valid.
    for path in PROPERTIES_ROOT.rglob("*.properties"):
        if path not in expected_properties:
            path.unlink()
    for path in TEXTURE_ROOT.rglob("*.png"):
        if path not in expected_textures:
            path.unlink()

    write_preview(matrix)

    tracked = sorted(source_sheets | expected_properties | expected_textures | {PREVIEW_PATH})
    manifest = {
        "schemaVersion": 1,
        "method": "ctm_compact",
        "sourceSubtiles": len(SHEET_SLOTS),
        "subtileResolution": SUBTILE,
        "runtimeTilesPerCombination": COMPACT_TILE_COUNT,
        "generatedCombinations": len(expected_properties),
        "files": {
            path.relative_to(ROOT).as_posix(): sha256(path)
            for path in tracked
        },
    }
    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST_PATH.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return len(source_sheets), len(expected_properties), len(expected_textures)


if __name__ == "__main__":
    sheets, combinations, sprites = generate()
    print(
        f"generated {sheets} artist tileset sheets, {combinations} compact CTM combinations "
        f"and {sprites} Continuity sprites"
    )
