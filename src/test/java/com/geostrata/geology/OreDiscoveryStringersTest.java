package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreDiscoveryStringersTest {
    private static final OreGenerationProfile.DiscoveryStringers IRON =
            new OreGenerationProfile.DiscoveryStringers(14, 52.0, 96.0, 0.62, 0.88, 1.0, 0, 0.0);
    private static final OreGenerationProfile.DiscoveryStringers COPPER =
            new OreGenerationProfile.DiscoveryStringers(12, 48.0, 88.0, 0.58, 0.84, 4.0, 4, -1.0);

    @Test
    void configuredStringersAreDeterministicAndExtendBeyondTheParentBody() {
        for (OreGenerationProfile.DiscoveryStringers tuning : new OreGenerationProfile.DiscoveryStringers[]{IRON, COPPER}) {
            OreDepositGeometry.Body body = body(8675309L);
            OreDiscoveryStringers.Field first = OreDiscoveryStringers.forBody(body, tuning);
            OreDiscoveryStringers.Field repeated = OreDiscoveryStringers.forBody(body, tuning);

            assertTrue(first.enabled());
            assertEquals(first, repeated);
            assertTrue(extendsBeyond(body.bounds(), first.bounds()));
            assertTrue(hasStringerOutsideParentEnvelope(body, first));
        }
    }

    @Test
    void configuredStringersHaveCaveFacingCandidateSpaceOutsideTheirThinCore() {
        for (OreGenerationProfile.DiscoveryStringers tuning : new OreGenerationProfile.DiscoveryStringers[]{IRON, COPPER}) {
            OreDiscoveryStringers.Field field = OreDiscoveryStringers.forBody(body(8675309L), tuning);
            assertTrue(hasHaloOnlyVoxel(field));
        }
    }

    @Test
    void disabledProfileCreatesNoDiscoveryStringers() {
        OreDepositGeometry.Body body = body(8675309L);
        OreDiscoveryStringers.Field field = OreDiscoveryStringers.forBody(
                body,
                OreGenerationProfile.DiscoveryStringers.disabled()
        );

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

    private static OreDepositGeometry.Body body(long seed) {
        OreDepositCandidatePlanner.Proposal proposal = new OreDepositCandidatePlanner.Proposal(
                "test_ore",
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
