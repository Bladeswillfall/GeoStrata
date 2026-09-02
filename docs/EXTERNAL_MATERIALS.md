# External material compatibility catalogue

GeoStrata should eventually own the **natural geological occurrence** of supported ores and minerals while the detected provider mod continues to own items, recipes, processing and progression.

The machine-readable backlog is:

```text
src/main/resources/data/geostrata/compatibility/external_materials.json
```

It is planning metadata only. Nothing in the catalogue enables a dependency, registers a block, suppresses another mod's worldgen, or causes GeoStrata to place provider-owned material yet.

## Canonical material first

Provider support should not duplicate geology. Schema 2 separates two concerns:

1. `canonicalMaterials` describe **where and how a material can form**;
2. `providers` describe **which installed mod supplies the blocks/items for that material**.

If two installed mods both provide lead, GeoStrata therefore has one canonical `lead` geological model and two provider mappings. It must not generate independent TFMG-lead and Create-Nuclear-lead deposits.

Each canonical material owns one or more `formationRoutes`. A route binds together:

- current GeoStrata host lithologies;
- future host roles that must not be faked with an unrelated current rock;
- allowed geological provinces;
- one or more existing deposit-body styles; and
- any shared geological context that must exist before the route can be promoted to runtime generation.

This prevents a flat `hosts × provinces × styles` cross-product from producing combinations that were never intended. A future runtime occurrence should consume the same route concept rather than independently choosing a host, province and body style.

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

- **Pegmatite system** — granite-linked late felsic veins/lenses. Reuse existing vein/lens/disseminated geometry. Main beneficiaries: tin, lithium, thorium.
- **Mafic/ultramafic intrusive system** — future gabbro/peridotite-style hosts using existing disseminated and massive-lens ore geometry. Main beneficiaries: nickel and magnetite.
- **Skarn/contact replacement** — reuse existing intrusion/contact-metamorphism context plus massive/disseminated/vein geometry. Main beneficiaries: magnetite, zinc and lead.
- **Sandstone redox mineralisation** — initially reuse stratiform/disseminated geometry inside sandstone rather than implementing chemical transport simulation. Main beneficiary: uranium.
- **Banded iron-formation system** — recorded as deferred rather than required for the first magnetite implementation.

None of those currently requires a brand-new ore-body shape. The new work is chiefly **geological context and host generation**, not another collection of blob generators.

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
