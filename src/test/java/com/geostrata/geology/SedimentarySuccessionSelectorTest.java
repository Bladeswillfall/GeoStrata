package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SedimentarySuccessionSelectorTest {
    private static final double EPSILON = 1.0E-12;

    @Test
    void declaredContextReceivesFullScoreAndFallbackIsReduced() {
        Fixtures fixtures = fixtures();
        assertEquals(
                1.0,
                SedimentarySuccessionSelector.score(
                        GeologyProvince.SEDIMENTARY_BASIN,
                        fixtures.basin(),
                        fixtures.profiles()
                ),
                EPSILON
        );
        assertEquals(
                0.2,
                SedimentarySuccessionSelector.score(
                        GeologyProvince.SEDIMENTARY_BASIN,
                        fixtures.rift(),
                        fixtures.profiles()
                ),
                EPSILON
        );
    }

    @Test
    void deterministicWeightedChoiceRespondsToProvinceContext() {
        Fixtures fixtures = fixtures();
        SedimentarySuccessionSelector.Selection basin = SedimentarySuccessionSelector.selectForSite(
                1L, GeologyProvince.SEDIMENTARY_BASIN, 0, 0, fixtures.profiles(), fixtures.successions()
        );
        SedimentarySuccessionSelector.Selection basinAgain = SedimentarySuccessionSelector.selectForSite(
                1L, GeologyProvince.SEDIMENTARY_BASIN, 0, 0, fixtures.profiles(), fixtures.successions()
        );
        SedimentarySuccessionSelector.Selection rift = SedimentarySuccessionSelector.selectForSite(
                1L, GeologyProvince.RIFT_PROVINCE, 0, 0, fixtures.profiles(), fixtures.successions()
        );

        assertEquals(basin, basinAgain);
        assertEquals("basin_cycle", basin.succession().id());
        assertEquals("rift_cycle", rift.succession().id());
    }

    private static Fixtures fixtures() {
        SedimentarySuccessions.Succession basin = new SedimentarySuccessions.Succession(
                "basin_cycle",
                List.of(GeologyProvince.SEDIMENTARY_BASIN),
                "regional",
                List.of(
                        new SedimentarySuccessions.Bed("limestone", 1.0),
                        new SedimentarySuccessions.Bed("shale", 1.0),
                        new SedimentarySuccessions.Bed("limestone", 1.0)
                )
        );
        SedimentarySuccessions.Succession rift = new SedimentarySuccessions.Succession(
                "rift_cycle",
                List.of(GeologyProvince.RIFT_PROVINCE),
                "regional",
                List.of(
                        new SedimentarySuccessions.Bed("conglomerate", 1.0),
                        new SedimentarySuccessions.Bed("siltstone", 1.0),
                        new SedimentarySuccessions.Bed("conglomerate", 1.0)
                )
        );

        EnumMap<GeologyProvince, Map<String, Double>> weights = new EnumMap<>(GeologyProvince.class);
        Map<String, Double> uniform = Map.of(
                "limestone", 1.0,
                "shale", 1.0,
                "conglomerate", 1.0,
                "siltstone", 1.0
        );
        weights.put(GeologyProvince.SEDIMENTARY_BASIN, uniform);
        weights.put(GeologyProvince.RIFT_PROVINCE, uniform);

        GeologyProvinceProfiles.Snapshot profiles = new GeologyProvinceProfiles.Snapshot(
                "runtime_bias",
                192,
                Set.copyOf(uniform.keySet()),
                Map.copyOf(weights)
        );
        SedimentarySuccessions.Snapshot successions = new SedimentarySuccessions.Snapshot(
                "metadata_only",
                List.of(rift, basin),
                Map.of(basin.id(), basin, rift.id(), rift)
        );
        return new Fixtures(profiles, successions, basin, rift);
    }

    private record Fixtures(
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions,
            SedimentarySuccessions.Succession basin,
            SedimentarySuccessions.Succession rift
    ) {
    }
}
