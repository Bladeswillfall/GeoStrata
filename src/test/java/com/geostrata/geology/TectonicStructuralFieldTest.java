package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TectonicStructuralFieldTest {
    @Test
    void samplingIsDeterministic() {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                123456789L,
                GeologyProvince.OROGENIC_BELT,
                170,
                -575,
                48.0
        );

        assertEquals(field.sample(384, -192), field.sample(384, -192));
    }

    @Test
    void provinceSiteRemainsZeroDisplacementAnchor() {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                987654321L,
                GeologyProvince.RIFT_PROVINCE,
                128,
                -256,
                48.0
        );
        TectonicStructuralField.Sample sample = field.sample(128, -256);

        assertEquals(0.0, sample.foldOffset(), 1.0e-9);
        assertEquals(0.0, sample.faultOffset(), 1.0e-9);
    }

    @Test
    void nearestFaultProjectionLandsOnTheSameFaultFamily() {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                314159265L,
                GeologyProvince.CRATONIC_SHIELD,
                128,
                -256,
                48.0
        );
        TectonicStructuralField.FaultTrace trace = field.nearestFault(437, -219);
        TectonicStructuralField.FaultTrace rounded = field.nearestFault(
                (int) Math.round(trace.x()),
                (int) Math.round(trace.z())
        );

        assertTrue(Double.isFinite(trace.distanceToFault()));
        assertTrue(rounded.distanceToFault() <= 1.0, rounded.toString());
        assertEquals(TectonicStructuralField.FaultRegime.ANCIENT, trace.faultRegime());
    }

    @Test
    void riftFieldContainsDiscreteFaultBlocks() {
        TectonicStructuralField.Context field = TectonicStructuralField.forSite(
                456789123L,
                GeologyProvince.RIFT_PROVINCE,
                0,
                0,
                48.0
        );
        Set<Double> offsets = new HashSet<>();
        for (int x = -1536; x <= 1536; x += 64) {
            for (int z = -1536; z <= 1536; z += 64) {
                offsets.add(field.sample(x, z).faultOffset());
            }
        }

        assertTrue(offsets.size() >= 3, offsets.toString());
    }

    @Test
    void structuralIntensityTracksProvinceArchetype() {
        TectonicStructuralField.Settings craton = TectonicStructuralField.settingsFor(
                GeologyProvince.CRATONIC_SHIELD
        );
        TectonicStructuralField.Settings orogen = TectonicStructuralField.settingsFor(
                GeologyProvince.OROGENIC_BELT
        );
        TectonicStructuralField.Settings rift = TectonicStructuralField.settingsFor(
                GeologyProvince.RIFT_PROVINCE
        );

        assertTrue(orogen.foldAmplitudeCycleFraction() > craton.foldAmplitudeCycleFraction());
        assertTrue(orogen.faultThrowCycleFraction() > craton.faultThrowCycleFraction());
        assertTrue(rift.faultSpacingBlocks() < craton.faultSpacingBlocks());
        assertEquals(TectonicStructuralField.FaultRegime.REVERSE, orogen.faultRegime());
        assertEquals(TectonicStructuralField.FaultRegime.NORMAL, rift.faultRegime());
    }
}
