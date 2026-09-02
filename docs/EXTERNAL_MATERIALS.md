# External material compatibility catalogue

GeoStrata should eventually own the **natural geological occurrence** of supported ores and host rocks while the detected provider mod continues to own items, recipes, processing and progression.

The machine-readable backlog is:

```text
src/main/resources/data/geostrata/compatibility/external_materials.json
```

It is planning metadata only. Nothing in the catalogue enables a dependency, registers a block, suppresses another mod's worldgen, or causes GeoStrata to place provider-owned material yet.

## Why catalogue this first

Provider support should be boring to add. A new metal should normally be a data entry describing:

- which mod IDs provide it;
- which provider ore/output IDs or common tags identify it;
- which GeoStrata semantic role it maps to;
- which existing deposit styles can represent it;
- which existing GeoStrata lithologies are plausible hosts; and
- which missing host-rock families should be added later rather than faked with an unrelated rock.

Only genuinely new geological formation mechanisms should require new worldgen code.

## Create-family first tranche

| Provider | Natural material | Initial GeoStrata direction |
| --- | --- | --- |
| Create | Zinc | Carbonate/felsic-associated vein or stratiform occurrence |
| Create: Dreams & Desires | Tin | Felsic/pegmatitic vein or disseminated occurrence |
| Create: New Age | Thorium | Felsic/accessory-mineral vein or disseminated occurrence |
| Create: New Age | Magnetite | Mafic, skarn-like or massive/disseminated occurrence |
| Create: The Factory Must Grow | Lead | Carbonate/sedimentary vein or stratiform occurrence |
| Create: The Factory Must Grow | Nickel | Mafic/ultramafic disseminated or massive occurrence |
| Create: The Factory Must Grow | Lithium | Felsic/pegmatitic vein or pocket occurrence |
| Create: Nuclear | Uranium | Felsic, shale or future sandstone/unconformity occurrence |
| Create: Nuclear | Lead | Alias of the same canonical lead geology rather than a second lead system |

This is intentionally a **canonical-material** model. If two installed mods both provide lead, GeoStrata should generate one geological lead occurrence and select/interoperate with a provider output through tags or a narrow adapter. It should not generate two independent lead systems because two mods happened to register lead ore.

## Create-priority host rocks

Create makes vanilla andesite particularly important to progression, and the wider Create-family material set benefits from a real felsic intrusive host. The catalogue therefore records these vanilla rocks as priority geology gaps:

- `minecraft:andesite` -> `geostrata:rock/igneous/andesite`
- `minecraft:granite` -> `geostrata:rock/igneous/granite`
- `minecraft:diorite` -> `geostrata:rock/igneous/diorite`

These are Minecraft-owned blocks, not Create-owned blocks. Their inclusion means GeoStrata should preserve or eventually generate them coherently when it owns host geology; it does not mean they should only exist when Create is installed.

Granite is the important missing host for tin, lithium, thorium and some uranium geology. Until GeoStrata has a real granite/pegmatite model, those materials should retain explicit future-host requirements rather than silently substituting rhyolite everywhere.

## Implementation sequence

For each provider material, support is complete only after all of the following are true:

1. provider detection is loader-safe and optional;
2. provider block/output IDs or common tags are verified for the supported Minecraft/mod version;
3. the canonical material has an occurrence entry using existing deposit styles where possible;
4. required host lithologies exist or the occurrence deliberately uses a smaller geologically valid subset;
5. GeoStrata can emit the provider's economy output without copying its processing system;
6. replacement generation is benchmarked for availability and discoverability; and
7. provider-native natural worldgen is suppressed only after the replacement path is proven safe.

The standalone GeoStrata jar must still load with none of these providers installed.

## Version caveat

Create-family addons change registry IDs and loader support between releases. This catalogue targets the GeoStrata development baseline (Minecraft 1.20.1) unless an entry is later made version-specific. Registry IDs are compatibility inputs that must be re-verified before an adapter is promoted from `catalogued` planning metadata to runtime support.
