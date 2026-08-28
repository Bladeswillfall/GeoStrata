# Lithology generation methods

GeoStrata no longer uses vanilla `minecraft:ore` blobs as the baseline generator for natural rock or soil blocks.

## Baseline geometry

The existing `*_ore` feature IDs are retained as stable datapack/worldgen identifiers, but the names are historical. All fourteen catalogued rock baselines now use `geostrata:strata_lens` with data-driven geometry suited to their body style and province suitability.

- Bedded sedimentary rocks use broad, comparatively planar lenses.
- Coarse clastics use local tapered lenses/beds.
- Slate, schist and quartzite use thinner, more deformed fallback bands; gneiss uses a broader, thicker fallback body; marble uses a local band/lens.
- Basalt uses a broad, thin sheet-like profile; rhyolite uses a smaller, thicker local volcanic-body profile.

One coherent body attempt replaces the old two ore-blob attempts for the eight lithologies migrated in this pass.

## Correlated authority

With the experiment companion active, the correlated runtime is authoritative for the configured sedimentary-basin, rift and orogenic successions. All seven sedimentary lithologies are suppressed as independent fallback lenses inside experiment-owned chunks and instead come from the shared terrain-aware stratigraphic field.

In owned orogenic chunks, the existing metamorphic band decision transforms mudrock parent beds into slate/schist/gneiss. The same band decision now transforms carbonate parent beds into marble. Baseline metamorphic lenses remain a fallback outside correlated ownership.

Basalt and rhyolite remain independent bodies and may cut sedimentary strata; that is intentional for igneous rock.

## Known boundary

Quartzite has the correct coherent metamorphic-band fallback geometry, but it is not yet parent-derived because GeoStrata does not currently define a quartz-rich sandstone parent lithology. Do not fake that relationship. When a valid parent exists, route quartzite through the same parent-aware metamorphic runtime.

Sandy loam now uses the same native `minecraft:disk` plus terrain/biome suitability approach as the other surface loams rather than an underground ore feature.
