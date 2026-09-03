package com.geostrata.platform.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BitumenSurfaceEvidenceTest {
    @Test
    void downhillAndLevelTerrainAreFavoredOverUphillSpread() {
        int downhill = BitumenSurfaceEvidence.selectionChance(0.82, -1, 2);
        int level = BitumenSurfaceEvidence.selectionChance(0.82, 0, 2);
        int uphill = BitumenSurfaceEvidence.selectionChance(0.82, 1, 2);

        assertTrue(downhill > level);
        assertTrue(level > uphill);
    }

    @Test
    void strongerReservoirsAndDepressionsBuildDeeperCrusts() {
        int weak = BitumenSurfaceEvidence.layerCount(BitumenSurfaceEvidence.MIN_PRESSURE, 0, 1);
        int strong = BitumenSurfaceEvidence.layerCount(0.96, 0, 1);
        int depression = BitumenSurfaceEvidence.layerCount(0.96, -2, 1);
        int strongCenter = BitumenSurfaceEvidence.layerCount(0.96, 0, 0);

        assertTrue(strong > weak);
        assertTrue(depression > strong);
        assertTrue(strongCenter > strong);
    }
}
