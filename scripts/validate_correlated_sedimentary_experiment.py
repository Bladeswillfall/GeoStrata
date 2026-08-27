#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/geostrata/geology"


def load(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def require(condition: bool, message: str):
    if not condition:
        raise SystemExit(message)


def namespaced_path(identifier: str, expected_namespace: str):
    require(isinstance(identifier, str) and ":" in identifier, f"invalid namespaced identifier: {identifier!r}")
    namespace, path = identifier.split(":", 1)
    require(namespace == expected_namespace, f"identifier must use {expected_namespace} namespace: {identifier}")
    require(path and not path.startswith("/") and ".." not in path, f"invalid identifier path: {identifier}")
    return path


experiment = load(DATA / "correlated_sedimentary_experiment.json")
successions_root = load(DATA / "sedimentary_successions.json")
lithologies_root = load(DATA / "lithologies.json")
profiles_root = load(DATA / "province_profiles.json")

require(experiment.get("schemaVersion") == 1, "correlated experiment schemaVersion must be 1")
require(experiment.get("model") == "geostrata:correlated_sedimentary_experiment", "unexpected correlated experiment model")
require(experiment.get("runtimeStatus") == "metadata_only", "correlated experiment must remain metadata_only")
require(experiment.get("enabled") is False, "core correlated experiment must remain disabled by default")

successions = {entry["id"]: entry for entry in successions_root.get("successions", [])}
lithologies = {entry["id"]: entry for entry in lithologies_root.get("lithologies", [])}
province_ids = {entry["province"] for entry in profiles_root.get("profiles", [])}
blend_width = profiles_root.get("blendWidthBlocks")
require(isinstance(blend_width, int) and blend_width > 0, "province profile blend width must be a positive integer")

target_ids = experiment.get("targetSuccessionIds")
require(isinstance(target_ids, list) and target_ids, "targetSuccessionIds must be a non-empty array")
require(len(target_ids) == len(set(target_ids)), "targetSuccessionIds must not contain duplicates")
require(len(target_ids) == 1, "the first correlated experiment must target exactly one succession")

selected = []
for succession_id in target_ids:
    require(succession_id in successions, f"unknown target succession: {succession_id}")
    succession = successions[succession_id]
    require(succession.get("continuity") == "regional", f"initial experiment target must be regional: {succession_id}")
    selected.append(succession)

allowed_provinces = experiment.get("allowedProvinces")
require(isinstance(allowed_provinces, list) and allowed_provinces, "allowedProvinces must be a non-empty array")
require(len(allowed_provinces) == len(set(allowed_provinces)), "allowedProvinces must not contain duplicates")
require(set(allowed_provinces) <= province_ids, f"unknown allowed province(s): {set(allowed_provinces) - province_ids}")
context_union = {context for succession in selected for context in succession.get("contexts", [])}
require(set(allowed_provinces) <= context_union, "allowed provinces must be declared contexts of the target succession")

expected_lithologies = {
    bed["lithology"]
    for succession in selected
    for bed in succession.get("beds", [])
}
superseded = experiment.get("supersededLithologies")
require(isinstance(superseded, list) and superseded, "supersededLithologies must be a non-empty array")
require(len(superseded) == len(set(superseded)), "supersededLithologies must not contain duplicates")
require(set(superseded) == expected_lithologies,
        f"supersededLithologies must exactly match target succession lithologies; expected={sorted(expected_lithologies)} actual={sorted(superseded)}")
for lithology_id in superseded:
    require(lithology_id in lithologies, f"unknown superseded lithology: {lithology_id}")
    require(lithologies[lithology_id].get("rockClass") == "sedimentary", f"experiment may only supersede sedimentary lithologies: {lithology_id}")

boundary_distance = experiment.get("minimumBoundaryDistanceBlocks")
require(isinstance(boundary_distance, int) and 0 <= boundary_distance <= blend_width,
        f"minimumBoundaryDistanceBlocks must be within 0..{blend_width}")

biome_tag_path = namespaced_path(experiment.get("registrationBiomeTag"), "geostrata")
biome_tag_file = ROOT / "src/main/resources/data/geostrata/tags/worldgen/biome" / f"{biome_tag_path}.json"
require(biome_tag_file.is_file(), f"registration biome tag does not exist: {biome_tag_file}")

host_tag_path = namespaced_path(experiment.get("hostBlockTag"), "geostrata")
host_tag_file = ROOT / "src/main/resources/data/geostrata/tags/blocks" / f"{host_tag_path}.json"
require(host_tag_file.is_file(), f"host block tag does not exist: {host_tag_file}")

window = experiment.get("verticalWindow")
require(isinstance(window, dict), "verticalWindow must be an object")
require(window.get("anchor") == "sea_level", "initial correlated experiment must use sea_level vertical anchoring")
min_offset = window.get("minOffsetBlocks")
max_offset = window.get("maxOffsetBlocks")
require(isinstance(min_offset, int) and isinstance(max_offset, int), "vertical offsets must be integers")
require(-256 <= min_offset < max_offset <= 256, "vertical offsets must be ordered and stay within -256..256 blocks")
require(32 <= max_offset - min_offset <= 256, "vertical experiment span must be within 32..256 blocks")

print(
    "Validated disabled correlated sedimentary experiment: "
    f"{target_ids[0]}, {len(superseded)} superseded lithologies, "
    f"{min_offset:+d}..{max_offset:+d} from sea level"
)
