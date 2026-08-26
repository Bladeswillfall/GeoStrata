# Sedimentary succession contract

GeoStrata's first `strata_lens` migrations prove that individual rock bodies can be broader, tapered and province-aware, but four independent feature attempts are still not stratigraphy. `data/geostrata/geology/sedimentary_successions.json` defines the next contract: ordered motifs that can later correlate multiple sedimentary lithologies into one geological succession.

The file is deliberately `metadata_only`. Nothing in the current generator selects or places these successions yet, and changing this data cannot silently rewrite worlds. Runtime consumption will be introduced separately with deterministic sampling, regression tests and diagnostics before it can affect chunk generation.

## Semantics

Each succession contains an `id`, one or more broad geological `contexts`, `continuity`, and an ordered `beds` array. Bed order is globally declared as `lower_to_upper`. `relativeThickness` is a dimensionless ratio within the motif, not a number of Minecraft blocks. A future generator may scale a whole motif to terrain, province, exposure and continuity while preserving those proportions.

Contexts are geological province IDs, not hard biome permissions. Biome affinity remains a separate composable filter owned by each lithology, such as `geostrata:has_fluvial_rocks` for siltstone. Likewise, successions contain lithology IDs rather than block IDs, so terrain and content integrations can keep extending GeoStrata through tags without entering the geological model.

The bundled motifs are intentionally broad rather than claims that every real-world basin has one universal layer order. They provide distinct starting families for basin mudrock/carbonate cycles, shelf chalk/carbonate cycles, rift fining-upward clastics and local orogenic-fan fining-upward deposits. Datapacks may eventually replace the metadata when building a different geological profile, but runtime override semantics will be defined alongside the consumer rather than assumed now.

## Validation

`python3 scripts/validate_sedimentary_successions.py` cross-checks the succession file against the live lithology catalog and province profiles. It rejects unknown or non-sedimentary lithologies, unknown province contexts, malformed ordering, invalid thickness ratios, duplicate IDs and accidental runtime activation. The bundled succession set must collectively cover every live sedimentary lithology, including lithologies that have not yet migrated from the ore-style compatibility baseline.
