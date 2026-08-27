package com.geostrata.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OreHostTest {
    @Test
    void everyHostRoundTripsThroughItsStableId() {
        for (OreHost host : OreHost.values()) {
            assertEquals(host, OreHost.byId(host.asString()));
        }
    }

    @Test
    void materialDefaultsAreGeologicallyValidHosts() {
        assertEquals(OreHost.SHALE, OreHost.defaultFor("coal"));
        assertEquals(OreHost.SHALE, OreHost.defaultFor("iron"));
        assertEquals(OreHost.SHALE, OreHost.defaultFor("copper"));
        assertEquals(OreHost.SLATE, OreHost.defaultFor("gold"));
        assertThrows(IllegalArgumentException.class, () -> OreHost.defaultFor("unsupported"));
    }
}
