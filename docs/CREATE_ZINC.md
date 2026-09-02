# Create zinc runtime integration

GeoStrata owns natural zinc occurrence only when Create's `create:raw_zinc` output is present.

The first runtime route is intentionally narrow and uses geology GeoStrata already supports:

- hosts: shale and siltstone;
- provinces: sedimentary basin and rift province;
- body style: stratiform;
- grades: Poor, Medium, Rich and Massive with the shared 1/2/4/8 yield contract.

The carbonate-replacement/skarn routes remain catalogued for later shared skarn geology rather than being approximated with the wrong model.

Zinc loot resolves through `geostrata:provider_outputs/zinc`, whose Create item entry is optional. This keeps the standalone GeoStrata data pack valid when Create is absent. The experimental companion removes Create's native `create:zinc_ore` placed feature only when `create:raw_zinc` is registered.

The initial zinc asset set covers only the two hosts that this route can generate. Other block-state host values fall back to the shale model; no unused host composites or Continuity repeat tiles are shipped for this optional ore.
