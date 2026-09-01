package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SedimentaryStratigraphicFieldTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void flatFieldMapsVerticalCoordinateIntoRepeatedMotif() {
        SedimentaryContactPlanner.Plan plan = twoBedPlan(0.0);
        SedimentaryStratigraphicField.Field field = SedimentaryStratigraphicField.forSite(
                1L,
                0,
                0,
                new SedimentaryStratigraphicField.Parameters(20.0, 0.0, 0.0, 64.0)
        );

        SedimentaryStratigraphicField.Sample bottom = field.sample(0, 0.0, 0, plan);
        SedimentaryStratigraphicField.Sample contact = field.sample(0, 10.0, 0, plan);
        SedimentaryStratigraphicField.Sample nextCycle = field.sample(0, 20.0, 0, plan);
        SedimentaryStratigraphicField.Sample below = field.sample(0, -1.0, 0, plan);

        assertEquals(0L, bottom.cycleIndex());
        assertEquals(0.0, bottom.fraction(), EPSILON);
        assertEquals("lower", bottom.bed().lithology());

        assertEquals(0L, contact.cycleIndex());
        assertEquals(0.5, contact.fraction(), EPSILON);
        assertEquals("upper", contact.bed().lithology());

        assertEquals(1L, nextCycle.cycleIndex());
        assertEquals(0.0, nextCycle.fraction(), EPSILON);
        assertEquals("lower", nextCycle.bed().lithology());

        assertEquals(-1L, below.cycleIndex());
        assertEquals(0.95, below.fraction(), EPSILON);
        assertEquals("upper", below.bed().lithology());
    }

    @Test
    void provinceSiteIsZeroOffsetAnchorAndHonorsPlanPhase() {
        SedimentaryContactPlanner.Plan plan = twoBedPlan(0.35);
        SedimentaryStratigraphicField.Field field = SedimentaryStratigraphicField.forSite(
                8675309L,
                -320,
                704,
                new SedimentaryStratigraphicField.Parameters(48.0, 0.25, 6.0, 192.0)
        );

        assertEquals(0.0, field.verticalOffset(-320, 704), EPSILON);
        SedimentaryStratigraphicField.Sample sample = field.sample(-320, 0.0, 704, plan);
        assertEquals(0.35, sample.fraction(), EPSILON);
        assertEquals(0.35, sample.stratigraphicCoordinate(), EPSILON);
    }

    @Test
    void movingAlongAContactSurfacePreservesStratigraphicFraction() {
        SedimentaryContactPlanner.Plan plan = twoBedPlan(0.2);
        SedimentaryStratigraphicField.Field field = SedimentaryStratigraphicField.forSite(
                42L,
                100,
                -200,
                new SedimentaryStratigraphicField.Parameters(64.0, 0.18, 5.0, 160.0)
        );

        int x = 340;
        int z = -75;
        double contactY = field.verticalOffset(x, z);
        SedimentaryStratigraphicField.Sample sample = field.sample(x, contactY, z, plan);

        assertEquals(0.2, sample.fraction(), 1.0e-10);
        assertEquals(plan.bedAt(0.2).lithology(), sample.bed().lithology());
    }

    @Test
    void precomputedVerticalOffsetMatchesNormalSampling() {
        SedimentaryContactPlanner.Plan plan = twoBedPlan(0.17);
        SedimentaryStratigraphicField.Field field = SedimentaryStratigraphicField.forSite(
                8675309L,
                -192,
                384,
                new SedimentaryStratigraphicField.Parameters(52.0, 0.22, 7.0, 176.0)
        );
        int x = 73;
        int z = -41;
        double extraOffset = 11.75;
        double totalOffset = field.verticalOffset(x, z) + extraOffset;

        for (double y : new double[]{-128.0, -1.0, 63.0, 192.5, 639.0}) {
            SedimentaryStratigraphicField.Sample normal = field.sample(x, y, z, plan, extraOffset);
            SedimentaryStratigraphicField.Sample cached = field.sampleAtVerticalOffset(y, plan, totalOffset);
            assertEquals(normal, cached);
            assertEquals(cached.bed(), field.bedAtVerticalOffset(y, plan, totalOffset));
        }
    }

    @Test
    void bedRunEndsOnLastIntegerBeforeNextContact() {
        SedimentaryContactPlanner.Plan plan = twoBedPlan(0.0);
        SedimentaryStratigraphicField.Field field = SedimentaryStratigraphicField.forSite(
                1L,
                0,
                0,
                new SedimentaryStratigraphicField.Parameters(20.0, 0.0, 0.0, 64.0)
        );
        double verticalOffset = 3.5;
        SedimentaryStratigraphicField.Sample sample = field.sampleAtVerticalOffset(4.0, plan, verticalOffset);

        assertEquals("lower", sample.bed().lithology());
        assertEquals(13, field.bedRunEndY(sample, 4, plan));
        assertEquals("lower", field.bedAtVerticalOffset(13.0, plan, verticalOffset).lithology());
        assertEquals("upper", field.bedAtVerticalOffset(14.0, plan, verticalOffset).lithology());
    }

    @Test
    void fieldDerivationIsDeterministicForAProvinceSite() {
        SedimentaryStratigraphicField.Parameters parameters =
                new SedimentaryStratigraphicField.Parameters(56.0, 0.3, 4.5, 144.0);

        SedimentaryStratigraphicField.Field first =
                SedimentaryStratigraphicField.forSite(123456789L, 768, -1536, parameters);
        SedimentaryStratigraphicField.Field second =
                SedimentaryStratigraphicField.forSite(123456789L, 768, -1536, parameters);

        assertEquals(first, second);
        assertTrue(Math.hypot(first.dipX(), first.dipZ()) <= parameters.maxDip() + EPSILON);
    }

    @Test
    void rejectsInvalidParametersAndNonFiniteSamples() {
        assertThrows(IllegalArgumentException.class,
                () -> new SedimentaryStratigraphicField.Parameters(0.5, 0.1, 0.0, 64.0));
        assertThrows(IllegalArgumentException.class,
                () -> new SedimentaryStratigraphicField.Parameters(32.0, 1.1, 0.0, 64.0));
        assertThrows(IllegalArgumentException.class,
                () -> new SedimentaryStratigraphicField.Parameters(32.0, 0.1, 33.0, 64.0));
        assertThrows(IllegalArgumentException.class,
                () -> new SedimentaryStratigraphicField.Parameters(32.0, 0.1, 2.0, 0.0));

        assertThrows(IllegalArgumentException.class,
                () -> new SedimentaryStratigraphicField.Field(0, 0, 32.0, 1.0, 1.0, 0.0, 64.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new SedimentaryStratigraphicField.Field(0, 0, 32.0, 0.0, 0.0, 33.0, 64.0, 0.0));

        SedimentaryStratigraphicField.Field field = SedimentaryStratigraphicField.forSite(
                1L,
                0,
                0,
                new SedimentaryStratigraphicField.Parameters(32.0, 0.0, 0.0, 64.0)
        );
        assertThrows(IllegalArgumentException.class,
                () -> field.sample(0, Double.NaN, 0, twoBedPlan(0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> field.sample(0, 0.0, 0, twoBedPlan(0.0), Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> field.sampleAtVerticalOffset(0.0, twoBedPlan(0.0), Double.POSITIVE_INFINITY));
    }

    private static SedimentaryContactPlanner.Plan twoBedPlan(double phase) {
        return new SedimentaryContactPlanner.Plan(
                "two_bed_cycle",
                "regional",
                2.0,
                phase,
                List.of(
                        new SedimentaryContactPlanner.Interval(0, "lower", 1.0, 0.0, 0.5),
                        new SedimentaryContactPlanner.Interval(1, "upper", 1.0, 0.5, 1.0)
                )
        );
    }
}
