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
        "correlated placed feature must not hide random placement semantics")

require(experiment.get("enabled") is False,
        "core correlated experiment must remain disabled")
require(experiment.get("runtimeStatus") == "metadata_only",
        "core correlated experiment must remain metadata_only")

require("GeoStrata.id(\"correlated_sedimentary\")" in features_source,
        "GeoStrataFeatures must register the correlated feature type")
require("!experiment.enabled()" in feature_source,
        "correlated feature must fail closed when the experiment is disabled")
require("CorrelatedExperimentChunkOwnership.ownershipForChunk" in feature_source,
        "correlated feature must use shared chunk-normalized ownership")

require("correlated_sedimentary_experiment" not in worldgen_source,
        "standalone core must not register the experimental placed feature into biome worldgen")

require("CorrelatedExperimentChunkOwnership.suppressionActiveFor" in lens_source,
        "baseline strata lenses must have an activation-gated suppression fast path")
require(lens_source.count("CorrelatedExperimentChunkOwnership.ownershipForChunk") >= 2,
        "baseline strata lenses must check both origin and destination chunk ownership")
require("mutable.getX()" in lens_source and "mutable.getZ()" in lens_source,
        "cross-chunk lens placement must clip candidate blocks entering owned chunks")

require("Math.floorDiv" in ownership_source and "CHUNK_CENTER_OFFSET" in ownership_source,
        "chunk ownership adapter must normalize coordinates with floor division")
require("experiment.enabled()" in ownership_source,
        "suppression fast path must require explicit experiment activation")
require("ownershipAt(" in ownership_source,
        "chunk ownership adapter must delegate to the canonical experiment ownership evaluator")

print(
    "Validated correlated core staging: feature type and chunk-normalized suppression are prepared, "
    "while standalone biome registration and activation remain absent"
)
