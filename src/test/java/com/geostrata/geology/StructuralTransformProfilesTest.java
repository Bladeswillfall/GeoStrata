package com.geostrata.geology;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class StructuralTransformProfilesTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void parsesExactProvinceCoverageAndPhysicalScales() {
        StructuralTransformProfiles.Snapshot snapshot = StructuralTransformProfiles.parse(root("metadata_only", true, 68.0));

        assertEquals("metadata_only", snapshot.runtimeStatus());
        assertEquals(5, snapshot.profiles().size());
        assertEquals(68.0, snapshot.profileFor(GeologyProvince.OROGENIC_BELT).maxDipDegrees(), EPSILON);
        assertEquals(64.0, snapshot.profileFor(GeologyProvince.RIFT_PROVINCE).maxFaultDisplacementBlocks(), EPSILON);
    }

    @Test
    void rejectsRuntimeActivationMissingProvinceAndUnsafeDip() {
        assertThrows(IllegalArgumentException.class,
                () -> StructuralTransformProfiles.parse(root("experimental_runtime", true, 68.0)));
        assertThrows(IllegalArgumentException.class,
                () -> StructuralTransformProfiles.parse(root("metadata_only", false, 68.0)));
        assertThrows(IllegalArgumentException.class,
                () -> StructuralTransformProfiles.parse(root("metadata_only", true, 76.0)));
    }

    private static JsonObject root(String runtimeStatus, boolean includeRift, double orogenicDip) {
        String rift = includeRift
                ? profile("rift_province", 48.0, 10.0, 512.0, 64.0)
                : "";
        return JsonParser.parseString("""
                {
                  "schemaVersion": 1,
                  "model": "geostrata:structural_transform_profiles",
                  "runtimeStatus": "%s",
                  "profiles": [
                    %s,
                    %s,
                    %s,
                    %s
                    %s
                  ]
                }
                """.formatted(
                runtimeStatus,
                profile("sedimentary_basin", 18.0, 6.0, 512.0, 8.0),
                profile("cratonic_shield", 28.0, 12.0, 640.0, 16.0),
                profile("orogenic_belt", orogenicDip, 48.0, 256.0, 48.0),
                profile("volcanic_arc", 38.0, 20.0, 384.0, 32.0),
                rift.isEmpty() ? "" : "," + rift
        )).getAsJsonObject();
    }

    private static String profile(
            String province,
            double dip,
            double foldAmplitude,
            double foldWavelength,
            double faultDisplacement
    ) {
        return """
                {
                  "province": "%s",
                  "maxDipDegrees": %s,
                  "maxFoldAmplitudeBlocks": %s,
                  "foldWavelengthBlocks": %s,
                  "maxFaultDisplacementBlocks": %s,
                  "faultPlaneOffsetRangeBlocks": 192.0
                }
                """.formatted(province, dip, foldAmplitude, foldWavelength, faultDisplacement);
    }
}
