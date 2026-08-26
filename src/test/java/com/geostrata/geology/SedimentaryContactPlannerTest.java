package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SedimentaryContactPlannerTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void normalizesRelativeThicknessAndPreservesLowerToUpperOrder() {
        SedimentarySuccessions.Succession succession = succession(
                new SedimentarySuccessions.Bed("siltstone", 0.8),
                new SedimentarySuccessions.Bed("shale", 1.2),
                new SedimentarySuccessions.Bed("limestone", 1.0),
                new SedimentarySuccessions.Bed("shale", 0.7),
                new SedimentarySuccessions.Bed("mudstone", 0.9)
        );

        SedimentaryContactPlanner.Plan plan = SedimentaryContactPlanner.plan(1234L, 256, -512, succession);

        assertEquals(4.6, plan.totalRelativeThickness(), EPSILON);
        assertEquals(5, plan.intervals().size());
        assertEquals(List.of("siltstone", "shale", "limestone", "shale", "mudstone"),
                plan.intervals().stream().map(SedimentaryContactPlanner.Interval::lithology).toList());
        assertEquals(List.of(0, 1, 2, 3, 4),
                plan.intervals().stream().map(SedimentaryContactPlanner.Interval::ordinal).toList());
        assertEquals(0.0, plan.intervals().get(0).lowerFraction(), EPSILON);
        assertEquals(0.8 / 4.6, plan.intervals().get(0).upperFraction(), EPSILON);
        assertEquals(2.0 / 4.6, plan.intervals().get(1).upperFraction(), EPSILON);
        assertEquals(3.0 / 4.6, plan.intervals().get(2).upperFraction(), EPSILON);
        assertEquals(3.7 / 4.6, plan.intervals().get(3).upperFraction(), EPSILON);
        assertEquals(1.0, plan.intervals().get(4).upperFraction(), EPSILON);
    }

    @Test
    void exactContactBelongsToOverlyingBed() {
        SedimentaryContactPlanner.Plan plan = SedimentaryContactPlanner.plan(
                99L,
                0,
                0,
                succession(
                        new SedimentarySuccessions.Bed("lower", 1.0),
                        new SedimentarySuccessions.Bed("middle", 2.0),
                        new SedimentarySuccessions.Bed("upper", 1.0)
                )
        );

        assertEquals("lower", plan.bedAt(0.0).lithology());
        assertEquals("lower", plan.bedAt(Math.nextDown(0.25)).lithology());
        assertEquals("middle", plan.bedAt(0.25).lithology());
        assertEquals("middle", plan.bedAt(Math.nextDown(0.75)).lithology());
        assertEquals("upper", plan.bedAt(0.75).lithology());
        assertEquals("upper", plan.bedAt(Math.nextDown(1.0)).lithology());
        assertThrows(IllegalArgumentException.class, () -> plan.bedAt(1.0));
        assertThrows(IllegalArgumentException.class, () -> plan.bedAt(-0.001));
        assertThrows(IllegalArgumentException.class, () -> plan.bedAt(Double.NaN));
    }

    @Test
    void phaseIsStableAndSiteAnchored() {
        SedimentarySuccessions.Succession succession = succession(
                new SedimentarySuccessions.Bed("shale", 1.0),
                new SedimentarySuccessions.Bed("limestone", 1.0)
        );

        SedimentaryContactPlanner.Plan first = SedimentaryContactPlanner.plan(8675309L, -320, 704, succession);
        SedimentaryContactPlanner.Plan repeated = SedimentaryContactPlanner.plan(8675309L, -320, 704, succession);
        SedimentaryContactPlanner.Plan neighboringSite = SedimentaryContactPlanner.plan(8675309L, 448, 704, succession);

        assertEquals(first.phase(), repeated.phase(), 0.0);
        assertTrue(first.phase() >= 0.0 && first.phase() < 1.0);
        assertTrue(neighboringSite.phase() >= 0.0 && neighboringSite.phase() < 1.0);
        assertTrue(Double.compare(first.phase(), neighboringSite.phase()) != 0);
    }

    @Test
    void rejectsInvalidThicknessOrEmptySuccession() {
        assertThrows(IllegalArgumentException.class, () -> SedimentaryContactPlanner.plan(
                1L,
                0,
                0,
                new SedimentarySuccessions.Succession(
                        "empty",
                        List.of(GeologyProvince.SEDIMENTARY_BASIN),
                        "regional",
                        List.of()
                )
        ));

        assertThrows(IllegalArgumentException.class, () -> SedimentaryContactPlanner.plan(
                1L,
                0,
                0,
                succession(
                        new SedimentarySuccessions.Bed("shale", 1.0),
                        new SedimentarySuccessions.Bed("limestone", Double.POSITIVE_INFINITY)
                )
        ));
    }

    @Test
    void rejectsOutOfOrderIntervalOrdinals() {
        List<SedimentaryContactPlanner.Interval> intervals = List.of(
                new SedimentaryContactPlanner.Interval(1, "shale", 1.0, 0.0, 0.5),
                new SedimentaryContactPlanner.Interval(0, "limestone", 1.0, 0.5, 1.0)
        );

        assertThrows(IllegalArgumentException.class, () -> new SedimentaryContactPlanner.Plan(
                "broken_order",
                "regional",
                2.0,
                0.25,
                intervals
        ));
    }

    private static SedimentarySuccessions.Succession succession(SedimentarySuccessions.Bed... beds) {
        return new SedimentarySuccessions.Succession(
                "test_cycle",
                List.of(GeologyProvince.SEDIMENTARY_BASIN),
                "regional",
                List.of(beds)
        );
    }
}
