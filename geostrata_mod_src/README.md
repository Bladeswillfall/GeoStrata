# GeoStrata v0.1 (Source Data Layer)

This folder contains the source files for a Fabric mod metadata + data bundle targeting Minecraft 1.20.1.

## What it does right now

- Declares a loadable Fabric mod id: `geostrata`.
- Ships versioned JSON tables for:
  - Default overworld geology slice layering.
  - Underground layer ranges and allowed host block pools.
  - Zone-based underground block usage policy (what blocks are valid and where).
  - Conquest Reforged remap profile by layer/zone with safe fallback pools.
  - Conquest zone-palette map and material texture-channel map for ore grade visuals.
  - Ore host-rock rules by material.
  - Ore grade schema (`poor`, `medium`, `rich`, `massive`) with yield/XP multipliers.
  - Material authority split (`shared` vs `distinct`) with canonical item family mapping.
  - Controlled ore families and the native worldgen IDs intended for suppression.
  - Per-material deposit profiles for coal, iron, copper, and gold.
  - Optional material activation metadata (currently Create zinc).

## Underground generation scope in v0.1

- GeoStrata defines lithology layers and host block pools for the underground.
- GeoStrata defines which ore materials are valid in which layers/host blocks.
- GeoStrata **does not** alter cave carvers, cave features, or cave decoration in this stage.
- CR remapping uses optional tags (`required: false`) so missing CR block IDs safely fall back to vanilla host blocks.

## What it does not do yet

- It does not yet inject custom worldgen placements by itself.
- It does not yet register custom blocks, loot tables, or recipes.
- It does not yet perform runtime config toggling of other mods.

## Packaging note

- No `.jar` artifacts are committed in this repository state.
- The intent is to keep GeoStrata source/data editable here and package it into a `.jar` later in your release workflow.

The goal of this v0.1 artifact is to establish a stable, versioned integration contract that later KubeJS/datapack/runtime layers can consume.
