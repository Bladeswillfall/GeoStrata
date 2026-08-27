# Correlated sedimentary experiment

GeoStrata carries an experimental correlated sedimentary generator without making it part of standalone worldgen. The normal mod remains on the proven baseline unless the separate `experiment-companion` artifact explicitly activates and biome-registers the correlated consumer.

`data/geostrata/geology/correlated_sedimentary_experiment.json` is the staging contract. Core ships it as `metadata_only` with `enabled: false`. The companion does **not** replace that resource. Core uses Fabric Loader's native mod-presence check for `geostrata_correlated_experiment` to promote the validated snapshot to `experimental_runtime`.

Activation therefore does not depend on resource ordering or a second JSON protocol.

`GeologyDataReload` parses the geology resources once in dependency order and publishes them atomically. Cross-resource validation lives in `CorrelatedSedimentaryExperimentParser`. `CorrelatedSedimentaryExperiment` owns runtime ownership evaluation.

## First experiment scope

The first target is `basin_mudrock_carbonate_cycle`: limestone, shale, mudstone and siltstone. Those four rocks already use GeoStrata's `strata_lens` baseline. Chalk, conglomerate and breccia remain entirely baseline during this experiment.

When the companion is loaded, a single chunk-normalized ownership decision controls both sides of the handoff:

- `CorrelatedSedimentaryFeature` mutates only experiment-owned chunks;
- superseded `StrataLensFeature` bodies do not start inside owned chunks;
- a lens starting in a baseline chunk clips candidate blocks that cross into an owned chunk.

`CorrelatedExperimentChunkOwnership` maps every X/Z coordinate, including negative coordinates, to the center of its 16x16 chunk before invoking the canonical experiment evaluator. This prevents random placed-feature origins and cross-boundary candidates from disagreeing about ownership.

Without the companion, the lens suppression fast path is inactive and ordinary core generation does not perform destination ownership checks.

## Standalone-safe activation boundary

Core registers the `geostrata:correlated_sedimentary` feature type and ships configured/placed feature data, but `GeoStrataWorldgen` deliberately does **not** add the correlated placed feature to any biome.

That distinction protects compatibility. Adding even a no-op feature to biome decoration can change feature ordering and decoration seeding. Merely installing standalone GeoStrata therefore does not insert the experimental consumer into another terrain mod's generation pipeline.

The `experiment-companion` subproject builds a separate Fabric jar. It depends on GeoStrata and registers `geostrata:correlated_sedimentary_experiment` through `geostrata:has_common_rocks` at `UNDERGROUND_DECORATION`. Its Fabric mod ID is the activation signal. Installing it is an explicit request to alter worldgen behavior. Removing it leaves the base GeoStrata jar independently usable in ordinary Fabric or other modpacks.

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

## Exact runtime inspection

`CorrelatedSedimentaryRuntime` is the single resolver for both experimental worldgen and diagnostics. It normalizes the requested position to the owning chunk center, evaluates ownership, selects the succession, plans its contacts and constructs the site-anchored field once. This prevents diagnostics from sampling a point that the chunk-level generator did not own or from rebuilding a different field.

With the companion installed, `/geostrata experiment` now reports the exact field lithology at the command source, the actual block at that position, cycle position and whether the position is inside the active mutation window. The actual block is intentionally not required to match the field: caves, ores and all other non-host material are preserved by design. The command is an inspection aid for fresh experiment worlds, not a repair or replacement pass.

## Validation

`GeologyResourceContractTest` parses the shipped geology graph, checks disabled and companion-present activation states, decodes worldgen resources and validates companion metadata. Behavior tests cover the standalone boundary and ownership handoff.

Java tests cover native activation, suppression scope, deterministic ownership, province-boundary exclusion and positive/negative chunk-center normalization. Both core and companion production Java are subject to the repository-wide PMD cyclomatic and cognitive complexity ceiling of 20 with no grandfathered methods.

## Activation sequence

1. **complete** — load and validate the experiment contract.
2. **complete** — expose deterministic ownership diagnostics.
3. **complete** — implement/register correlated feature type and data while keeping it unreachable from standalone biome worldgen.
4. **complete** — prepare activation-gated baseline suppression and cross-chunk clipping using shared ownership.
5. **complete as an optional artifact**: build the separate companion whose native mod presence activates core and whose initializer registers the correlated placed feature.
6. **in progress** — exact runtime-field inspection is available; fresh-world abundance, contacts, cave/cliff exposure, performance and compatibility still need evaluation before considering wider distribution.

Standalone GeoStrata remains on the baseline unless the companion is deliberately installed.
