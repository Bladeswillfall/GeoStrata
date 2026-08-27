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

When the experiment is eventually enabled, the four named baseline features must be suppressed **only where the correlated experiment owns generation**. Outside the owned region, including province contacts and unsupported geological provinces, the current baseline remains authoritative. This creates a useful A/B testing surface and avoids globally removing proven generation while the correlated system is still experimental.

The loader exposes `suppressesBaselineLithology(...)` for that future integration, but current `StrataLensFeature` generation does not call it yet. Loading/diagnostics therefore cannot alter chunks.

## Ownership envelope

The first contract limits ownership to sedimentary-basin interiors and requires at least 96 blocks of distance from the nearest geological province boundary. Province-profile blending remains 192 blocks wide, so the experiment deliberately stays out of the most ambiguous half of the regional transition.

The experiment registers through `geostrata:has_common_rocks`, whose standalone default is the overworld. Geological ownership is then narrowed by the deterministic province/succession model rather than by hardcoded vanilla biome IDs.

The configured host material remains `geostrata:worldgen/base_stone_replaceables`. Correlated generation must therefore preserve the same terrain-mod compatibility seam as every existing GeoStrata rock body.

`/geostrata experiment` reports the loaded activation state. When disabled it shows the target/superseded scope; when enabled by a future datapack it reports whether the current X/Z is owned, the ownership reason, province, approximate boundary distance and selected succession. The command does not inspect or mutate blocks.

## Dormant feature implementation

`CorrelatedSedimentaryFeature` now implements the eventual chunk-local mutation path, but the placed feature is deliberately **not registered into any biome** yet. The feature type and configured/placed data can therefore compile and decode without creating a reachable generation path in ordinary worlds.

If it is later biome-registered and the experiment is enabled, the feature:

- evaluates ownership at the current chunk center;
- reuses the selected succession, contact plan and deterministic stratigraphic field;
- resolves output blocks only at the world-mutation boundary from the runtime lithology catalog;
- scans only the experiment's bounded sea-level-relative vertical window;
- replaces only blocks in `hostBlockTag`, preserving caves, ores and any material outside GeoStrata's host-stone contract;
- uses one coherent field for the whole chunk rather than independent random deposits.

The intended biome registration stage is `UNDERGROUND_DECORATION`, after vanilla's underground-ore stage. That sequencing is deliberate: vanilla ores already present in the chunk are not members of `base_stone_replaceables`, so the correlated pass preserves them instead of preventing their generation.

`scripts/validate_dormant_correlated_feature.py` enforces the staging boundary. CI currently requires the type/data to exist while rejecting any biome-worldgen reference or early baseline suppression. The next activation PR must change those assertions atomically rather than allowing one side of the handoff to land alone.

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

The Java reload parser mirrors the important cross-resource safety checks for datapack overrides. Unit tests pin disabled-state behavior, explicit activation status, full suppression coverage, deterministic owned-region evaluation and province-boundary exclusion.

## Activation sequence

The implementation stages are intentionally separate:

1. **complete** — load and validate this contract in Java;
2. **complete** — expose a diagnostic showing whether the current position/chunk is owned by the experiment;
3. **complete but intentionally unreachable** — implement/register the correlated feature type and data while keeping the placed feature out of biome worldgen;
4. atomically register the correlated placed feature at `UNDERGROUND_DECORATION` and make existing superseded lens features suppress themselves only inside the same owned chunks;
5. ship a separate experimental datapack that flips `enabled` and `runtimeStatus` together;
6. evaluate fresh-world abundance, contacts, cave/cliff exposure, performance and compatibility before considering any default activation.

The standalone default remains unchanged until those stages have demonstrated that the correlated generator is strictly better than the baseline.
