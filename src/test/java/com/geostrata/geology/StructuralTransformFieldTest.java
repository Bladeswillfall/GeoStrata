package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StructuralTransformFieldTest {
    private static final double EPSILON = 1.0e-10;

    @Test
    void deterministicSiteFieldKeepsProvinceSiteAsZeroOffsetAnchor() {
        StructuralTransformProfiles.Profile profile = new StructuralTransformProfiles.Profile(
                GeologyProvince.OROGENIC_BELT,
                68.0,
                48.0,
                256.0,
                48.0,
                192.0
        );
        StructuralDeformationResponse.Result response = response(1.0, 1.0, 1.0);

        StructuralTransformField.Field first = StructuralTransformField.forSite(
                123456789L,
                768,
                -1536,
                profile,
                response
        );
        StructuralTransformField.Field second = StructuralTransformField.forSite(
                123456789L,
                768,
                -1536,
                profile,
                response
        );

        assertEquals(first, second);
        assertEquals(68.0, first.dipDegrees(), EPSILON);
        assertEquals(0.0, first.sample(768.0, -1536.0).totalOffset(), EPSILON);
        assertTrue(Double.isFinite(first.dipGradient()));
    }

    @Test
    void openFoldAndFaultPrimitiveComposeWithoutMovingBlocks() {
        StructuralTransformField.Field field = new StructuralTransformField.Field(
                0,
                0,
                1.0,
                0.0,
                0.0,
                0.0,
                10.0,
                100.0,
                0.0,
                50.0,
                20.0
        );

        StructuralTransformField.Sample crest = field.sample(25.0, 0.0);
        StructuralTransformField.Sample acrossFault = field.sample(60.0, 0.0);

        assertEquals(10.0, crest.foldOffset(), EPSILON);
        assertEquals(0.0, crest.faultOffset(), EPSILON);
        assertEquals(20.0, acrossFault.faultOffset(), EPSILON);
        assertEquals(80.0 - acrossFault.totalOffset(), acrossFault.transformY(80.0), EPSILON);
    }

    @Test
    void deterministicStructuralOrientationVariesBySite() {
        StructuralTransformProfiles.Profile profile = new StructuralTransformProfiles.Profile(
                GeologyProvince.RIFT_PROVINCE,
                48.0,
                10.0,
                512.0,
                64.0,
                192.0
        );
        StructuralDeformationResponse.Result response = response(0.7, 0.2, 1.0);

        StructuralTransformField.Field first = StructuralTransformField.forSite(42L, 0, 0, profile, response);
        StructuralTransformField.Field second = StructuralTransformField.forSite(42L, 768, 0, profile, response);

        assertNotEquals(first.normalX(), second.normalX());
        assertNotEquals(first.faultPlaneOffsetBlocks(), second.faultPlaneOffsetBlocks());
    }

    @Test
    void provinceBoundaryBlendsTransformOffsetsWithoutHardSeam() {
        StructuralTransformField.Sample primary = new StructuralTransformField.Sample(20.0, 10.0, 30.0);
        StructuralTransformField.Sample neighbor = new StructuralTransformField.Sample(0.0, -10.0, -10.0);

        StructuralTransformField.Sample boundary = StructuralTransformField.blend(primary, neighbor, 0.0);
        StructuralTransformField.Sample interior = StructuralTransformField.blend(primary, neighbor, 1.0);

        assertEquals(10.0, boundary.dipOffset(), EPSILON);
        assertEquals(0.0, boundary.foldOffset(), EPSILON);
        assertEquals(10.0, boundary.faultOffset(), EPSILON);
        assertEquals(primary, interior);
    }

    @Test
    void rejectsInvalidProfilesFieldsAndSamples() {
        assertThrows(IllegalArgumentException.class, () -> new StructuralTransformProfiles.Profile(
                GeologyProvince.OROGENIC_BELT,
                80.0,
                48.0,
                256.0,
                48.0,
                192.0
        ));
        assertThrows(IllegalArgumentException.class, () -> new StructuralTransformProfiles.Profile(
                GeologyProvince.OROGENIC_BELT,
                60.0,
                80.0,
                256.0,
                48.0,
                192.0
        ));

        StructuralTransformField.Field field = new StructuralTransformField.Field(
                0,
                0,
                1.0,
                0.0,
                20.0,
                Math.tan(Math.toRadians(20.0)),
                0.0,
                128.0,
                0.0,
                64.0,
                0.0
        );
        assertThrows(IllegalArgumentException.class, () -> field.sample(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> field.sample(0.0, 0.0).transformY(Double.NaN));
    }

    private static StructuralDeformationResponse.Result response(double dip, double fold, double fault) {
        return new StructuralDeformationResponse.Result(1.0, 1.0, 1.0, 1.0, 1.0, dip, fold, fault);
    }
}
