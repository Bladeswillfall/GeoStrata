package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TerrainAwareStructuralFieldTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void flatTerrainLeavesTheBaseFieldUnchanged() {
        SedimentaryStratigraphicField.Field base = flatBaseField();
        TerrainAwareStructuralField.HeightPatch patch = new TerrainAwareStructuralField.HeightPatch(
                0, 0, 128, 80.0, 80.0, 80.0, 80.0
        );
        TerrainAwareStructuralField.Field field = TerrainAwareStructuralField.apply(
                base,
                GeologyProvince.OROGENIC_BELT,
                patch,
                80.0
        );

        assertEquals(0.0, field.terrainOffset(64, 64), EPSILON);
        assertEquals(base.verticalOffset(64, 64), field.verticalOffset(64, 64), EPSILON);
    }

    @Test
    void provinceResponseControlsHowStronglyBedsFollowRelief() {
        SedimentaryStratigraphicField.Field base = flatBaseField();
        TerrainAwareStructuralField.HeightPatch patch = new TerrainAwareStructuralField.HeightPatch(
                0, 0, 128, 80.0, 144.0, 80.0, 144.0
        );
        TerrainAwareStructuralField.Field orogenic = TerrainAwareStructuralField.apply(
                base,
                GeologyProvince.OROGENIC_BELT,
                patch,
                80.0
        );
        TerrainAwareStructuralField.Field cratonic = TerrainAwareStructuralField.apply(
                base,
                GeologyProvince.CRATONIC_SHIELD,
                patch,
                80.0
        );

        assertEquals(35.2, orogenic.terrainOffset(128, 64), EPSILON);
        assertEquals(5.12, cratonic.terrainOffset(128, 64), EPSILON);

        SedimentaryStratigraphicField.Sample contact = orogenic.sample(
                128,
                orogenic.verticalOffset(128, 64),
                64,
                twoBedPlan()
        );
        assertEquals(0.0, contact.fraction(), EPSILON);
    }

    @Test
    void fixedGridInterpolationIsContinuousAcrossPatchBoundaries() {
        TerrainAwareStructuralField.HeightSource heights =
                (x, z) -> 80.0 + 0.25 * x - 0.125 * z;
        TerrainAwareStructuralField.HeightPatch west = TerrainAwareStructuralField.HeightPatch.sample(
                heights, 64, 64, 128
        );
        TerrainAwareStructuralField.HeightPatch east = TerrainAwareStructuralField.HeightPatch.sample(
                heights, 192, 64, 128
        );

        assertEquals(west.heightAt(128, 64), east.heightAt(128, 64), EPSILON);
        assertEquals(104.0, west.heightAt(128, 64), EPSILON);
    }

    @Test
    void negativeCoordinatesUseFloorAlignedGridCells() {
        TerrainAwareStructuralField.HeightPatch patch = TerrainAwareStructuralField.HeightPatch.sample(
                (x, z) -> x + z,
                -1,
                -129,
                128
        );

        assertEquals(-128, patch.originX());
        assertEquals(-256, patch.originZ());
        assertEquals(-130.0, patch.heightAt(-1, -129), EPSILON);
    }

    @Test
    void rejectsInvalidOrOutOfPatchSamples() {
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainAwareStructuralField.Response(1.1));
        assertThrows(IllegalArgumentException.class,
                () -> new TerrainAwareStructuralField.HeightPatch(
                        0, 0, 0, 0.0, 0.0, 0.0, 0.0
                ));

        TerrainAwareStructuralField.HeightPatch patch = new TerrainAwareStructuralField.HeightPatch(
                0, 0, 128, 0.0, 0.0, 0.0, 0.0
        );
        assertThrows(IllegalArgumentException.class, () -> patch.heightAt(129, 0));
    }

    private static SedimentaryStratigraphicField.Field flatBaseField() {
        return SedimentaryStratigraphicField.forSite(
                1L,
                0,
                0,
                new SedimentaryStratigraphicField.Parameters(20.0, 0.0, 0.0, 64.0)
        );
    }

    private static SedimentaryContactPlanner.Plan twoBedPlan() {
        return new SedimentaryContactPlanner.Plan(
                "two_bed_cycle",
                "regional",
                2.0,
                0.0,
                List.of(
                        new SedimentaryContactPlanner.Interval(0, "lower", 1.0, 0.0, 0.5),
                        new SedimentaryContactPlanner.Interval(1, "upper", 1.0, 0.5, 1.0)
                )
        );
    }
}
