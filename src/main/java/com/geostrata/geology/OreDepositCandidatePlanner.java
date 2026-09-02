package com.geostrata.geology;

import java.util.Optional;

/** Pure, non-mutating proposal and eligibility planner for staged ore deposits. */
public final class OreDepositCandidatePlanner {
    public static final int HORIZONTAL_CELL_SIZE = 160;
    public static final int VERTICAL_CELL_SIZE = 64;

    private static final long MATERIAL_SALT = 0xA24BAED4963EE407L;
    private static final long ANCHOR_X_SALT = 0x9FB21C651E98DF25L;
    private static final long ANCHOR_Y_SALT = 0xC13FA9A902A6328FL;
    private static final long ANCHOR_Z_SALT = 0x91E10DA5C79E7B1DL;
    private static final long STYLE_SALT = 0xD6E8FEB86659FD93L;

    private OreDepositCandidatePlanner() {
    }

    /** Candidate density is defined entirely by the occurrence LUT. */
    public static Frequency frequency(OreOccurrenceCatalog.Occurrence occurrence) {
        if (occurrence == null) {
            throw new IllegalArgumentException("ore occurrence must not be null");
        }
        OreGenerationProfile.CandidateGrid grid = occurrence.generation().candidateGrid();
        return new Frequency(
                grid.horizontalCellSize(),
                grid.verticalCellSize(),
                grid.horizontalMargin(),
                grid.verticalMargin(),
                grid.horizontalSearchPaddingBlocks(),
                grid.verticalSearchPaddingBlocks()
        );
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
        Frequency frequency = frequency(occurrence);
        return proposeCell(
                worldSeed,
                Math.floorDiv(blockX, frequency.horizontalCellSize()),
                Math.floorDiv(blockY, frequency.verticalCellSize()),
                Math.floorDiv(blockZ, frequency.horizontalCellSize()),
                occurrence
        );
    }

    /** Builds the deterministic proposal for an already resolved material cell. */
    public static Proposal proposeCell(
            long worldSeed,
            int cellX,
            int cellY,
            int cellZ,
            OreOccurrenceCatalog.Occurrence occurrence
    ) {
        if (occurrence == null) {
            throw new IllegalArgumentException("ore occurrence must not be null");
        }
        if (occurrence.depositStyles().isEmpty()) {
            throw new IllegalArgumentException("ore occurrence must declare at least one deposit style");
        }

        Frequency frequency = frequency(occurrence);
        long salt = MATERIAL_SALT ^ Integer.toUnsignedLong(occurrence.id().hashCode());
        int anchorX = anchor(
                cellX,
                frequency.horizontalCellSize(),
                frequency.horizontalMargin(),
                GeologyDeterminism.unitRoll(worldSeed, cellX, cellY, cellZ, salt ^ ANCHOR_X_SALT)
        );
        int anchorY = anchorYForCell(worldSeed, cellX, cellY, cellZ, occurrence);
        int anchorZ = anchor(
                cellZ,
                frequency.horizontalCellSize(),
                frequency.horizontalMargin(),
                GeologyDeterminism.unitRoll(worldSeed, cellX, cellY, cellZ, salt ^ ANCHOR_Z_SALT)
        );
        String style = selectStyle(
                occurrence,
                GeologyDeterminism.unitRoll(worldSeed, cellX, cellY, cellZ, salt ^ STYLE_SALT)
        );
        return new Proposal(
                occurrence.id(),
                style,
                cellX,
                cellY,
                cellZ,
                anchorX,
                anchorY,
                anchorZ
        );
    }

    /** Returns only the deterministic Y anchor when an occurrence is already available. */
    static int anchorYForCell(
            long worldSeed,
            int cellX,
            int cellY,
            int cellZ,
            OreOccurrenceCatalog.Occurrence occurrence
    ) {
        if (occurrence == null) {
            throw new IllegalArgumentException("ore occurrence must not be null");
        }
        Frequency frequency = frequency(occurrence);
        long salt = MATERIAL_SALT ^ Integer.toUnsignedLong(occurrence.id().hashCode());
        return anchor(
                cellY,
                frequency.verticalCellSize(),
                frequency.verticalMargin(),
                GeologyDeterminism.unitRoll(worldSeed, cellX, cellY, cellZ, salt ^ ANCHOR_Y_SALT)
        );
    }

    /** Compatibility helper; loaded catalog data is preferred over any material-name special case. */
    static int anchorYForCell(long worldSeed, int cellX, int cellY, int cellZ, String material) {
        if (material == null) {
            throw new IllegalArgumentException("ore material must not be null");
        }
        OreOccurrenceCatalog.Occurrence occurrence = OreOccurrenceCatalog.current().byId().get(material);
        OreGenerationProfile.CandidateGrid grid = occurrence == null
                ? OreGenerationProfile.defaults().candidateGrid()
                : occurrence.generation().candidateGrid();
        long salt = MATERIAL_SALT ^ Integer.toUnsignedLong(material.hashCode());
        return anchor(
                cellY,
                grid.verticalCellSize(),
                grid.verticalMargin(),
                GeologyDeterminism.unitRoll(worldSeed, cellX, cellY, cellZ, salt ^ ANCHOR_Y_SALT)
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

    private static String selectStyle(OreOccurrenceCatalog.Occurrence occurrence, double roll) {
        double totalWeight = occurrence.depositStyles().stream()
                .mapToDouble(occurrence.generation()::depositStyleWeight)
                .sum();
        double target = roll * totalWeight;
        double cumulative = 0.0;
        String fallback = occurrence.depositStyles().get(occurrence.depositStyles().size() - 1);
        for (String style : occurrence.depositStyles()) {
            cumulative += occurrence.generation().depositStyleWeight(style);
            if (target < cumulative) {
                return style;
            }
        }
        return fallback;
    }

    private static int anchor(int cell, int size, int margin, double roll) {
        int span = size - 2 * margin;
        return cell * size + margin + (int) Math.floor(roll * span);
    }

    public record Frequency(
            int horizontalCellSize,
            int verticalCellSize,
            int horizontalMargin,
            int verticalMargin,
            int horizontalSearchPaddingBlocks,
            int verticalSearchPaddingBlocks
    ) {
        public Frequency {
            if (horizontalCellSize < 1 || verticalCellSize < 1
                    || horizontalMargin < 0 || verticalMargin < 0
                    || horizontalMargin * 2 >= horizontalCellSize
                    || verticalMargin * 2 >= verticalCellSize
                    || horizontalSearchPaddingBlocks < 0 || verticalSearchPaddingBlocks < 0) {
                throw new IllegalArgumentException("ore frequency profile dimensions must be positive and leave anchor space");
            }
        }
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
