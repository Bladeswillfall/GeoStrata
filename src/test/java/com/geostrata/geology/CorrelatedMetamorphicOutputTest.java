package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CorrelatedMetamorphicOutputTest {
    private static final long SEED = 123456789L;
    private static final int X = 1000;
    private static final int Z = -500;

    @Test
    void orogenicMudrockResolvesToMetamorphicOutput() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = site(GeologyProvince.OROGENIC_BELT, "shale");

        String output = site.outputLithology(SEED, X, 0, Z, catalog());

        assertTrue(Set.of("slate", "schist", "gneiss").contains(output));
    }

    @Test
    void orogenicCarbonateResolvesToMarble() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = site(GeologyProvince.OROGENIC_BELT, "limestone");

        assertEquals("marble", site.outputLithology(SEED, X, 0, Z, catalog()));
    }

    @Test
    void orogenicQuartzSandstoneResolvesToQuartzite() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = site(GeologyProvince.OROGENIC_BELT, "sandstone");

        assertEquals("quartzite", site.outputLithology(SEED, X, 0, Z, catalog()));
    }

    @Test
    void nonMetamorphicParentIsPreserved() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = site(GeologyProvince.OROGENIC_BELT, "siltstone");

        assertEquals("siltstone", site.outputLithology(SEED, X, 0, Z, catalog()));
    }

    @Test
    void metamorphicParentsOutsideOrogenicExperimentArePreserved() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite mudrock = site(GeologyProvince.SEDIMENTARY_BASIN, "shale");
        CorrelatedSedimentaryRuntime.TerrainAwareSite sandstone = site(GeologyProvince.SEDIMENTARY_BASIN, "sandstone");

        assertEquals("shale", mudrock.outputLithology(SEED, X, 0, Z, catalog()));
        assertEquals("sandstone", sandstone.outputLithology(SEED, X, 0, Z, catalog()));
    }

    @Test
    void cachedColumnMatchesFreshSamplingAcrossExtendedHeight() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = layeredSite();
        LithologyCatalog.Snapshot catalog = catalog();
        CorrelatedSedimentaryRuntime.Column cached = site.column(SEED, X, Z);

        for (int y = -128; y <= 640; y++) {
            assertEquals(
                    site.outputLithology(SEED, X, y, Z, catalog),
                    cached.outputLithology(y, catalog),
                    "cached column diverged at y=" + y
            );
        }
    }

    private static CorrelatedSedimentaryRuntime.TerrainAwareSite site(
            GeologyProvince province,
            String parentLithology
    ) {
        SedimentarySuccessions.Succession succession = new SedimentarySuccessions.Succession(
                "test",
                List.of(province),
                "local",
                List.of(new SedimentarySuccessions.Bed(parentLithology, 1.0))
        );
        SedimentaryContactPlanner.Plan plan = new SedimentaryContactPlanner.Plan(
                "test",
                "local",
                1.0,
                0.0,
                List.of(new SedimentaryContactPlanner.Interval(0, parentLithology, 1.0, 0.0, 1.0))
        );
        return site(province, succession, plan);
    }

    private static CorrelatedSedimentaryRuntime.TerrainAwareSite layeredSite() {
        SedimentarySuccessions.Succession succession = new SedimentarySuccessions.Succession(
                "layered_test",
                List.of(GeologyProvince.OROGENIC_BELT),
                "local",
                List.of(
                        new SedimentarySuccessions.Bed("shale", 1.0),
                        new SedimentarySuccessions.Bed("siltstone", 1.0),
                        new SedimentarySuccessions.Bed("limestone", 2.0)
                )
        );
        SedimentaryContactPlanner.Plan plan = new SedimentaryContactPlanner.Plan(
                "layered_test",
                "local",
                4.0,
                0.17,
                List.of(
                        new SedimentaryContactPlanner.Interval(0, "shale", 1.0, 0.0, 0.25),
                        new SedimentaryContactPlanner.Interval(1, "siltstone", 1.0, 0.25, 0.5),
                        new SedimentaryContactPlanner.Interval(2, "limestone", 2.0, 0.5, 1.0)
                )
        );
        return site(GeologyProvince.OROGENIC_BELT, succession, plan);
    }

    private static CorrelatedSedimentaryRuntime.TerrainAwareSite site(
            GeologyProvince province,
            SedimentarySuccessions.Succession succession,
            SedimentaryContactPlanner.Plan plan
    ) {
        SedimentaryStratigraphicField.Field baseField = new SedimentaryStratigraphicField.Field(
                1165,
                -602,
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
                province,
                122.0,
                succession.id()
        );
        CorrelatedSedimentaryRuntime.Site base = new CorrelatedSedimentaryRuntime.Site(
                1000,
                -504,
                ownership,
                succession,
                plan,
                baseField
        );
        TerrainMorphologySample terrain = new TerrainMorphologySample(80.0, 0.0, 0.0, 0.0, 0.0);
        TerrainAwareStructuralField.TerrainPatch patch = new TerrainAwareStructuralField.TerrainPatch(
                896,
                -512,
                128,
                terrain,
                terrain,
                terrain,
                terrain
        );
        return new CorrelatedSedimentaryRuntime.TerrainAwareSite(
                base,
                TerrainAwareStructuralField.apply(baseField, province, patch, 80.0)
        );
    }

    private static LithologyCatalog.Snapshot catalog() {
        LithologyCatalog.Entry shale = entry("shale", "mudrock");
        LithologyCatalog.Entry siltstone = entry("siltstone", "silt_clastic");
        LithologyCatalog.Entry sandstone = entry("sandstone", "quartz_sandstone");
        LithologyCatalog.Entry limestone = entry("limestone", "carbonate");
        return new LithologyCatalog.Snapshot(
                "metadata_only",
                List.of(shale, siltstone, sandstone, limestone),
                Map.of(
                        "shale", shale,
                        "siltstone", siltstone,
                        "sandstone", sandstone,
                        "limestone", limestone
                )
        );
    }

    private static LithologyCatalog.Entry entry(String id, String genesis) {
        return new LithologyCatalog.Entry(
                id,
                "geostrata:" + id,
                "sedimentary",
                genesis,
                "bedded",
                "mid",
                "local",
                "geostrata:has_common_rocks",
                id + "_ore"
        );
    }
}
