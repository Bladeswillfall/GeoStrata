package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreDepositGeometryTest {
    private static final List<String> STYLES = List.of(
            "coal_seam",
            "vein",
            "stratiform",
            "disseminated",
            "massive_lens_or_pocket"
    );

    @Test
    void everyDeclaredStyleProducesAConcentratedAnchoredBody() {
        for (String style : STYLES) {
            OreDepositGeometry.Body body = OreDepositGeometry.forCandidate(8675309L, candidate(style));
            OreDepositGeometry.Sample anchor = body.sample(body.anchorX(), body.anchorY(), body.anchorZ());

            assertEquals(style, body.style());
            assertTrue(body.lengthRadius() > body.thicknessRadius());
            assertTrue(anchor.economic());
            assertEquals(OreGrade.MASSIVE, anchor.grade());
            assertFalse(anchor.trace());
            assertEquals("massive", anchor.zone());
        }
    }

    @Test
    void geometryIsStableForSeedAndCandidateIncludingNegativeCoordinates() {
        OreDepositCandidatePlanner.Candidate candidate = candidate("vein");
        OreDepositGeometry.Body first = OreDepositGeometry.forCandidate(42L, candidate);
        OreDepositGeometry.Body repeated = OreDepositGeometry.forCandidate(42L, candidate);
        OreDepositGeometry.Body otherSeed = OreDepositGeometry.forCandidate(43L, candidate);

        assertEquals(first, repeated);
        assertNotEquals(first, otherSeed);
        assertEquals(2, first.branches().size());
        assertEquals(35.782474705575, first.lengthRadius(), 1.0e-12);
        assertEquals(2.818203125585, first.widthRadius(), 1.0e-12);
        assertEquals(2.333116619565, first.thicknessRadius(), 1.0e-12);
        assertEquals(5.983455788356, first.azimuthRadians(), 1.0e-12);
        assertEquals(-1.262883441316, first.dipRadians(), 1.0e-12);
        assertEquals(
                first.sample(first.anchorX() - 7, first.anchorY() + 3, first.anchorZ() + 5),
                repeated.sample(repeated.anchorX() - 7, repeated.anchorY() + 3, repeated.anchorZ() + 5)
        );
    }

    @Test
    void economicBodyFadesIntoNonEconomicTraceThenOutside() {
        OreDepositGeometry.Body body = OreDepositGeometry.forCandidate(1234L, candidate("massive_lens_or_pocket"));
        boolean foundTrace = false;
        boolean foundOutside = false;

        for (int offset = 1; offset <= 128; offset++) {
            OreDepositGeometry.Sample sample = body.sample(
                    body.anchorX() + offset,
                    body.anchorY(),
                    body.anchorZ()
            );
            foundTrace |= sample.trace() && !sample.economic();
            foundOutside |= !sample.trace() && !sample.economic();
        }

        assertTrue(foundTrace);
        assertTrue(foundOutside);
    }

    @Test
    void disseminatedEnvelopeContainsStableHostGaps() {
        OreDepositGeometry.Body body = OreDepositGeometry.forCandidate(99L, candidate("disseminated"));
        boolean foundEconomic = false;
        boolean foundTrace = false;

        for (int x = body.anchorX() - 12; x <= body.anchorX() + 12; x++) {
            OreDepositGeometry.Sample sample = body.sample(x, body.anchorY(), body.anchorZ());
            foundEconomic |= sample.economic();
            foundTrace |= sample.trace();
        }

        assertTrue(foundEconomic);
        assertTrue(foundTrace);
    }

    @Test
    void rejectsUnsupportedStyleAndMissingCandidate() {
        assertThrows(IllegalArgumentException.class, () -> OreDepositGeometry.forCandidate(1L, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> OreDepositGeometry.forCandidate(1L, candidate("unsupported"))
        );
    }

    private static OreDepositCandidatePlanner.Candidate candidate(String style) {
        OreDepositCandidatePlanner.Proposal proposal = new OreDepositCandidatePlanner.Proposal(
                "copper",
                style,
                -1,
                0,
                2,
                -48,
                20,
                96
        );
        return new OreDepositCandidatePlanner.Candidate(proposal, GeologyProvince.RIFT_PROVINCE, "basalt");
    }
}
