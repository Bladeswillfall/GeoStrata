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

Vanilla granite and diorite are the first bundled examples. They are now valid igneous lithologies in the semantic catalog and province profiles, but this change alone does not add new pluton geometry or make the province models emit them. Their first runtime use belongs in the existing intrusive architecture rather than a second generator.

The same ownership rule is intended for compatibility adapters: a loaded third-party provider can supply the material while GeoStrata supplies geological meaning and, where appropriate, shared geometry. Optional-mod activation remains an adapter concern; the core catalog must not pretend an absent provider block exists.

## Correlated authority

With the experiment companion active, the correlated runtime is authoritative for the configured sedimentary-basin, rift and orogenic successions. All seven sedimentary lithologies are suppressed as independent fallback lenses inside experiment-owned chunks and instead come from the shared terrain-aware stratigraphic field.

The correlated contract uses the active dimension bounds as its vertical domain rather than a fixed sea-level-relative window. Bed/cycle thickness stays geological rather than scaling with the number of vertical blocks in the world.

The field samples the active terrain generator on a shared coarse grid. Positive prominence can strengthen province-specific uplift/folding; increasingly negative prominence attenuates that response so deep ravines primarily expose existing geology rather than bending strata down to the ravine floor.

In owned orogenic chunks, the existing metamorphic band decision transforms mudrock parent beds into slate/schist/gneiss. The same band decision transforms carbonate parent beds into marble. Baseline metamorphic lenses remain a fallback outside correlated ownership.

Basalt and rhyolite remain independent bodies and may cut sedimentary strata; that is intentional for igneous rock.

## Known boundary

Quartzite has the correct coherent metamorphic-band fallback geometry, but it is not yet parent-derived because GeoStrata does not currently define a quartz-rich sandstone parent lithology. Do not fake that relationship. When a valid parent exists, route quartzite through the same parent-aware metamorphic runtime.

Sandy loam uses the same native `minecraft:disk` plus terrain/biome suitability approach as the other surface loams rather than an underground ore feature.
