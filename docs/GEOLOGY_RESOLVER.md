# Semantic geology resolver

`GeologyResolver` is a read-only facade over GeoStrata's existing geological fields. It exists so worldgen, diagnostics and compatibility adapters can ask the same semantic question:

```text
what lithology does GeoStrata's geological model say exists at X/Y/Z?
```

It is deliberately **not** a second geology generator.

## Authority rule

Geological geometry remains owned by the existing model:

1. province/context selection;
2. correlated succession/contact planning where that runtime owns the chunk;
3. province-background architecture everywhere else in the advanced runtime;
4. terrain-aware structural deformation and sutures;
5. metamorphic and fault-damage transformations already owned by those runtimes;
6. future queryable intrusion and other body fields.

The resolver only composes those answers and returns semantic provenance. It must not introduce its own noise, layer thickness, fold equations, replacement order or material-placement rules.

## Current scope

The resolver now covers both queryable authorities used by the advanced companion runtime:

- `CorrelatedSedimentaryRuntime.TerrainAwareSite` has first authority in chunks it owns;
- `ProvinceBackgroundRuntime.Chunk` supplies the remaining province architecture.

The returned result contains:

- final lithology;
- optional parent lithology;
- geological province at that depth;
- resolver source (`CORRELATED_STRATIGRAPHY` or `PROVINCE_BACKGROUND`).

Parent lithology is present for correlated stratigraphy because that runtime explicitly resolves a parent bed before metamorphism. Province-background models currently expose their final lithology but do not claim a universal parent relationship, so their parent is empty rather than guessed.

Province provenance follows the same depth-dependent suture ownership as the background lithology. A terrane projected beneath its neighbour therefore reports the terrane that actually supplied the resolved rock, not merely the surface province.

Coordinates not owned by either queryable semantic runtime return `Optional.empty()`.

Baseline fallback lenses/bodies still exist and still generate as before. The resolver does not reverse-engineer those from placed blocks or create replacement geometry. Each body system should join the resolver only when its existing geometry can be exposed as a deterministic query.

## Prepared chunk queries

One-off callers may use `GeologyResolver.resolve(world, x, y, z)` directly.

Worldgen paths that inspect many blocks should call `GeologyResolver.prepareChunk(...)` once and reuse the returned `PreparedChunk`. This preserves the existing chunk-level terrain/province work instead of rebuilding semantic geology for every voxel.

The experimental ore host resolver is the first production consumer. It still gives already-placed GeoStrata host blocks first priority, but virtual host qualification now asks one prepared `GeologyResolver` context instead of independently knowing about correlated and province-background runtimes.

## Parity rule

Resolver tests compare its answers directly with the existing authoritative runtimes:

```text
existing geological runtime answer == resolver answer
```

They also verify that correlated authority wins when both sources are supplied and that no semantic owner remains an empty result.

If those invariants fail, the resolver is wrong; the underlying geology should not be changed to accommodate it.

## Compatibility role

Future integrations such as TerraFirmaCraft should consume the semantic result and map it to their own registered materials. They should not copy GeoStrata's geometry or recreate its folds/layers in adapter code.

Conceptually:

```text
GeoStrata fields
    ↓
GeologyResolver
    ↓
semantic lithology/context
    ↓
material adapter
    ├─ GeoStrata blocks
    └─ third-party blocks (for example TFC)
```

## Next extensions

Extend the resolver only as existing systems become queryable:

- baseline strata/lens bodies;
- intrusive bodies;
- special event geology such as kimberlite/lamproite;
- additional process provenance where a real consumer needs it.

Do not build those systems inside `GeologyResolver` itself.
