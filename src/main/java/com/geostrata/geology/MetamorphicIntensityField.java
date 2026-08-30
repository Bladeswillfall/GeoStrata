package com.geostrata.geology;

/**
 * Pure broad-scale metamorphic grade field.
 *
 * <p>The field combines the existing geological province model with smooth,
 * low-frequency seed-derived variation. Terrain morphology may contribute a
 * deliberately small adjustment, but regional geological history remains the
 * dominant signal.</p>
 */
public final class MetamorphicIntensityField {
    public static final int REGIONAL_GRID_SPACING_BLOCKS = 384;
    public static final int DEFAULT_PROVINCE_BLEND_WIDTH_BLOCKS = 192;

    private static final long REGIONAL_VARIATION_SALT = 0x8CB92BA72F3D8DD7L;

    private MetamorphicIntensityField() {
    }

    public static Sample sample(long worldSeed, int blockX, int blockZ) {
        return sample(worldSeed, blockX, blockZ, DEFAULT_PROVINCE_BLEND_WIDTH_BLOCKS, null);
    }

    public static Sample sample(
            long worldSeed,
            int blockX,
            int blockZ,
            int provinceBlendWidthBlocks,
            TerrainMorphologySample terrain
    ) {
        return sample(
                worldSeed,
                blockX,
                blockZ,
                provinceBlendWidthBlocks,
                terrain,
                GeologyProvinceSampler.sample(worldSeed, blockX, blockZ)
        );
    }

    /** Reuses an already-resolved province sample when a chunk context supplied it. */
    public static Sample sample(
            long worldSeed,
            int blockX,
            int blockZ,
            int provinceBlendWidthBlocks,
            TerrainMorphologySample terrain,
            GeologyProvinceSampler.Sample province
    ) {
        if (provinceBlendWidthBlocks <= 0) {
            throw new IllegalArgumentException("province blend width must be positive");
        }
        if (province == null) {
            throw new IllegalArgumentException("province sample must not be null");
        }

        double interior = province.interiorBlend(provinceBlendWidthBlocks);
        double provinceBaseline = blend(
                baselineFor(province.province()),
                baselineFor(province.neighborProvince()),
                interior
        );
        double variationAmplitude = blend(
                regionalVariationFor(province.province()),
                regionalVariationFor(province.neighborProvince()),
                interior
        );
        double regionalSignal = regionalSignal(worldSeed, blockX, blockZ);
        double regionalAdjustment = regionalSignal * variationAmplitude;
        double terrainAdjustment = terrain == null
                ? 0.0
                : terrainAdjustment(
                        terrain,
                        blend(
                                terrainCouplingFor(province.province()),
                                terrainCouplingFor(province.neighborProvince()),
                                interior
                        )
                );
        double intensity = clamp01(provinceBaseline + regionalAdjustment + terrainAdjustment);
        return new Sample(
                province,
                provinceBaseline,
                regionalSignal,
                regionalAdjustment,
                terrainAdjustment,
                intensity,
                suitability(intensity)
        );
    }

    public static double baselineFor(GeologyProvince province) {
        if (province == null) {
            throw new IllegalArgumentException("geological province must not be null");
        }
        return switch (province) {
            case SEDIMENTARY_BASIN -> 0.12;
            case CRATONIC_SHIELD -> 0.52;
            case OROGENIC_BELT -> 0.72;
            case VOLCANIC_ARC -> 0.55;
            case RIFT_PROVINCE -> 0.32;
        };
    }

    public static Suitability suitability(double intensity) {
        if (!Double.isFinite(intensity)) {
            throw new IllegalArgumentException("metamorphic intensity must be finite");
        }
        double clamped = clamp01(intensity);
        return new Suitability(
                window(clamped, 0.12, 0.22, 0.40, 0.56),
                window(clamped, 0.36, 0.48, 0.68, 0.82),
                rise(clamped, 0.64, 0.84)
        );
    }

    static double regionalSignal(long worldSeed, int blockX, int blockZ) {
        int cellX = Math.floorDiv(blockX, REGIONAL_GRID_SPACING_BLOCKS);
        int cellZ = Math.floorDiv(blockZ, REGIONAL_GRID_SPACING_BLOCKS);
        long originX = (long) cellX * REGIONAL_GRID_SPACING_BLOCKS;
        long originZ = (long) cellZ * REGIONAL_GRID_SPACING_BLOCKS;
        double xFraction = ((long) blockX - originX) / (double) REGIONAL_GRID_SPACING_BLOCKS;
        double zFraction = ((long) blockZ - originZ) / (double) REGIONAL_GRID_SPACING_BLOCKS;
        double xBlend = smoothStep(xFraction);
        double zBlend = smoothStep(zFraction);

        double northWest = latticeValue(worldSeed, cellX, cellZ);
        double northEast = latticeValue(worldSeed, cellX + 1, cellZ);
        double southWest = latticeValue(worldSeed, cellX, cellZ + 1);
        double southEast = latticeValue(worldSeed, cellX + 1, cellZ + 1);
        double north = interpolate(northWest, northEast, xBlend);
        double south = interpolate(southWest, southEast, xBlend);
        return interpolate(north, south, zBlend);
    }

    static double terrainAdjustment(TerrainMorphologySample terrain, double coupling) {
        if (terrain == null) {
            throw new IllegalArgumentException("terrain morphology must not be null");
        }
        double reliefSignal = clamp01(terrain.relief() / 96.0);
        double prominenceSignal = clamp(terrain.prominence() / 48.0, -1.0, 1.0);
        double morphology = clamp(0.65 * reliefSignal + 0.35 * prominenceSignal, -1.0, 1.0);
        return clamp(coupling, 0.0, 1.0) * morphology;
    }

    private static double regionalVariationFor(GeologyProvince province) {
        return switch (province) {
            case SEDIMENTARY_BASIN -> 0.08;
            case CRATONIC_SHIELD -> 0.15;
            case OROGENIC_BELT -> 0.18;
            case VOLCANIC_ARC -> 0.14;
            case RIFT_PROVINCE -> 0.12;
        };
    }

    private static double terrainCouplingFor(GeologyProvince province) {
        return switch (province) {
            case SEDIMENTARY_BASIN -> 0.01;
            case CRATONIC_SHIELD -> 0.02;
            case OROGENIC_BELT -> 0.08;
            case VOLCANIC_ARC -> 0.05;
            case RIFT_PROVINCE -> 0.04;
        };
    }

    private static double latticeValue(long worldSeed, int cellX, int cellZ) {
        return GeologyDeterminism.unitRoll(worldSeed, cellX, 0, cellZ, REGIONAL_VARIATION_SALT) * 2.0 - 1.0;
    }

    private static double window(double value, double outerLow, double innerLow, double innerHigh, double outerHigh) {
        return Math.min(rise(value, outerLow, innerLow), 1.0 - rise(value, innerHigh, outerHigh));
    }

    private static double rise(double value, double start, double end) {
        if (value <= start) {
            return 0.0;
        }
        if (value >= end) {
            return 1.0;
        }
        return smoothStep((value - start) / (end - start));
    }

    private static double blend(double primary, double neighbor, double interiorBlend) {
        return GeologyProvinceProfiles.blend(primary, neighbor, interiorBlend);
    }

    private static double smoothStep(double value) {
        double clamped = clamp01(value);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double interpolate(double start, double end, double fraction) {
        return start + (end - start) * fraction;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }

    public record Sample(
            GeologyProvinceSampler.Sample province,
            double provinceBaseline,
            double regionalSignal,
            double regionalAdjustment,
            double terrainAdjustment,
            double intensity,
            Suitability suitability
    ) {
    }

    public record Suitability(double slate, double schist, double gneiss) {
        public String dominantLithology() {
            if (slate <= 0.0 && schist <= 0.0 && gneiss <= 0.0) {
                return "none";
            }
            if (gneiss >= schist && gneiss >= slate) {
                return "gneiss";
            }
            return schist >= slate ? "schist" : "slate";
        }
    }
}
