#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONFIGURED = ROOT / "src/main/resources/data/geostrata/worldgen/configured_feature/correlated_sedimentary_experiment.json"
PLACED = ROOT / "src/main/resources/data/geostrata/worldgen/placed_feature/correlated_sedimentary_experiment.json"
EXPERIMENT = ROOT / "src/main/resources/data/geostrata/geology/correlated_sedimentary_experiment.json"
FEATURES_JAVA = ROOT / "src/main/java/com/geostrata/worldgen/feature/GeoStrataFeatures.java"
FEATURE_JAVA = ROOT / "src/main/java/com/geostrata/worldgen/feature/CorrelatedSedimentaryFeature.java"
WORLDGEN_JAVA = ROOT / "src/main/java/com/geostrata/worldgen/GeoStrataWorldgen.java"
LENS_JAVA = ROOT / "src/main/java/com/geostrata/worldgen/feature/StrataLensFeature.java"
OWNERSHIP_JAVA = ROOT / "src/main/java/com/geostrata/geology/CorrelatedExperimentChunkOwnership.java"


def load(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def require(condition: bool, message: str):
    if not condition:
        raise SystemExit(message)


configured = load(CONFIGURED)
placed = load(PLACED)
experiment = load(EXPERIMENT)
features_source = FEATURES_JAVA.read_text(encoding="utf-8")
feature_source = FEATURE_JAVA.read_text(encoding="utf-8")
worldgen_source = WORLDGEN_JAVA.read_text(encoding="utf-8")
lens_source = LENS_JAVA.read_text(encoding="utf-8")
ownership_source = OWNERSHIP_JAVA.read_text(encoding="utf-8")

require(configured == {"type": "geostrata:correlated_sedimentary", "config": {}},
        "correlated configured feature must remain the default-config GeoStrata feature")
require(placed.get("feature") == "geostrata:correlated_sedimentary_experiment",
        "correlated placed feature must point to its configured feature")
require(placed.get("placement") == [],
        "correlated placed feature must run once through biome feature registration without random placement modifiers")

require(experiment.get("enabled") is False,
        "core correlated experiment must remain disabled after the atomic handoff")
require(experiment.get("runtimeStatus") == "metadata_only",
        "core correlated experiment must remain metadata_only after the atomic handoff")

require("GeoStrata.id(\"correlated_sedimentary\")" in features_source,
        "GeoStrataFeatures must register the correlated feature type")
require("!experiment.enabled()" in feature_source,
        "correlated feature must fail closed when the experiment is disabled")
require("CorrelatedExperimentChunkOwnership.ownershipForChunk" in feature_source,
        "correlated feature must use shared chunk-normalized ownership")

require("correlated_sedimentary_experiment" in worldgen_source,
        "correlated placed feature must be registered into biome worldgen in the handoff")
require("GenerationStep.Feature.UNDERGROUND_DECORATION" in worldgen_source,
        "correlated placed feature must run at UNDERGROUND_DECORATION after vanilla ores")
require("BiomeSelectors.tag(HAS_COMMON_ROCKS)" in worldgen_source,
        "initial correlated registration must use the GeoStrata common-rock biome seam")

require("CorrelatedExperimentChunkOwnership.suppressesBaselineLithology" in lens_source,
        "baseline strata lenses must use the same ownership service for suppression")
require("Math.floorDiv" in ownership_source and "CHUNK_CENTER_OFFSET" in ownership_source,
        "chunk ownership adapter must normalize coordinates with floor division")
require("ownershipAt(" in ownership_source,
        "chunk ownership adapter must delegate to the canonical experiment ownership evaluator")

print(
    "Validated atomic correlated handoff: later-stage registration + shared chunk ownership + "
    "baseline suppression are wired while core activation remains disabled"
)
