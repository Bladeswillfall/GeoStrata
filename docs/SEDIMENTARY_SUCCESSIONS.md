# Sedimentary succession contract

GeoStrata's first `strata_lens` migrations prove that individual rock bodies can be broader, tapered and province-aware, but four independent feature attempts are still not stratigraphy. `data/geostrata/geology/sedimentary_successions.json` defines the next contract: ordered motifs that can later correlate multiple sedimentary lithologies into one geological succession.

The file remains deliberately `metadata_only`. Nothing in the current chunk generator selects or places these successions, and changing this data cannot silently rewrite worlds. The server loads and validates the metadata so diagnostics can inspect deterministic candidate successions, but worldgen consumption will be introduced separately with its own regression tests and compatibility review.

## Semantics

Each succession contains an `id`, one or more broad geological `contexts`, `continuity`, and an ordered `beds` array. Bed order is globally declared as `lower_to_upper`. `relativeThickness` is a dimensionless ratio within the motif, not a number of Minecraft blocks. A future generator may scale a whole motif to terrain, province, exposure and continuity while preserving those proportions.

Contexts are geological province IDs, not hard biome permissions. Biome affinity remains a separate composable filter owned by each lithology, such as `geostrata:has_fluvial_rocks` for siltstone. Likewise, successions contain lithology IDs rather than block IDs, so terrain and content integrations can keep extending GeoStrata through tags without entering the geological model.

The bundled motifs are intentionally broad rather than claims that every real-world basin has one universal layer order. They provide distinct starting families for basin mudrock/carbonate cycles, shelf chalk/carbonate cycles, rift fining-upward clastics and local orogenic-fan fining-upward deposits. Datapacks can replace the resource, but malformed or incomplete overrides fail reload rather than silently degrading the model.

## Diagnostic selection

`/geostrata succession` selects a diagnostic succession for the current primary province site and its nearest neighboring province site. The selection is deterministic from world seed and site coordinates, so walking around inside one province does not make the result flicker with player position.

Each succession receives a thickness-weighted mean of the province's existing lithology suitability values. A succession that declares that province as a context receives its full score; an out-of-context succession receives a 0.2 fallback multiplier. A stable weighted roll then selects a family. This keeps contexts influential without turning them into a second set of hard lithology permissions.

The command is intentionally explicit that the result is diagnostic metadata only. Chunk generation still runs the currently migrated independent `strata_lens` features; the selected succession does not yet place, suppress or reorder any blocks.

## Normalized contact planning

`SedimentaryContactPlanner` is the next pure layer between succession selection and world mutation. Given a selected succession and its deterministic province-site anchor, it converts the lower-to-upper relative thicknesses into contiguous normalized intervals spanning exactly `0.0..1.0`. It does **not** choose Minecraft Y levels, inspect biomes, access registries or place blocks.

Contacts are lower-inclusive and upper-exclusive. An exact internal contact therefore belongs to the overlying bed rather than whichever feature happened to execute first. The top boundary at `1.0` is outside the motif; wrapping or repetition, if introduced later, must be an explicit worldgen rule rather than an accidental modulo operation.

The plan also records a deterministic phase value derived from world seed and province-site coordinates. That phase is metadata for future regional vertical alignment and does not currently offset contacts or affect generation. Anchoring it to the province site means the value is stable while moving around within the same geological region.

This normalized plan deliberately separates **order and ownership** from **scale and exposure**. A future runtime consumer can decide how many blocks a motif spans, how its contacts dip/warp, where terrain exposes it and how biome filters affect individual lithologies without changing the canonical lower-to-upper ownership rule.

`/geostrata column` exposes that normalized plan in-game for the current primary province site. It reports each lower-to-upper bed as a percentage interval plus the deterministic site phase. Those percentages are motif proportions only; they are deliberately not presented as Minecraft block heights or promises about generated material.

## Validation

The ordered Java reload parser cross-checks succession data against the already parsed lithology catalog. It rejects unknown or non-sedimentary lithologies, unknown province contexts, malformed ordering, invalid thickness ratios, duplicate IDs and accidental worldgen activation. The bundled set must collectively cover every live sedimentary lithology.

`GeologyResourceContractTest` exercises the shipped resource graph and its required basin and rift context coverage. Existing JUnit tests cover parsing failures, context scoring, deterministic weighted selection and normalized contact ownership.
