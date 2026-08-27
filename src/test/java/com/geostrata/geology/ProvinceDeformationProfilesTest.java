package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ProvinceDeformationProfilesTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void parsesExactProvinceCoverage() {
        ProvinceDeformationProfiles.Snapshot snapshot = ProvinceDeformationProfiles.parse(
                root("metadata_only", true, 0.5, 0.3, 0.2, 0.40)
        );

        assertEquals("metadata_only", snapshot.runtimeStatus());
        assertEquals(5, snapshot.profiles().size());
        assertEquals(160.0, snapshot.normalization().reliefScaleBlocks(), EPSILON);
        assertEquals(0.55, snapshot.profileFor(GeologyProvince.OROGENIC_BELT).baselineIntensity(), EPSILON);
        assertEquals(1.0, snapshot.profileFor(GeologyProvince.RIFT_PROVINCE).faultPotential(), EPSILON);
    }

    @Test
    void rejectsMissingProvinceAndRuntimeActivation() {
        assertThrows(IllegalArgumentException.class, () -> ProvinceDeformationProfiles.parse(
                root("metadata_only", false, 0.5, 0.3, 0.2, 0.40)
        ));
        assertThrows(IllegalArgumentException.class, () -> ProvinceDeformationProfiles.parse(
                root("runtime_bias", true, 0.5, 0.3, 0.2, 0.40)
        ));
    }

    @Test
    void rejectsInvalidNormalizationAndUnboundedIntensity() {
        assertThrows(IllegalArgumentException.class, () -> ProvinceDeformationProfiles.parse(
                root("metadata_only", true, 0.5, 0.3, 0.3, 0.40)
        ));
        assertThrows(IllegalArgumentException.class, () -> ProvinceDeformationProfiles.parse(
                root("metadata_only", true, 0.5, 0.3, 0.2, 0.55)
        ));
    }

    private static JsonObject root(
            String runtimeStatus,
            boolean includeRift,
            double reliefWeight,
            double slopeWeight,
            double ridgeWeight,
            double orogenicTerrainCoupling
    ) {
        String rift = includeRift
                ? """
                  ,{
                    "province": "rift_province",
                    "baselineIntensity": 0.35,
                    "terrainCoupling": 0.35,
                    "dipPotential": 0.70,
                    "foldPotential": 0.15,
                    "faultPotential": 1.00
                  }
                  """
                : "";
        return JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:province_deformation_profiles",
                  "runtimeStatus": "%s",
                  "morphologyNormalization": {
                    "reliefScaleBlocks": 160.0,
                    "slopeScale": 0.5,
                    "ridgeProminenceScaleBlocks": 64.0,
                    "reliefWeight": %s,
                    "slopeWeight": %s,
                    "ridgeWeight": %s
                  },
                  "profiles": [
                    {
                      "province": "sedimentary_basin",
                      "baselineIntensity": 0.08,
                      "terrainCoupling": 0.22,
                      "dipPotential": 0.70,
                      "foldPotential": 0.20,
                      "faultPotential": 0.15
                    },
                    {
                      "province": "cratonic_shield",
                      "baselineIntensity": 0.18,
                      "terrainCoupling": 0.12,
                      "dipPotential": 0.30,
                      "foldPotential": 0.35,
                      "faultPotential": 0.20
                    },
                    {
                      "province": "orogenic_belt",
                      "baselineIntensity": 0.55,
                      "terrainCoupling": %s,
                      "dipPotential": 0.95,
                      "foldPotential": 1.00,
                      "faultPotential": 0.75
                    },
                    {
                      "province": "volcanic_arc",
                      "baselineIntensity": 0.35,
                      "terrainCoupling": 0.25,
                      "dipPotential": 0.45,
                      "foldPotential": 0.30,
                      "faultPotential": 0.55
                    }
                    %s
                  ]
                }
                """.formatted(
                runtimeStatus,
                reliefWeight,
                slopeWeight,
                ridgeWeight,
                orogenicTerrainCoupling,
                rift
        )).getAsJsonObject();
    }
}
