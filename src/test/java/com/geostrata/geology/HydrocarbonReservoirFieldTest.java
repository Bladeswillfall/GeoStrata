package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HydrocarbonReservoirFieldTest {
    @Test
    void onlySedimentaryGeologyCanHostReservoirs() {
        assertEquals(0.0, HydrocarbonReservoirField.activationChance("igneous", "mudrock"));
        assertEquals(0.0, HydrocarbonReservoirField.activationChance("metamorphic", "carbonate"));
        assertTrue(HydrocarbonReservoirField.activationChance("sedimentary", "mudrock") > 0.0);
    }

    @Test
    void sourceRichMudrockIsFavoredOverOtherSediments() {
        double mudrock = HydrocarbonReservoirField.activationChance("sedimentary", "mudrock");
        double sandstone = HydrocarbonReservoirField.activationChance("sedimentary", "quartz_sandstone");
        double coarse = HydrocarbonReservoirField.activationChance("sedimentary", "coarse_clastic");
        assertTrue(mudrock > sandstone);
        assertTrue(sandstone > coarse);
    }

    @Test
    void reservoirSamplingIsDeterministicAndCellBounded() {
        Optional<HydrocarbonReservoirField.Reservoir> found = Optional.empty();
        int x = 0;
        int z = 0;
        for (int cell = 0; cell < 512 && found.isEmpty(); cell++) {
            int cellX = cell % 32;
            int cellZ = cell / 32;
            int baseX = cellX * HydrocarbonReservoirField.CELL_SIZE;
            int baseZ = cellZ * HydrocarbonReservoirField.CELL_SIZE;
            for (int dx = 96; dx <= 288 && found.isEmpty(); dx += 32) {
                for (int dz = 96; dz <= 288 && found.isEmpty(); dz += 32) {
                    x = baseX + dx;
                    z = baseZ + dz;
                    found = HydrocarbonReservoirField.sample(8675309L, x, z, "sedimentary", "mudrock");
                }
            }
        }

        assertTrue(found.isPresent(), "expected the deterministic search window to contain a reservoir");
        HydrocarbonReservoirField.Reservoir reservoir = found.orElseThrow();
        assertEquals(
                found,
                HydrocarbonReservoirField.sample(8675309L, x, z, "sedimentary", "mudrock")
        );
        int minX = reservoir.cellX() * HydrocarbonReservoirField.CELL_SIZE;
        int minZ = reservoir.cellZ() * HydrocarbonReservoirField.CELL_SIZE;
        assertTrue(reservoir.seepX() >= minX && reservoir.seepX() < minX + HydrocarbonReservoirField.CELL_SIZE);
        assertTrue(reservoir.seepZ() >= minZ && reservoir.seepZ() < minZ + HydrocarbonReservoirField.CELL_SIZE);
    }

    @Test
    void firedampRequiresSedimentarySourceRockAndBurial() {
        assertEquals(0.0, HydrocarbonReservoirField.gasSourceAffinity("igneous", "mudrock"));
        assertEquals(0.0, HydrocarbonReservoirField.gasPotential(
                8675309L, 0, -48, 0, "igneous", "mudrock"
        ));
        assertEquals(0.0, HydrocarbonReservoirField.gasPotential(
                8675309L, 0, 96, 0, "sedimentary", "mudrock"
        ));
        assertTrue(
                HydrocarbonReservoirField.gasSourceAffinity("sedimentary", "mudrock")
                        > HydrocarbonReservoirField.gasSourceAffinity("sedimentary", "quartz_sandstone")
        );
    }

    @Test
    void firedampPotentialIsSparseDeterministicAndStrongerAtDepth() {
        double deep = 0.0;
        int x = 0;
        int z = 0;
        for (int cell = 0; cell < 512 && deep == 0.0; cell++) {
            int cellX = cell % 32;
            int cellZ = cell / 32;
            x = cellX * HydrocarbonReservoirField.GAS_CELL_SIZE + HydrocarbonReservoirField.GAS_CELL_SIZE / 2;
            z = cellZ * HydrocarbonReservoirField.GAS_CELL_SIZE + HydrocarbonReservoirField.GAS_CELL_SIZE / 2;
            deep = HydrocarbonReservoirField.gasPotential(
                    8675309L, x, -48, z, "sedimentary", "mudrock"
            );
        }

        assertTrue(deep > 0.0, "expected the deterministic search window to contain gas-prone geology");
        assertEquals(
                deep,
                HydrocarbonReservoirField.gasPotential(8675309L, x, -48, z, "sedimentary", "mudrock")
        );
        double shallower = HydrocarbonReservoirField.gasPotential(
                8675309L, x, 32, z, "sedimentary", "mudrock"
        );
        assertTrue(deep > shallower);
    }

    @Test
    void igneousSamplesNeverCreateReservoirs() {
        assertFalse(HydrocarbonReservoirField.sample(1L, 0, 0, "igneous", "mudrock").isPresent());
    }
}
