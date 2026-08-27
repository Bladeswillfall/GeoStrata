# Province deformation response

GeoStrata's terrain-aware structural model is currently diagnostic-only. `data/geostrata/geology/province_deformation_profiles.json` defines how broad geological province context responds to coarse terrain morphology without granting that response authority over generated blocks.

## Why the first contract is normalized

The current profile outputs are **potentials from 0 to 1**, not physical dip angles, fold wavelengths or fault offsets. That is deliberate.

Terrain-aware structure still needs in-game evaluation before values such as a 300-block fold wavelength or a 40-block fault displacement become world-generation compatibility commitments. The normalized stage lets GeoStrata test whether the *relative structural behavior* is correct first:

- orogenic belts should retain substantial deformation even under subdued terrain and should strengthen strongly in major relief;
- rift provinces should favor faulting over folding;
- sedimentary basins should remain comparatively gentle;
- cratonic shields should not manufacture young mountain-belt deformation merely because terrain is rugged;
- volcanic arcs may carry meaningful deformation, but sedimentary folding is not their universal expression.

## Morphology normalization

`morphologyNormalization` converts raw `TerrainMorphologySample` observations into three bounded signals:

- `reliefScaleBlocks` normalizes broad local height range;
- `slopeScale` normalizes the centered X/Z gradient magnitude;
- `ridgeProminenceScaleBlocks` normalizes **positive** center prominence over cardinal neighbors.

The three weights must sum exactly to 1 within validation tolerance:

```text
terrainSignal = reliefSignal * reliefWeight
              + slopeSignal * slopeWeight
              + ridgeSignal * ridgeWeight
```

Each component is clamped to `0..1`. Negative prominence is not treated as ridge expression; valleys may still contribute through relief and slope.

The bundled diagnostic normalization currently uses:

- 160 blocks of relief for a saturated relief signal;
- 0.5 vertical blocks per horizontal block for a saturated slope signal;
- 64 blocks of positive prominence for a saturated ridge signal;
- weights of 0.5 relief, 0.3 slope and 0.2 ridge prominence.

These are tuning values, not geological laws.

## Province profiles

Every live `GeologyProvince` must appear exactly once. Each profile declares:

- `baselineIntensity` — structural deformation that exists even when the terrain signal is zero;
- `terrainCoupling` — the maximum additional structural intensity that terrain morphology may contribute;
- `dipPotential` — how strongly the resulting structural intensity expresses through dip-style deformation;
- `foldPotential` — how strongly it expresses through folding;
- `faultPotential` — how strongly it expresses through faulting.

`baselineIntensity + terrainCoupling` may not exceed 1. This makes the response bounded by construction rather than relying on arbitrary clipping of profile data.

The evaluated intensity is:

```text
intensity = baselineIntensity + terrainCoupling * terrainSignal
```

The style potentials are then:

```text
dip   = intensity * dipPotential
fold  = intensity * foldPotential
fault = intensity * faultPotential
```

This means terrain **modulates** geology instead of defining it. A flat orogenic belt still retains its structural baseline; a dramatic mountain in a sedimentary basin does not automatically become an orogen.

## Province-boundary blending

Structural response uses the same conceptual Voronoi-boundary handoff as lithology suitability. The deformation resource currently declares a 192-block blend width.

At the exact boundary, primary and neighboring province responses contribute equally. Moving into the primary province increases its share smoothly until it reaches 100% after the blend width is traversed.

Blending the evaluated response prevents visible hard changes in structural potential at province boundaries while retaining deterministic seed/X/Z behavior.

## Diagnostics

Two read-only commands expose the staging model:

- `/geostrata terrain` — raw active-generator height, relief, slope vector, prominence and sample spacing;
- `/geostrata structure` — primary/neighbor province blend plus normalized terrain signal, overall deformation intensity, dip potential, fold potential and fault potential.

Neither command changes blocks. `StructuralDeformationResponse` is pure mathematical staging and `ProvinceDeformationProfiles` remains `metadata_only`.

## Runtime boundary

Do not convert these normalized values directly into generated folds/faults yet.

The next structural milestone is to define explicit deterministic transforms with physical scales and regression vectors, then compose them with the existing stratigraphic coordinate field in diagnostics. Only after those transforms have been profiled across vanilla and terrain-mod worlds should they be allowed to affect correlated lithology ownership.

See `TERRAIN_AWARE_GEOLOGY.md` for the overall architecture.
