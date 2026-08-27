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
frequency and inter-deposit spacing remain deliberately unset. No ore blocks are registered or
placed, grades are not assigned, and vanilla ore generation is not suppressed.
The current baseline therefore remains unchanged.

## Grade contract

`trace` is reserved for non-economic evidence. The four economic grade names
are fixed, in ascending order:

1. `poor`
2. `medium`
3. `rich`
4. `massive`

The catalog intentionally marks yield and experience behavior as
`not_implemented`. Numeric drop and XP values should be introduced together
with the grade block/loot implementation so metadata cannot imply gameplay that
does not exist.

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
3. add Poor/Medium/Rich/Massive block, loot, yield and XP behavior with Trace as
   non-economic evidence;
4. activate deposits behind an explicit experimental boundary and evaluate
   abundance, readability and performance in fresh worlds;
5. suppress overlapping native generation only when replacement coverage is
   proven; and
6. add guarded provider-mod occurrences without transferring ownership of their
   item economies into core GeoStrata.
