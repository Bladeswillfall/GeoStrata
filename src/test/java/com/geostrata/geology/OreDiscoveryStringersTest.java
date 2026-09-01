package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreDiscoveryStringersTest {
    @Test
    void commonMetalStringersAreDeterministicAndExtendBeyondTheParentBody() {
        for (String material : new String[]{"iron", "copper"}) {
            OreDepositGeometry.Body body = body(material, 8675309L);
            OreDiscoveryStringers.Field first = OreDiscoveryStringers.forBody(body);
            OreDiscoveryStringers.Field repeated = OreDiscoveryStringers.forBody(body);

            assertTrue(first.enabled());
            assertEquals(first, repeated);
            assertTrue(extendsBeyond(body.bounds(), first.bounds()));
            assertTrue(hasStringerOutsideParentEnvelope(body, first));
        }
    }

    @Test
    void commonMetalsHaveCaveFacingCandidateSpaceOutsideTheirThinStringers() {
        for (String material : new String[]{"iron", "copper"}) {
            OreDiscoveryStringers.Field field = OreDiscoveryStringers.forBody(body(material, 8675309L));
            assertTrue(hasHaloOnlyVoxel(field));
        }
    }

    @Test
    void optimizedSamplerMatchesExactStringerQueries() {
        for (String material : new String[]{"iron", "copper"}) {
            OreDiscoveryStringers.Field field = OreDiscoveryStringers.forBody(body(material, 8675309L));
            OreDiscoveryStringers.Sampler sampler = field.sampler();
            OreDepositGeometry.Bounds bounds = field.bounds();
            for (int x = bounds.minX(); x <= bounds.maxX(); x += 3) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z += 3) {
                    for (int y = bounds.minY(); y <= bounds.maxY(); y += 3) {
                        OreDiscoveryStringers.Proximity expected = field.contains(x, y, z)
                                ? OreDiscoveryStringers.Proximity.STRINGER
                                : field.nearStringer(x, y, z)
                                        ? OreDiscoveryStringers.Proximity.NEAR_STRINGER
                                        : OreDiscoveryStringers.Proximity.OUTSIDE;
                        assertEquals(expected, sampler.proximity(x, y, z));
                    }
                }
            }
        }
    }

    @Test
    void otherMaterialsDoNotGainDiscoveryStringersYet() {
        OreDepositGeometry.Body body = body("gold", 8675309L);
        OreDiscoveryStringers.Field field = OreDiscoveryStringers.forBody(body);

        assertFalse(field.enabled());
        assertEquals(body.bounds(), field.bounds());
    }

    private static boolean extendsBeyond(
            OreDepositGeometry.Bounds parent,
            OreDepositGeometry.Bounds discovery
    ) {
        return discovery.minX() < parent.minX()
                || discovery.minY() < parent.minY()
                || discovery.minZ() < parent.minZ()
                || discovery.maxX() > parent.maxX()
                || discovery.maxY() > parent.maxY()
                || discovery.maxZ() > parent.maxZ();
    }

    private static boolean hasStringerOutsideParentEnvelope(
            OreDepositGeometry.Body body,
            OreDiscoveryStringers.Field field
    ) {
        OreDepositGeometry.Bounds bounds = field.bounds();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    if (field.contains(x, y, z)) {
                        OreDepositGeometry.Sample sample = body.sample(x, y, z);
                        if (!sample.economic() && !sample.trace()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasHaloOnlyVoxel(OreDiscoveryStringers.Field field) {
        OreDepositGeometry.Bounds bounds = field.bounds();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    if (field.nearStringer(x, y, z) && !field.contains(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static OreDepositGeometry.Body body(String material, long seed) {
        OreDepositCandidatePlanner.Proposal proposal = new OreDepositCandidatePlanner.Proposal(
                material,
                "stratiform",
                -1,
                0,
                2,
                -48,
                20,
                96
        );
        return OreDepositGeometry.forProposal(seed, proposal);
    }
}
