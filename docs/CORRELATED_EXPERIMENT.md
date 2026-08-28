# Correlated sedimentary experiment

GeoStrata carries an experimental correlated geology consumer without making it part of standalone worldgen. The normal mod remains on the compatibility baseline unless the separate `experiment-companion` artifact explicitly activates and biome-registers the correlated feature.

`data/geostrata/geology/correlated_sedimentary_experiment.json` is the contract. Core ships it as `metadata_only` with `enabled: false`. The companion does **not** replace that resource; Fabric mod presence for `geostrata_correlated_experiment` promotes the already-validated snapshot to `experimental_runtime`.

Activation therefore does not depend on resource-pack ordering or a second JSON protocol.

## Current experiment scope

The experiment currently targets two existing succession models:

- `basin_mudrock_carbonate_cycle` in sedimentary-basin interiors;
- `orogenic_fan_fining_upward` in orogenic-belt interiors.

The target set is deliberately expressed as data. `CorrelatedSedimentaryExperimentParser` validates every target ID, validates each allowed province against at least one target context, and requires `supersededLithologies` to equal the exact union of target-bed lithologies.

The current union is limestone, shale, mudstone, siltstone, breccia and conglomerate. All six baseline features now use `StrataLensFeature`, so they share the same experiment-aware suppression path.

The orogenic succession uses the already-loaded `local` field profile; the basin succession uses the `regional` profile. No second structural generator is needed.

## Shared ownership handoff

When the companion is loaded, one chunk-normalized ownership decision controls both sides of the handoff:

- `CorrelatedSedimentaryFeature` mutates only experiment-owned chunks;
- superseded `StrataLensFeature` bodies do not start inside owned chunks;
- a lens starting in a baseline chunk clips candidate blocks that cross into an owned chunk.

`CorrelatedExperimentChunkOwnership` maps every X/Z coordinate, including negative coordinates, to the center of its 16×16 chunk before invoking the canonical evaluator. Random placed-feature origins and cross-boundary candidates therefore cannot disagree about ownership.

Without the companion, the suppression fast path is inactive and ordinary core generation does not perform destination ownership checks.

## Standalone-safe activation boundary

Core registers the `geostrata:correlated_sedimentary` feature type and ships configured/placed feature data, but `GeoStrataWorldgen` deliberately does **not** add the correlated placed feature to any biome.

That distinction protects compatibility. Adding even a no-op feature to biome decoration can change feature ordering and decoration seeding. Merely installing standalone GeoStrata therefore does not insert the experimental consumer into another terrain mod's generation pipeline.

The `experiment-companion` subproject registers `geostrata:correlated_sedimentary_experiment` through `geostrata:has_common_rocks` at `UNDERGROUND_DECORATION`. `has_common_rocks` defaults to `#minecraft:is_overworld`, so province/succession ownership—not biome registration—is the narrow geological mutation gate.

The companion is intentionally not part of the normal development-pack dependency set. Use it only for fresh/disposable experiment worlds until the correlated generator is promoted.

## Ownership envelope

Owned chunks must be in one of the allowed provinces, must select one of the target successions, and must be at least 96 blocks from the nearest geological province boundary.

Province blending is 192 blocks wide, so this avoids the most ambiguous transition area during testing.

The ordinary mutation host remains `geostrata:worldgen/base_stone_replaceables`. Third-party terrain blocks can participate through that public tag instead of a hard dependency.

## Correlated mutation

When the companion is installed and the experiment owns a chunk, `CorrelatedSedimentaryFeature`:

- uses shared chunk-center ownership;
- reuses the selected succession, contact plan and deterministic base stratigraphic field;
- samples the active terrain generator on the shared 128-block terrain grid;
- applies the existing province-aware drape/open-fold transform;
- resolves output blocks from the runtime lithology catalog only at the mutation boundary;
- scans only the bounded sea-level-relative experiment window;
- preserves caves, ordinary ores and other non-host material;
- uses one coherent field across the chunk instead of independent random deposits.

The current mutation window is 96 blocks below to 48 blocks above sea level. It is an experimental envelope, not a permanent geological depth law.

## Orogenic metamorphism

Orogenic experiment chunks also consume the staged metamorphic model without registering a second feature.

`CorrelatedSedimentaryRuntime.TerrainAwareSite.outputLithology(...)` first resolves the virtual sedimentary parent bed. If the owned province is an orogenic belt and the parent lithology's catalog `genesis` is `mudrock`, the same structural vertical offset and cycle scale feed `MetamorphicBandPlanner`, while `MetamorphicIntensityField` supplies slate/schist/gneiss suitability.

Non-mudrock beds remain their correlated sedimentary lithology.

The terrain evidence for metamorphic intensity is interpolated from the exact terrain patch already owned by the structural field, so metamorphism does not resample the chunk generator independently.

### Legacy metamorphic placeholders

The old standalone slate, marble, quartzite, schist and gneiss baseline features run earlier in ordinary underground generation and are not part of the sedimentary suppression union.

Inside an **owned orogenic experiment chunk only**, the correlated pass therefore also treats an existing GeoStrata lithology-catalog block whose `rockClass` is `metamorphic` as replaceable legacy placeholder material. Its position is rewritten from the authoritative correlated parent/output model.

This removes contradictory random metamorphic blobs from the test area without globally disabling them. Graded ore blocks are unaffected because they are not lithology-catalog rock entries.

Marble and quartzite are not yet emitted by the correlated model; they need explicit carbonate/quartz-rich parent rules rather than intensity-only placement.

## Exact runtime inspection

`CorrelatedSedimentaryRuntime` remains the single resolver for both experimental mutation and diagnostics. It normalizes the requested position to the owning chunk center, evaluates ownership, selects the succession, plans contacts, constructs the site-anchored field and applies the terrain transform.

`/geostrata experiment` reports ownership, the terrain-adjusted **parent field** lithology, actual block, cycle position, terrain/fold offset and whether the point is inside the mutation window. In an orogenic mudrock bed, the actual block may intentionally be slate/schist/gneiss rather than the reported sedimentary parent field.

`/geostrata metamorphism` remains the separate read-only diagnostic for inspecting broad metamorphic intensity and suitability.

## Determinism

The experiment follows `docs/DETERMINISM.md`:

- province and succession selection are functions of world seed and stable site coordinates;
- chunk ownership is normalized before evaluation;
- structural fields and metamorphic bands use stable salts and coordinates;
- terrain evidence comes from the unchanged active deterministic chunk generator;
- no first-visited state, runtime UUID or process-local random source creates geological identity.

Changing GeoStrata/mod/terrain-generator versions, datapacks or worldgen configuration is still a changed generation input. With identical generation inputs and seed, the experiment is reproducible.

## Validation

`GeologyResourceContractTest` parses the shipped geology graph, checks disabled and companion-present activation states, decodes worldgen resources and validates companion metadata.

Behavior/regression tests cover deterministic ownership, chunk normalization, structural interpolation, metamorphic band selection, parent-rock boundaries and the standalone activation boundary. Production Java remains subject to the repository's PMD complexity ceiling.

## Activation sequence

1. **complete** — load and validate the correlated experiment contract.
2. **complete** — expose deterministic ownership diagnostics.
3. **complete** — register the dormant core feature and keep it unreachable from standalone biome worldgen.
4. **complete** — coordinate baseline lens suppression and cross-chunk clipping.
5. **complete** — provide the separate opt-in companion artifact.
6. **complete** — extend the same experiment to coherent orogenic parent strata.
7. **experimental runtime** — derive slate/schist/gneiss from orogenic mudrock parents inside the same correlated pass.
8. **in progress** — evaluate abundance, contacts, cave/cliff exposure, performance and compatibility in fresh worlds before considering wider activation.

Standalone GeoStrata remains on the baseline unless the companion is deliberately installed.
