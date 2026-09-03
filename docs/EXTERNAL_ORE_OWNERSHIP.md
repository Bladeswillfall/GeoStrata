# External ore ownership

GeoStrata owns **natural geological occurrence**, not another mod's processing economy.

For an external material, the runtime contract is:

1. GeoStrata defines one canonical geological occurrence for the material.
2. Provider availability is resolved once during geology-data reload.
3. An occurrence may use the existing `providerMod` + `outputItem` pair, or an ordered `providers` list when several mods can supply the same material.
4. For an ordered list, the first registered output wins. If none are registered, the optional occurrence is omitted.
5. The resolved occurrence then uses the normal GeoStrata candidate, formation-route, deposit-style and grade pipeline. Chunk generation does not scan mods or choose providers.
6. Provider-native natural generation is suppressed only after that provider's exact 1.20.1 registry/output/worldgen contract has been verified and GeoStrata replacement generation is active.
7. Provider mods retain their items, recipes and processing chains.

## Almost Unified boundary

Almost Unified is intentionally **not a GeoStrata dependency**.

GeoStrata must be correct without it: one canonical geological occurrence, provider-gated activation, verified duplicate-worldgen suppression, valid common tags, and usable provider outputs.

A modpack may then use Almost Unified to choose dominant tagged resources and rewrite/merge recipes. That is a downstream item/recipe-normalisation concern; Almost Unified does not replace GeoStrata's geology or duplicate-worldgen ownership.

The pack's Almost Unified priority order should be kept consistent with any ordered GeoStrata provider list for a material. GeoStrata does not read Almost Unified configuration or call its API.

## Multi-provider promotion rule

Do not add several provider outputs to one loot tag and call the material unified. Expanded loot tags can contain several installed items, which would make block drops provider-ambiguous.

Before the first true multi-provider runtime material is promoted, its drop path must preserve the same resolved provider choice used by the occurrence, while retaining Silk Touch, Fortune and explosion behaviour. Until that is implemented and tested, multi-provider declarations are a reload-time capability rather than permission to ship ambiguous drops.

This keeps the compatibility system incremental: no new worldgen pass, no provider polling in chunk generation, and no Almost Unified coupling.
