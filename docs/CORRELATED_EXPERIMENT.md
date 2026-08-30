# Correlated sedimentary experiment

GeoStrata carries an experimental correlated geology consumer without making it part of standalone worldgen. The normal mod remains on the compatibility baseline unless the separate `experiment-companion` artifact explicitly activates and biome-registers the correlated feature.

`data/geostrata/geology/correlated_sedimentary_experiment.json` is the contract. Core ships schema 2 as `metadata_only` with `enabled: false`. Fabric mod presence for `geostrata_correlated_experiment` promotes that already-validated snapshot to `experimental_runtime`; the companion does not replace the JSON resource.

## Current experiment scope

The experiment currently targets four succession models:

- `basin_mudrock_carbonate_cycle`;
- `shelf_chalk_carbonate_cycle`;
- `rift_fining_upward_clastics`;
- `orogenic_fan_fining_upward`.

The allowed provinces are sedimentary basin, rift province and orogenic belt. `CorrelatedSedimentaryExperimentParser` validates every target and requires `supersededLithologies` to equal the exact union of target-bed lithologies: limestone, chalk, shale, mudstone, siltstone, breccia and conglomerate.

All seven have coherent `StrataLensFeature` fallbacks. When the experiment owns a chunk those independent sedimentary lenses are suppressed and the shared correlated field becomes authoritative there. Basalt and rhyolite remain independent igneous bodies and may cut the correlated strata.

## Shared ownership handoff

One chunk-normalized ownership decision controls both sides of the handoff:

- `CorrelatedSedimentaryFeature` mutates only experiment-owned chunks;
- superseded fallback lenses do not start in owned chunks;
- a fallback lens starting outside an owned chunk clips candidate blocks that cross into it.

`CorrelatedExperimentChunkOwnership` normalizes X/Z to the owning 16×16 chunk before evaluating province, boundary distance and succession. Generation order therefore cannot make neighboring feature invocations disagree about ownership.

Owned chunks must select an allowed target succession and remain at least 96 blocks from the nearest province boundary. Province blending is 192 blocks wide, so the experiment deliberately avoids the most ambiguous transition zone during testing.

## Province background matrix

The companion also runs a late background pass after the correlated feature. Its purpose is to ensure that otherwise-unclaimed natural host stone is still interpreted as GeoStrata geology instead of remaining vanilla stone.

This is not a single-rock province fill. For each geological province the pass ranks the ordinary strata-lens lithologies by the existing `province_profiles.json` suitability weights and uses the strongest four as a repeated regional sequence. Event-only pipe lithologies such as kimberlite and lamproite are excluded because their baseline features are not ordinary strata lenses.

The sequence reuses the existing regional stratigraphic field, contact planner and active-terrain structural transform. As a result, the base matrix has coherent dipped/warped contacts and the same province-specific terrain response rather than per-block noise or biome-driven stone selection.

Typical current palettes are:

- sedimentary basin: limestone, shale, mudstone, siltstone;
- cratonic shield: gneiss, schist, quartzite, slate;
- orogenic belt: schist, quartzite, gneiss, slate;
- volcanic arc: basalt, rhyolite, breccia, gneiss;
- rift province: basalt, conglomerate, shale, siltstone.

The background pass only mutates `geostrata:worldgen/base_stone_replaceables`. Correlated strata and earlier GeoStrata bodies are already non-host blocks and therefore remain authoritative. Ores, caves, fluids and unrelated blocks are untouched. Known structure-piece bounding volumes are skipped so structure-placed raw stone is not reinterpreted as terrain geology.

This background matrix exists only in the opt-in companion while the world-level geology model is being visually validated. Standalone GeoStrata continues to use the conservative compatibility fallback.

## Dimension-relative vertical domain

Schema 2 replaces the old sea-level-relative test window with:

```json
"verticalDomain": "dimension_bounds"
```

The correlated pass therefore clamps against the active world's actual `bottomY` and `topY`, not vanilla Y coordinates and not sea level ± fixed offsets. A taller or deeper dimension exposes more of the same stratigraphic field; GeoStrata does **not** multiply cycle thickness, bed thickness, warp wavelength or metamorphic band scale merely because the dimension is taller.

The mutation predicate still controls what can actually change. Normal correlated generation replaces only `geostrata:worldgen/base_stone_replaceables`, so bedrock, caves, fluids, structures, ores and unrelated blocks remain untouched. Third-party natural stone can participate by extending that tag from a compatibility datapack.

The experimental ore resolver uses the same correlated vertical domain when it needs the virtual host identity beneath an ore body, preventing deep or high ore candidates from falling back to a second height model.

## Terrain-mod response

`ChunkGeneratorTerrainMorphologySampler` asks the **active** chunk generator for `OCEAN_FLOOR_WG` heights. GeoStrata therefore sees terrain produced by compatible terrain mods without hard-coding their biome IDs, mountain heights or world presets.

The structural field samples that terrain on a fixed 128-block world grid and interpolates between shared corners. Province archetypes decide how strongly the stratigraphy responds:

- stable cratonic and basin settings drape only weakly;
- volcanic arcs and rifts respond moderately;
- orogenic belts respond most strongly.

Positive terrain prominence remains strong ridge/uplift evidence. Increasingly negative prominence is treated primarily as erosional evidence: both drape and prominence-driven folding are smoothly attenuated, down to 20% of their normal province response. A deep ravine should therefore cut through and expose existing strata rather than pull the geological field down to follow the ravine floor.

Fold amplification remains capped at 25% of one succession cycle. Extreme mountains can expose uplifted/deformed geology without stretching ordinary bed thickness to match mountain height.

## Correlated mutation

When the companion is installed and the experiment owns a chunk, `CorrelatedSedimentaryFeature`:

- reuses the selected succession, contact plan and deterministic stratigraphic field;
- samples the active terrain generator through the shared terrain patch;
- applies province-aware drape/open-fold response with erosional attenuation;
- evaluates the field across the active dimension's real vertical bounds;
- resolves output blocks only at the world-mutation boundary;
- preserves caves and non-host material;
- uses one coherent field across chunk boundaries instead of independent random deposits.

## Orogenic metamorphism

Orogenic experiment chunks consume the staged metamorphic model inside the same correlated pass rather than registering another competing feature.

`CorrelatedSedimentaryRuntime.TerrainAwareSite.outputLithology(...)` first resolves the sedimentary parent bed. In an owned orogenic belt:

- mudrock parents may become slate, schist or gneiss according to the existing metamorphic intensity/band model;
- carbonate parents may become marble using the same band ownership decision;
- other parents remain unchanged.

Quartzite remains a coherent fallback body because GeoStrata does not yet define the quartz-rich sandstone parent needed for a legitimate parent-derived rule. Do not invent that parent relationship solely to eliminate the fallback.

Inside owned orogenic chunks the correlated pass may also replace earlier GeoStrata metamorphic fallback blocks with the authoritative parent-derived result. Graded ore blocks are unaffected because they are not lithology-catalog rock entries.

## Exact runtime inspection

`CorrelatedSedimentaryRuntime` is the shared resolver for mutation and diagnostics. It evaluates ownership, succession, contact plan, structural field and terrain transform from the same deterministic inputs.

`/geostrata experiment` reports ownership, field lithology, actual block, cycle position, structural offsets and the effective mutation domain. Because the domain is dimension-relative, that reported range should match the current world's bottom through top-1.

`/geostrata terrain`, `/geostrata field` and `/geostrata metamorphism` remain read-only diagnostics for inspecting the terrain evidence and geological response.

## Determinism

The experiment follows `docs/DETERMINISM.md`:

- province and succession selection derive from world seed and stable site coordinates;
- chunk ownership is normalized before evaluation;
- structural fields and metamorphic bands use stable salts and coordinates;
- terrain evidence comes from the unchanged active deterministic chunk generator;
- no first-visited state, runtime UUID or process-local random source creates geological identity.

Changing the terrain generator, GeoStrata version, datapacks or other worldgen inputs is still a changed generation input. With identical inputs and seed, the experiment is reproducible.

## Validation and status

`GeologyResourceContractTest` validates the bundled graph, requires natural-rock fallback placements to use dimension-relative vertical anchors, checks the full-dimension correlated contract and verifies companion staging. Behavior tests cover deterministic ownership, structural interpolation, erosional attenuation, metamorphic band selection and parent-rock boundaries.

The remaining work is empirical rather than architectural: evaluate abundance, contacts, cliff/ravine exposure, extreme-height terrain, performance and compatibility in fresh worlds before considering promotion into standalone worldgen.

Standalone GeoStrata remains on the fallback baseline unless the companion is deliberately installed.
