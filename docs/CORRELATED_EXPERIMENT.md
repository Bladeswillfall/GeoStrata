# Correlated sedimentary experiment

GeoStrata now has enough deterministic geology infrastructure to attempt correlated sedimentary worldgen, but the standalone generator remains on the proven baseline until an optional experiment companion is deliberately installed.

`data/geostrata/geology/correlated_sedimentary_experiment.json` defines the core staging contract. The bundled resource remains `metadata_only` and `enabled: false`. `CorrelatedSedimentaryExperiment` loads and validates that contract as server data; an activating overlay must explicitly pair `enabled: true` with `runtimeStatus: experimental_runtime`.

## First experiment scope

The initial experiment targets only `basin_mudrock_carbonate_cycle`: limestone, shale, mudstone and siltstone. Those are exactly the four sedimentary rocks already migrated to the tested `strata_lens` implementation. Chalk, conglomerate and breccia stay on their baseline features.

When activation is present, the four superseded lens lithologies are suppressed only in chunks owned by the correlated experiment. Outside that ownership envelope the baseline remains authoritative.

## Standalone-safe activation boundary

The core jar registers the `correlated_sedimentary` **feature type** and ships its configured/placed feature data, but `GeoStrataWorldgen` deliberately does not add that placed feature to any biome. This distinction protects standalone world determinism: Minecraft seeds decoration using a feature's generation step and index, so inserting even a no-op placed feature can perturb the decoration seeds of later features.

Instead, core prepares the other half of the handoff. `CorrelatedExperimentChunkOwnership` maps every X/Z coordinate to the center of its 16×16 chunk before consulting the canonical experiment evaluator. `StrataLensFeature` uses this adapter only when the experiment is both enabled and the lithology is in the superseded set.

If an owned chunk is active, a superseded lens whose origin lies there returns without generating. A lens originating in a neighboring baseline chunk also clips candidate blocks that would cross into the owned chunk. That destination-chunk check prevents old-style lenses leaking across ownership boundaries. With bundled core data (`enabled: false`), the suppression fast path is false and no per-block ownership checks run.

The eventual biome registration and activation therefore belong to a separate experimental companion artifact, not to standalone core. Installing that companion is an explicit request to alter worldgen ordering and generation behavior.

## Ownership envelope

The first contract limits ownership to sedimentary-basin interiors and requires at least 96 blocks from the nearest geological province boundary. Province-profile blending remains 192 blocks wide, so the experiment stays out of the most ambiguous half of the regional transition.

The future companion registers through `geostrata:has_common_rocks`, whose standalone default is the overworld. The actual ownership decision is then narrowed by deterministic province and succession selection rather than vanilla biome IDs.

The mutation host remains `geostrata:worldgen/base_stone_replaceables`, preserving GeoStrata's terrain-mod compatibility seam. `/geostrata experiment` reports the loaded activation/ownership state without inspecting or changing blocks.

## Correlated feature implementation

`CorrelatedSedimentaryFeature` is the chunk-local mutation consumer. If it becomes reachable through the optional companion and the experiment owns the current chunk, it:

- uses the shared chunk-center ownership adapter;
- reuses the selected succession, normalized contacts and deterministic stratigraphic field;
- resolves GeoStrata output blocks only at the mutation boundary through the runtime lithology catalog;
- scans only the bounded sea-level-relative vertical window;
- replaces only `hostBlockTag` members, preserving caves, ores and other non-host blocks;
- uses one coherent stratigraphic field for the whole chunk.

The companion will register this placed feature at `UNDERGROUND_DECORATION`, after vanilla's underground-ore stage. Vanilla ore blocks are not members of `base_stone_replaceables`, so the correlated pass preserves them.

`scripts/validate_correlated_core_staging.py` enforces the standalone boundary: the feature type/data and chunk-normalized suppression preparation must exist, but core biome registration and core activation must remain absent.

## Vertical staging

The first experimental mutation window is sea-level-relative, from 96 blocks below to 48 blocks above. This is a bounded test envelope, not a permanent geological law. `depthAffinity` remains qualitative and terrain-agnostic.

## Biome affinity is not stratigraphic permission

A lithology catalog entry's `biomeTag` records baseline feature affinity, not a universal subsurface rule. Siltstone, for example, is not forbidden below non-river biomes merely because its current baseline feature is fluvial. Correlated subsurface ownership comes from the geological succession; biome affinity can remain a separate exposure, weathering or deposition concern.

## Validation

`scripts/validate_correlated_sedimentary_experiment.py` cross-checks the core experiment against succession, lithology and province-profile resources. It rejects core runtime activation, invalid targets/contexts/suppression sets, non-sedimentary targets, invalid boundary distances, missing GeoStrata extension tags and malformed vertical windows.

The Java reload parser mirrors cross-resource safety checks for data overlays. Unit tests cover disabled state, explicit activation status, full suppression scope, deterministic owned-region evaluation, province-boundary exclusion and chunk-center normalization.

## Activation sequence

1. **complete** — load and validate the experiment contract;
2. **complete** — expose ownership diagnostics;
3. **complete** — implement/register the correlated feature type and data while keeping it unreachable from standalone biome worldgen;
4. **complete in core staging** — prepare activation-gated lens suppression and cross-chunk clipping using shared ownership;
5. build a separate resource/marker companion that conditionally causes biome registration and provides the explicit activation overlay;
6. evaluate fresh-world abundance, contacts, cave/cliff exposure, performance and compatibility before considering any broader distribution.

Standalone GeoStrata remains on the baseline unless the experimental companion is installed.
