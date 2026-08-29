# Semantic geology resolver

`GeologyResolver` is a read-only facade over GeoStrata's existing geological fields. It exists so worldgen, diagnostics and compatibility adapters can ask the same semantic question:

```text
what lithology does GeoStrata's geological model say exists at X/Y/Z?
```

It is deliberately **not** a second geology generator.

## Authority rule

Geological geometry remains owned by the existing model:

1. province/context selection;
2. succession/contact planning;
3. stratigraphic fields;
4. terrain-aware drape/fold deformation;
5. metamorphic transformation;
6. future queryable fault, intrusion and other body fields.

The resolver only composes those answers and returns semantic provenance.

It must not introduce its own noise, layer thickness, fold equations, replacement order or material-placement rules.

## Current scope

The first implementation resolves only geology owned by `CorrelatedSedimentaryRuntime.TerrainAwareSite` because that system is already deterministic and queryable without reading placed world blocks.

The returned result currently contains:

- final lithology;
- parent lithology;
- geological province;
- resolver source (`CORRELATED_STRATIGRAPHY`).

Coordinates not owned by a queryable semantic field return `Optional.empty()`.

This is intentional. Baseline fallback lenses/bodies still exist and still generate exactly as before, but the resolver does not reverse-engineer them from placed blocks or create replacement geometry. Each fallback/body system should join the resolver only when its existing geometry can be exposed as a deterministic query.

## Generation safety

The current worldgen placement paths are unchanged by the first resolver slice. Resolver tests compare its answers directly against the existing terrain-aware stratigraphic and metamorphic runtime.

This gives us an explicit parity gate before any generator is switched to consume the facade directly:

```text
existing geological field answer == resolver answer
```

If that invariant fails, the resolver is wrong; the underlying geology should not be changed to accommodate it.

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
- fault/displacement fields;
- special event geology such as kimberlite/lamproite;
- ore/mineral host context where a semantic query is preferable to reading placed blocks.

Do not build these systems inside `GeologyResolver` itself.
