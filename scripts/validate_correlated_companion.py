#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE_CONTRACT = ROOT / "src/main/resources/data/geostrata/geology/correlated_sedimentary_experiment.json"
CORE_ACTIVATION_JAVA = ROOT / "src/main/java/com/geostrata/geology/CorrelatedExperimentActivation.java"
CORE_EXPERIMENT_JAVA = ROOT / "src/main/java/com/geostrata/geology/CorrelatedSedimentaryExperiment.java"
CORE_WORLDGEN = ROOT / "src/main/java/com/geostrata/worldgen/GeoStrataWorldgen.java"
COMPANION_ROOT = ROOT / "experiment-companion"
COMPANION_MARKER = COMPANION_ROOT / "src/main/resources/data/geostrata/geology/correlated_sedimentary_activation.json"
FORBIDDEN_OVERRIDE = COMPANION_ROOT / "src/main/resources/data/geostrata/geology/correlated_sedimentary_experiment.json"
COMPANION_METADATA = COMPANION_ROOT / "src/main/resources/fabric.mod.json"
COMPANION_JAVA = COMPANION_ROOT / "src/main/java/com/geostrata/experiment/CorrelatedExperimentCompanion.java"
COMPANION_BUILD = COMPANION_ROOT / "build.gradle"
SETTINGS = ROOT / "settings.gradle"


def load(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def require(condition: bool, message: str):
    if not condition:
        raise SystemExit(message)


core = load(CORE_CONTRACT)
marker = load(COMPANION_MARKER)
metadata = load(COMPANION_METADATA)
activation_source = CORE_ACTIVATION_JAVA.read_text(encoding="utf-8")
experiment_source = CORE_EXPERIMENT_JAVA.read_text(encoding="utf-8")
core_worldgen = CORE_WORLDGEN.read_text(encoding="utf-8")
java_source = COMPANION_JAVA.read_text(encoding="utf-8")
build_source = COMPANION_BUILD.read_text(encoding="utf-8")
settings_source = SETTINGS.read_text(encoding="utf-8")

require(core.get("enabled") is False and core.get("runtimeStatus") == "metadata_only",
        "standalone core experiment must remain disabled metadata")
require(not FORBIDDEN_OVERRIDE.exists(),
        "companion must not override the core experiment resource; mod resource ordering is not an activation contract")
require(marker == {
            "schemaVersion": 1,
            "model": "geostrata:correlated_sedimentary_activation",
            "experiment": "geostrata:correlated_sedimentary_experiment",
        }, "unexpected correlated activation marker")

require("correlated_sedimentary_activation.json" in experiment_source,
        "core experiment loader must look for the unique companion activation marker")
require("CorrelatedExperimentActivation.apply" in experiment_source,
        "core experiment loader must validate activation through the dedicated activation parser")
require("experimental_runtime" in activation_source and "metadata_only" in activation_source,
        "activation parser must explicitly promote disabled metadata to experimental runtime")

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

require(core.get("registrationBiomeTag") == "geostrata:has_common_rocks",
        "first experiment core contract must register through geostrata:has_common_rocks")
require("BiomeSelectors.tag" in java_source and "has_common_rocks" in java_source,
        "companion must select the core experiment registration biome tag")
require("GenerationStep.Feature.UNDERGROUND_DECORATION" in java_source,
        "correlated experiment must register at underground decoration")
require("correlated_sedimentary_experiment" in java_source,
        "companion must register the correlated experiment placed feature")
require("correlated_sedimentary_experiment" not in core_worldgen,
        "standalone core must not register the correlated experiment placed feature")

print(
    "Validated correlated experiment companion: separate opt-in artifact, unique activation marker, "
    "shared ownership contract and no standalone biome registration"
)
