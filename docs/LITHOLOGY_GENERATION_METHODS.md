# Lithology generation methods

GeoStrata no longer uses vanilla `minecraft:ore` blobs as the baseline generator for natural rock or soil blocks.

## Baseline geometry

The existing `*_ore` feature IDs are retained as stable datapack/worldgen identifiers, but the names are historical. All fourteen ordinary GeoStrata rock fallbacks use `geostrata:strata_lens` with data-driven geometry suited to their body style and province suitability.

- Bedded sedimentary rocks use broad, comparatively planar lenses.
- Coarse clastics use local tapered lenses/beds.
- Slate, schist and quartzite use thinner, more deformed fallback bands; gneiss uses a broader, thicker fallback body; marble uses a local band/lens.
- Basalt uses a broad, thin sheet-like profile; rhyolite uses a smaller, thicker local volcanic-body profile.

Baseline placed features use `geostrata:subsurface_anchor` instead of a fixed Minecraft `height_range`. After the ordinary in-chunk X/Z choice, the modifier reads that column's `OCEAN_FLOOR_WG` height and chooses the body's Y anchor between the active world's real bottom and the generated rock surface. A tall mountain can therefore receive fallback geology even when the dimension's ceiling is unchanged, while a deeper custom dimension naturally exposes a larger subsurface column.

The replacement predicate remains authoritative: bedrock, air, caves, fluids and unrelated blocks are skipped rather than overwritten. Body thickness and shape are **not** scaled with either terrain height or dimension height.

The independent lenses remain conservative compatibility fallbacks: terrain height does not increase their per-chunk attempt count. Coherent full-domain geology is the responsibility of the correlated field where that experiment owns a chunk.

## Block ownership and generation authority

A semantic lithology does not have to be a block owned by GeoStrata. The block namespace is only the **material ownership** boundary.

Generation is a separate contract. Every lithology declares exactly one of:

- `baselineFeature`: an explicit configured/placed fallback body; or
- `runtimeAuthority`: an existing semantic runtime that is solely responsible for producing that lithology.

A provider-owned block cannot claim a GeoStrata fallback feature. A GeoStrata-owned block may be runtime-only, but it still has to satisfy the ordinary GeoStrata material, asset, mining and tag contracts.

Vanilla granite and diorite are the first provider-owned examples. They use `runtimeAuthority: volcanic_arc_complex`, so GeoStrata gives the vanilla blocks geological meaning and coherent placement without creating duplicate granite/diorite blocks or fallback features.

Hornfels is the first GeoStrata-owned runtime-only example. It uses `runtimeAuthority: contact_metamorphism`; there is deliberately no `hornfels_ore` feature and therefore no independent random hornfels body.

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

The granite/diorite split is therefore compositional zoning inside geometry GeoStrata already calculates. It adds no new noise, cell lattice, random roll or mutable geology state.

### Contact aureole

The deep outer shell of that same volcanic-complex geometry also marks a narrow `contact_aureole` in the surrounding country rock. The geometry layer does **not** choose the metamorphic product. `ContactMetamorphism` resolves the existing parent lithology through the catalog's semantic `genesis`:

- mudrock, silt-rich and low/medium-grade foliated parents → hornfels;
- carbonate parents → marble;
- quartz-rich metamorphic parents → quartzite;
- unsupported/high-grade parents remain unchanged.

This avoids the visibly simple but geologically wrong solution of drawing a universal hornfels donut around every pluton. In the current Volcanic Arc basement, schist portions of the aureole bake to hornfels while quartzite remains quartzite and high-grade gneiss remains gneiss.

The aureole reuses the existing complex radius and adds no second thermal noise field. Basalt dikes and sills keep their existing precedence; this slice does not add separate aureoles around every small dike/sill.

## Correlated authority

With the experiment companion active, the correlated runtime is authoritative for the configured sedimentary-basin, rift and orogenic successions. All seven sedimentary lithologies are suppressed as independent fallback lenses inside experiment-owned chunks and instead come from the shared terrain-aware stratigraphic field.

The correlated contract uses the active dimension bounds as its vertical domain rather than a fixed sea-level-relative window. Bed/cycle thickness stays geological rather than scaling with the number of vertical blocks in the world.

The field samples the active terrain generator on a shared coarse grid. Positive prominence can strengthen province-specific uplift/folding; increasingly negative prominence attenuates that response so deep ravines primarily expose existing geology rather than bending strata down to the ravine floor.

In owned orogenic chunks, the existing metamorphic band decision transforms mudrock parent beds into slate/schist/gneiss. The same parent-aware path can transform carbonate parent beds into marble. Baseline metamorphic lenses remain a fallback outside correlated ownership.

Basalt and rhyolite remain independent bodies and may cut sedimentary strata; that is intentional for igneous rock.

## Known boundary

Quartzite has the correct coherent metamorphic-band fallback geometry, but the correlated stratigraphic runtime does not yet define a quartz-rich sandstone parent lithology. Do not fake that relationship. When a valid parent exists, route quartzite through the same parent-aware metamorphic runtime.

Sandy loam uses the same native `minecraft:disk` plus terrain/biome suitability approach as the other surface loams rather than an underground ore feature.
