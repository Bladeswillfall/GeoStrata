package com.geostrata.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreHostTest {
    @Test
    void everyHostRoundTripsThroughItsStableId() {
        for (OreHost host : OreHost.values()) {
            assertTrue(OreHost.supports(host.asString()));
            assertEquals(host, OreHost.byId(host.asString()));
        }
    }

    @Test
    void semanticLithologyWithoutBakedOreTextureIsNotRenderable() {
        assertTrue(OreHost.supports("granite"));
        assertEquals(OreHost.GRANITE, OreHost.byId("granite"));
        assertTrue(OreHost.supports("gabbro"));
        assertTrue(OreHost.supports("peridotite"));
        assertTrue(OreHost.supports("sandstone"));
        assertEquals(OreHost.SANDSTONE, OreHost.byId("sandstone"));
        assertFalse(OreHost.supports("diorite"));
        assertFalse(OreHost.supports("hornfels"));
        assertThrows(IllegalArgumentException.class, () -> OreHost.byId("diorite"));
    }

    @Test
    void materialDefaultsAreGeologicallyValidHosts() {
        assertEquals(OreHost.SHALE, OreHost.defaultFor("coal"));
        assertEquals(OreHost.SHALE, OreHost.defaultFor("iron"));
        assertEquals(OreHost.SHALE, OreHost.defaultFor("copper"));
        assertEquals(OreHost.GRANITE, OreHost.defaultFor("tin"));
        assertEquals(OreHost.SLATE, OreHost.defaultFor("gold"));
        assertThrows(IllegalArgumentException.class, () -> OreHost.defaultFor("unsupported"));
    }
}
