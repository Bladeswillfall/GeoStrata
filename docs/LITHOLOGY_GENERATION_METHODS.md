# Lithology generation methods

GeoStrata no longer uses vanilla `minecraft:ore` blobs as the baseline generator for natural rock or soil blocks.

## Baseline geometry

The existing `*_ore` feature IDs are retained as stable datapack/worldgen identifiers, but the names are historical. All fourteen GeoStrata-owned ordinary rock baselines use `geostrata:strata_lens` with data-driven geometry suited to their body style and province suitability.

- Bedded sedimentary rocks use broad, comparatively planar lenses.
- Coarse clastics use local tapered lenses/beds.
- Slate, schist and quartzite use thinner, more deformed fallback bands; gneiss uses a broader, thicker fallback body; marble uses a local band/lens.
- Basalt uses a broad, thin sheet-like profile; rhyolite uses a smaller, thicker local volcanic-body profile.

Baseline placed features use `geostrata:subsurface_anchor` instead of a fixed Minecraft `height_range`. After the ordinary in-chunk X/Z choice, the modifier reads that column's `OCEAN_FLOOR_WG` height and chooses the body's Y anchor between the active world's real bottom and the generated rock surface. A tall mountain can therefore receive fallback geology even when the dimension's ceiling is unchanged, while a deeper custom dimension naturally exposes a larger subsurface column.

The replacement predicate remains authoritative: bedrock, air, caves, fluids and unrelated blocks are skipped rather than overwritten. Body thickness and shape are **not** scaled with either terrain height or dimension height.

The independent lenses remain conservative compatibility fallbacks: terrain height does not increase their per-chunk attempt count. Coherent full-domain geology is the responsibility of the correlated field where that experiment owns a chunk.

## Provider-owned lithologies

A semantic lithology does not have to be a block owned by GeoStrata. The block namespace is the provider boundary.

GeoStrata-owned entries (`geostrata:*`) must keep their explicit GeoStrata baseline feature and remain covered by the material/asset contract. Provider-owned entries use an existing registered block directly and set `baselineFeature` to `null`; GeoStrata does not create a duplicate block, texture set or fallback feature for them.

Vanilla granite and diorite are the first bundled examples. The provider-neutral contract gives them semantic identities and province profiles without adding duplicate blocks, textures or fallback features.

The same ownership rule is intended for compatibility adapters: a loaded third-party provider can supply the material while GeoStrata supplies geological meaning and, where appropriate, shared geometry. Optional-mod activation remains an adapter concern; the core catalog must not pretend an absent provider block exists.

## Volcanic-arc intrusive zoning

The advanced Volcanic Arc runtime reuses its existing deterministic volcanic-complex ellipsoid rather than adding a second intrusion generator.

Within that same complex:

- the shallow zone remains rhyolite;
- the deeper inner root resolves to vanilla granite;
- the deeper outer margin resolves to vanilla diorite;
- the existing rhyolite breccia halo applies only to the shallow volcanic zone;
- existing basalt dikes retain first precedence and may cross-cut the complex;
- existing finite basalt sills retain their current geometry and ordering.

The granite/diorite split is therefore compositional zoning inside geometry GeoStrata already calculates. It adds no new noise, cell lattice, random roll or mutable geology state. Contact metamorphism remains separate future work; the plutonic root does not invent a hornfels substitute from an unrelated existing rock.

## Correlated authority

With the experiment companion active, the correlated runtime is authoritative for the configured sedimentary-basin, rift and orogenic successions. All seven sedimentary lithologies are suppressed as independent fallback lenses inside experiment-owned chunks and instead come from the shared terrain-aware stratigraphic field.

The correlated contract uses the active dimension bounds as its vertical domain rather than a fixed sea-level-relative window. Bed/cycle thickness stays geological rather than scaling with the number of vertical blocks in the world.

The field samples the active terrain generator on a shared coarse grid. Positive prominence can strengthen province-specific uplift/folding; increasingly negative prominence attenuates that response so deep ravines primarily expose existing geology rather than bending strata down to the ravine floor.

In owned orogenic chunks, the existing metamorphic band decision transforms mudrock parent beds into slate/schist/gneiss. The same band decision transforms carbonate parent beds into marble. Baseline metamorphic lenses remain a fallback outside correlated ownership.

Basalt and rhyolite remain independent bodies and may cut sedimentary strata; that is intentional for igneous rock.

## Known boundary

Quartzite has the correct coherent metamorphic-band fallback geometry, but it is not yet parent-derived because GeoStrata does not currently define a quartz-rich sandstone parent lithology. Do not fake that relationship. When a valid parent exists, route quartzite through the same parent-aware metamorphic runtime.

Sandy loam uses the same native `minecraft:disk` plus terrain/biome suitability approach as the other surface loams rather than an underground ore feature.
