package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeologyResolverTest {
    private static final long SEED = 123456789L;

    @Test
    void resolverMatchesExistingTerrainAwareOutput() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = site(GeologyProvince.OROGENIC_BELT, "shale");
        LithologyCatalog.Snapshot catalog = catalog("shale", "mudrock");
        int[][] coordinates = {
                {930, -48, -490},
                {1000, 0, -500},
                {1018, 37, -455}
        };

        for (int[] coordinate : coordinates) {
            int x = coordinate[0];
            int y = coordinate[1];
            int z = coordinate[2];
            GeologyResolver.Result resolved = GeologyResolver.resolve(SEED, x, y, z, site, catalog);

            assertEquals(site.outputLithology(SEED, x, y, z, catalog), resolved.lithology());
            assertEquals(site.sample(x, y, z).bed().lithology(), resolved.parentLithology());
        }
    }

    @Test
    void resolverReportsSemanticProvenanceWithoutChangingMetamorphism() {
        CorrelatedSedimentaryRuntime.TerrainAwareSite site = site(GeologyProvince.OROGENIC_BELT, "limestone");
        GeologyResolver.Result resolved = GeologyResolver.resolve(
                SEED,
                1000,
                0,
                -500,
                site,
                catalog("limestone", "carbonate")
        );

        assertEquals("limestone", resolved.parentLithology());
        assertEquals("marble", resolved.lithology());
        assertEquals(GeologyProvince.OROGENIC_BELT, resolved.province());
        assertEquals(GeologyResolver.Source.CORRELATED_STRATIGRAPHY, resolved.source());
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
                0.08,
                0.15,
                2.5,
                96.0,
                0.4
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
        TerrainAwareStructuralField.TerrainPatch patch = new TerrainAwareStructuralField.TerrainPatch(
                896,
                -512,
                128,
                new TerrainMorphologySample(72.0, 0.0, 0.0, 28.0, 8.0),
                new TerrainMorphologySample(104.0, 0.0, 0.0, 42.0, 22.0),
                new TerrainMorphologySample(84.0, 0.0, 0.0, 34.0, 12.0),
                new TerrainMorphologySample(118.0, 0.0, 0.0, 50.0, 30.0)
        );
        return new CorrelatedSedimentaryRuntime.TerrainAwareSite(
                base,
                TerrainAwareStructuralField.apply(baseField, province, patch, 80.0)
        );
    }

    private static LithologyCatalog.Snapshot catalog(String id, String genesis) {
        LithologyCatalog.Entry entry = new LithologyCatalog.Entry(
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
        return new LithologyCatalog.Snapshot("metadata_only", List.of(entry), Map.of(id, entry));
    }
}
