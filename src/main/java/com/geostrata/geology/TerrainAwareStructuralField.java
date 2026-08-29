package com.geostrata.geology;

/**
 * Applies a continuous, coarse terrain-height transform to a stratigraphic field.
 *
 * <p>Terrain is sampled on a fixed world grid and bilinearly interpolated, so
 * neighboring chunks share the same boundary heights. The province site remains
 * the structural anchor and province archetypes control broad drape plus
 * prominence-amplified open folding without following every surface block.
 * Positive prominence is treated as stronger uplift/ridge evidence; increasingly
 * negative prominence attenuates both responses so deep valleys and ravines tend
 * to expose existing geology instead of dragging the geological field downward.</p>
 */
public final class TerrainAwareStructuralField {
    public static final int DEFAULT_GRID_SPACING_BLOCKS = 128;
    public static final double MAX_FOLD_AMPLITUDE_CYCLE_FRACTION = 0.25;
    public static final double MIN_EROSIONAL_RESPONSE_FACTOR = 0.20;

    private TerrainAwareStructuralField() {
    }

    public static Field apply(
            SedimentaryStratigraphicField.Field baseField,
            GeologyProvince province,
            TerrainPatch localPatch,
            double anchorHeight
    ) {
        if (baseField == null) {
            throw new IllegalArgumentException("base stratigraphic field must not be null");
        }
        if (province == null) {
            throw new IllegalArgumentException("geological province must not be null");
        }
        if (localPatch == null) {
            throw new IllegalArgumentException("local terrain patch must not be null");
        }
        if (!Double.isFinite(anchorHeight)) {
            throw new IllegalArgumentException("terrain anchor height must be finite");
        }
        return new Field(baseField, responseFor(province), localPatch, anchorHeight);
    }

    public static Response responseFor(GeologyProvince province) {
        if (province == null) {
            throw new IllegalArgumentException("geological province must not be null");
        }
        return switch (province) {
            case SEDIMENTARY_BASIN -> new Response(0.18, 0.05);
            case CRATONIC_SHIELD -> new Response(0.08, 0.02);
            case OROGENIC_BELT -> new Response(0.55, 0.75);
            case VOLCANIC_ARC -> new Response(0.35, 0.35);
            case RIFT_PROVINCE -> new Response(0.45, 0.20);
        };
    }

    public record Response(double drapeCoupling, double foldCoupling) {
        public Response {
            if (!unitInterval(drapeCoupling) || !unitInterval(foldCoupling)) {
                throw new IllegalArgumentException("structural couplings must be finite and within 0..1");
            }
        }
    }

    public record TerrainPatch(
            int originX,
            int originZ,
            int spacingBlocks,
            TerrainMorphologySample northWest,
            TerrainMorphologySample northEast,
            TerrainMorphologySample southWest,
            TerrainMorphologySample southEast
    ) {
        public TerrainPatch {
            if (spacingBlocks <= 0) {
                throw new IllegalArgumentException("terrain patch spacing must be positive");
            }
            if (northWest == null || northEast == null || southWest == null || southEast == null) {
                throw new IllegalArgumentException("terrain patch samples must not be null");
            }
        }

        static TerrainPatch sample(HeightSource heights, int sampleX, int sampleZ, int spacingBlocks) {
            if (heights == null) {
                throw new IllegalArgumentException("terrain height source must not be null");
            }
            if (spacingBlocks <= 0) {
                throw new IllegalArgumentException("terrain patch spacing must be positive");
            }

            int originX = Math.floorDiv(sampleX, spacingBlocks) * spacingBlocks;
            int originZ = Math.floorDiv(sampleZ, spacingBlocks) * spacingBlocks;
            int eastX = Math.addExact(originX, spacingBlocks);
            int southZ = Math.addExact(originZ, spacingBlocks);
            return new TerrainPatch(
                    originX,
                    originZ,
                    spacingBlocks,
                    sampleAt(heights, originX, originZ, spacingBlocks),
                    sampleAt(heights, eastX, originZ, spacingBlocks),
                    sampleAt(heights, originX, southZ, spacingBlocks),
                    sampleAt(heights, eastX, southZ, spacingBlocks)
            );
        }

        public double heightAt(int x, int z) {
            return valueAt(
                    x,
                    z,
                    northWest.centerHeight(),
                    northEast.centerHeight(),
                    southWest.centerHeight(),
                    southEast.centerHeight()
            );
        }

        public double prominenceAt(int x, int z) {
            return valueAt(
                    x,
                    z,
                    northWest.prominence(),
                    northEast.prominence(),
                    southWest.prominence(),
                    southEast.prominence()
            );
        }

        public TerrainMorphologySample morphologyAt(int x, int z) {
            return new TerrainMorphologySample(
                    heightAt(x, z),
                    valueAt(x, z, northWest.gradientX(), northEast.gradientX(), southWest.gradientX(), southEast.gradientX()),
                    valueAt(x, z, northWest.gradientZ(), northEast.gradientZ(), southWest.gradientZ(), southEast.gradientZ()),
                    valueAt(x, z, northWest.relief(), northEast.relief(), southWest.relief(), southEast.relief()),
                    prominenceAt(x, z)
            );
        }

        private double valueAt(
                int x,
                int z,
                double northWestValue,
                double northEastValue,
                double southWestValue,
                double southEastValue
        ) {
            if (!contains(x, z)) {
                throw new IllegalArgumentException(
                        "terrain patch does not cover " + x + "," + z
                                + " from origin " + originX + "," + originZ
                );
            }
            double xFraction = ((double) x - originX) / spacingBlocks;
            double zFraction = ((double) z - originZ) / spacingBlocks;
            double north = interpolate(northWestValue, northEastValue, xFraction);
            double south = interpolate(southWestValue, southEastValue, xFraction);
            return interpolate(north, south, zFraction);
        }

        private boolean contains(int x, int z) {
            long east = (long) originX + spacingBlocks;
            long south = (long) originZ + spacingBlocks;
            return x >= originX && x <= east && z >= originZ && z <= south;
        }

        private static TerrainMorphologySample sampleAt(
                HeightSource heights,
                int x,
                int z,
                int spacingBlocks
        ) {
            return TerrainMorphologySample.fromCardinalHeights(
                    heights.heightAt(x, z),
                    heights.heightAt(Math.subtractExact(x, spacingBlocks), z),
                    heights.heightAt(Math.addExact(x, spacingBlocks), z),
                    heights.heightAt(x, Math.subtractExact(z, spacingBlocks)),
                    heights.heightAt(x, Math.addExact(z, spacingBlocks)),
                    spacingBlocks
            );
        }
    }

    public record Field(
            SedimentaryStratigraphicField.Field baseField,
            Response response,
            TerrainPatch localPatch,
            double anchorHeight
    ) {
        public Field {
            if (baseField == null || response == null || localPatch == null) {
                throw new IllegalArgumentException("terrain-aware field components must not be null");
            }
            if (!Double.isFinite(anchorHeight)) {
                throw new IllegalArgumentException("terrain anchor height must be finite");
            }
        }

        public double terrainResponseFactor(int x, int z) {
            double prominence = localPatch.prominenceAt(x, z);
            if (prominence >= 0.0) {
                return 1.0;
            }
            return Math.max(
                    MIN_EROSIONAL_RESPONSE_FACTOR,
                    1.0 + prominence / localPatch.spacingBlocks()
            );
        }

        public double drapeOffset(int x, int z) {
            return response.drapeCoupling()
                    * (localPatch.heightAt(x, z) - anchorHeight)
                    * terrainResponseFactor(x, z);
        }

        public double foldAmplitude(int x, int z) {
            double reliefAmplitude = Math.abs(localPatch.prominenceAt(x, z))
                    * response.foldCoupling()
                    * terrainResponseFactor(x, z);
            double maximumAmplitude = baseField.cycleThicknessBlocks() * MAX_FOLD_AMPLITUDE_CYCLE_FRACTION;
            return Math.min(reliefAmplitude, maximumAmplitude);
        }

        public double foldOffset(int x, int z) {
            return foldAmplitude(x, z) * baseField.warpShape(x, z);
        }

        public double terrainOffset(int x, int z) {
            return drapeOffset(x, z) + foldOffset(x, z);
        }

        public double verticalOffset(int x, int z) {
            return baseField.verticalOffset(x, z) + terrainOffset(x, z);
        }

        public SedimentaryStratigraphicField.Sample sample(
                int x,
                double y,
                int z,
                SedimentaryContactPlanner.Plan plan
        ) {
            return baseField.sample(x, y, z, plan, terrainOffset(x, z));
        }
    }

    @FunctionalInterface
    interface HeightSource {
        double heightAt(int x, int z);
    }

    private static double interpolate(double start, double end, double fraction) {
        return start + (end - start) * fraction;
    }

    private static boolean unitInterval(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}
