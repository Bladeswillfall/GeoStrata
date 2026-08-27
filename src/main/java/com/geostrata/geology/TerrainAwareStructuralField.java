package com.geostrata.geology;

/**
 * Applies a continuous, coarse terrain-height transform to a stratigraphic field.
 *
 * <p>Terrain is sampled on a fixed world grid and bilinearly interpolated, so
 * neighboring chunks share the same boundary heights. The province site remains
 * the structural anchor and province archetypes control how strongly beds follow
 * broad terrain rather than draping one-to-one over the surface.</p>
 */
public final class TerrainAwareStructuralField {
    public static final int DEFAULT_GRID_SPACING_BLOCKS = 128;

    private TerrainAwareStructuralField() {
    }

    public static Field apply(
            SedimentaryStratigraphicField.Field baseField,
            GeologyProvince province,
            HeightPatch localPatch,
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
            case SEDIMENTARY_BASIN -> new Response(0.18);
            case CRATONIC_SHIELD -> new Response(0.08);
            case OROGENIC_BELT -> new Response(0.55);
            case VOLCANIC_ARC -> new Response(0.35);
            case RIFT_PROVINCE -> new Response(0.45);
        };
    }

    public record Response(double terrainCoupling) {
        public Response {
            if (!Double.isFinite(terrainCoupling) || terrainCoupling < 0.0 || terrainCoupling > 1.0) {
                throw new IllegalArgumentException("terrain coupling must be finite and within 0..1");
            }
        }
    }

    public record HeightPatch(
            int originX,
            int originZ,
            int spacingBlocks,
            double northWestHeight,
            double northEastHeight,
            double southWestHeight,
            double southEastHeight
    ) {
        public HeightPatch {
            if (spacingBlocks <= 0) {
                throw new IllegalArgumentException("terrain patch spacing must be positive");
            }
            requireFinite(northWestHeight, northEastHeight, southWestHeight, southEastHeight);
        }

        static HeightPatch sample(HeightSource heights, int sampleX, int sampleZ, int spacingBlocks) {
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
            return new HeightPatch(
                    originX,
                    originZ,
                    spacingBlocks,
                    heights.heightAt(originX, originZ),
                    heights.heightAt(eastX, originZ),
                    heights.heightAt(originX, southZ),
                    heights.heightAt(eastX, southZ)
            );
        }

        public double heightAt(int x, int z) {
            if (!contains(x, z)) {
                throw new IllegalArgumentException(
                        "terrain patch does not cover " + x + "," + z
                                + " from origin " + originX + "," + originZ
                );
            }
            double xFraction = ((double) x - originX) / spacingBlocks;
            double zFraction = ((double) z - originZ) / spacingBlocks;
            double north = interpolate(northWestHeight, northEastHeight, xFraction);
            double south = interpolate(southWestHeight, southEastHeight, xFraction);
            return interpolate(north, south, zFraction);
        }

        private boolean contains(int x, int z) {
            long east = (long) originX + spacingBlocks;
            long south = (long) originZ + spacingBlocks;
            return x >= originX && x <= east && z >= originZ && z <= south;
        }
    }

    public record Field(
            SedimentaryStratigraphicField.Field baseField,
            Response response,
            HeightPatch localPatch,
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

        public double terrainOffset(int x, int z) {
            return response.terrainCoupling() * (localPatch.heightAt(x, z) - anchorHeight);
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

    private static void requireFinite(double... heights) {
        for (double height : heights) {
            if (!Double.isFinite(height)) {
                throw new IllegalArgumentException("terrain patch heights must be finite");
            }
        }
    }
}
