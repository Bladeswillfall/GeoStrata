package com.geostrata.geology;

/** Shared fault binding for fracture-controlled ore veins. */
public final class FaultControlledOrePlanner {
    public static final double CAPTURE_DISTANCE_BLOCKS = 96.0;
    public static final double PROVINCE_BOUNDARY_MARGIN_BLOCKS = 16.0;
    private static final double STRIKE_SAMPLE_DISTANCE_BLOCKS = 24.0;

    private FaultControlledOrePlanner() {
    }

    public static Binding bind(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            double cycleThicknessBlocks
    ) {
        return bind(worldSeed, proposal, cycleThicknessBlocks, null);
    }

    /** Reuses precomputed province sites when a worldgen caller already owns a regional context. */
    public static Binding bind(
            long worldSeed,
            OreDepositCandidatePlanner.Proposal proposal,
            double cycleThicknessBlocks,
            GeologyProvinceSampler.Context provinceContext
    ) {
        if (proposal == null) {
            throw new IllegalArgumentException("ore proposal must not be null");
        }
        if (!Double.isFinite(cycleThicknessBlocks) || cycleThicknessBlocks < 1.0) {
            throw new IllegalArgumentException("structural cycle thickness must be finite and at least one block");
        }
        if (!"vein".equals(proposal.depositStyle())) {
            return Binding.unbound(proposal);
        }

        GeologyProvinceSampler.Sample province = provinceContext == null
                ? GeologyProvinceSampler.sample(worldSeed, proposal.anchorX(), proposal.anchorZ())
                : provinceContext.sample(proposal.anchorX(), proposal.anchorZ());
        TectonicStructuralField.Context tectonics = TectonicStructuralField.forSite(
                worldSeed,
                province.province(),
                province.siteX(),
                province.siteZ(),
                cycleThicknessBlocks
        );
        TectonicStructuralField.FaultTrace trace = tectonics.nearestFault(
                proposal.anchorX(),
                proposal.anchorY(),
                proposal.anchorZ()
        );
        if (trace.distanceToFault() > CAPTURE_DISTANCE_BLOCKS
                || province.distanceToBoundary()
                <= trace.distanceToFault() + PROVINCE_BOUNDARY_MARGIN_BLOCKS) {
            return Binding.unbound(proposal);
        }

        OreDepositCandidatePlanner.Proposal aligned = new OreDepositCandidatePlanner.Proposal(
                proposal.material(),
                proposal.depositStyle(),
                proposal.cellX(),
                proposal.cellY(),
                proposal.cellZ(),
                (int) Math.round(trace.x()),
                proposal.anchorY(),
                (int) Math.round(trace.z())
        );
        return new Binding(
                proposal,
                aligned,
                true,
                localStrikeRadians(tectonics, trace, proposal.anchorY())
        );
    }

    private static double localStrikeRadians(
            TectonicStructuralField.Context tectonics,
            TectonicStructuralField.FaultTrace trace,
            int y
    ) {
        int forwardX = (int) Math.round(trace.x() + tectonics.faultCos() * STRIKE_SAMPLE_DISTANCE_BLOCKS);
        int forwardZ = (int) Math.round(trace.z() + tectonics.faultSin() * STRIKE_SAMPLE_DISTANCE_BLOCKS);
        int backwardX = (int) Math.round(trace.x() - tectonics.faultCos() * STRIKE_SAMPLE_DISTANCE_BLOCKS);
        int backwardZ = (int) Math.round(trace.z() - tectonics.faultSin() * STRIKE_SAMPLE_DISTANCE_BLOCKS);
        TectonicStructuralField.FaultTrace forward = tectonics.nearestFault(forwardX, y, forwardZ);
        TectonicStructuralField.FaultTrace backward = tectonics.nearestFault(backwardX, y, backwardZ);
        if (forward.faultIndex() != trace.faultIndex() || backward.faultIndex() != trace.faultIndex()) {
            return Math.atan2(tectonics.faultSin(), tectonics.faultCos());
        }
        double dx = forward.x() - backward.x();
        double dz = forward.z() - backward.z();
        if (Math.hypot(dx, dz) < 1.0) {
            return Math.atan2(tectonics.faultSin(), tectonics.faultCos());
        }
        return Math.atan2(dz, dx);
    }

    /**
     * The public proposal remains the candidate-owned anchor used for abundance and affinity.
     * bodyProposal may be moved onto a shared fault only after that candidate decision is stable.
     */
    public record Binding(
            OreDepositCandidatePlanner.Proposal proposal,
            OreDepositCandidatePlanner.Proposal bodyProposal,
            boolean faultAligned,
            double faultStrikeRadians
    ) {
        public Binding {
            if (proposal == null || bodyProposal == null || !Double.isFinite(faultStrikeRadians)) {
                throw new IllegalArgumentException("ore structural binding must be valid");
            }
            if (!proposal.material().equals(bodyProposal.material())
                    || !proposal.depositStyle().equals(bodyProposal.depositStyle())
                    || proposal.cellX() != bodyProposal.cellX()
                    || proposal.cellY() != bodyProposal.cellY()
                    || proposal.cellZ() != bodyProposal.cellZ()) {
                throw new IllegalArgumentException("ore structural binding may move geometry but not candidate identity");
            }
        }

        private static Binding unbound(OreDepositCandidatePlanner.Proposal proposal) {
            return new Binding(proposal, proposal, false, 0.0);
        }

        /** Uses the loaded ore LUT when available; neutral tuning keeps standalone callers deterministic. */
        public OreDepositGeometry.Body body(long worldSeed) {
            OreOccurrenceCatalog.Occurrence occurrence = OreOccurrenceCatalog.current().byId().get(proposal.material());
            OreGenerationProfile generation = occurrence == null
                    ? OreGenerationProfile.defaults()
                    : occurrence.generation();
            return body(worldSeed, generation);
        }

        public OreDepositGeometry.Body body(long worldSeed, OreGenerationProfile generation) {
            if (generation == null) {
                throw new IllegalArgumentException("ore generation tuning must not be null");
            }
            OreDepositGeometry.Body base = OreDepositGeometry.forProposal(worldSeed, bodyProposal, generation);
            if (!faultAligned) {
                return base;
            }
            return new OreDepositGeometry.Body(
                    base.worldSeed(),
                    base.material(),
                    base.style(),
                    base.anchorX(),
                    base.anchorY(),
                    base.anchorZ(),
                    base.lengthRadius(),
                    base.widthRadius(),
                    base.thicknessRadius(),
                    faultStrikeRadians,
                    0.0,
                    base.warpAmplitude(),
                    base.warpWavelength(),
                    base.warpPhase(),
                    base.branches(),
                    base.traceNormalScale(),
                    base.grades()
            );
        }
    }
}
