# Lithology generation methods

GeoStrata no longer uses vanilla `minecraft:ore` blobs as the baseline generator for natural rock or soil blocks.

## Legacy/fallback geometry

The existing `*_ore` feature IDs are retained as stable datapack/worldgen identifiers, but the names are historical. The fourteen ordinary GeoStrata rock fallback resources use `geostrata:strata_lens` with data-driven geometry suited to their body style and province suitability; normal core worldgen no longer attaches those fallback rock placed features to overworld biomes.

- Bedded sedimentary rocks use broad, comparatively planar lenses.
- Coarse clastics use local tapered lenses/beds.
- Slate, schist and quartzite use thinner, more deformed fallback bands; gneiss uses a broader, thicker fallback body; marble uses a local band/lens.
- Basalt uses a broad, thin sheet-like profile; rhyolite uses a smaller, thicker local volcanic-body profile.

If explicitly re-enabled by a datapack or compatibility layer, those placed features use `geostrata:subsurface_anchor` instead of a fixed Minecraft `height_range`. After the ordinary in-chunk X/Z choice, the modifier reads that column's `OCEAN_FLOOR_WG` height and chooses the body's Y anchor between the active world's real bottom and the generated rock surface. A tall mountain can therefore receive fallback geology even when the dimension's ceiling is unchanged, while a deeper custom dimension naturally exposes a larger subsurface column.

The replacement predicate remains authoritative: bedrock, air, caves, fluids and unrelated blocks are skipped rather than overwritten. Body thickness and shape are **not** scaled with either terrain height or dimension height.

These independent lenses are compatibility resources rather than the normal core authority. Coherent full-domain geology is owned by the correlated sedimentary and province-background runtimes.

## Block ownership and generation authority

A semantic lithology does not have to be a block owned by GeoStrata. The block namespace is only the **material ownership** boundary.

Generation is a separate contract. Every lithology declares exactly one of:

- `baselineFeature`: an explicit configured/placed fallback body; or
- `runtimeAuthority`: an existing semantic runtime that is solely responsible for producing that lithology.

A provider-owned block cannot claim a GeoStrata fallback feature. A GeoStrata-owned block may be runtime-only, but it still has to satisfy the ordinary GeoStrata material, asset, mining and tag contracts.

Vanilla andesite, granite and diorite use `runtimeAuthority: volcanic_arc_complex`, so GeoStrata gives those vanilla blocks geological meaning and coherent placement without creating duplicate blocks or fallback features.

Vanilla sandstone is the first provider-owned sedimentary example. It uses `runtimeAuthority: sedimentary_stratigraphy`: GeoStrata reuses `minecraft:sandstone` in the shared succession/stratigraphic runtime rather than adding a duplicate sandstone block or an independent sandstone blob feature. Correlated owned chunks and the province-background sedimentary path can therefore share the same parent semantics.

Hornfels is a GeoStrata-owned runtime-only contact product. It uses `runtimeAuthority: contact_metamorphism`; there is deliberately no `hornfels_ore` feature and therefore no independent random hornfels body.

Phyllite follows the same ownership rule for regional metamorphism. It uses `runtimeAuthority: regional_metamorphism`; the existing metamorphic field and Orogenic background gradient produce it between slate and schist, with no `phyllite_ore` feature or separate generator.

The same ownership rule is intended for compatibility adapters: a loaded third-party provider can supply the material while GeoStrata supplies geological meaning and, where appropriate, shared geometry. Optional-mod activation remains an adapter concern; the core catalog must not pretend an absent provider block exists.

## Volcanic-arc intrusive zoning

The core Volcanic Arc runtime reuses its existing deterministic volcanic-complex ellipsoid rather than adding a second intrusion generator.

Within that same complex:

- the shallow evolved core resolves to rhyolite;
- the surrounding shallow/intermediate shell resolves to vanilla andesite;
- the deeper inner root resolves to vanilla granite;
- the deeper outer margin resolves to vanilla diorite;
- the existing breccia halo applies only to the shallow volcanic zone;
- existing basalt dikes retain first precedence and may cross-cut the complex;
- existing finite basalt sills retain their current geometry and ordering.

The rhyolite/andesite/granite/diorite split is therefore compositional zoning inside geometry GeoStrata already calculates. It adds no new noise, cell lattice, random roll, block registration or mutable geology state.

### Contact aureole

The deep outer shell of that same volcanic-complex geometry also marks a narrow `contact_aureole` in the surrounding country rock. The geometry layer does **not** choose the metamorphic product. `ContactMetamorphism` resolves the existing parent lithology through the catalog's semantic `genesis`:

- mudrock, silt-rich and low/medium-grade foliated parents → hornfels;
- carbonate parents → marble;
- quartz sandstone and quartz-rich metamorphic parents → quartzite;
- unsupported/high-grade parents remain unchanged.

This avoids the visibly simple but geologically wrong solution of drawing a universal hornfels donut around every pluton. In the current Volcanic Arc basement, schist portions of the aureole bake to hornfels while quartzite remains quartzite and high-grade gneiss remains gneiss. If future country rock supplies sandstone at an intrusion contact, the same parent rule yields quartzite without special-casing the block provider.

The aureole reuses the existing complex radius and adds no second thermal noise field. Basalt dikes and sills keep their existing precedence; this slice does not add separate aureoles around every small dike/sill.

## Correlated authority

In normal core worlds, the correlated runtime is authoritative for the configured sedimentary-basin, rift and orogenic successions. All target succession lithologies are emitted from the shared terrain-aware stratigraphic field instead of independent fallback bodies where the correlated contract owns a chunk.

The correlated contract uses the active dimension bounds as its vertical domain rather than a fixed sea-level-relative window. Bed/cycle thickness stays geological rather than scaling with the number of vertical blocks in the world.

The field samples the active terrain generator on a shared coarse grid. Positive prominence can strengthen province-specific uplift/folding; increasingly negative prominence attenuates that response so deep ravines primarily expose existing geology rather than bending strata down to the ravine floor.

The orogenic fan now follows the more complete fining-upward order:

```text
breccia → conglomerate → sandstone → siltstone → shale
```

In owned orogenic chunks, metamorphic suitability combines with the resolved parent lithology:

- mudrock parents → slate/phyllite/schist/gneiss according to the existing grade-band selector;
- carbonate parents → marble;
- quartz-sandstone parents → quartzite;
- unsupported parents remain unchanged.

Sandstone outside an orogenic metamorphic context remains ordinary vanilla sandstone. Province-background metamorphic architecture owns the remaining appropriate metamorphic terrain; phyllite is runtime-only and therefore does not add another baseline lens.

Basalt and rhyolite remain independent bodies and may cut sedimentary strata; that is intentional for igneous rock.

## Remaining boundary

Provider-owned sandstone deliberately has no independent GeoStrata fallback feature. It exists where the core sedimentary-stratigraphy runtime owns the geology. Disabling the correlated contract with a datapack is a diagnostic/recovery action and does not resurrect a random duplicate sandstone body or the retired legacy rock attachments.

Sandy loam uses the same native `minecraft:disk` plus terrain/biome suitability approach as the other surface loams rather than an underground ore feature.
