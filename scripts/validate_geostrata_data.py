#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "geostrata_mod_src" / "data" / "geostrata"


def load(path: Path):
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def main():
    grade_schema = load(ROOT / "ores" / "ore_grade_schema.json")
    allowed_grades = {g["id"] for g in grade_schema["grades"]}
    if allowed_grades != {"poor", "medium", "rich", "massive"}:
        raise SystemExit(f"Unexpected grade set: {sorted(allowed_grades)}")

    materials = load(ROOT / "materials" / "material_authority.json")
    shared_materials = set(materials["shared_materials"])

    controlled = load(ROOT / "ores" / "controlled_materials.json")
    controlled_materials = {e["material"] for e in controlled["authoritative_overworld_materials"]}
    if controlled_materials != shared_materials:
        raise SystemExit(
            f"Controlled materials {sorted(controlled_materials)} do not match shared materials {sorted(shared_materials)}"
        )

    deposit_dir = ROOT / "ores" / "deposits"
    deposit_files = sorted(deposit_dir.glob("*.json"))
    if not deposit_files:
        raise SystemExit("No deposit profile files found")

    for deposit_file in deposit_files:
        profile = load(deposit_file)
        mat = profile["material"]
        if mat not in shared_materials:
            raise SystemExit(f"Deposit profile {deposit_file.name} uses unknown material: {mat}")

        weights = profile["grade_weights"]
        if set(weights.keys()) != allowed_grades:
            raise SystemExit(f"Deposit profile {deposit_file.name} grades mismatch: {sorted(weights.keys())}")

        total_weight = sum(weights.values())
        if total_weight != 100:
            raise SystemExit(f"Deposit profile {deposit_file.name} grade weights must sum to 100, got {total_weight}")

    print("GeoStrata data validation passed")


if __name__ == "__main__":
    main()
