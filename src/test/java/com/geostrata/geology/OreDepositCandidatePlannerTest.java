package com.geostrata.geology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OreDepositCandidatePlannerTest {
    @Test
    void proposalIsStableAcrossItsCellIncludingNegativeCoordinates() {
        OreOccurrenceCatalog.Occurrence occurrence = iron();
        OreDepositCandidatePlanner.Proposal nearEdge = OreDepositCandidatePlanner.propose(
                8675309L,
                -1,
                -1,
                -1,
                occurrence
        );
        OreDepositCandidatePlanner.Proposal farEdge = OreDepositCandidatePlanner.propose(
                8675309L,
                -OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE + 1,
                -63,
                -OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE + 1,
                occurrence
        );

        assertEquals(nearEdge, farEdge);
        assertEquals(-1, nearEdge.cellX());
        assertEquals(-1, nearEdge.cellY());
        assertEquals(-1, nearEdge.cellZ());
        assertEquals("stratiform", nearEdge.depositStyle());
        assertTrue(nearEdge.anchorX() >= -OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE + 16
                && nearEdge.anchorX() <= -17);
        assertTrue(nearEdge.anchorY() >= -56 && nearEdge.anchorY() <= -9);
        assertTrue(nearEdge.anchorZ() >= -OreDepositCandidatePlanner.HORIZONTAL_CELL_SIZE + 16
                && nearEdge.anchorZ() <= -17);
        assertTrue(occurrence.depositStyles().contains(nearEdge.depositStyle()));
    }

    @Test
    void rareOresUseDenserCandidateProfilesWithoutExcessVerticalSearch() {
        OreDepositCandidatePlanner.Frequency common = OreDepositCandidatePlanner.frequency(iron());
        OreDepositCandidatePlanner.Frequency gold = OreDepositCandidatePlanner.frequency(gold());
        OreDepositCandidatePlanner.Frequency emerald = OreDepositCandidatePlanner.frequency(emerald());

        assertEquals(160, common.horizontalCellSize());
        assertEquals(64, common.verticalCellSize());
        assertEquals(224, common.horizontalSearchPaddingBlocks());
        assertEquals(224, common.verticalSearchPaddingBlocks());
        assertEquals(64, gold.horizontalCellSize());
        assertEquals(64, gold.verticalCellSize());
        assertEquals(160, gold.horizontalSearchPaddingBlocks());
        assertEquals(64, gold.verticalSearchPaddingBlocks());
        assertEquals(32, emerald.horizontalCellSize());
        assertEquals(16, emerald.verticalCellSize());
        assertEquals(16, emerald.horizontalSearchPaddingBlocks());
        assertEquals(16, emerald.verticalSearchPaddingBlocks());
    }

    @Test
    void seedAndMaterialParticipateInTheProposal() {
        OreDepositCandidatePlanner.Proposal first = OreDepositCandidatePlanner.propose(1L, 0, 32, 0, iron());
        OreDepositCandidatePlanner.Proposal otherSeed = OreDepositCandidatePlanner.propose(2L, 0, 32, 0, iron());
        OreDepositCandidatePlanner.Proposal otherMaterial = OreDepositCandidatePlanner.propose(1L, 0, 32, 0, gold());

        assertNotEquals(first, otherSeed);
        assertNotEquals(first.anchorX(), otherMaterial.anchorX());
        assertNotEquals(first.material(), otherMaterial.material());
    }

    @Test
    void acceptanceRequiresBothDeclaredProvinceAndHost() {
        OreOccurrenceCatalog.Occurrence occurrence = iron();
        OreDepositCandidatePlanner.Proposal proposal = OreDepositCandidatePlanner.propose(42L, 500, 20, -700, occurrence);

        assertTrue(OreDepositCandidatePlanner.accept(
                proposal,
                occurrence,
                GeologyProvince.OROGENIC_BELT,
                "shale"
        ).isPresent());
        assertFalse(OreDepositCandidatePlanner.accept(
                proposal,
                occurrence,
                GeologyProvince.SEDIMENTARY_BASIN,
                "shale"
        ).isPresent());
        assertFalse(OreDepositCandidatePlanner.accept(
                proposal,
                occurrence,
                GeologyProvince.OROGENIC_BELT,
                "granite"
        ).isPresent());
        assertFalse(OreDepositCandidatePlanner.accept(
                proposal,
                occurrence,
                GeologyProvince.OROGENIC_BELT,
                null
        ).isPresent());
        OreDepositCandidatePlanner.Proposal unsupportedStyle = new OreDepositCandidatePlanner.Proposal(
                proposal.material(),
                "coal_seam",
                proposal.cellX(),
                proposal.cellY(),
                proposal.cellZ(),
                proposal.anchorX(),
                proposal.anchorY(),
                proposal.anchorZ()
        );
        assertFalse(OreDepositCandidatePlanner.accept(
                unsupportedStyle,
                occurrence,
                GeologyProvince.OROGENIC_BELT,
                "shale"
        ).isPresent());
    }

    @Test
    void rejectsMismatchedOccurrence() {
        OreDepositCandidatePlanner.Proposal proposal = OreDepositCandidatePlanner.propose(42L, 0, 0, 0, iron());
        assertThrows(
                IllegalArgumentException.class,
                () -> OreDepositCandidatePlanner.accept(
                        proposal,
                        gold(),
                        GeologyProvince.OROGENIC_BELT,
                        "slate"
                )
        );
    }

    private static OreOccurrenceCatalog.Occurrence iron() {
        return new OreOccurrenceCatalog.Occurrence(
                "iron",
                "minecraft",
                "minecraft:raw_iron",
                List.of("shale"),
                List.of(GeologyProvince.OROGENIC_BELT),
                List.of("vein", "stratiform", "massive_lens_or_pocket"),
                gradeBlocks("iron")
        );
    }

    private static OreOccurrenceCatalog.Occurrence gold() {
        return new OreOccurrenceCatalog.Occurrence(
                "gold",
                "minecraft",
                "minecraft:raw_gold",
                List.of("slate"),
                List.of(GeologyProvince.OROGENIC_BELT),
                List.of("vein", "disseminated"),
                gradeBlocks("gold")
        );
    }

    private static OreOccurrenceCatalog.Occurrence emerald() {
        return new OreOccurrenceCatalog.Occurrence(
                "emerald",
                "minecraft",
                "minecraft:emerald",
                List.of("schist"),
                List.of(GeologyProvince.OROGENIC_BELT),
                List.of("micro_vein"),
                gradeBlocks("emerald")
        );
    }

    private static Map<OreGrade, String> gradeBlocks(String material) {
        return Map.of(
                OreGrade.POOR, "geostrata:poor_" + material + "_ore",
                OreGrade.MEDIUM, "geostrata:medium_" + material + "_ore",
                OreGrade.RICH, "geostrata:rich_" + material + "_ore",
                OreGrade.MASSIVE, "geostrata:massive_" + material + "_ore"
        );
    }
}
