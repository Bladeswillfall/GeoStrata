# Create: Dreams & Desires tin runtime integration

GeoStrata owns natural tin occurrence only when `create_dd:raw_tin` is present. The provider retains its item, recipes and processing chain.

Tin uses two existing formation routes:

- ordinary granite/gneiss hydrothermal veins and disseminations in volcanic-arc or orogenic geology;
- rarer vein or massive-lens deposits restricted to a volcanic-arc `pegmatite_fertile_margin`.

Both routes reuse the existing deposit geometry and deterministic candidate planner. No pegmatite block, new noise field or separate generation pass is added.

Poor, Medium, Rich and Massive blocks share the external-ore 1/2/4/8 raw-item economy. Loot resolves through the optional `geostrata:provider_outputs/tin` tag, so standalone GeoStrata data remains valid. When the provider output is registered, the experiment companion removes the verified `create_dd:tin_ore` placed feature to prevent duplicate natural generation.

Granite is promoted into the shared ore-host state matrix as part of this integration. Existing core ores use their established fallback appearance for that new state; tin provides dedicated native-resolution granite and gneiss composites.
