package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FaultDamageZoneTest {
    @Test
    void onlyRiftAndOrogenicFaultsProduceDamageZones() {
        long seed = 246813579L;
        assertHasDamage(seed, GeologyProvince.RIFT_PROVINCE);
        assertHasDamage(seed, GeologyProvince.OROGENIC_BELT);
        assertNoDamage(seed, GeologyProvince.SEDIMENTARY_BASIN);
        assertNoDamage(seed, GeologyProvince.CRATONIC_SHIELD);
        assertNoDamage(seed, GeologyProvince.VOLCANIC_ARC);
    }

    @Test
    void damageZoneFollowsTheFaultAtSampleElevation() {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                8675309L,
                GeologyProvince.RIFT_PROVINCE,
                0,
                0,
                48.0
        );
        double y = -96.0;
        TectonicStructuralField.FaultTrace trace = field.nearestFault(120, y, -70);
        int x = (int) Math.round(trace.x());
        int z = (int) Math.round(trace.z());
        TectonicStructuralField.Column column = field.column(x, z);

        assertTrue(FaultDamageZone.contains(GeologyProvince.RIFT_PROVINCE, column, y));
        assertFalse(FaultDamageZone.contains(GeologyProvince.RIFT_PROVINCE, column, y + 384.0));
    }

    private static void assertHasDamage(long seed, GeologyProvince province) {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(seed, province, 0, 0, 48.0);
        TectonicStructuralField.FaultTrace trace = field.nearestFault(100, 0.0, 100);
        TectonicStructuralField.Column column = field.column(
                (int) Math.round(trace.x()),
                (int) Math.round(trace.z())
        );
        assertTrue(FaultDamageZone.contains(province, column, 0.0));
    }

    private static void assertNoDamage(long seed, GeologyProvince province) {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(seed, province, 0, 0, 48.0);
        TectonicStructuralField.FaultTrace trace = field.nearestFault(100, 0.0, 100);
        TectonicStructuralField.Column column = field.column(
                (int) Math.round(trace.x()),
                (int) Math.round(trace.z())
        );
        assertFalse(FaultDamageZone.contains(province, column, 0.0));
    }
}
