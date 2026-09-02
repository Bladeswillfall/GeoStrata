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
    void candidateDensityComesFromOccurrenceData() {
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
    void zeroWeightDepositStyleIsNeverSelected() {
        OreOccurrenceCatalog.Occurrence occurrence = occurrence(
                "iron",
                List.of("vein", "stratiform"),
                new OreGenerationProfile(
                        1.0,
                        OreGenerationProfile.defaults().candidateGrid(),
                        List.of(),
                        Map.of(),
                        Map.of(),
                        Map.of("vein", 0.0, "stratiform", 1.0)
                )
        );

        for (long seed = 0; seed < 32; seed++) {
            assertEquals("stratiform", OreDepositCandidatePlanner.propose(seed, 0, 0, 0, occurrence).depositStyle());
        }
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
        return occurrence("iron", List.of("vein", "stratiform", "massive_lens_or_pocket"), OreGenerationProfile.defaults());
    }

    private static OreOccurrenceCatalog.Occurrence gold() {
        return new OreOccurrenceCatalog.Occurrence(
                "gold",
                "minecraft",
                "minecraft:raw_gold",
                List.of("slate"),
                List.of(GeologyProvince.OROGENIC_BELT),
                List.of("vein", "disseminated"),
                profile(new OreGenerationProfile.CandidateGrid(64, 64, 8, 8, 160, 64)),
                OreOccurrenceCatalog.TerrainFilter.none(),
                OreGrade.MASSIVE,
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
                profile(new OreGenerationProfile.CandidateGrid(32, 16, 4, 2, 16, 16)),
                OreOccurrenceCatalog.TerrainFilter.none(),
                OreGrade.RICH,
                gradeBlocks("emerald")
        );
    }

    private static OreOccurrenceCatalog.Occurrence occurrence(
            String material,
            List<String> styles,
            OreGenerationProfile generation
    ) {
        return new OreOccurrenceCatalog.Occurrence(
                material,
                "minecraft",
                "minecraft:raw_" + material,
                List.of("shale"),
                List.of(GeologyProvince.OROGENIC_BELT),
                styles,
                generation,
                OreOccurrenceCatalog.TerrainFilter.none(),
                OreGrade.MASSIVE,
                gradeBlocks(material)
        );
    }

    private static OreGenerationProfile profile(OreGenerationProfile.CandidateGrid grid) {
        return new OreGenerationProfile(1.0, grid, List.of(), Map.of(), Map.of(), Map.of());
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
