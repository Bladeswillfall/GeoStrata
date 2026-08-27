# Correlated sedimentary experiment

GeoStrata now has enough deterministic geology infrastructure to attempt correlated sedimentary worldgen, but the standalone generator remains on the proven baseline until an experimental consumer is explicitly enabled.

`data/geostrata/geology/correlated_sedimentary_experiment.json` defines that staging contract. The core resource is deliberately `metadata_only` and `enabled: false`. A future experimental datapack may override the resource to activate the consumer; the normal GeoStrata jar must not silently switch world-generation modes.

`CorrelatedSedimentaryExperiment` loads the contract as server data and independently revalidates its succession, lithology and province references for datapack overrides. Disabled data must declare `runtimeStatus: metadata_only`; an activating override must explicitly declare both `enabled: true` and `runtimeStatus: experimental_runtime`. This prevents a partial override from accidentally turning mutation on.

## First experiment scope

The initial experiment targets only `basin_mudrock_carbonate_cycle`. Its unique lithologies are:

- limestone;
- shale;
- mudstone;
- siltstone.

Those are exactly the four sedimentary rocks already migrated from vanilla ore blobs to GeoStrata's tested `strata_lens` implementation. Chalk, conglomerate and breccia stay on their baseline features during this experiment.

When the experiment is enabled, the four named baseline features are suppressed only where the correlated experiment owns generation. Outside the owned region, including province contacts and unsupported geological provinces, the current baseline remains authoritative. This creates a useful A/B testing surface and avoids globally removing proven generation while the correlated system is still experimental.

## Atomic ownership handoff

The correlated placed feature is now registered into `geostrata:has_common_rocks` at `UNDERGROUND_DECORATION`, while the superseded `strata_lens` features remain registered at `UNDERGROUND_ORES`. Both paths use `CorrelatedExperimentChunkOwnership`, which normalizes any block coordinate to the center of its 16×16 chunk before consulting the canonical experiment ownership evaluator.

This registration/suppression handoff is intentionally atomic. If the experiment owns a chunk, a superseded lens whose origin is in that chunk returns without generating. A lens originating in a neighboring baseline chunk also clips individual candidate blocks when they cross into an owned chunk. This prevents old-style lenses from leaking across a chunk boundary and surviving inside the correlated region simply because their origin was outside it.

The helper has an explicit activation fast path. With the bundled core resource (`enabled: false`), superseded lenses do not perform per-block ownership checks and the correlated feature itself immediately returns. Therefore the reachable feature registration does not change default chunk output.

The later `UNDERGROUND_DECORATION` stage is deliberate: vanilla ores have already generated, and those ore blocks are not members of `geostrata:worldgen/base_stone_replaceables`. The correlated pass therefore replaces natural host stone while preserving caves, ores and other non-host blocks.

## Ownership envelope

The first contract limits ownership to sedimentary-basin interiors and requires at least 96 blocks of distance from the nearest geological province boundary. Province-profile blending remains 192 blocks wide, so the experiment deliberately stays out of the most ambiguous half of the regional transition.

The experiment registers through `geostrata:has_common_rocks`, whose standalone default is the overworld. Geological ownership is then narrowed by the deterministic province/succession model rather than by hardcoded vanilla biome IDs.

The configured host material remains `geostrata:worldgen/base_stone_replaceables`. Correlated generation therefore preserves the same terrain-mod compatibility seam as every existing GeoStrata rock body.

`/geostrata experiment` reports the loaded activation state. When disabled it shows the target/superseded scope; when enabled by a future datapack it reports whether the current X/Z is owned, the ownership reason, province, approximate boundary distance and selected succession. The command does not inspect or mutate blocks.

## Correlated feature implementation

`CorrelatedSedimentaryFeature` is the chunk-local mutation consumer. When enabled and owned, it:

- evaluates ownership through the shared chunk-center adapter;
- reuses the selected succession, contact plan and deterministic stratigraphic field;
- resolves output blocks only at the world-mutation boundary from the runtime lithology catalog;
- scans only the experiment's bounded sea-level-relative vertical window;
- replaces only blocks in `hostBlockTag`, preserving caves, ores and any material outside GeoStrata's host-stone contract;
- uses one coherent field for the whole chunk rather than independent random deposits.

`scripts/validate_correlated_handoff.py` enforces the complete staging boundary. CI requires the later-stage registration, shared chunk ownership, destination-chunk clipping and baseline suppression to exist together while also requiring the bundled experiment to remain disabled and metadata-only.

## Vertical staging

The initial experimental window is expressed relative to sea level, from 96 blocks below to 48 blocks above. This is intentionally not a permanent geological rule. It is a bounded mutation envelope for the first test consumer so the generator does not scan or replace an entire dimension column.

The vertical window belongs to the experiment contract rather than the lithology catalog. Lithology `depthAffinity` remains qualitative and terrain-agnostic; a later geological model may replace this temporary bounded window with a richer crust/exposure system.

## Biome affinity is not stratigraphic permission

A lithology catalog entry's `biomeTag` records the current baseline feature affinity. It is useful for compatibility and legacy placement, but it must not be interpreted as a universal subsurface law. For example, siltstone is not geologically forbidden beneath non-river biomes merely because the baseline feature is targeted through `geostrata:has_fluvial_rocks`.

The correlated experiment therefore selects its succession from province geology and uses the succession's ordered beds as subsurface ownership. Surface/biome affinity can remain a separate exposure, weathering or deposition concern in later stages.

## Validation

`scripts/validate_correlated_sedimentary_experiment.py` cross-checks the experiment against the live succession, lithology and province-profile resources. CI rejects:

- runtime activation in the core resource;
- unknown or multiple first-stage succession targets;
- non-regional first-stage targets;
- allowed provinces not declared by the target succession;
- a superseded lithology set that differs from the target succession's beds;
- non-sedimentary superseded rocks;
- boundary exclusion wider than the province blend width;
- missing GeoStrata biome/host tags;
- malformed or excessively broad sea-level-relative vertical windows.

The Java reload parser mirrors the important cross-resource safety checks for datapack overrides. Unit tests pin disabled-state behavior, explicit activation status, full suppression coverage, deterministic owned-region evaluation, province-boundary exclusion and chunk-center normalization.

## Activation sequence

The implementation stages are intentionally separate:

1. **complete** — load and validate this contract in Java;
2. **complete** — expose a diagnostic showing whether the current position/chunk is owned by the experiment;
3. **complete** — implement/register the correlated feature type and data;
4. **complete while default-disabled** — register the correlated placed feature at `UNDERGROUND_DECORATION` and atomically suppress/clip superseded lenses in the same owned chunks;
5. ship a separate experimental datapack that flips `enabled` and `runtimeStatus` together;
6. evaluate fresh-world abundance, contacts, cave/cliff exposure, performance and compatibility before considering any default activation.

The standalone default remains unchanged until those stages have demonstrated that the correlated generator is strictly better than the baseline.
