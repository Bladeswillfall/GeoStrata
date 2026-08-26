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

## Validation

`python3 scripts/validate_sedimentary_successions.py` cross-checks the succession file against the live lithology catalog and province profiles. It rejects unknown or non-sedimentary lithologies, unknown province contexts, malformed ordering, invalid thickness ratios, duplicate IDs and accidental worldgen activation. The bundled succession set must collectively cover every live sedimentary lithology, including lithologies that have not yet migrated from the ore-style compatibility baseline.

The Java reload parser independently enforces the same safety properties for datapack overrides. JUnit tests cover parsing failures, context scoring and deterministic weighted selection before any future worldgen consumer is allowed to use the model.
