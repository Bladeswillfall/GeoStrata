# Optional GeoStrata integrations

This directory is for integration-specific source/reference material. Nothing here is required by the standalone GeoStrata jar.

Compatibility work should consume the extension points documented in [`docs/COMPATIBILITY.md`](../docs/COMPATIBILITY.md) before introducing Java adapters or external compile dependencies.

A deep integration may eventually become its own distributable datapack/resource pack or companion mod. Keeping that content separate prevents optional mods from leaking into core registries and worldgen.
