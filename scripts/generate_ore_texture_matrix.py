#!/usr/bin/env python3
"""Generate GeoStrata's host-aware ore texture/model matrix.

Artists edit the 16x16 host tiles, the four dense mineral master overlays and
ore_texture_matrix.json. This script derives nested grade overlays, flat
composites, block models and blockstates. Generated files are committed so the
game and normal resource packs do not need Pillow at runtime.
"""

from __future__ import annotations

import json
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageEnhance, ImageFont
except ImportError as exc:
    raise SystemExit("Pillow is required: python -m pip install Pillow") from exc


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "geostrata"
MATRIX_PATH = RESOURCES / "data" / "geostrata" / "materials" / "ore_texture_matrix.json"
GRADES = ("poor", "medium", "rich", "massive")


def load_matrix() -> dict[str, object]:
    with MATRIX_PATH.open("r", encoding="utf-8") as handle:
        matrix = json.load(handle)
    if matrix.get("schemaVersion") != 1 or matrix.get("resolution") != 16:
        raise SystemExit("ore texture matrix must use schema 1 at 16x16")
    if tuple(matrix.get("grades", {})) != GRADES:
        raise SystemExit(f"grade order must be {GRADES}")
    return matrix


def load_rgba(path: Path, size: int) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    if image.size != (size, size):
        raise SystemExit(f"{path.relative_to(ROOT)} must be exactly {size}x{size}")
    return image


def ranked_pixels(master: Image.Image, material: str) -> list[tuple[int, int]]:
    """Rank opaque source pixels as coherent fragments, not salt-and-pepper."""
    alpha = master.getchannel("A")
    candidates = [(x, y) for y in range(16) for x in range(16) if alpha.getpixel((x, y)) >= 32]
    if len(candidates) < 118:
        raise SystemExit(f"{material} master contains only {len(candidates)} usable pixels; need 118")

    def score(point: tuple[int, int]) -> tuple[int, int]:
        x, y = point
        neighbourhood = sum(
            alpha.getpixel(((x + dx) % 16, (y + dy) % 16))
            for dy in (-1, 0, 1)
            for dx in (-1, 0, 1)
        )
        stable_tie = ((x * 73 + y * 151 + sum(map(ord, material)) * 29) ^ (x * y * 17)) & 255
        return neighbourhood, stable_tie

    return sorted(candidates, key=score, reverse=True)


def grade_overlays(matrix: dict[str, object], material: str, size: int) -> dict[str, Image.Image]:
    source = ASSETS / "textures" / "block" / "ore_source" / "master" / f"{material}.png"
    master = load_rgba(source, size)
    ranking = ranked_pixels(master, material)
    output: dict[str, Image.Image] = {}
    for grade in GRADES:
        count = matrix["grades"][grade]["targetPixels"]
        selected = set(ranking[:count])
        overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        for point in selected:
            red, green, blue, _ = master.getpixel(point)
            overlay.putpixel(point, (red, green, blue, 255))
        path = ASSETS / "textures" / "block" / "ore_source" / material / f"{grade}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        overlay.save(path, optimize=True)
        output[grade] = overlay
    return output


def composite(host: Image.Image, overlay: Image.Image) -> Image.Image:
    result = host.copy()
    ore_pixels = {
        (x, y)
        for y in range(16)
        for x in range(16)
        if overlay.getpixel((x, y))[3] > 0
    }
    rim = {
        ((x + dx) % 16, (y + dy) % 16)
        for x, y in ore_pixels
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))
    } - ore_pixels
    for point in rim:
        red, green, blue, alpha = result.getpixel(point)
        result.putpixel(point, (int(red * 0.88), int(green * 0.88), int(blue * 0.88), alpha))
    result.alpha_composite(overlay)
    return result


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def write_preview(matrix: dict[str, object]) -> None:
    scale = 2
    tile = 16 * scale
    left = 104
    top = 48
    materials = list(matrix["ores"])
    hosts = matrix["hosts"]
    width = left + len(materials) * len(GRADES) * tile + 8
    height = top + len(hosts) * tile + 28
    preview = Image.new("RGB", (width, height), "#17191d")
    draw = ImageDraw.Draw(preview)
    font = ImageFont.load_default()

    for material_index, material in enumerate(materials):
        x = left + material_index * len(GRADES) * tile
        draw.text((x + 2, 6), material.upper(), font=font, fill="#f0f0ed")
        for grade_index, grade in enumerate(GRADES):
            draw.text((x + grade_index * tile + 2, 22), grade[:3].upper(), font=font, fill="#b8b9bd")

    for host_index, host in enumerate(hosts):
        y = top + host_index * tile
        draw.text((6, y + 10), host, font=font, fill="#d3d4d7")
        for material_index, material in enumerate(materials):
            valid_hosts = set(matrix["ores"][material]["validHosts"])
            for grade_index, grade in enumerate(GRADES):
                path = ASSETS / "textures" / "block" / "ore" / material / host / f"{grade}.png"
                texture = Image.open(path).convert("RGB").resize((tile, tile), Image.Resampling.NEAREST)
                if host not in valid_hosts:
                    texture = ImageEnhance.Brightness(texture).enhance(0.38)
                x = left + (material_index * len(GRADES) + grade_index) * tile
                preview.paste(texture, (x, y))
                outline = "#676a70" if host in valid_hosts else "#33363c"
                draw.rectangle((x, y, x + tile - 1, y + tile - 1), outline=outline)

    draw.text(
        (6, height - 18),
        "Full brightness = valid geological occurrence | Dim = asset-ready only",
        font=font,
        fill="#8f9298",
    )
    path = ROOT / "docs" / "images" / "ore-texture-matrix-preview.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    preview.save(path, optimize=True)


def generate() -> tuple[int, int]:
    matrix = load_matrix()
    size = matrix["resolution"]
    host_root = ASSETS / "textures" / "block" / "host"
    hosts = {
        host: load_rgba(host_root / f"{host}.png", size)
        for host in matrix["hosts"]
    }
    for host in matrix["hosts"]:
        write_json(ASSETS / "models" / "block" / f"{host}.json", {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": f"geostrata:block/host/{host}"},
        })

    composite_count = 0
    model_count = 0
    for material, ore in matrix["ores"].items():
        overlays = grade_overlays(matrix, material, size)
        for grade in GRADES:
            variants: dict[str, object] = {}
            for host in matrix["hosts"]:
                texture_path = ASSETS / "textures" / "block" / "ore" / material / host / f"{grade}.png"
                texture_path.parent.mkdir(parents=True, exist_ok=True)
                composite(hosts[host], overlays[grade]).save(texture_path, optimize=True)

                model_id = f"geostrata:block/ore/{material}/{host}/{grade}"
                model_path = ASSETS / "models" / "block" / "ore" / material / host / f"{grade}.json"
                write_json(model_path, {
                    "parent": "minecraft:block/cube_all",
                    "textures": {"all": f"geostrata:block/ore/{material}/{host}/{grade}"},
                })
                variants[f"host={host}"] = {"model": model_id}
                composite_count += 1
                model_count += 1

            block_name = f"{grade}_{material}_ore"
            write_json(ASSETS / "blockstates" / f"{block_name}.json", {"variants": variants})
            default_model = f"geostrata:block/ore/{material}/{ore['defaultHost']}/{grade}"
            write_json(ASSETS / "models" / "item" / f"{block_name}.json", {"parent": default_model})

    write_preview(matrix)
    return composite_count, model_count


if __name__ == "__main__":
    composites, models = generate()
    print(f"generated {composites} host-aware ore composites and {models} block models")
