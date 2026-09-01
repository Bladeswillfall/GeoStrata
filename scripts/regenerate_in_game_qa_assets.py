#!/usr/bin/env python3
"""One-shot migration for the September 2026 in-game QA findings."""

from __future__ import annotations

from pathlib import Path
import shutil
import subprocess
import sys

from PIL import Image, ImageEnhance

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(
            f"{label}: expected exactly one source match in {path.relative_to(ROOT)}, found {count}"
        )
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def patch_source_contracts() -> None:
    matrix_generator = ROOT / "scripts/generate_ore_texture_matrix.py"
    replace_once(
        matrix_generator,
        'tiles = " ".join(f"geostrata:optifine/ctm/host/{host}/{index}" for index in range(CONTINUITY_VARIANTS))',
        'tiles = " ".join(f"geostrata:textures/optifine/ctm/host/{host}/{index}" for index in range(CONTINUITY_VARIANTS))',
        "canonical host Continuity path",
    )
    replace_once(
        matrix_generator,
        '        "method=random\\n"\n        f"matchTiles=geostrata:block/host/{host}\\n"\n        f"tiles={tiles}\\n",',
        '        "method=random\\n"\n        f"matchTiles=geostrata:block/host/{host}\\n"\n        "prioritize=false\\n"\n        f"tiles={tiles}\\n",',
        "host overlay processing order",
    )

    ore_generator = ROOT / "scripts/generate_ore_continuity_tilesets.py"
    replace_once(
        ore_generator,
        '        "connect=state\\n"',
        '        "connect=block\\n"',
        "ore CTM cross-host connectivity",
    )

    ore_validator = ROOT / "scripts/validate_ore_continuity_tilesets.py"
    replace_once(
        ore_validator,
        '        f"geostrata:optifine/ctm/host/{host}/{index}"',
        '        f"geostrata:textures/optifine/ctm/host/{host}/{index}"',
        "validator canonical host Continuity path",
    )
    replace_once(
        ore_validator,
        '        f"matchTiles=geostrata:block/host/{host}\\n"\n        f"tiles={tiles}\\n"',
        '        f"matchTiles=geostrata:block/host/{host}\\n"\n        "prioritize=false\\n"\n        f"tiles={tiles}\\n"',
        "validator host overlay processing order",
    )
    replace_once(
        ore_validator,
        '        "connect=state\\n"',
        '        "connect=block\\n"',
        "validator ore CTM cross-host connectivity",
    )

    geology_validator = ROOT / "scripts/validate_geology_catalog.py"
    replace_once(
        geology_validator,
        '            f"geostrata:optifine/ctm/host/{host}/{index}"',
        '            f"geostrata:textures/optifine/ctm/host/{host}/{index}"',
        "geology validator canonical host Continuity path",
    )
    replace_once(
        geology_validator,
        '            f"matchTiles=geostrata:block/host/{host}\\n"\n            f"tiles={tiles}\\n"',
        '            f"matchTiles=geostrata:block/host/{host}\\n"\n            "prioritize=false\\n"\n            f"tiles={tiles}\\n"',
        "geology validator host overlay processing order",
    )


def patch_build_workflow() -> None:
    workflow = ROOT / ".github/workflows/build.yml"
    replace_once(
        workflow,
        """      - name: Validate ore Continuity tilesets
        run: python3 scripts/validate_ore_continuity_tilesets.py

      - name: Validate optional integration data
""",
        """      - name: Validate ore Continuity tilesets
        run: python3 scripts/validate_ore_continuity_tilesets.py

      - name: Validate host Continuity transitions
        run: python3 scripts/validate_host_continuity_transitions.py

      - name: Validate optional integration data
""",
        "host transition CI validation",
    )


def patch_docs() -> None:
    path = ROOT / "docs/ORE_SYSTEM.md"
    text = path.read_text(encoding="utf-8")
    start = text.index("### Connected ore tilesets\n")
    end = text.index("\n## Ownership and compatibility\n", start)
    replacement = """### Dithered host transitions

Continuity adds a narrow, two-pixel dither at exposed boundaries between
different GeoStrata rock blocks. This is an overlay-only visual treatment: it
does not alter block states, geology, collision, mining, or world generation.

`scripts/generate_host_continuity_transitions.py` derives the overlay sprites
directly from the existing 16x16 host textures. One overlay rule per host is
shared against every other GeoStrata host, so a contact borrows a few pixels
from the neighbouring rock instead of ending on a hard one-pixel line. Normal
host `method=random` rules use canonical texture paths and `prioritize=false`,
allowing the boundary overlay to run before the subtle interior variation.

### Connected ore tilesets

Graded ores use a separate topology-aware Continuity layer so exposed deposits
read as one mineral body instead of a grid of repeated single-block sprites.
The current generator uses the normal full-size 16x16 graded mineral overlays
as its source field; the obsolete 40x24/8x8 authoring sheets no longer ship as
runtime textures.

`scripts/generate_ore_continuity_tilesets.py` derives the five sprites required
by Continuity's `ctm_compact` method: isolated, fully connected, vertical,
horizontal, and missing-diagonal states.

The generated properties match one material, grade and host state, but use
`connect=block`. Adjacent blocks of the same material and grade therefore
connect across a host-rock state change while each block still uses its local
host composite. Grade boundaries remain visible because Poor, Medium, Rich and
Massive are distinct block IDs.

Only combinations listed in each material's `validHosts` are emitted. Flat
host/material/grade textures remain the renderer-independent fallback when
Continuity is absent.

Generated ore CTM assets remain covered by
`data/geostrata/materials/ore_ctm_manifest.json`. Regenerate ores with
`python3 scripts/generate_ore_continuity_tilesets.py`; regenerate rock-boundary
overlays with `python3 scripts/generate_host_continuity_transitions.py`.
Normal CI validates both committed outputs without requiring Pillow.

`docs/images/host-tiling-preview.png` remains the quick check for host
repetition. `docs/images/ore-ctm-tileset-preview.png` shows the five compact ore
CTM outputs for each grade and material using its default host.

![Host, mineral and grade authoring matrix](images/ore-texture-matrix-preview.png)

![Seamless host tiling and Continuity variation](images/host-tiling-preview.png)

![Compact connected ore tilesets](images/ore-ctm-tileset-preview.png)

Full-bright cells are currently permitted by geological occurrence rules. Dim
cells are generated and asset-ready but will not occur naturally unless the
occurrence catalog is expanded later.
"""
    path.write_text(text[:start] + replacement + text[end:], encoding="utf-8")


def soften_gneiss() -> None:
    path = ROOT / "src/main/resources/assets/geostrata/textures/block/host/gneiss.png"
    image = Image.open(path).convert("RGB")
    ImageEnhance.Contrast(image).enhance(0.60).save(path, optimize=True)


def remove_obsolete_tilesets() -> None:
    path = ROOT / "src/main/resources/assets/geostrata/textures/block/ore_source/tileset"
    if path.exists():
        shutil.rmtree(path)


def run(*args: str) -> None:
    subprocess.run(args, cwd=ROOT, check=True)


def cleanup_one_shot_files() -> None:
    workflow = ROOT / ".github/workflows/qa-asset-regeneration.yml"
    if workflow.exists():
        workflow.unlink()
    Path(__file__).unlink()


def main() -> None:
    patch_source_contracts()
    patch_build_workflow()
    patch_docs()
    soften_gneiss()
    remove_obsolete_tilesets()

    run(sys.executable, "scripts/generate_ore_texture_matrix.py")
    run(sys.executable, "scripts/generate_host_continuity_transitions.py")
    run(sys.executable, "scripts/generate_ore_continuity_tilesets.py")
    run(sys.executable, "scripts/validate_ore_continuity_tilesets.py")
    run(sys.executable, "scripts/validate_host_continuity_transitions.py")
    run(sys.executable, "scripts/validate_geology_catalog.py")

    cleanup_one_shot_files()


if __name__ == "__main__":
    main()
