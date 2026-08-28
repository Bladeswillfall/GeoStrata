# Possible feature: geology-driven petroleum systems

Status: exploratory design bookmark. This is not an implementation commitment.

## Intent

GeoStrata may eventually model naturally occurring petroleum as a consequence of the same geology that produces strata, structures, sediments and mineral deposits. The base mod should remain geological and pre-industrial: it may generate natural hydrocarbons and historically plausible raw materials, but refining, engines, pumpjacks and industrial processing belong to optional integrations.

The core rule is:

> petroleum should be generated from geology, not scattered as an unrelated resource.

## Geological model

A future petroleum system should be able to reason about:

1. organic-rich source rock;
2. sufficient burial/maturity for hydrocarbon generation;
3. migration through permeable rock, fractures or faults;
4. porous reservoir rock;
5. an effective seal/cap rock;
6. structural or stratigraphic traps;
7. leakage paths that may produce natural surface evidence.

This should build on GeoStrata's existing sedimentary successions, province context and structural field rather than introducing an independent random oil-chunk generator.

Thermal maturity should be an explicit geological input capable of distinguishing immature organic-rich material, an oil-generating window and gas-prone/overmature material. Gas-specific manifestations, including damp, belong to the separate geological-gas proposal rather than being implemented here.

## Reservoir geometry

A petroleum reservoir should **follow the actual geometry of its porous host strata and geological trap**, including folding, faulting, dip and stratigraphic boundaries. Do not generate an unrelated ellipsoid/blob and merely treat suitable blocks inside it as petroleum-bearing.

Most reservoir volume remains solid porous host rock. Where the local geology makes free space plausible, worldgen may add bounded cavities/voids within or immediately connected to the reservoir host and populate those spaces with physical crude. Suitable causes include fractures, fault damage, dissolution/karst-style voids, existing cave intersections or other trap geometry that can plausibly hold free liquid. These voids are manifestations of the reservoir, not a second independent oil generator, and should inherit the same finite reservoir accounting.

## Deterministic identity and chunk boundaries

Reservoir identity and immutable geometry should be derived deterministically from the world seed, dimension and a stable coarse reservoir anchor rather than from first-visited chunk order or a runtime-random UUID.

Given the same reservoir identity, GeoStrata should be able to reconstruct the same candidate extent and intersect it with the same host strata, trap, seal and maturity fields. Each chunk generates only its own local slice of that reservoir and must not depend on neighbouring chunks being generated first.

Do not persist a voxel map or block list for the reservoir. Persist only mutable state that cannot be reconstructed from seed and geology, initially the reservoir's **remaining recoverable petroleum in mB**. Immutable geometry, original placement and local manifestations should remain deterministic. In a Fabric 1.20.1 implementation, Minecraft's world-level persistent-state facility is the preferred place for that mutable depletion record unless a narrower native mechanism already exists.

## Reservoir capacity: K.I.S.S.

Do **not** model porosity, permeability or a hidden reservoir-quality score. GeoStrata is building a readable gameplay geology system, not a reservoir-engineering simulation.

If the host geology and trap are suitable, the result is a reservoir with one finite petroleum capacity. Use a simple deterministic capacity rule, such as small / medium / large reservoir classes, informed by the reservoir's geological extent rather than additional hidden simulation statistics.

Host type decides whether the geology can act as a reservoir; it does not carry a per-block oil efficiency, yield or permeability value. Technology mods remain responsible for extraction rate and machine behaviour.

## Base vanilla gameplay interactions

Keep natural petroleum materials readable through ordinary Minecraft conventions rather than adding petroleum-specific tool tiers or hand-extraction mechanics.

- **Bitumen / tar layers:** use 1–8 snow-style layers. They can be broken by hand but hand-breaking yields nothing. A shovel is the harvesting tool and a wooden shovel is sufficient.
- **Snow-style layer economy:** mirror vanilla snow-layer behaviour in base-item terms rather than conserving material. Each added/crafted layer costs the equivalent of **2 bitumen items**, while shovel-harvesting returns **1 bitumen item per layer**. Therefore 1–8 layers cost 2/4/6/8/10/12/14/16 bitumen items respectively and drop 1/2/3/4/5/6/7/8 bitumen items when broken. Rebuilding and then harvesting layers intentionally loses half of the invested bitumen, just as placed snow layers lose half of their snowball-equivalent crafting cost when broken.
- **Layer height / full block:** 1–8 layers occupy 1/8, 1/4, 3/8, 1/2, 5/8, 3/4, 7/8 and one full block of visual/collision height respectively. **Eight layers are the full bitumen block.** There is no separate compacted/storage bitumen block and no separate 9:1 storage recipe.
- **Oil sands:** follow ordinary sand-family interaction. Shovels are the natural fast tool, no minimum tool tier is required, and hand mining can still recover the material.
- **Oil shale:** follow ordinary stone-family interaction. A pickaxe is required for proper drops; wooden tier is sufficient.
- **Petroleum-stained reservoir hosts:** inherit the underlying host block's tool and harvest tier. Petroleum presence never raises the mining tier.
- **Crude oil:** ordinary player interaction is through actual free-fluid source blocks and buckets. Petroleum-bearing solids do not become hand-extractable crude through mining or crafting.

Bitumen should also act as an early-game **adhesive alternative** where a vanilla recipe uses slimeballs as glue. Add explicit alternative recipes rather than placing bitumen in a generic slimeball tag. Initial candidates are Sticky Pistons and Leads. Do not substitute bitumen where slime itself is materially important, such as Slime Blocks or Magma Cream.

Avoid inventing extra waterproofing, fuel, dye or processing systems until a concrete gameplay need justifies them.

## Natural manifestations

Possible world features include:

- oil-bearing or bituminous reservoir rock, especially porous sedimentary hosts;
- oil sands / bituminous sandstone;
- oil shale as kerogen-rich source rock rather than a block containing free crude;
- small free-crude lenses or pockets inside some reservoirs;
- rare larger subsurface crude pools where fractures, cavities or local geometry justify them;
- very rare surface oil seeps or small crude puddles where an underlying system has a migration path to the surface;
- tar / bitumen accumulations and oil-stained sediment around long-lived seeps;
- rare coastal tarballs where offshore or coastal seep geology makes them plausible.

Most petroleum should remain in porous rock. Large hollow underground lakes of crude should be exceptional rather than the default, preserving the exploration appeal of old BuildCraft-style discoveries without making them the geological model.

## Reservoir accounting

Use millibuckets directly as the common petroleum quantity: one mB extracted is one mB depleted. Do not introduce per-mod petroleum units or depletion multipliers unless playtesting demonstrates a real need.

For the initial balance target, cap a single reservoir's **free-fluid crude component at 5,000,000 mB (5,000 buckets)**. This is a provisional maximum, not a promise that large reservoirs commonly reach it.

Petroleum-bearing reservoir rock is separate from that free-fluid cap. A later integration may count surrounding saturated/bituminous host rock toward a larger **recoverable reserve** exposed to an extractor such as Create: Diesel Generators. This lets geology contribute additional petroleum capacity without requiring GeoStrata to generate millions of millibuckets as physical liquid blocks, and gives balancing room above or below the initial 5,000,000 mB free-fluid ceiling.

**Free crude follows vanilla fluid accounting:** one full crude-oil source block is one bucket, i.e. **1,000 mB**. Buckets interact only with actual free-fluid source blocks; flowing crude and petroleum-bearing solid blocks are not alternate bucket sources.

**Petroleum-bearing solids are geological storage, not fluid containers.** Oil-bearing reservoir rock, oil sands, oil shale and stained host rock do not release crude when broken and cannot be bucketed. Their petroleum content remains part of the finite reservoir model. Converting that stored petroleum into crude fluid requires an appropriate extractor supplied by a scoped technology-mod integration, which then withdraws the produced mB from the same reservoir.

GeoStrata should expose geological placement and finite remaining petroleum quantity, but it should **not** define pump rate, depletion curves, pressure decline, productivity, machine yield or late-field behaviour. Those are gameplay/economy decisions owned by the industrial mod doing the extraction. Add a generic GeoStrata pressure/productivity model only if a real integration later proves it necessary.

## Surface evidence and discovery

Surface occurrences should be clues to subsurface geology rather than isolated decoration. A seep, bituminous ground or tar accumulation should imply that a plausible petroleum system exists below or nearby.

Potential controls include sedimentary-basin context, source/reservoir/seal relationships, structural traps, faults or fractures, erosion/exposure and appropriate coastal settings.

Biome alone should not decide petroleum occurrence.

GeoStrata should add **no dedicated oil detector, scanner or prospecting tool**. Base-mod discovery comes from geological/environmental clues; technology mods may provide their own surveying or prospecting mechanics.

## Gameplay boundary

GeoStrata itself should not become an oil-tech mod.

Natural hydrocarbons can exist with limited pre-industrial uses such as waterproofing, sealing or adhesive applications where historically plausible. Industrial extraction and refinement should remain outside the core feature.

Optional integrations may consume the same geological system. For example, Create: Diesel Generators could eventually extract GeoStrata-defined reservoirs instead of relying only on abstract per-chunk oil reserves, while GeoStrata remains useful without that mod installed.

## Compatibility direction

The desired ownership boundary is:

- **GeoStrata:** determines where petroleum exists, finite geological quantity, visible evidence, and how deposits relate to host rock and structure.
- **Technology mods:** determine extraction machinery, extraction rate, depletion behaviour, refining, fuels and downstream processing.

Any compatibility API or data contract should be added only when a real integration needs it.

## Open questions

- How much free liquid should be generated versus represented by saturated host rock?
- Can existing structural-field data identify useful trap geometries cheaply enough during chunk generation?
- Which natural hydrocarbon blocks/fluids justify existing in the base mod versus remaining purely environmental evidence?

## Non-goals for now

- no petroleum worldgen implementation;
- no new crude-oil fluid or blocks yet;
- no refinery, fuel or power progression;
- no dedicated petroleum prospecting tool;
- no porosity/permeability or reservoir-quality simulation;
- no GeoStrata-defined pump/depletion/productivity model;
- no dependency on Create: Diesel Generators or another technology mod;
- no gas/damp implementation in this feature;
- no attempt to replace another mod's oil system until an explicit compatibility design is agreed.

This document exists to preserve the design direction so it can be expanded or rejected deliberately later rather than implemented piecemeal.
