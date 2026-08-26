# Stratigraphic field tuning contract

GeoStrata separates the mathematics of a sedimentary spatial field from the numbers used to tune that field. `data/geostrata/geology/sedimentary_field_profiles.json` is the first explicit tuning contract for the normalized succession model.

The resource is deliberately `metadata_only`. It does not enable correlated world generation, replace blocks, or change the current independent `strata_lens` features. Its values are initial diagnostic parameters so the virtual field can be inspected and profiled before any runtime consumer is approved.

## Continuity profiles

Each succession already declares `continuity` as either `regional` or `local`. Field profiles map those semantic continuity classes to spatial parameters:

- `cycleThicknessBlocks` — the total vertical scale of one complete lower-to-upper motif when visualized as a repeated diagnostic field;
- `maxDip` — maximum vertical change per horizontal block; the actual site dip is deterministically sampled from zero up to this value;
- `warpAmplitudeBlocks` — sinusoidal contact displacement away from the site anchor;
- `warpWavelengthBlocks` — horizontal wavelength of that broad contact warp.

The initial regional profile uses a 48-block motif, maximum dip `0.08`, four blocks of warp and a 192-block wavelength. The local profile uses a 32-block motif, maximum dip `0.16`, five blocks of warp and a 96-block wavelength. These values are conservative starting points for diagnostics, not claims about final geological scale.

No absolute Y level, sea level, biome ID, terrain-generator ID or optional-mod ID appears in the profile. The field remains anchored to deterministic geological province sites, so terrain mods can change relief without redefining the underlying coordinate model.

## Runtime loading and dry-run diagnostics

`SedimentaryFieldProfiles` loads the profile resource through Fabric's server-data resource manager. Datapack overrides are validated again at reload time rather than relying only on CI validation of the bundled resource. The Java parser independently enforces metadata-only status, exact `local`/`regional` continuity coverage, safe spatial limits and the two-block minimum virtual bed thickness.

`/geostrata field` is the first end-to-end read-only consumer of the complete sedimentary model. At the command source's actual X/Y/Z it:

1. samples the deterministic geological province and province-site anchor;
2. selects that site's diagnostic sedimentary succession from the province profile;
3. builds the normalized lower-to-upper contact plan and exact contact ownership;
4. resolves the succession's `local` or `regional` field profile;
5. derives the site-anchored dip/warp field from world seed and site coordinates;
6. samples the virtual cycle, normalized position and lithology owner at the source Y.

The command reports the virtual lithology, succession, province, cycle index, normalized position, structural offset and cycle scale. It is deliberately explicit that this is a virtual model only: it does not inspect generated blocks and it does not place, remove or suppress any blocks.

Together, `/geostrata succession`, `/geostrata column` and `/geostrata field` form progressively more concrete diagnostics: family selection, normalized contact geometry, then full X/Y/Z virtual ownership. This makes it possible to evaluate the intended correlated geology before granting it world-generation authority.

## Validation rules

`scripts/validate_sedimentary_field_profiles.py` cross-checks the profile resource against the live succession metadata. CI requires:

- exact coverage of every continuity value currently used by a succession;
- cycle thickness from 8 to 256 blocks;
- maximum dip no greater than `0.35` vertical blocks per horizontal block;
- warp amplitude no greater than 25% of one cycle;
- warp wavelength at least two complete cycles and no more than 2048 blocks;
- every declared succession bed to retain at least two virtual blocks at that continuity's diagnostic cycle scale;
- `runtimeStatus` to remain `metadata_only`.

These limits are guardrails for the current experiment, not a public compatibility promise. Changing them later is acceptable while the field is diagnostic-only. Once a runtime consumer is enabled, tuning changes will become world-generation compatibility changes and must be treated much more conservatively.

## Activation sequence

The intended path from this metadata to real blocks is staged:

1. validate and load the profiles as server data;
2. expose a read-only diagnostic showing the virtual bed owner at the player's X/Y/Z;
3. compare the virtual field with fresh-world terrain and cave exposures;
4. only then introduce an opt-in correlated succession generator;
5. remove overlapping independent sedimentary features only after the correlated generator is demonstrated to preserve standalone compatibility and acceptable abundance.

Steps 1 and 2 are now implemented. The next engineering milestone is therefore an explicitly opt-in runtime experiment, not silent activation of the field in ordinary worlds.

This order keeps GeoStrata's standalone Fabric contract intact and gives terrain/biome integrations a stable data seam before the geology model becomes more authoritative.
