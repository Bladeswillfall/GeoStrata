#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE_CONTRACT = ROOT / "src/main/resources/data/geostrata/geology/correlated_sedimentary_experiment.json"
COMPANION_ROOT = ROOT / "experiment-companion"
COMPANION_CONTRACT = COMPANION_ROOT / "src/main/resources/data/geostrata/geology/correlated_sedimentary_experiment.json"
COMPANION_METADATA = COMPANION_ROOT / "src/main/resources/fabric.mod.json"
COMPANION_JAVA = COMPANION_ROOT / "src/main/java/com/geostrata/experiment/CorrelatedExperimentCompanion.java"
COMPANION_BUILD = COMPANION_ROOT / "build.gradle"
SETTINGS = ROOT / "settings.gradle"
CORE_WORLDGEN = ROOT / "src/main/java/com/geostrata/worldgen/GeoStrataWorldgen.java"


def load(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def require(condition: bool, message: str):
    if not condition:
        raise SystemExit(message)


core = load(CORE_CONTRACT)
companion = load(COMPANION_CONTRACT)
metadata = load(COMPANION_METADATA)
java_source = COMPANION_JAVA.read_text(encoding="utf-8")
build_source = COMPANION_BUILD.read_text(encoding="utf-8")
settings_source = SETTINGS.read_text(encoding="utf-8")
core_worldgen = CORE_WORLDGEN.read_text(encoding="utf-8")

require(core.get("enabled") is False and core.get("runtimeStatus") == "metadata_only",
        "standalone core experiment must remain disabled metadata")
require(companion.get("enabled") is True and companion.get("runtimeStatus") == "experimental_runtime",
        "companion overlay must explicitly activate experimental runtime")

shared_fields = (
    "schemaVersion",
    "model",
    "targetSuccessionIds",
    "allowedProvinces",
    "supersededLithologies",
    "minimumBoundaryDistanceBlocks",
    "registrationBiomeTag",
    "hostBlockTag",
    "verticalWindow",
)
for field in shared_fields:
    require(companion.get(field) == core.get(field),
            f"companion experiment field {field} must match the standalone core contract")

require(companion.get("registrationBiomeTag") == "geostrata:has_common_rocks",
        "first experiment companion must register through geostrata:has_common_rocks")
require(metadata.get("id") == "geostrata_correlated_experiment",
        "unexpected companion mod id")
require(metadata.get("depends", {}).get("geostrata", "").startswith(">="),
        "companion must declare GeoStrata as a required dependency")
require(metadata.get("entrypoints", {}).get("main") == [
            "com.geostrata.experiment.CorrelatedExperimentCompanion"
        ], "unexpected companion entrypoint")

require("include 'experiment-companion'" in settings_source,
        "settings.gradle must include the experiment companion subproject")
require("configuration: 'namedElements'" in build_source,
        "companion must compile against GeoStrata's Loom namedElements output")
require("config/pmd/geostrata-complexity.xml" in build_source,
        "companion production Java must use the repository PMD complexity ruleset")

require("BiomeSelectors.tag" in java_source and "has_common_rocks" in java_source,
        "companion must select the GeoStrata registration biome tag")
require("GenerationStep.Feature.UNDERGROUND_DECORATION" in java_source,
        "correlated experiment must register at underground decoration")
require("correlated_sedimentary_experiment" in java_source,
        "companion must register the correlated experiment placed feature")
require("correlated_sedimentary_experiment" not in core_worldgen,
        "standalone core must not register the correlated experiment placed feature")

print(
    "Validated correlated experiment companion: separate opt-in artifact, matching activation overlay, "
    "shared GeoStrata ownership contract and no standalone biome registration"
)
