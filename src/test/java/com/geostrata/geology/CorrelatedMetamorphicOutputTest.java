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
    void nonMudrockParentIsPreserved() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = site(GeologyProvince.OROGENIC_BELT, "siltstone");

        assertEquals("siltstone", site.outputLithology(SEED, X, 0, Z, catalog()));
    }

    @Test
    void mudrockOutsideOrogenicExperimentIsPreserved() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = site(GeologyProvince.SEDIMENTARY_BASIN, "shale");

        assertEquals("shale", site.outputLithology(SEED, X, 0, Z, catalog()));
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
                "test"
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
        return new LithologyCatalog.Snapshot(
                "metadata_only",
                List.of(shale, siltstone),
                Map.of("shale", shale, "siltstone", siltstone)
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
