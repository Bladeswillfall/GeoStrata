# Possible feature: geological gas and mine damp

Status: exploratory design bookmark. This is not an implementation commitment.

## Relationship to petroleum

This proposal is a sibling to #81, **Geology-driven petroleum systems**:

https://github.com/Bladeswillfall/GeoStrata/pull/81

It shares the same source-rock / burial / thermal-maturity logic. Organic-rich material should be able to remain immature, enter an oil-generating window, or become gas-prone/overmature. Coal-bearing and other carbonaceous successions may also contribute to geological gas potential without making coal placement itself a gas detector.

Gas-specific generation and presentation live here so the petroleum proposal does not silently grow into a second feature.

## Core scope

GeoStrata gas is initially **environmental evidence only**.

The base mod should not add a collectable natural-gas fluid, gas tanks, gas extraction machinery, fuel processing or a gas economy. Industrial use can be reconsidered separately if a real integration later needs it.

There is no dedicated gas detector or prospecting tool. Players discover gas-prone geology through diegetic clues.

## Geological model

Gas evidence should derive from geology rather than random cave decoration. Candidate controls include:

- gas-prone thermal maturity of organic-rich source rocks;
- carbonaceous / coal-bearing sedimentary successions where geologically appropriate;
- migration through permeable strata, faults and fractures;
- sealed pockets and structural/stratigraphic traps;
- leakage paths into caves, mines, seabeds or the surface.

The system should reuse GeoStrata's existing province, stratigraphy and structural fields rather than maintain an independent gas-chunk generator.

## Damp

Include **mine/cave damp** as localized atmospheric evidence of gas accumulation.

The visual target is Minecraft's existing fog language rather than a bespoke volumetric-gas renderer. Entering a gas-bearing pocket should be able to reduce visibility / tint the local view in the same broad way vanilla fog communicates immersion and atmosphere.

Implementation should first test whether the desired localized camera effect can be expressed through Minecraft 1.20.1's existing fog pipeline/hooks. Vanilla fog is primarily camera/environment driven rather than an arbitrary voxel-volume system, so this proposal does **not** commit to a custom volumetric renderer if the native path cannot express local damp cleanly.

Damp is evidence only in the initial scope: no suffocation, poisoning, ignition or explosion simulation is implied by the fog effect.

## Other evidence

Prefer existing Minecraft visual language where useful:

- subtle gas seep particles from fractures or porous surfaces;
- underwater bubble/particle evidence above plausible seabed leaks;
- localized damp/fog in sealed or poorly ventilated underground spaces;
- geological association with source rocks, faults, fractures and mature petroleum/carbonaceous systems.

Evidence should be sparse enough that it remains a clue, not constant cave ambience.

## Ownership boundary

- **GeoStrata:** determines where gas-prone geology and environmental evidence occur.
- **Technology mods:** if gas extraction/use is ever supported, they own machinery, extraction rates, depletion behaviour, storage, processing and fuel economy.

Do not build a GeoStrata gas-extraction API until a real integration demonstrates that one is needed.

## Open questions

- What minimum gas-potential field is needed beyond the thermal-maturity information already required by #81?
- Can localized damp be expressed convincingly with the vanilla 1.20.1 fog pipeline without a custom renderer?
- How should sealed underground spaces be identified cheaply enough for world generation / presentation?
- Which particle/bubble clues add information without turning every gas-bearing area into visual noise?

## Non-goals for now

- no extractable gas resource;
- no gas fluid/block volume simulation;
- no gas tanks or processing;
- no dedicated gas detector;
- no poisoning/suffocation mechanics;
- no gas ignition or explosion system;
- no custom volumetric renderer;
- no independent random gas worldgen detached from GeoStrata geology.

This document exists to keep geological gas/damp scoped separately from petroleum while preserving the shared maturity model and exploration language.
