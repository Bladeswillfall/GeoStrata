# Possible feature: geological natural gas and firedamp

Status: exploratory design bookmark. This is not an implementation commitment.

## Relationship to petroleum

This proposal is a sibling to #81, **Geology-driven petroleum systems**:

https://github.com/Bladeswillfall/GeoStrata/pull/81

It shares the same source-rock, burial and thermal-maturity logic. Organic-rich material may remain immature, enter an oil-generating window, or become gas-prone/overmature. Coal-bearing and other carbonaceous successions may also contribute where geologically appropriate without making coal placement itself a gas detector.

Gas-specific presentation remains separate so the petroleum proposal does not grow into a second gameplay system.

## Core scope

GeoStrata natural gas is initially **geological/environmental evidence only**.

The base mod should not add a collectable gas fluid, gas blocks, tanks, extraction machinery, processing, fuel economy, hazards or a dedicated detector. Industrial use can be reconsidered only when a real integration demonstrates a need for it.

The initial geological-gas concept is methane-dominant natural gas and its underground **firedamp** manifestation. Other historical mine damps such as blackdamp, whitedamp, afterdamp and stinkdamp are outside this proposal; modelling them would require atmosphere, ventilation or combustion systems that GeoStrata does not need.

## Geological model

Natural-gas potential should derive from the same geology already required by petroleum rather than from an independent gas generator.

At minimum it may depend on:

- gas-prone thermal maturity of organic-rich source material;
- carbonaceous / coal-bearing successions where appropriate;
- GeoStrata's existing province, stratigraphy and structural context.

Keep this as a deterministic, recomputable geological field until a real gameplay consumer requires finite reservoir state. Do not persist gas quantity, pressure or depletion state before anything can consume it.

## Firedamp presentation

Rare intersections between strongly gas-prone geology and underground air may manifest as firedamp.

The intended visual language is **low-lying, ground-hugging mist**, not Minecraft's camera-distance fog that hides distant terrain. Prefer a lightweight translucent particle effect or similarly simple existing atmospheric mechanism. The mist should sit close to cave or mine floors, drift subtly and remain sparse enough to read as an unusual geological clue rather than normal cave ambience.

The mist is visual shorthand for an otherwise invisible gas accumulation; it is not a claim that methane itself is visibly foggy.

Do not build gas blocks, voxel volumes, cave flood-fill, sealed-space simulation or a custom volumetric renderer to support this effect. A deterministic local firedamp region plus client-side presentation is sufficient for the initial concept.

## Ownership boundary

- **GeoStrata:** determines where gas-prone geology and rare firedamp evidence occur.
- **Technology mods:** if gas extraction/use is ever supported, they own machinery, extraction rates, depletion behaviour, storage, processing and fuel economy.

Do not build a GeoStrata gas-extraction API until a real integration demonstrates that one is needed.

## Non-goals for now

- no extractable gas resource;
- no gas fluid/block simulation;
- no persistent gas reservoir state;
- no gas tanks or processing;
- no dedicated gas detector;
- no poisoning or suffocation mechanics;
- no gas ignition or explosion system;
- no ventilation or mine-atmosphere simulation;
- no multiple historical damp types;
- no custom volumetric renderer;
- no independent random gas worldgen detached from GeoStrata geology.

This document exists to keep natural gas/firedamp small, geology-driven and separate from petroleum while preserving the shared maturity model and leaving future integrations to justify any additional complexity.
