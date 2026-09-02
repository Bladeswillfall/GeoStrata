# Ore and mineral system

GeoStrata is staging a geology-driven ore system. The stable occurrence contract
is `data/geostrata/geology/ore_occurrences.json`, loaded and validated with the
rest of the server-data geology graph. Real deposit placement exists behind a
separate disabled-by-default experiment; ordinary GeoStrata worlds remain on
the pre-deposit baseline unless the experiment companion or an explicit server
data override activates it.

## Current implemented boundary

The catalog currently defines coal, iron, copper, gold and emerald. Each
occurrence declares the geological facts that decide where that material is
allowed to exist:

- the mod that owns the material economy;
- the canonical output item;
- valid GeoStrata host lithologies;
- valid geological province contexts;
- one or more reusable deposit styles;
- optional terrain requirements; and
- an optional natural-grade ceiling.

Schema 3 also gives every occurrence a required `generation` profile. This is
the human-editable gameplay/tuning layer rather than a second generator. It can
declare:

- `activationChance` — base probability for a deterministic candidate;
- `candidateGrid` — horizontal/vertical candidate spacing, margins and search
  padding;
- `depthAffinity` — flat or linearly interpolated vertical multipliers;
- `provinceMultipliers` — soft weighting inside already-valid geological
  provinces;
- `biomeMultipliers` — soft environmental bonuses sampled from the surface
  environment above the candidate X/Z; and
- `depositStyleWeights` — relative weights when an occurrence can use several
  formation styles.

The central rule is deliberately asymmetric:

> geology decides whether an occurrence is valid; the generation LUT can tilt
> how often an already-valid occurrence activates.

`hostLithologies`, `provinceContexts` and terrain filters therefore remain hard
geological gates. A biome bonus cannot make coal appear in an invalid province
or an invalid host system. Conversely, biome multipliers are required to be at
least `1.0`: they can make an appropriate environment more rewarding, but they
cannot remove a resource from otherwise valid geology. If multiple matching
biome tags overlap, only the strongest bonus applies rather than multiplying the
bonuses together.

This is the intended extension path for future ores. If a provider adds tin and
GeoStrata already has suitable formation styles, adding tin should primarily be
a new occurrence/LUT entry plus its blocks/assets/provider integration. New
Java worldgen is only justified when the material needs a genuinely new
formation mechanism that the existing style library cannot represent.

The currently supported styles are deliberately limited to `coal_seam`,
`vein`, `micro_vein`, `stratiform`, `disseminated` and
`massive_lens_or_pocket`. Data reload fails if an occurrence references an
unknown lithology, province or style. Use `/geostrata ore <material>` to inspect
the loaded occurrence contract.

### Current baseline tuning

The first calibrated biome affinities are intentionally few and player-readable.
They are bonuses to already-valid geology rather than an attempt to make every
ore correlate with a surface biome:

| Material | Base activation | Candidate grid | Biome bonus |
| --- | ---: | --- | ---: |
| Coal | 80% | 160×160×64 | swamp soils ×1.15 |
| Iron | 50% | 160×160×64 | none |
| Copper | 36% | 160×160×64 | none |
| Gold | 80% | 64×64×64 | badlands soils ×1.15 |
| Emerald | 8% | 32×32×16 | mountain rocks ×1.25 |

Coal's earlier ×1.30 placeholder made an otherwise-valid swamp candidate
activate at the 100% cap (`0.8 × 1.30`). The calibrated ×1.15 value instead
moves the candidate chance from 80% to 92%: a worthwhile prospecting advantage
without turning the biome into a guarantee. Iron's provisional mountain bonus
was removed because it did not express a comparably clear gameplay relationship
and its existing depth profile already strongly rewards appropriate elevations.

These percentages are candidate activation probabilities, not percentages of
chunks containing ore. Hard province eligibility, depth affinity, terrain
filters, body bounds and valid host clipping still reduce actual world
abundance substantially. An unmatched biome uses multiplier `1.0`, so valid
resources remain available outside their special zones.

`OreDepositCandidatePlanner` reads candidate density directly from each
occurrence. For each material and candidate cell it derives one jittered anchor
and one weighted deposit style without consuming Minecraft feature RNG state.
The candidate is therefore stable regardless of chunk generation order. There
are no material-name switches for gold, emerald or future ores in the planner.

`OreDepositGeometry` gives each declared style a distinct body: low-dip coal
sheets, branched tubular veins, small fracture veins, broad stratiform lenses,
sparse disseminated envelopes and compact massive lenses/pockets. The seed
derives the baseline orientation, scale and warp. A nearby fracture-style
`vein` may subsequently have its anchor and main-axis azimuth bound to the
shared tectonic fault field; its dimensions, warp, branches, concentration and
grade logic remain the existing body geometry. The body sampler grades economic
blocks from edge to core, applies stable block-coordinate dithering at grade
boundaries and represents the surrounding halo or disseminated host gaps as
non-economic Trace.

The older anchor-host qualification path remains useful to diagnostics, but
runtime placement deliberately does not read a remote candidate anchor block
from another chunk. Active placement clips each economic voxel to a locally
present host lithology allowed by that material. This avoids chunk-loading/order
hazards and lets a single body cross a geological contact while preserving the
correct host state on each placed ore block.

### Experimental deposit placement

`data/geostrata/geology/ore_deposit_experiment.json` is the explicit activation
boundary. The bundled resource has `enabled=false`. Per-material abundance no
longer lives in this file: it belongs to the occurrence LUT. The experiment file
only carries a global `activationScale` for whole-experiment testing.

For each nearby deterministic candidate, runtime placement now performs the
following sequence:

1. create the occurrence-specific candidate;
2. for fracture-style veins, compute the optional shared-fault binding while
   retaining the original candidate as the owner of eligibility and abundance;
3. reject candidates outside the occurrence's hard geological province list;
4. combine the occurrence's depth, province and surface-biome affinity at the
   original candidate location;
5. apply that affinity to the occurrence's deterministic base activation roll;
6. apply any hard terrain filter; and
7. for candidates that survive those gates, construct the body from the bound
   geometry (if any) and clip economic voxels to valid local host lithologies
   and the current chunk.

Biome affinity is sampled from the active chunk generator's biome source at the
surface environment above the deterministic candidate X/Z. The surface Y comes
from the generator's existing terrain-height query, so cave biomes do not stand
in for the landscape above a deposit. This does not force-load a generated chunk
and does not depend on which neighbouring chunk happened to generate first. It
also lets compatible terrain/biome generators participate through their normal
biome source rather than requiring GeoStrata to hard-code individual terrain
mods.

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
ore generation is still **not suppressed** by the standalone core mod. This
experiment exists to measure body abundance, readability, performance and
economic coverage before GeoStrata is allowed to become the exclusive
generation owner.

### Shared fault-controlled veins

`FaultControlledOrePlanner` is the single structural binding for experimental
`vein` proposals. Candidate cells continue to own the deterministic random roll,
hard province eligibility, terrain requirements and environmental affinity.
The binding may be computed before those cheap gates, but it does not generate a
second candidate, consume a second random stream, or reroll abundance after
moving the eventual body. It keeps the original proposal for candidate-owned
decisions and separately holds the fault-snapped body proposal used only for
geometry after the candidate survives.

A vein whose original anchor lies within 96 blocks of the shared fault family,
and safely inside its owning province rather than near a province boundary, is
projected onto the nearest fault trace at the candidate's actual Y. The planner
then projects two nearby points through the same `TectonicStructuralField`
`nearestFault` primitive and uses their secant as the local strike of the
meandering trace. The existing vein body is re-oriented along that strike.

This deliberately does not create a second ore-specific fracture simulator. The
same fault trace displaces strata, exposes damage-zone breccia, controls
structural diamonds and anchors nearby fracture-style veins. Non-`vein` deposit
styles bypass this binding unchanged. `/geostrata ore <material> candidate`
uses the same planner and reports when a preview is `fault-aligned`.

### Emerald occurrence

Emerald remains tied to mountain/orogenic gameplay without a bespoke emerald
prospecting subsystem. Its occurrence is restricted to `orogenic_belt`, requires
at least 24 blocks of coarse 128-block-scale terrain relief with positive
prominence, and uses the shared `micro_vein` body. Its generation LUT also gives
mountain-tagged terrain a ×1.25 soft activation bonus. Mountains are therefore
better prospecting territory without becoming the only place emerald can exist
inside valid geology.

Valid hosts are, in preferred geological order, schist, shale, marble, gneiss,
limestone and slate. The list deliberately excludes quartzite, igneous rocks and
coarse clastics rather than turning rare edge cases into extra gameplay rules.
Existing parent-aware metamorphism already lets shale/carbonate systems continue
into slate/schist/gneiss and marble in orogenic chunks.

The shared grade contract still registers Poor/Medium/Rich/Massive blocks for
consistent loot, Silk Touch and assets, but emerald declares
`maximumNaturalGrade=rich`. Massive emerald is thus asset/economy compatible but
is not placed by ordinary generation. If a future generic structural-intersection
rule justifies exceptional massive pockets, that can lift the cap without adding
an emerald-only structural system.

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

Graded ores use Continuity's native `repeat` method rather than
neighbour-topology `ctm_compact`. Compact CTM splits an individual block face
into halves and quadrants, which is useful for borders but still repeats
block-local mineral art through the interior of a large deposit. The result
looked connected only in some edge cases while a four-block-wide seam still
visibly repeated every block.

`scripts/generate_ore_continuity_tilesets.py` builds one deterministic, periodic
64x64 mineral field per material and grade, then crops it into a 4x4 set of
sixteen 16x16 tiles. Continuity selects those tiles from world position and face
direction. Adjacent ore blocks therefore show adjacent pieces of one larger
mineral field instead of copies of the same one-block motif.

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
rock-boundary overlays with
`python3 scripts/generate_host_continuity_transitions.py`. Normal CI validates
both committed outputs without requiring Pillow and rejects ore fields that
collapse back into repeated block-local sprites.

`docs/images/host-tiling-preview.png` remains the quick check for host
repetition. `docs/images/ore-ctm-tileset-preview.png` assembles the actual 4x4
repeat field for every material and grade using its default host.

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

1. **complete** — validate phase-one host, province, style and output contracts;
2. **complete** — implement deterministic deposit candidates from world seed and
   geological context without mutating blocks;
3. **complete** — add Poor/Medium/Rich/Massive block, loot, yield and XP behavior
   with Trace as non-economic evidence;
4. **complete** — construct and sample deterministic style-specific bodies,
   concentration, dithered grades and non-economic Trace without mutating blocks;
5. **complete, experimental opt-in** — place deterministic bodies chunk-locally,
   clip them to valid host lithologies and expose conservative abundance tuning
   while leaving the default world and native ore generation unchanged;
6. **complete** — move candidate density, activation, depth bias, province bias,
   biome bonuses and style weights into the schema-3 occurrence LUT so future
   ores do not require material-name worldgen switches;
7. suppress overlapping native generation only when replacement coverage is
   proven by fresh-world abundance/economy tests; and
8. add guarded provider-mod occurrences without transferring ownership of their
   item economies into core GeoStrata.
