# Lithology generation methods

GeoStrata no longer uses vanilla `minecraft:ore` blobs as the baseline generator for natural rock or soil blocks.

## Baseline geometry

The existing `*_ore` feature IDs are retained as stable datapack/worldgen identifiers, but the names are historical. All fourteen catalogued rock baselines now use `geostrata:strata_lens` with data-driven geometry suited to their body style and province suitability.

- Bedded sedimentary rocks use broad, comparatively planar lenses.
- Coarse clastics use local tapered lenses/beds.
- Slate, schist and quartzite use thinner, more deformed fallback bands; gneiss uses a broader, thicker fallback body; marble uses a local band/lens.
- Basalt uses a broad, thin sheet-like profile; rhyolite uses a smaller, thicker local volcanic-body profile.

Baseline placed features use `above_bottom` / `below_top` vertical anchors rather than absolute Y values. The margins preserve the existing vanilla 1.20.1 envelope closely while allowing taller/deeper dimensions to move the fallback envelope with their actual world bounds. The per-body geometry itself is **not** scaled with dimension height.

The independent lenses remain conservative compatibility fallbacks: increasing dimension height does not automatically increase their per-chunk attempt count. Coherent full-height geology is the responsibility of the correlated field where that experiment owns a chunk.

## Correlated authority

With the experiment companion active, the correlated runtime is authoritative for the configured sedimentary-basin, rift and orogenic successions. All seven sedimentary lithologies are suppressed as independent fallback lenses inside experiment-owned chunks and instead come from the shared terrain-aware stratigraphic field.

The correlated contract uses the active dimension bounds as its vertical domain rather than a fixed sea-level-relative window. Bed/cycle thickness stays geological rather than scaling with the number of vertical blocks in the world.

The field samples the active terrain generator on a shared coarse grid. Positive prominence can strengthen province-specific uplift/folding; increasingly negative prominence attenuates that response so deep ravines primarily expose existing geology rather than bending strata down to the ravine floor.

In owned orogenic chunks, the existing metamorphic band decision transforms mudrock parent beds into slate/schist/gneiss. The same band decision transforms carbonate parent beds into marble. Baseline metamorphic lenses remain a fallback outside correlated ownership.

Basalt and rhyolite remain independent bodies and may cut sedimentary strata; that is intentional for igneous rock.

## Known boundary

Quartzite has the correct coherent metamorphic-band fallback geometry, but it is not yet parent-derived because GeoStrata does not currently define a quartz-rich sandstone parent lithology. Do not fake that relationship. When a valid parent exists, route quartzite through the same parent-aware metamorphic runtime.

Sandy loam uses the same native `minecraft:disk` plus terrain/biome suitability approach as the other surface loams rather than an underground ore feature.
