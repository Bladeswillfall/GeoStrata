package com.geostrata.platform.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricCreateDieselGeneratorsIntegrationTest {
    @Test
    void freeCrudeOnlyMaterializesBeforeItsReserveIsConsumed() {
        assertTrue(FabricCreateDieselGeneratorsIntegration.canMaterializeFreeCrude(12_000, 12_000));
        assertFalse(FabricCreateDieselGeneratorsIntegration.canMaterializeFreeCrude(11_999, 12_000));
        assertFalse(FabricCreateDieselGeneratorsIntegration.canMaterializeFreeCrude(0, 12_000));
        assertFalse(FabricCreateDieselGeneratorsIntegration.canMaterializeFreeCrude(-1, 12_000));
    }
}
