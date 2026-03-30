# GeoStrata v0.1 (Source Data Layer)

This folder contains the source files for a Fabric mod metadata + data bundle targeting Minecraft 1.20.1.

## What it does right now

- Declares a loadable Fabric mod id: `geostrata`.
- Ships versioned JSON tables for:
  - Default overworld geology slice layering.
  - Ore grade schema (`poor`, `medium`, `rich`, `massive`) with yield/XP multipliers.
  - Material authority split (`shared` vs `distinct`) with canonical item family mapping.
  - Controlled ore families and the native worldgen IDs intended for suppression.
  - Per-material deposit profiles for coal, iron, copper, and gold.
  - Optional material activation metadata (currently Create zinc).

## What it does not do yet

- It does not yet inject custom worldgen placements by itself.
- It does not yet register custom blocks, loot tables, or recipes.
- It does not yet perform runtime config toggling of other mods.

## Packaging note

- No `.jar` artifacts are committed in this repository state.
- The intent is to keep GeoStrata source/data editable here and package it into a `.jar` later in your release workflow.

The goal of this v0.1 artifact is to establish a stable, versioned integration contract that later KubeJS/datapack/runtime layers can consume.
