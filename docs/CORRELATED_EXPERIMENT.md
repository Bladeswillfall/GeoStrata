# Correlated sedimentary experiment

GeoStrata carries an experimental correlated sedimentary generator without making it part of standalone worldgen. The normal mod remains on the proven baseline unless the separate `experiment-companion` artifact explicitly activates and biome-registers the correlated consumer.

`data/geostrata/geology/correlated_sedimentary_experiment.json` is the staging contract. Core ships it as `metadata_only` with `enabled: false`. The companion does **not** replace that resource. Instead it supplies the unique `geostrata:geology/correlated_sedimentary_activation.json` marker; core validates the marker and promotes its already-validated snapshot to `experimental_runtime`.

Using a separate marker path is deliberate. Fabric mod-resource ordering is not a safe contract for same-path overrides, so activation must not depend on which mod resource wins load order.

Parsing and cross-resource validation live in `CorrelatedSedimentaryExperimentParser`. `CorrelatedExperimentActivation` validates the optional marker. `CorrelatedSedimentaryExperiment` owns reload lifecycle and runtime ownership evaluation.

## First experiment scope

The first target is `basin_mudrock_carbonate_cycle`: limestone, shale, mudstone and siltstone. Those four rocks already use GeoStrata's `strata_lens` baseline. Chalk, conglomerate and breccia remain entirely baseline during this experiment.

When activation is present, a single chunk-normalized ownership decision controls both sides of the handoff:

- `CorrelatedSedimentaryFeature` mutates only experiment-owned chunks;
- superseded `StrataLensFeature` bodies do not start inside owned chunks;
- a lens starting in a baseline chunk clips candidate blocks that cross into an owned chunk.

`CorrelatedExperimentChunkOwnership` maps every X/Z coordinate, including negative coordinates, to the center of its 16x16 chunk before invoking the canonical experiment evaluator. This prevents random placed-feature origins and cross-boundary candidates from disagreeing about ownership.

With the bundled disabled contract and no marker, the lens suppression fast path is inactive and ordinary core generation does not perform destination ownership checks.

## Standalone-safe activation boundary

Core registers the `geostrata:correlated_sedimentary` feature type and ships configured/placed feature data, but `GeoStrataWorldgen` deliberately does **not** add the correlated placed feature to any biome.

That distinction protects compatibility. Adding even a no-op feature to biome decoration can change feature ordering and decoration seeding. Merely installing standalone GeoStrata therefore does not insert the experimental consumer into another terrain mod's generation pipeline.

The `experiment-companion` subproject builds a separate Fabric jar. It depends on GeoStrata, registers `geostrata:correlated_sedimentary_experiment` through `geostrata:has_common_rocks` at `UNDERGROUND_DECORATION`, and supplies the unique activation marker. Installing it is an explicit request to alter worldgen behavior; removing it leaves the base GeoStrata jar independently usable in ordinary Fabric or other modpacks.

The companion is intentionally **not** part of the normal development-pack dependency set. Until the generator has been evaluated in-game, use it only for fresh or disposable experiment worlds.

## Ownership envelope

The first contract owns only sedimentary-basin interiors at least 96 blocks from the nearest province boundary. Province blending is 192 blocks wide, so the experiment avoids the most ambiguous transition area.

The mutation host is still `geostrata:worldgen/base_stone_replaceables`. Third-party terrain blocks can participate through GeoStrata's public replacement tag instead of a hard dependency.

`/geostrata experiment` reports activation and ownership state without inspecting or modifying blocks.

## Correlated mutation

When the companion is installed and the experiment owns the chunk, `CorrelatedSedimentaryFeature`:

- uses shared chunk-center ownership;
- reuses the selected succession, contact plan and deterministic stratigraphic field;
- resolves output blocks from the runtime lithology catalog only at the mutation boundary;
- scans only the bounded sea-level-relative experiment window;
- replaces only `hostBlockTag` members, preserving caves, ores and other non-host material;
- uses one coherent field across the chunk instead of independent random deposits.

The companion registers at `UNDERGROUND_DECORATION`, after vanilla underground ores. Vanilla ore blocks are not members of `base_stone_replaceables`, so the correlated replacement pass preserves them.

The current vertical window is 96 blocks below to 48 blocks above sea level. It is a bounded experimental mutation envelope, not a permanent geological law; lithology `depthAffinity` remains qualitative and terrain-agnostic.

## Validation

`scripts/validate_correlated_sedimentary_experiment.py` validates the geological/data contract. `scripts/validate_correlated_core_staging.py` validates the standalone boundary. `scripts/validate_correlated_companion.py` requires the separate artifact, unique activation marker and biome registration while explicitly forbidding a same-path experiment override and confirming standalone core remains unregistered and disabled.

Java tests cover activation-marker promotion, malformed markers, suppression scope, deterministic ownership, province-boundary exclusion and positive/negative chunk-center normalization. Both core and companion production Java are subject to the repository-wide PMD cyclomatic and cognitive complexity ceiling of 20 with no grandfathered methods.

## Activation sequence

1. **complete** — load and validate the experiment contract.
2. **complete** — expose deterministic ownership diagnostics.
3. **complete** — implement/register correlated feature type and data while keeping it unreachable from standalone biome worldgen.
4. **complete** — prepare activation-gated baseline suppression and cross-chunk clipping using shared ownership.
5. **complete as an optional artifact** — build the separate companion that biome-registers the correlated placed feature and supplies a load-order-independent activation marker.
6. **next** — evaluate fresh-world abundance, contacts, cave/cliff exposure, performance and compatibility before considering wider distribution.

Standalone GeoStrata remains on the baseline unless the companion is deliberately installed.
