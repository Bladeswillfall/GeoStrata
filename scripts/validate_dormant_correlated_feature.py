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

require(configured == {"type": "geostrata:correlated_sedimentary", "config": {}},
        "correlated configured feature must remain the default-config GeoStrata feature")
require(placed.get("feature") == "geostrata:correlated_sedimentary_experiment",
        "correlated placed feature must point to its configured feature")
require(placed.get("placement") == [],
        "dormant correlated placed feature must not add random placement modifiers")
require(experiment.get("enabled") is False,
        "core correlated experiment must remain disabled while the feature is dormant")
require("GeoStrata.id(\"correlated_sedimentary\")" in features_source,
        "GeoStrataFeatures must register the correlated feature type")
require("!experiment.enabled()" in feature_source,
        "correlated feature must fail closed when the experiment is disabled")
require("correlated_sedimentary_experiment" not in worldgen_source,
        "dormant correlated placed feature must not be registered into biome worldgen yet")
require("suppressesBaselineLithology" not in lens_source,
        "baseline strata lenses must not be suppressed before correlated biome registration is atomic")

print("Validated dormant correlated sedimentary feature: type/data present, biome registration and suppression absent")
