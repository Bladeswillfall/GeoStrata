package com.geostrata.geology;

import java.util.List;

/** Builds a thin Fireclay body directly beneath an already-qualified coal-family seam. */
public final class FireclayUnderclayPlanner {
    private static final String FIRECLAY = "fireclay";
    private static final String STRATIFORM = "stratiform";
    private static final double MIN_THICKNESS_RADIUS = 0.75;
    private static final double MAX_THICKNESS_RADIUS = 1.5;
    private static final double THICKNESS_SCALE = 0.55;
    private static final double SEPARATION_GAP = 0.75;

    private FireclayUnderclayPlanner() {
    }

    public static boolean supportsParent(String material) {
        return "coal".equals(material) || "lignite".equals(material);
    }

    public static OreDepositCandidatePlanner.Proposal proposal(
            OreDepositCandidatePlanner.Proposal parentProposal,
            OreDepositGeometry.Body parentBody
    ) {
        requireParent(parentProposal, parentBody);
        double childThickness = thicknessRadius(parentBody);
        double separation = parentBody.thicknessRadius() + childThickness + SEPARATION_GAP;
        double sinDip = Math.sin(parentBody.dipRadians());
        double horizontalNormal = separation * sinDip;
        int offsetX = (int) Math.round(horizontalNormal * Math.cos(parentBody.azimuthRadians()));
        int offsetY = Math.max(1, (int) Math.ceil(separation * Math.cos(parentBody.dipRadians())));
        int offsetZ = (int) Math.round(horizontalNormal * Math.sin(parentBody.azimuthRadians()));
        return new OreDepositCandidatePlanner.Proposal(
                FIRECLAY,
                STRATIFORM,
                parentProposal.cellX(),
                parentProposal.cellY(),
                parentProposal.cellZ(),
                parentBody.anchorX() + offsetX,
                parentBody.anchorY() - offsetY,
                parentBody.anchorZ() + offsetZ
        );
    }

    public static OreDepositGeometry.Body body(
            OreDepositCandidatePlanner.Proposal fireclayProposal,
            OreDepositGeometry.Body parentBody,
            OreGenerationProfile generation
    ) {
        if (fireclayProposal == null || parentBody == null || generation == null) {
            throw new IllegalArgumentException("fireclay proposal, parent body and generation tuning must not be null");
        }
        if (!FIRECLAY.equals(fireclayProposal.material()) || !STRATIFORM.equals(fireclayProposal.depositStyle())) {
            throw new IllegalArgumentException("derived underclay proposal must be stratiform fireclay");
        }
        double footprintScale = Math.min(1.0, generation.bodyScale());
        return new OreDepositGeometry.Body(
                parentBody.worldSeed(),
                FIRECLAY,
                STRATIFORM,
                fireclayProposal.anchorX(),
                fireclayProposal.anchorY(),
                fireclayProposal.anchorZ(),
                parentBody.lengthRadius() * footprintScale,
                parentBody.widthRadius() * footprintScale,
                thicknessRadius(parentBody),
                parentBody.azimuthRadians(),
                parentBody.dipRadians(),
                parentBody.warpAmplitude() * footprintScale,
                parentBody.warpWavelength(),
                parentBody.warpPhase(),
                List.of(),
                generation.traceNormalScale(),
                generation.grades()
        );
    }

    private static double thicknessRadius(OreDepositGeometry.Body parentBody) {
        return Math.max(
                MIN_THICKNESS_RADIUS,
                Math.min(MAX_THICKNESS_RADIUS, parentBody.thicknessRadius() * THICKNESS_SCALE)
        );
    }

    private static void requireParent(
            OreDepositCandidatePlanner.Proposal parentProposal,
            OreDepositGeometry.Body parentBody
    ) {
        if (parentProposal == null || parentBody == null) {
            throw new IllegalArgumentException("underclay parent proposal and body must not be null");
        }
        if (!supportsParent(parentProposal.material()) || !parentProposal.material().equals(parentBody.material())) {
            throw new IllegalArgumentException("fireclay underclay requires a matching coal or lignite parent");
        }
    }
}
