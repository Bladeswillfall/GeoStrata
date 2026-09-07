# External material compatibility catalogue

GeoStrata owns the **natural geological occurrence** of supported runtime ores and minerals while the detected provider mod continues to own items, recipes, processing and progression.

The machine-readable planning backlog is:

```text
src/main/resources/data/geostrata/compatibility/external_materials.json
```

That catalogue remains planning metadata: it does not itself enable dependencies, register blocks, or place provider-owned material.

Runtime provider occurrences live separately in:

```text
src/main/resources/data/geostrata/geology/external_ore_occurrences.json
```

Those occurrences are part of the normal core ore runtime. At reload, GeoStrata keeps only occurrences whose provider output is actually registered, selects the first available provider from ordered fallbacks, and leaves absent providers absent. Fabric core worldgen suppresses only the verified native ore features associated with a registered provider output; provider machinery, recipes, processing and progression remain untouched. No experiment companion is required and the standalone GeoStrata jar still loads with none of the optional providers installed.

## Canonical material first

Provider support should not duplicate geology. Schema 2 separates two concerns:

1. `canonicalMaterials` describe **where and how a material can form**;
2. `providers` describe **which installed mod supplies the blocks/items for that material**.

If two installed mods both provide lead, GeoStrata therefore has one canonical `lead` geological model and two provider mappings. It must not generate independent TFMG-lead and Create-Nuclear-lead deposits.

Each canonical material owns one or more `formationRoutes`. A route binds together:

- current GeoStrata host lithologies;
- future host roles that must not be faked with an unrelated current rock;
- allowed geological provinces;
- optional semantic `bodyStyles` when a route depends on an existing geological structure rather than merely a rock name;
- one or more existing deposit-body styles; and
- any shared geological context that must exist before the route can be promoted to runtime generation.

This prevents a flat `hosts × provinces × body styles × deposit styles` cross-product from producing combinations that were never intended. The runtime occurrence contract uses the same route concept rather than independently choosing those properties.

## Create-family first tranche

| Canonical material | Provider(s) in this tranche | Main formation routes |
| --- | --- | --- |
| Zinc | Create | carbonate replacement; clastic sediment-hosted; later skarn/contact |
| Tin | Create: Dreams & Desires | granite hydrothermal; pegmatite/greisen |
| Thorium | Create: New Age | felsic accessory; pegmatite accessory |
| Magnetite | Create: New Age | mafic magmatic; skarn/contact; later banded formation |
| Lead | TFMG; Create: Nuclear | carbonate replacement; clastic sediment-hosted; later skarn/contact |
| Nickel | TFMG | mafic disseminated; ultramafic sulfide |
| Lithium | TFMG | granitic pegmatite |
| Uranium | Create: Nuclear | felsic hydrothermal; sandstone redox |

The route catalogue deliberately reuses GeoStrata's existing `vein`, `micro_vein`, `stratiform`, `disseminated` and `massive_lens_or_pocket` geometry wherever those shapes are sufficient. New Java ore-body geometry is not justified merely because a new metal is added.

## Core geology exposed by the ore work

Compatibility is also revealing reusable gaps in the core geology model. These are **not conditional Create features**.

### Igneous lithologies

- **Andesite** is now a current GeoStrata lithology backed by `minecraft:andesite`. The Volcanic Arc runtime places it as the intermediate outer/shallow shell around evolved rhyolite cores, whether Create is installed or not.
- **Granite** is already a current GeoStrata lithology backed by `minecraft:granite` and is now available to formation-route planning.
- **Diorite** is already a current GeoStrata lithology backed by `minecraft:diorite`.

Create raised the priority of andesite, but it does not own the rock or gate its geology.

### Shared formation contexts

The catalogue records shared geological work instead of inventing a generator per ore:

- **Pegmatite system — implemented context.** The existing Volcanic Arc pluton now labels the evolved outer part of its granite core as `pegmatite_fertile_margin`. Formation routes can require that semantic body style, while reusing existing vein/lens/disseminated ore geometry. This adds no new pegmatite block, noise field or independent generation pass. A distinct pegmatite lithology can still be added later if it earns its gameplay/visual cost. Main beneficiaries: tin, lithium, thorium.
- **Mafic/ultramafic intrusive system** — implemented gabbro/peridotite hosts reuse the Volcanic Arc complex and existing disseminated/massive-lens ore geometry. Main beneficiaries: nickel and magnetite.
- **Skarn/contact replacement** — reuse existing intrusion/contact-metamorphism context plus massive/disseminated/vein geometry. Main beneficiaries: magnetite, zinc and lead.
- **Sandstone redox mineralisation** — initially reuse stratiform/disseminated geometry inside sandstone rather than implementing chemical transport simulation. Main beneficiary: uranium.
- **Banded iron-formation system** — recorded as deferred rather than required for the first magnetite implementation.

None of these contexts requires a brand-new ore-body shape. The new work is chiefly **geological context and host generation**, not another collection of blob generators.

## Gameified-realism rule

The aim is for geology to create player-readable clues without requiring a geology degree.

Examples:

- granite country plus coarse late intrusive veins can become a tin/lithium clue;
- limestone/marble near intrusive contact zones can become magnetite/zinc/lead territory;
- mafic/ultramafic bodies can become the strong nickel clue;
- sandstone basins can support a distinct uranium route rather than making uranium a generic deep-rock ore.

Those relationships should increase the value of understanding the world while keeping resources available through more than one plausible route where gameplay needs it.

## Promotion sequence

For each provider material, runtime support is complete only after all of the following are true:

1. provider detection is loader-safe and optional;
2. provider block/output IDs or common tags are verified for the supported Minecraft/mod version;
3. the canonical material is promoted into the runtime occurrence/LUT contract using its validated formation routes;
4. required host lithologies and shared geology contexts exist, or the initial implementation deliberately enables only the valid subset of routes;
5. GeoStrata can emit/interoperate with the provider's economy output without copying its processing system;
6. replacement generation is benchmarked for availability, discoverability and generation cost; and
7. provider-native natural worldgen is suppressed only after the replacement path is proven safe.

The standalone GeoStrata jar must still load with none of these providers installed.

## Version caveat

Create-family addons change registry IDs and loader support between releases. This catalogue targets the GeoStrata development baseline (Minecraft 1.20.1) unless an entry is later made version-specific. Registry IDs are compatibility inputs that must be re-verified before an adapter is promoted from `catalogued` planning metadata to runtime support.
