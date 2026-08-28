# World-generation determinism contract

GeoStrata world generation is intended to be reproducible from the Minecraft world seed.

## Reproducibility promise

Two newly generated worlds should receive the same GeoStrata geology when all generation inputs are identical:

- Minecraft version;
- Fabric Loader/API and mod versions;
- enabled mods and their world-generation configuration;
- enabled datapacks/resource data that affect world generation;
- world preset, dimension/noise settings and generator configuration;
- GeoStrata version and GeoStrata server-data overrides;
- world seed.

The promise is not that a seed alone overrides changed inputs. Changing a terrain mod version, datapack, GeoStrata tuning value, feature registration order or generator settings is a world-generation change and may legitimately produce different unexplored chunks.

## Immutable geology rule

New GeoStrata geological identity and geometry must be derived from stable inputs, normally:

```text
world seed + dimension/generator evidence + world coordinates + stable caller salt + loaded geology data
```

Immutable geology must not depend on wall-clock time, random UUIDs, process-local entropy, chunk visit order, first-loaded state or mutable runtime history.

Prefer `GeologyDeterminism` and pure coordinate samplers for ownership and geological geometry. Stable salts used by runtime generation are compatibility-sensitive: changing a salt or hash mapping changes generation for the same seed.

Chunk-spanning bodies must be proposed from shared deterministic anchors and each chunk must generate only its own slice. A neighboring chunk being generated first must not change ownership or body shape.

## Terrain-aware generation

Reading terrain does not break seed-locking when the active generator is itself unchanged and deterministic. GeoStrata therefore samples the active chunk generator directly for broad morphology. This lets the same seed respond consistently to vanilla or a terrain mod without storing first-observed terrain state.

This also means a changed terrain generator is a changed generation input. The same numerical seed with a different terrain mod or generator configuration is not expected to preserve identical GeoStrata contacts.

## Minecraft feature RNG

The current compatibility baseline still contains ordinary configured/placed features and `StrataLensFeature` geometry that consume Minecraft's feature RNG. Minecraft seeds that stream from world-generation state, so an identical modpack/configuration and seed remains reproducible.

That legacy path is deterministic but less isolated: changing feature registration/order or placement configuration can alter its RNG stream. As geological body families are migrated, new immutable ownership and geometry should use GeoStrata's seed/coordinate hashing instead of depending on incidental RNG consumption order.

Using Minecraft RNG for a native feature implementation is acceptable only when the random stream is itself world-seed-derived and no persistent geological identity depends on first-run order.

## Mutable gameplay state

Future finite/depletable systems may persist quantities that cannot be reconstructed after gameplay changes them. Mutable state must be attached to a deterministic geological identity; it must never be used to invent that identity on first discovery.

For example, a future finite reservoir may persist remaining quantity, but its anchor and immutable geometry must still be recomputable from the same seed and coordinates.

## Regression policy

World-generation hashes, salts and fixed regression vectors are compatibility surfaces. Tests should pin representative deterministic outputs. Deliberate changes are allowed, but they must be treated as explicit world-generation changes rather than accidental refactors.

Source under `com.geostrata.geology` and `com.geostrata.worldgen` must not use obvious process-local entropy sources such as `Math.random`, wall-clock time, `ThreadLocalRandom`, `SecureRandom` or random UUID generation.
