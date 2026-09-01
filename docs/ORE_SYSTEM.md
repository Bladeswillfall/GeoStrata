# Ore and mineral system

GeoStrata is staging a geology-driven ore system. The stable occurrence contract
is `data/geostrata/geology/ore_occurrences.json`, loaded and validated with the
rest of the server-data geology graph. Real deposit placement now exists behind
a separate disabled-by-default experiment; ordinary GeoStrata worlds remain on
the pre-deposit baseline unless a datapack explicitly opts in.

## Current implemented boundary

The catalog currently defines coal, iron, copper, gold and emerald. Emerald is the first
occurrence to add an explicit terrain filter and natural-grade ceiling. Each occurrence declares:

- the mod that owns the material economy;
- the canonical output item;
- valid GeoStrata host lithologies;
- valid geological province contexts; and
- one or more deposit styles.

The supported styles are deliberately limited to `coal_seam`, `vein`,
`stratiform`, `disseminated` and `massive_lens_or_pocket`. Data reload fails if
an occurrence references an unknown lithology, province or style. Use
`/geostrata ore <material>` to inspect the loaded occurrence contract.

`OreDepositCandidatePlanner` divides the world into deterministic 256×256×64
candidate cells. For each material and cell it derives one jittered anchor and
one style from the occurrence's allowed styles without consuming Minecraft
feature RNG state. The candidate cell is therefore stable regardless of chunk
generation order.

`OreDepositGeometry` gives each declared style a distinct body: low-dip coal
sheets, branched tubular veins, broad stratiform lenses, sparse disseminated
envelopes and compact massive lenses/pockets. The seed derives the baseline
orientation, scale, warp and the two restrained side branches used by veins. A
nearby fracture-style vein may subsequently have its anchor and main-axis
azimuth bound to the shared tectonic fault field; its dimensions, warp, branches,
concentration and grade logic remain the existing body geometry. The body sampler
grades economic blocks from edge to core, applies stable block-coordinate
dithering at grade boundaries and represents the surrounding halo or
disseminated host gaps as non-economic Trace.

The older anchor-host qualification path remains useful to diagnostics, but
runtime placement deliberately does not read a remote candidate anchor block
from another chunk. Active placement first checks the candidate's geological
province, then clips each economic voxel to a locally present host lithology
allowed by that material. This avoids chunk-loading/order hazards and lets a
single body cross a geological contact while preserving the correct host state
on each placed ore block.

### Experimental deposit placement

`data/geostrata/geology/ore_deposit_experiment.json` is the explicit activation
boundary. The bundled resource has `enabled=false`. A test datapack may replace
that resource with `enabled=true`; no code or client resource pack is required.
The feature is registered across the overworld but returns immediately while the
contract is disabled, so the normal generated-world baseline is unchanged.

The initial activation probability is intentionally conservative and applies to
each material's deterministic 256×256×64 candidate cell before province and
host clipping:

| Material | Candidate activation |
| --- | ---: |
| Coal | 4.0% |
| Iron | 2.5% |
| Copper | 1.8% |
| Gold | 0.8% |
| Emerald | 0.4% |

These are experiment values, not a promised economy. The bodies are much larger
than vanilla single-feature ore blobs, so high candidate frequency would swamp
the graded yield model before abundance has been measured in fresh worlds.

Placement is chunk-local. Every generated chunk re-evaluates the nearby stable
candidate cells, builds any active body whose conservative bounds intersect the
chunk, samples only the clipped local volume, and writes only that chunk's
valid-host economic voxels. It never reaches into a neighboring chunk to finish
a body. The neighboring chunk independently reaches the same deterministic
answer when it generates, which removes cross-chunk mutation ordering from the
shape.

Direct GeoStrata rock blocks resolve their lithology from the loaded catalog. If
the separate correlated sedimentary experiment owns a chunk, ore placement can
also resolve the correlated field against its vanilla host-replacement tag. The
virtual result is the authoritative final correlated output, including
parent-aware metamorphism and overturned stratigraphy, rather than merely the
parent sedimentary bed. That allows an ore voxel to acquire the correct future
host identity even when ore placement runs before the companion sedimentary
replacement feature; the later sedimentary pass will not overwrite the graded
ore block.

Trace remains evidence-only and does not place a block. Vanilla/provider-native
ore generation is still **not suppressed**. This experiment exists to measure
body abundance, readability, performance and economic coverage before GeoStrata
is allowed to become the exclusive generation owner.

### Shared fault-controlled veins

`FaultControlledOrePlanner` is the single structural binding for experimental
`vein` proposals. Candidate cells continue to own abundance: activation is
resolved from material plus cell coordinates before structural binding, so
moving a vein onto a fault cannot reroll whether that deposit exists.

A vein whose original anchor lies within 96 blocks of the shared fault family,
and safely inside its owning province rather than near a province boundary, is
projected onto the nearest fault trace at the candidate's actual Y. The planner
then projects two nearby points through the same `TectonicStructuralField`
`nearestFault` primitive and uses their secant as the local strike of the
meandering trace. The existing vein body is re-oriented along that strike.

This deliberately does not create a second ore-specific fracture simulator. The
same fault trace displaces strata, exposes damage-zone breccia, controls
structural diamonds and anchors nearby fracture-style veins. Non-vein deposit
styles bypass this binding unchanged. `/geostrata ore <material> candidate`
uses the same planner and reports when a preview is `fault-aligned`.

### Emerald occurrence

Emerald remains tied to mountain/orogenic gameplay without a bespoke emerald prospecting
subsystem. Its occurrence is restricted to `orogenic_belt`, requires at least 24 blocks of
coarse 128-block-scale terrain relief with positive prominence, and uses the shared `vein`
body. This means terrain tells the player which mountain belts are worth exploring while the
local host rock clips the vein naturally.

Valid hosts are, in preferred geological order, schist, shale, marble, gneiss, limestone and
slate. The list deliberately excludes quartzite, igneous rocks and coarse clastics rather than
turning rare edge cases into extra gameplay rules. Existing parent-aware metamorphism already
lets shale/carbonate systems continue into slate/schist/gneiss and marble in orogenic chunks.

The shared grade contract still registers Poor/Medium/Rich/Massive blocks for consistent loot,
Silk Touch and assets, but emerald declares `maximumNaturalGrade=rich`. Massive emerald is thus
asset/economy compatible but is not placed by ordinary generation. If a future generic
structural-intersection rule justifies exceptional massive pockets, that can lift the cap
without adding an emerald-only structural system.

Emerald uses the same `FaultControlledOrePlanner` as other fracture-style veins.
A qualifying candidate near a real Orogenic fault therefore snaps to that shared
fault trace and follows its local meandering strike; a candidate too far from a
fault retains the ordinary deterministic vein geometry. Emerald does not own a
parallel fault simulator.

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

Every graded ore block stores a stable `host` block-state property. Its model
selects one flat 16x16 composite texture for that host, material and grade; the
renderer does not stack raised ore panels over a generic rock. Silk Touch copies
the host property into the dropped block item so replacing it preserves the
correct host appearance.

`data/geostrata/materials/ore_texture_matrix.json` is the artist-facing source
of truth. It declares all rock hosts, the four ordered density targets, each
material's default item-model host and the subset of hosts that geology may
actually generate. `scripts/generate_ore_texture_matrix.py` combines:

1. one native 16x16 host tile in `textures/block/host`;
2. one native 16x16 dense mineral source in `ore_source/master`;
3. nested Poor, Medium, Rich and Massive masks; and
4. a restrained one-pixel integration rim derived from the host.

The script writes the transparent grade overlays, all host/material/grade
composites, block models, blockstates and item-model defaults. Generated PNG and
JSON assets are committed; Pillow is an authoring dependency only and is not
required by Minecraft or a resource pack. Repository validation requires exact
16x16 dimensions, complete model coverage, increasing density and agreement
between `validHosts` and the runtime ore-occurrence catalog.

The bundled host and mineral tiles are placeholder art in a restrained vanilla
Minecraft visual language. They are deliberately separated from generated
composites so an artist can replace a host or mineral source and regenerate the
whole matrix without editing hundreds of final textures by hand.

Every host source is also checked as a self-contained seamless 16x16 tile. Its
wrap-edge contrast may not exceed the ordinary contrast inside the texture by a
conspicuous margin, preventing a bright or dark grid from appearing across a
large exposure. Ore composites inherit the same host edge, so they remain
compatible with adjacent host blocks and with normal Minecraft rendering.

The development pack already includes Continuity `3.0.0+1.20.1` and Indium.
When Continuity is active, generated `method=random` definitions select one of
four subtle variants for each host. Only the inner 12x12 pixels vary; the
two-pixel edge guard is byte-identical to the base host. This reduces obvious
single-tile repetition without creating a seam where a varied host touches a
fallback host or a graded ore composite. Continuity remains a client-side visual
enhancement rather than a core GeoStrata dependency: without it, the base tile
and all host/material/grade composites still render normally.

### Dithered host transitions

Continuity adds a narrow, two-pixel dither at exposed boundaries between
different GeoStrata rock blocks. This is an overlay-only visual treatment: it
does not alter block states, geology, collision, mining, or world generation.

`scripts/generate_host_continuity_transitions.py` derives the overlay sprites
directly from the existing 16x16 host textures. One overlay rule per host is
shared against every other GeoStrata host, so a contact borrows a few pixels
from the neighbouring rock instead of ending on a hard one-pixel line. Normal
host `method=random` rules use canonical texture paths and `prioritize=false`,
allowing the boundary overlay to run before the subtle interior variation.

### Spatial multi-block ore tilesets

Graded ores use Continuity's native `repeat` method rather than neighbour-topology
`ctm_compact`. Compact CTM splits an individual block face into halves and
quadrants, which is useful for borders but still repeats block-local mineral art
through the interior of a large deposit. The result looked connected only in
some edge cases while a four-block-wide seam still visibly repeated every block.

`scripts/generate_ore_continuity_tilesets.py` now builds one deterministic,
periodic 64x64 mineral field per material and grade, then crops it into a 4x4
set of sixteen 16x16 tiles. Continuity selects those tiles from world position
and face direction. Adjacent ore blocks therefore show adjacent pieces of one
larger mineral field instead of copies of the same one-block motif.

The four grades share the same material-specific spatial score field and differ
only by their declared density target, so Poor, Medium, Rich and Massive remain
visually related as concentration increases. Each repeat tile is also guaranteed
a small visible mineral sample at low density so a legitimate ore block cannot
become visually empty.

The same mineral field is composited independently against every valid host.
When a deposit crosses from one host rock to another, the background changes but
the mineral coordinates remain aligned naturally; no `connect=block` rule or
second rendering system is required. Flat host/material/grade composites remain
the renderer-independent fallback when Continuity is absent.

Generated ore assets are covered by schema 3 of
`data/geostrata/materials/ore_ctm_manifest.json`: `method=repeat`, a 4x4 field,
and sixteen runtime tiles per material/grade/host combination. Regenerate ores
with `python3 scripts/generate_ore_continuity_tilesets.py`; regenerate
rock-boundary overlays with `python3 scripts/generate_host_continuity_transitions.py`.
Normal CI validates both committed outputs without requiring Pillow and rejects
ore fields that collapse back into repeated block-local sprites.

`docs/images/host-tiling-preview.png` remains the quick check for host
repetition. `docs/images/ore-ctm-tileset-preview.png` now assembles the actual
4x4 repeat field for every material and grade using its default host.

![Host, mineral and grade authoring matrix](images/ore-texture-matrix-preview.png)

![Seamless host tiling and Continuity variation](images/host-tiling-preview.png)

![Spatial multi-block ore fields](images/ore-ctm-tileset-preview.png)

Full-bright cells are currently permitted by geological occurrence rules. Dim
cells are generated and asset-ready but will not occur naturally unless the
occurrence catalog is expanded later.

## Ownership and compatibility

GeoStrata is the intended overworld generation owner for every enabled ore or
mineral occurrence. The provider mod continues to own the material's item,
recipes and processing economy. Phase one uses Minecraft outputs; a future
provider-backed entry such as Create zinc should remain dormant unless its
provider is installed, then generate through GeoStrata while dropping the
provider-compatible material.

Provider-native ore generation must only be suppressed after the experimental
replacement deposits have demonstrated acceptable availability and economy.
Suppressing first would create worlds with missing resources. External host-rock
compatibility should extend a stable host/semantic contract rather than replace
registered block IDs after startup.

## Staged implementation path

1. **complete** — validate phase-one host, province, style and output contracts.
2. **complete** — implement deterministic deposit candidates from world seed
   and geological context without mutating blocks;
3. **complete** — add Poor/Medium/Rich/Massive block, loot, yield and XP behavior
   with Trace as non-economic evidence;
4. **complete** — construct and sample deterministic style-specific bodies,
   concentration, dithered grades and non-economic Trace without mutating blocks;
5. **complete, experimental opt-in** — place deterministic bodies chunk-locally,
   clip them to valid host lithologies and expose conservative abundance tuning
   while leaving the default world and native ore generation unchanged;
6. suppress overlapping native generation only when replacement coverage is
   proven by fresh-world abundance/economy tests; and
7. add guarded provider-mod occurrences without transferring ownership of their
   item economies into core GeoStrata.
