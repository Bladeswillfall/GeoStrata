# Optional GeoStrata integrations

This directory is for distributable integration artifacts. Nothing here is required by the standalone GeoStrata jar.

Compatibility work should consume the extension points documented in [`docs/COMPATIBILITY.md`](../docs/COMPATIBILITY.md) before introducing Java adapters or external compile dependencies.

A deep integration may become its own datapack, resource pack or companion mod. Do not keep speculative palettes or registry workspaces here before an adapter consumes them.

## Current artifacts

- [`conquest-reforged/`](conquest-reforged/) — data-only Minecraft 1.20.1 bridge. It classifies a conservative Conquest Reforged natural-rock/soil/sediment palette into GeoStrata's existing replacement tags and adds the selected rock palette to vanilla sculk replacement. It copies no Conquest assets and uses only optional external tag entries.

Geolosys 1.20.1 is Forge-only, so no dead Fabric adapter is kept here. Its prospecting/deposit bridge belongs after GeoStrata has a real Forge loader artifact.
