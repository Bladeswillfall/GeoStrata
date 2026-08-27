# Ore and mineral system

GeoStrata is now staging a geology-driven ore system. The first live contract is
`data/geostrata/geology/ore_occurrences.json`, loaded and validated with the rest
of the server-data geology graph.

## Current implemented boundary

The catalog currently defines the phase-one materials coal, iron, copper and
gold. Each occurrence declares:

- the mod that owns the material economy;
- the canonical output item;
- valid GeoStrata host lithologies;
- valid geological province contexts; and
- one or more deposit styles.

The supported styles are deliberately limited to `coal_seam`, `vein`,
`stratiform`, `disseminated` and `massive_lens_or_pocket`. Data reload fails if
an occurrence references an unknown lithology, province or style. Use
`/geostrata ore <material>` to inspect the loaded occurrence contract.

`OreDepositCandidatePlanner` now divides the world into deterministic
256×256×64 candidate cells. For each material and cell it derives one jittered
anchor and one style from the occurrence's allowed styles, without consuming
Minecraft feature RNG state. The proposal becomes eligible only when the
anchor's geological province and host lithology both match the occurrence
contract. `/geostrata ore <material> candidate` exposes the proposal and its
eligibility at the player's current 3D cell.

This is candidate planning, not deposit activation. Every cell can be inspected
without implying that every proposal will become a deposit; activation
frequency and inter-deposit spacing remain deliberately unset. Grade blocks and
their mining economy now exist, but no candidate places them and vanilla ore
generation is not suppressed. The generated-world baseline therefore remains
unchanged.

## Grade contract

`trace` is reserved for non-economic evidence. The four economic grade names
are fixed, in ascending order:

1. `poor`
2. `medium`
3. `rich`
4. `massive`

Every phase-one material has one registered block for each economic grade.
Grades share the same economics across materials:

| Grade | Base output | Mining XP |
| --- | ---: | ---: |
| Poor | 1 | 0–1 |
| Medium | 2 | 1–2 |
| Rich | 4 | 2–4 |
| Massive | 8 | 4–8 |

The block loot tables apply Minecraft's standard `ore_drops` Fortune formula.
Silk Touch returns the exact graded block and suppresses mining XP through the
normal Minecraft mining path. Material still determines the canonical output
and minimum tool tier; host rock does not alter yield or XP.

These numbers are a fixed core contract at this stage, not independently
reloadable tuning. Resource reload validates the declared values, and repository
validation cross-checks them against every bundled loot table. Making economics
fully datapack-driven would require a custom loot function so actual drops cannot
drift from the catalog.

The bundled models use a GeoStrata placeholder host texture plus increasingly
large vanilla-material inclusions, making grade readable before production
host-specific texture sets exist. This is an explicit placeholder asset
boundary, not the final art direction.

## Ownership and compatibility

GeoStrata is the intended overworld generation owner for every enabled ore or
mineral occurrence. The provider mod continues to own the material's item,
recipes and processing economy. Phase one uses Minecraft outputs; a future
provider-backed entry such as Create zinc should remain dormant unless its
provider is installed, then generate through GeoStrata while dropping the
provider-compatible material.

Provider-native ore generation must only be suppressed after GeoStrata's
replacement deposits are active and validated. Suppressing first would create
worlds with missing resources. External host-rock compatibility should extend a
stable host/semantic contract rather than replace registered block IDs after
startup.

## Staged implementation path

1. **complete** — validate phase-one host, province, style and output contracts.
2. **complete** — implement deterministic deposit candidates from world seed
   and geological context without mutating blocks;
3. **complete** — add Poor/Medium/Rich/Massive block, loot, yield and XP behavior
   with Trace as non-economic evidence;
4. activate deposits behind an explicit experimental boundary and evaluate
   abundance, readability and performance in fresh worlds;
5. suppress overlapping native generation only when replacement coverage is
   proven; and
6. add guarded provider-mod occurrences without transferring ownership of their
   item economies into core GeoStrata.
