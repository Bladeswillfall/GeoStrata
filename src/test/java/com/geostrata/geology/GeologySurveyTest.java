package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeologySurveyTest {
    @Test
    void repeatedSurveyIsDeterministicAndNeverWorseThanOrigin() {
        GeologyProvinceProfiles.Snapshot profiles = profiles();
        long seed = 8675309L;
        int originX = -400;
        int originZ = 725;

        GeologyProvinceSampler.Sample origin = GeologyProvinceSampler.sample(seed, originX, originZ);
        double originWeight = profiles.effectiveWeight(origin, "shale");
        GeologySurvey.Result first = GeologySurvey.findBest(seed, originX, originZ, "shale", profiles, 1536, 96);
        GeologySurvey.Result second = GeologySurvey.findBest(seed, originX, originZ, "shale", profiles, 1536, 96);

        assertEquals(first, second);
        assertTrue(first.weight() + 1.0E-12 >= originWeight);
        assertTrue(first.distanceSquared() <= 2L * 1536L * 1536L);
    }

    @Test
    void rejectsUnknownLithologyAndInvalidGeometry() {
        GeologyProvinceProfiles.Snapshot profiles = profiles();
        assertThrows(IllegalArgumentException.class,
                () -> GeologySurvey.findBest(1L, 0, 0, "limestone", profiles, 1536, 96));
        assertThrows(IllegalArgumentException.class,
                () -> GeologySurvey.findBest(1L, 0, 0, "shale", profiles, -1, 96));
        assertThrows(IllegalArgumentException.class,
                () -> GeologySurvey.findBest(1L, 0, 0, "shale", profiles, 1536, 0));
    }

    private static GeologyProvinceProfiles.Snapshot profiles() {
        EnumMap<GeologyProvince, Map<String, Double>> weights = new EnumMap<>(GeologyProvince.class);
        weights.put(GeologyProvince.SEDIMENTARY_BASIN, Map.of("shale", 1.0));
        weights.put(GeologyProvince.CRATONIC_SHIELD, Map.of("shale", 0.15));
        weights.put(GeologyProvince.OROGENIC_BELT, Map.of("shale", 0.45));
        weights.put(GeologyProvince.VOLCANIC_ARC, Map.of("shale", 0.2));
        weights.put(GeologyProvince.RIFT_PROVINCE, Map.of("shale", 0.7));
        return new GeologyProvinceProfiles.Snapshot(
                "runtime_bias",
                192,
                Set.of("shale"),
                Map.copyOf(weights)
        );
    }
}
