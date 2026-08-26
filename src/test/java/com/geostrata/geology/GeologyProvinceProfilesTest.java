package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeologyProvinceProfilesTest {
    private static final double EPSILON = 1.0e-12;

    @Test
    void interiorBlendUsesPrimaryProfile() {
        assertEquals(1.0, GeologyProvinceProfiles.blend(1.0, 0.2, 1.0), EPSILON);
    }

    @Test
    void boundaryBlendIsEven() {
        assertEquals(0.6, GeologyProvinceProfiles.blend(1.0, 0.2, 0.0), EPSILON);
    }

    @Test
    void transitionBlendMovesSmoothlyTowardPrimary() {
        assertEquals(0.8, GeologyProvinceProfiles.blend(1.0, 0.2, 0.5), EPSILON);
    }

    @Test
    void blendInputIsClamped() {
        assertEquals(0.6, GeologyProvinceProfiles.blend(1.0, 0.2, -10.0), EPSILON);
        assertEquals(1.0, GeologyProvinceProfiles.blend(1.0, 0.2, 10.0), EPSILON);
    }
}
