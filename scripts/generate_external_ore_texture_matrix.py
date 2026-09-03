#!/usr/bin/env python3
"""Generate checked-in external-ore assets from master overlays and the matrix."""

from __future__ import annotations

import json
import re
from pathlib import Path

from PIL import Image

from generate_ore_texture_matrix import ASSETS, GRADES, ROOT, composite, load_rgba, ranked_pixels, write_json

MATRIX_PATH = ROOT / "src/main/resources/data/geostrata/materials/external_ore_texture_matrix.json"
ORE_HOST_SOURCE = ROOT / "src/main/java/com/geostrata/block/OreHost.java"
OVERLAY_ROOT = ASSETS / "textures/block/external_ore_source"
COMPOSITE_ROOT = ASSETS / "textures/block/external_ore"
HOST_ROOT = ASSETS / "textures/block/host"
ORE_HOST = re.compile(r'\b[A-Z_]+\("([a-z_]+)"\)')


def load_matrix() -> dict[str, object]:
    matrix = json.loads(MATRIX_PATH.read_text(encoding="utf-8"))
    if (
        matrix.get("schemaVersion") != 1
        or matrix.get("model") != "geostrata:external_ore_texture_matrix"
        or matrix.get("resolution") != 16
        or tuple(matrix.get("grades", {})) != GRADES
    ):
        raise SystemExit("external ore texture matrix has an unsupported schema")
    return matrix


def grade_overlays(matrix: dict[str, object], material: str) -> dict[str, Image.Image]:
    master = load_rgba(OVERLAY_ROOT / "master" / f"{material}.png", matrix["resolution"])
    ranking = ranked_pixels(master, material)
    overlays: dict[str, Image.Image] = {}
    for grade in GRADES:
        selected = set(ranking[:matrix["grades"][grade]["targetPixels"]])
        overlay = Image.new("RGBA", master.size, (0, 0, 0, 0))
        for point in selected:
            red, green, blue, _ = master.getpixel(point)
            overlay.putpixel(point, (red, green, blue, 255))
        path = OVERLAY_ROOT / material / f"{grade}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        overlay.save(path, optimize=True)
        overlays[grade] = overlay
    return overlays


def generate() -> tuple[int, int]:
    matrix = load_matrix()
    ore_hosts = ORE_HOST.findall(ORE_HOST_SOURCE.read_text(encoding="utf-8"))
    if not ore_hosts:
        raise SystemExit("no OreHost values found")

    composite_count = 0
    model_count = 0
    for material, ore in matrix["ores"].items():
        default_host = ore["defaultHost"]
        valid_hosts = ore["validHosts"]
        if default_host not in valid_hosts:
            raise SystemExit(f"{material} defaultHost must be one of validHosts")

        hosts = {host: load_rgba(HOST_ROOT / f"{host}.png", matrix["resolution"]) for host in valid_hosts}
        overlays = grade_overlays(matrix, material)
        for grade in GRADES:
            for host in valid_hosts:
                texture = COMPOSITE_ROOT / material / host / f"{grade}.png"
                texture.parent.mkdir(parents=True, exist_ok=True)
                composite(hosts[host], overlays[grade]).save(texture, optimize=True)
                write_json(ASSETS / f"models/block/ore/{material}/{host}/{grade}.json", {
                    "parent": "minecraft:block/cube_all",
                    "textures": {"all": f"geostrata:block/external_ore/{material}/{host}/{grade}"},
                })
                composite_count += 1
                model_count += 1

            block_name = f"{grade}_{material}_ore"
            variants = {
                f"host={host}": {
                    "model": f"geostrata:block/ore/{material}/{host if host in valid_hosts else default_host}/{grade}"
                }
                for host in ore_hosts
            }
            write_json(ASSETS / f"blockstates/{block_name}.json", {"variants": variants})
            write_json(ASSETS / f"models/item/{block_name}.json", {
                "parent": f"geostrata:block/ore/{material}/{default_host}/{grade}"
            })

    return composite_count, model_count


if __name__ == "__main__":
    composites, models = generate()
    print(f"generated {composites} external-ore composites and {models} block models")
