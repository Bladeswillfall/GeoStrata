package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CorrelatedOverturnedFoldTest {
    @Test
    void correlatedBedOrderReversesUpwardOnOverturnedLimb() {
        ActiveLocation active = activeLocation();
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = site(active);
        CorrelatedSedimentaryRuntime.Column column = site.column(active.seed(), active.x(), active.z());

        assertTrue(column.foldPolarity().overturned());
        boolean foundReversedContact = false;
        SedimentaryStratigraphicField.Sample previous = column.sample(-128);
        double previousOffset = column.verticalOffset(-128);

        for (int y = -127; y <= 256; y++) {
            SedimentaryStratigraphicField.Sample current = column.sample(y);
            double currentOffset = column.verticalOffset(y);
            if (Double.compare(previousOffset, currentOffset) == 0) {
                assertTrue(
                        current.stratigraphicCoordinate() < previous.stratigraphicCoordinate(),
                        "stratigraphic coordinate must decrease upward away from fault jumps"
                );
                if (!current.bed().lithology().equals(previous.bed().lithology())) {
                    foundReversedContact = true;
                }
            }
            previous = current;
            previousOffset = currentOffset;
        }

        assertTrue(foundReversedContact, "expected upward traversal to cross a reversed stratigraphic contact");
    }

    private static CorrelatedSedimentaryRuntime.TerrainAwareSite site(ActiveLocation active) {
        SedimentarySuccessions.Succession succession = new SedimentarySuccessions.Succession(
                "overturned_test",
                List.of(GeologyProvince.OROGENIC_BELT),
                "local",
                List.of(
                        new SedimentarySuccessions.Bed("shale", 1.0),
                        new SedimentarySuccessions.Bed("limestone", 1.0)
                )
        );
        SedimentaryContactPlanner.Plan plan = new SedimentaryContactPlanner.Plan(
                "overturned_test",
                "local",
                2.0,
                0.0,
                List.of(
                        new SedimentaryContactPlanner.Interval(0, "shale", 1.0, 0.0, 0.5),
                        new SedimentaryContactPlanner.Interval(1, "limestone", 1.0, 0.5, 1.0)
                )
        );
        SedimentaryStratigraphicField.Field baseField = new SedimentaryStratigraphicField.Field(
                0,
                0,
                32.0,
                0.0,
                0.0,
                0.0,
                96.0,
                0.0
        );
        CorrelatedSedimentaryExperiment.Ownership ownership = new CorrelatedSedimentaryExperiment.Ownership(
                true,
                "owned",
                GeologyProvince.OROGENIC_BELT,
                256.0,
                succession.id()
        );
        CorrelatedSedimentaryRuntime.Site base = new CorrelatedSedimentaryRuntime.Site(
                active.x(),
                active.z(),
                ownership,
                succession,
                plan,
                baseField
        );
        TerrainMorphologySample terrain = new TerrainMorphologySample(80.0, 0.0, 0.0, 0.0, 0.0);
        int patchX = Math.floorDiv(active.x(), 128) * 128;
        int patchZ = Math.floorDiv(active.z(), 128) * 128;
        TerrainAwareStructuralField.TerrainPatch patch = new TerrainAwareStructuralField.TerrainPatch(
                patchX,
                patchZ,
                128,
                terrain,
                terrain,
                terrain,
                terrain
        );
        TerrainAwareStructuralField.Field field = TerrainAwareStructuralField.apply(
                baseField,
                GeologyProvince.OROGENIC_BELT,
                patch,
                80.0,
                active.field()
        );
        return new CorrelatedSedimentaryRuntime.TerrainAwareSite(base, field);
    }

    private static ActiveLocation activeLocation() {
        for (long seed = 0; seed < 4096; seed++) {
            TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                    seed,
                    GeologyProvince.OROGENIC_BELT,
                    0,
                    0,
                    32.0
            );
            TectonicFoldPolarity.Profile profile = TectonicFoldPolarity.forField(
                    GeologyProvince.OROGENIC_BELT,
                    field,
                    32.0,
                    80.0
            );
            if (!profile.active()) {
                continue;
            }
            for (int x = -1024; x <= 1024; x += 16) {
                for (int z = -1024; z <= 1024; z += 16) {
                    if (profile.transform(field, x, z).verticalScale() < -0.1) {
                        return new ActiveLocation(seed, x, z, field);
                    }
                }
            }
        }
        throw new AssertionError("expected an overturned orogenic test column");
    }

    private record ActiveLocation(
            long seed,
            int x,
            int z,
            TectonicStructuralField.Context field
    ) {
    }
}
