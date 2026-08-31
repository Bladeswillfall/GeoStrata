package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RiftListricFaultTest {
    @Test
    void riftFaultsFlattenWithDepth() {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                42424242L,
                GeologyProvince.RIFT_PROVINCE,
                0,
                0,
                48.0
        );

        assertTrue(field.faultCurvaturePerVerticalBlockSquared() != 0.0);
        assertTrue(field.faultDipDegrees(-192.0) < field.faultDipDegrees(128.0));
    }

    @Test
    void nonRiftFaultFamiliesRemainPlanar() {
        TectonicStructuralField.Context basin = TectonicStructuralField.forSite(
                42424242L,
                GeologyProvince.SEDIMENTARY_BASIN,
                0,
                0,
                48.0
        );
        TectonicStructuralField.Context orogen = TectonicStructuralField.forSite(
                42424242L,
                GeologyProvince.OROGENIC_BELT,
                0,
                0,
                48.0
        );

        assertEquals(0.0, basin.faultCurvaturePerVerticalBlockSquared(), 0.0);
        assertEquals(0.0, orogen.faultCurvaturePerVerticalBlockSquared(), 0.0);
    }
}
