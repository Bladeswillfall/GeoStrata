# Correlated sedimentary experiment

GeoStrata now has enough deterministic geology infrastructure to attempt correlated sedimentary worldgen, but the standalone generator remains on the proven baseline until an experimental consumer is explicitly enabled.

`data/geostrata/geology/correlated_sedimentary_experiment.json` defines that staging contract. The core resource is deliberately `metadata_only` and `enabled: false`. A future experimental datapack may override the resource to activate the consumer; the normal GeoStrata jar must not silently switch world-generation modes.

## First experiment scope

The initial experiment targets only `basin_mudrock_carbonate_cycle`. Its unique lithologies are:

- limestone;
- shale;
- mudstone;
- siltstone.

Those are exactly the four sedimentary rocks already migrated from vanilla ore blobs to GeoStrata's tested `strata_lens` implementation. Chalk, conglomerate and breccia stay on their baseline features during this experiment.

When the experiment is eventually enabled, the four named baseline features must be suppressed **only where the correlated experiment owns generation**. Outside the owned region, including province contacts and unsupported geological provinces, the current baseline remains authoritative. This creates a useful A/B testing surface and avoids globally removing proven generation while the correlated system is still experimental.

## Ownership envelope

The first contract limits ownership to sedimentary-basin interiors and requires at least 96 blocks of distance from the nearest geological province boundary. Province-profile blending remains 192 blocks wide, so the experiment deliberately stays out of the most ambiguous half of the regional transition.

The experiment registers through `geostrata:has_common_rocks`, whose standalone default is the overworld. Geological ownership is then narrowed by the deterministic province/succession model rather than by hardcoded vanilla biome IDs.

The configured host material remains `geostrata:worldgen/base_stone_replaceables`. Correlated generation must therefore preserve the same terrain-mod compatibility seam as every existing GeoStrata rock body.

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

## Activation sequence

The next implementation stages are intentionally separate:

1. load and validate this contract in Java;
2. expose a diagnostic showing whether the current position/chunk is owned by the experiment;
3. implement a registered-but-disabled correlated feature that performs no work unless the contract is enabled;
4. make the existing sedimentary lens features suppress themselves only inside owned experimental regions;
5. ship a separate experimental datapack that flips `enabled` to true;
6. evaluate fresh-world abundance, contacts, cave/cliff exposure, performance and compatibility before considering any default activation.

The standalone default remains unchanged until those stages have demonstrated that the correlated generator is strictly better than the baseline.
