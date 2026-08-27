# GeoStrata geology model

GeoStrata is moving from a collection of themed stone features toward a world-level geology system. This document defines the first live contract for that transition.

## Current runtime versus geological intent

The current 1.20.1 pre-alpha still generates most rock types with ordinary Minecraft configured/placed ore features. Those features are a compatibility baseline, not the intended final geology model.

Limestone, shale, mudstone and siltstone are the first active bedded-rock migrations. All four use GeoStrata's `strata_lens` feature type, producing coherent tapered beds/lenses with tilt and local warping while preserving the same `geostrata:worldgen/base_stone_replaceables` target contract. Their placement attempts are accepted according to the effective blended suitability of their lithology in the local geological province. The remaining rock types stay on the ore-style baseline and do not consume province weights yet.

The lens geometry is data-driven per configured feature. Long/short radius, central and edge thickness, maximum slope, warp amplitude/variation and warp wavelength are explicit config fields rather than Java constants. Limestone remains the broader carbonate pilot; shale uses a smaller, thinner and more deformable profile; mudstone uses a somewhat thicker, more massive low-slope bed with gentler warping; siltstone uses a thin, relatively planar profile suited to its fluvial targeting. Shale, mudstone and siltstone each move from two small ore-style attempts to one coherent bed attempt per chunk, so these migrations intentionally change body geometry and abundance together and should be evaluated in fresh chunks.

The production feature delegates its ellipse rotation, radial boundary, taper, slope and warp calculations to a pure allocation-free geometry helper. JUnit regression tests exercise that helper independently of Minecraft world mutation, so geometry refactors can be distinguished from deliberate world-generation changes.

`data/geostrata/geology/lithologies.json` records the semantic meaning of every live GeoStrata rock independently of the current generator. Its formation hints remain metadata while individual runtime consumers are introduced deliberately. The server also loads that file through `LithologyCatalog`, which validates the bundled or datapack-overridden semantic metadata and exposes a read-only snapshot to diagnostics and future generators. Registry IDs remain validated strings in this service; actual registry resolution belongs at the eventual world-mutation boundary.

Use `/geostrata lithology <id>` to inspect the currently loaded block identity, rock class, genesis, body style, depth affinity, continuity, biome tag and baseline feature. This command is a metadata diagnostic only and does not resolve or mutate world blocks.

## Regional province sampling

GeoStrata has a deterministic province sampler that assigns broad regional context. Province sites are jittered inside a 768-block grid and nearest-site ownership creates irregular Voronoi-style boundaries. Five archetypes currently exist: sedimentary basin, cratonic shield, orogenic belt, volcanic arc and rift province.

The sampler is a pure function of world seed and block X/Z. It stores no mutable world state, does not depend on chunk generation order and is covered by regression vectors. This is an important compatibility constraint: pregeneration, multiplayer and revisiting a partially explored world must all agree on province boundaries.

The sampler also tracks the second-nearest site and computes the exact local distance to the bisector between the two sites. Runtime generation can therefore blend neighboring province profiles across a transition zone instead of producing visible hard borders. `/geostrata province` reports the current province, neighboring province and approximate distance to that boundary.

## Province profiles

`data/geostrata/geology/province_profiles.json` defines relative suitability for every live lithology in every province and is loaded through Fabric's server-data resource manager. It can be overridden by a datapack at the same `geostrata:geology/province_profiles.json` resource ID.

The runtime loader also reads the lithology catalog and validates that each profile covers the current catalog exactly. A malformed or incomplete datapack override fails resource reload instead of silently changing the geology model.

Weights are preferences, not permissions. Every province/lithology pair has a positive weight, so a low value means uncommon rather than impossible. The default profile declares a 192-block transition width. Effective weights blend the primary and neighboring profile as the sampler approaches a boundary rather than abruptly switching probabilities on the Voronoi line.

Province profiles declare `runtime_bias`. A GeoStrata `strata_lens` targeting one catalogued GeoStrata lithology uses the effective weight as its placement-acceptance probability. Limestone, shale, mudstone and siltstone currently consume that contract; future migrations automatically inherit the same regional behavior when moved to the feature type.

Use `/geostrata profile` to inspect the strongest effective lithologies and current primary/neighbor blend at the command source's location. `/geostrata survey <lithology>` performs a coarse deterministic search of the same regional model around the command source and reports the nearest best sampled suitability target. The survey does not load chunks or search for generated blocks, so its result is a testing/navigation hint rather than a promise that a particular rock body exists at that coordinate.

Siltstone is additionally constrained by `geostrata:has_fluvial_rocks`, whose default contents are `#minecraft:is_river`. Pack and biome integrations may extend that GeoStrata-owned tag without changing the standalone generator, so the province model and biome affinity remain separate composable filters.

## Deterministic feature decisions

Province acceptance uses `GeologyDeterminism.unitRoll`, a stable world-seed/XYZ hash with a feature-specific salt. It deliberately does not consume Minecraft's feature RNG stream. Accepted strata lenses therefore retain their configured geometry RNG sequence independently of the regional acceptance decision.

The roll mapping is covered by fixed regression vectors. Changing its hash or salt is a world-generation compatibility change and should be treated accordingly.

## Terrain morphology evidence

`TerrainMorphologySample` is the first small terrain-aware seam in the refactored codebase. It converts a center height plus four cardinal observations into coarse relief, X/Z gradient, slope magnitude and ridge/valley prominence without depending on Minecraft classes.

`ChunkGeneratorTerrainMorphologySampler` supplies those observations from the active terrain generator's `OCEAN_FLOOR_WG` height at 128-block spacing. It asks the generator directly rather than loading neighboring chunks, so vanilla noise settings and compatible terrain generators can contribute through the same adapter without becoming core dependencies.

`TerrainAwareStructuralField` turns the same active-generator height evidence into a continuous broad structural adjustment. It samples height and ridge/valley prominence on a fixed 128-block world grid and bilinearly interpolates both between shared grid corners, so neighboring chunks cannot choose different terrain evidence at their boundary. The deterministic province site remains the zero-reference anchor.

Province archetypes apply two deliberately partial responses. Terrain drape lets beds follow a fraction of broad elevation change. Fold response uses the absolute interpolated prominence to amplify the field's existing seed-derived warp, so ridges and valleys strengthen open folds without adding a second unrelated wave system. Fold amplification is capped at 25% of one succession cycle and is strongest in orogenic belts, modest in volcanic arcs and rifts, and restrained in stable cratonic and sedimentary settings. This can produce substantially steeper contacts around relief, but the field remains single-valued: it does not yet model overturned folds or discrete fault planes.

Both `/geostrata field` and the opt-in correlated generator construct this transform through `ChunkGeneratorTerrainMorphologySampler.structuralField`. The standalone independent-feature baseline remains unchanged. Use `/geostrata terrain` to inspect relief, slope, ridge/valley prominence and the active province drape/fold response; `/geostrata field` reports the resulting drape and fold offsets separately. The response values are experimental world-generation tuning and must be evaluated in fresh companion worlds before broader activation.

## Sedimentary succession and spatial-field staging

GeoStrata now has a metadata-only sedimentary succession model plus a deterministic selector and normalized contact planner. Those components establish lower-to-upper bed order, relative thickness and exact contact ownership without changing generated blocks. The contact planner assigns internal boundaries to the overlying bed, replacing feature-registration order with an explicit future ownership rule.

`SedimentaryStratigraphicField` is the next pure staging layer. It accepts cycle thickness, maximum dip, warp amplitude and warp wavelength as explicit caller parameters, derives stable structural orientation from world seed plus province-site coordinates, and maps an X/Y/Z sample to an unbounded cycle index plus normalized position inside the selected motif. It has no Minecraft registry access and performs no world mutation.

The province site is a zero-offset structural anchor. At the site itself, `Y=0` maps exactly to the contact plan's deterministic phase; dip and warp deform contacts only away from that anchor. Repetition is represented explicitly by the returned cycle index rather than hidden inside the contact planner, so a future runtime consumer can choose whether and where repeated motifs are actually permitted.

No default cycle thickness, dip or warp values are part of the runtime contract yet. Those parameters must be tuned and validated separately before correlated succession generation is activated. This keeps the pure spatial mathematics testable without turning preliminary tuning choices into permanent world-generation behavior.

## Lithology fields

Each core rock defines:

- `rockClass` — sedimentary, igneous or metamorphic; this must agree with the corresponding block tag.
- `genesis` — a more specific formation/process description such as carbonate, mudrock, mafic extrusive or high-grade banded metamorphic rock.
- `bodyStyle` — the geometry the future generator should aim for, such as beds, bands, sheets, channels or basement massifs.
- `depthAffinity` — a qualitative tendency rather than an absolute Y range.
- `continuity` — whether the intended body should generally be local or regionally persistent.
- `biomeTag` — the current GeoStrata-owned biome affinity used by baseline generation.
- `baselineFeature` — the configured/placed feature currently representing this lithology, whether it uses a vanilla or GeoStrata feature type.

The qualitative fields are intentional. GeoStrata should not encode "gneiss lives below Y=-32" as a universal truth when a terrain mod may radically alter relief, sea level or crust exposure.

## Direction of travel

The intended generator architecture is:

1. choose a broad geological province from world seed and low-frequency fields;
2. blend its lithological suitability with the neighboring province near regional boundaries;
3. assign a lithological succession/body family within that regional context;
4. construct coherent beds, bands, intrusive/volcanic bodies and contacts across chunk boundaries;
5. expose those bodies through terrain, caves, erosion and hydrology;
6. place ores/minerals as consequences of host lithology and geological process;
7. let optional integrations add valid host blocks, biome mappings, surface palettes and structures without redefining the core geology.

This is deliberately different from running fourteen independent ore generators. Feature migrations remain incremental: one geological body family is introduced, built, profiled and observed before the next family replaces its baseline implementation.

## Compatibility

Third-party mods should not edit the core lithology catalog to add their own blocks. Pack authors may override province suitability weights when building a distinct worldgen profile, but third-party block/biome compatibility should normally remain in GeoStrata's semantic extension tags rather than introducing registry IDs into core Java.

For example, a terrain mod can add its natural stone to `geostrata:worldgen/base_stone_replaceables`; a Conquest integration can add suitable surface/building palettes without making Conquest a dependency of the core jar.

See `docs/COMPATIBILITY.md` for the existing data extension points.

## Validation

Run:

```text
python3 scripts/validate_geology_catalog.py
gradle test
```

The same validation command also checks the material-profile LUT against registered blocks, gameplay settings, mining tags and live assets.

The catalog validator enforces that every live rock appears exactly once in the catalog and exactly once in a rock-class tag, that referenced biome tags exist, and that each baseline configured/placed feature actually generates the catalogued block. `GeologyDataReload` validates the related geology resources once in dependency order. `GeologyResourceContractTest` sends the bundled files through those production parsers, then checks strata-lens resource pairing and bundled palette policy. Existing behavior tests cover province sampling, profile blending, suitability acceptance, coordinate hashing, strata-lens geometry, succession contacts and the stratigraphic field.
