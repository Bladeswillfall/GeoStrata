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

## Surface evidence

Surface occurrences should be clues to subsurface geology rather than isolated decoration. A seep, bituminous ground or tar accumulation should imply that a plausible petroleum system exists below or nearby.

Potential controls include sedimentary-basin context, source/reservoir/seal relationships, structural traps, faults or fractures, erosion/exposure and appropriate coastal settings.

Biome alone should not decide petroleum occurrence.

## Gameplay boundary

GeoStrata itself should not become an oil-tech mod.

Natural hydrocarbons can exist with limited pre-industrial uses such as waterproofing, sealing or adhesive applications where historically plausible. Industrial extraction and refinement should remain outside the core feature.

Optional integrations may consume the same geological system. For example, Create: Diesel Generators could eventually extract GeoStrata-defined reservoirs instead of relying only on abstract per-chunk oil reserves, while GeoStrata remains useful without that mod installed.

## Compatibility direction

The desired ownership boundary is:

- **GeoStrata:** determines where petroleum exists, what geological evidence is visible, and how deposits relate to host rock and structure.
- **Technology mods:** determine extraction machinery, refining, fuels and downstream processing.

Any compatibility API or data contract should be added only when a real integration needs it.

## Open questions

- What source-rock maturity approximation is useful without simulating geological time?
- How should reservoir porosity/permeability be represented without adding excessive block-state or worldgen cost?
- How much free liquid should be generated versus represented by saturated host rock?
- Can existing structural-field data identify useful trap geometries cheaply enough during chunk generation?
- How should finite reservoir volume be represented for optional extractors while remaining deterministic across chunk-generation order?
- Which natural hydrocarbon blocks/fluids justify existing in the base mod versus remaining purely environmental evidence?

## Non-goals for now

- no petroleum worldgen implementation;
- no new crude-oil fluid or blocks yet;
- no refinery, fuel or power progression;
- no dependency on Create: Diesel Generators or another technology mod;
- no attempt to replace another mod's oil system until an explicit compatibility design is agreed.

This document exists to preserve the design direction so it can be expanded or rejected deliberately later rather than implemented piecemeal.