package com.geostrata.geology;

import java.util.Optional;

/** Pure, non-mutating proposal and eligibility planner for staged ore deposits. */
public final class OreDepositCandidatePlanner {
    public static final int HORIZONTAL_CELL_SIZE = 256;
    public static final int VERTICAL_CELL_SIZE = 64;

    private static final int HORIZONTAL_MARGIN = 16;
    private static final int VERTICAL_MARGIN = 8;
    private static final long MATERIAL_SALT = 0xA24BAED4963EE407L;
    private static final long ANCHOR_X_SALT = 0x9FB21C651E98DF25L;
    private static final long ANCHOR_Y_SALT = 0xC13FA9A902A6328FL;
    private static final long ANCHOR_Z_SALT = 0x91E10DA5C79E7B1DL;
    private static final long STYLE_SALT = 0xD6E8FEB86659FD93L;

    private OreDepositCandidatePlanner() {
    }

    /**
     * Returns the sole proposal for a material in the queried 3D candidate cell.
     * A proposal is not an active deposit and carries no abundance decision.
     */
    public static Proposal propose(
            long worldSeed,
            int blockX,
            int blockY,
            int blockZ,
            OreOccurrenceCatalog.Occurrence occurrence
    ) {
        if (occurrence == null) {
            throw new IllegalArgumentException("ore occurrence must not be null");
        }
        if (occurrence.depositStyles().isEmpty()) {
            throw new IllegalArgumentException("ore occurrence must declare at least one deposit style");
        }

        int cellX = Math.floorDiv(blockX, HORIZONTAL_CELL_SIZE);
        int cellY = Math.floorDiv(blockY, VERTICAL_CELL_SIZE);
        int cellZ = Math.floorDiv(blockZ, HORIZONTAL_CELL_SIZE);
        long salt = MATERIAL_SALT ^ Integer.toUnsignedLong(occurrence.id().hashCode());
        int anchorX = anchor(
                cellX,
                HORIZONTAL_CELL_SIZE,
                HORIZONTAL_MARGIN,
                GeologyDeterminism.unitRoll(worldSeed, cellX, cellY, cellZ, salt ^ ANCHOR_X_SALT)
        );
        int anchorY = anchor(
                cellY,
                VERTICAL_CELL_SIZE,
                VERTICAL_MARGIN,
                GeologyDeterminism.unitRoll(worldSeed, cellX, cellY, cellZ, salt ^ ANCHOR_Y_SALT)
        );
        int anchorZ = anchor(
                cellZ,
                HORIZONTAL_CELL_SIZE,
                HORIZONTAL_MARGIN,
                GeologyDeterminism.unitRoll(worldSeed, cellX, cellY, cellZ, salt ^ ANCHOR_Z_SALT)
        );
        int styleIndex = (int) Math.floor(
                GeologyDeterminism.unitRoll(worldSeed, cellX, cellY, cellZ, salt ^ STYLE_SALT)
                        * occurrence.depositStyles().size()
        );
        return new Proposal(
                occurrence.id(),
                occurrence.depositStyles().get(styleIndex),
                cellX,
                cellY,
                cellZ,
                anchorX,
                anchorY,
                anchorZ
        );
    }

    /** Accepts a proposal only when its anchor has a declared province and host-rock context. */
    public static Optional<Candidate> accept(
            Proposal proposal,
            OreOccurrenceCatalog.Occurrence occurrence,
            GeologyProvince province,
            String hostLithology
    ) {
        if (proposal == null || occurrence == null || province == null) {
            throw new IllegalArgumentException("candidate proposal, occurrence and province must not be null");
        }
        if (!proposal.material().equals(occurrence.id())) {
            throw new IllegalArgumentException("candidate material does not match occurrence: " + proposal.material());
        }
        if (hostLithology == null
                || !occurrence.depositStyles().contains(proposal.depositStyle())
                || !occurrence.provinceContexts().contains(province)
                || !occurrence.hostLithologies().contains(hostLithology)) {
            return Optional.empty();
        }
        return Optional.of(new Candidate(proposal, province, hostLithology));
    }

    private static int anchor(int cell, int size, int margin, double roll) {
        int span = size - 2 * margin;
        return cell * size + margin + (int) Math.floor(roll * span);
    }

    public record Proposal(
            String material,
            String depositStyle,
            int cellX,
            int cellY,
            int cellZ,
            int anchorX,
            int anchorY,
            int anchorZ
    ) {
    }

    public record Candidate(Proposal proposal, GeologyProvince province, String hostLithology) {
    }
}
