package com.geostrata.geology;

/** Pure deterministic candidate planning for rare diamond pipes and deep structural occurrences. */
public final class DiamondGeologyPlanner {
    public static final int PIPE_CELL_SIZE = GeologyProvinceSampler.CELL_SIZE;
    public static final int STRUCTURAL_CELL_SIZE = 128;
    public static final double PIPE_MAX_ABS_TILT_PER_VERTICAL_BLOCK = 0.035;

    private static final double PIPE_FIXED_MAX_HORIZONTAL_REACH_BLOCKS = 14.0;

    private static final long PIPE_X_SALT = 0x8CB92BA72F3D8DD7L;
    private static final long PIPE_Z_SALT = 0x58F38DED09D2C7A9L;
    private static final long PIPE_TILT_X_SALT = 0xA24BAED4963EE407L;
    private static final long PIPE_TILT_Z_SALT = 0x9FB21C651E98DF25L;
    private static final long PIPE_RADIUS_SALT = 0xC13FA9A902A6328FL;
    private static final long PIPE_ACTIVATION_SALT = 0x91E10DA5C79E7B1DL;
    private static final long PIPE_CLUSTER_SALT = 0xD1B54A32D192ED03L;

    private static final long STRUCTURAL_X_SALT = 0xDB4F0B9175AE2165L;
    private static final long STRUCTURAL_Z_SALT = 0xBBE0563303A4615FL;
    private static final long STRUCTURAL_ACTIVATION_SALT = 0xC6BC279692B5C323L;
    private static final long STRUCTURAL_CLUSTER_SALT = 0xD6E8FEB86659FD93L;

    private DiamondGeologyPlanner() {
    }

    public static PipeCandidate pipe(long worldSeed, int cellX, int cellZ, PipeKind kind) {
        int minX = cellX * PIPE_CELL_SIZE + PIPE_CELL_SIZE / 4;
        int minZ = cellZ * PIPE_CELL_SIZE + PIPE_CELL_SIZE / 4;
        int span = PIPE_CELL_SIZE / 2;
        int anchorX = minX + (int) Math.floor(roll(worldSeed, cellX, cellZ, PIPE_X_SALT ^ kind.salt()) * span);
        int anchorZ = minZ + (int) Math.floor(roll(worldSeed, cellX, cellZ, PIPE_Z_SALT ^ kind.salt()) * span);
        double tiltX = signed(roll(worldSeed, cellX, cellZ, PIPE_TILT_X_SALT ^ kind.salt()))
                * PIPE_MAX_ABS_TILT_PER_VERTICAL_BLOCK;
        double tiltZ = signed(roll(worldSeed, cellX, cellZ, PIPE_TILT_Z_SALT ^ kind.salt()))
                * PIPE_MAX_ABS_TILT_PER_VERTICAL_BLOCK;
        double baseRadius = 1.8 + roll(worldSeed, cellX, cellZ, PIPE_RADIUS_SALT ^ kind.salt()) * 1.2;
        return new PipeCandidate(cellX, cellZ, anchorX, anchorZ, tiltX, tiltZ, baseRadius, kind);
    }

    /** Conservative horizontal reach for candidate discovery in a dimension of the given height. */
    public static int pipeSearchPaddingBlocks(int worldHeight) {
        if (worldHeight < 1) {
            throw new IllegalArgumentException("world height must be positive");
        }
        double maximumTiltReach = Math.hypot(
                PIPE_MAX_ABS_TILT_PER_VERTICAL_BLOCK,
                PIPE_MAX_ABS_TILT_PER_VERTICAL_BLOCK
        ) * worldHeight;
        return (int) Math.ceil(PIPE_FIXED_MAX_HORIZONTAL_REACH_BLOCKS + maximumTiltReach);
    }

    /**
     * Deterministic anchor used to sample short structural-diamond segments.
     * Actual corridor geometry comes from {@link TectonicStructuralField}; this
     * planner deliberately owns no second fault direction or tilt model.
     */
    public static StructuralCandidate structural(long worldSeed, int cellX, int cellZ) {
        int minX = cellX * STRUCTURAL_CELL_SIZE + STRUCTURAL_CELL_SIZE / 5;
        int minZ = cellZ * STRUCTURAL_CELL_SIZE + STRUCTURAL_CELL_SIZE / 5;
        int span = STRUCTURAL_CELL_SIZE * 3 / 5;
        int anchorX = minX + (int) Math.floor(roll(worldSeed, cellX, cellZ, STRUCTURAL_X_SALT) * span);
        int anchorZ = minZ + (int) Math.floor(roll(worldSeed, cellX, cellZ, STRUCTURAL_Z_SALT) * span);
        int clusters = 4 + (int) Math.floor(roll(worldSeed, cellX, cellZ, STRUCTURAL_CLUSTER_SALT) * 3.0);
        return new StructuralCandidate(cellX, cellZ, anchorX, anchorZ, clusters);
    }

    public static double pipeActivationRoll(long worldSeed, PipeCandidate candidate) {
        return roll(
                worldSeed,
                candidate.cellX(),
                candidate.cellZ(),
                PIPE_ACTIVATION_SALT ^ candidate.kind().salt()
        );
    }

    public static double structuralActivationRoll(long worldSeed, StructuralCandidate candidate) {
        return roll(worldSeed, candidate.cellX(), candidate.cellZ(), STRUCTURAL_ACTIVATION_SALT);
    }

    public static double pipeClusterRoll(long worldSeed, PipeCandidate candidate, int cluster, long salt) {
        return GeologyDeterminism.unitRoll(
                worldSeed,
                candidate.cellX(),
                cluster,
                candidate.cellZ(),
                PIPE_CLUSTER_SALT ^ candidate.kind().salt() ^ salt
        );
    }

    public static double structuralClusterRoll(long worldSeed, StructuralCandidate candidate, int cluster, long salt) {
        return GeologyDeterminism.unitRoll(
                worldSeed,
                candidate.cellX(),
                cluster,
                candidate.cellZ(),
                STRUCTURAL_CLUSTER_SALT ^ salt
        );
    }

    private static double roll(long worldSeed, int cellX, int cellZ, long salt) {
        return GeologyDeterminism.unitRoll(worldSeed, cellX, 0, cellZ, salt);
    }

    private static double signed(double roll) {
        return roll * 2.0 - 1.0;
    }

    public enum PipeKind {
        KIMBERLITE("kimberlite", 0x632BE59BD9B4E019L),
        LAMPROITE("lamproite", 0x9E3779B97F4A7C15L);

        private final String id;
        private final long salt;

        PipeKind(String id, long salt) {
            this.id = id;
            this.salt = salt;
        }

        public String id() {
            return id;
        }

        long salt() {
            return salt;
        }

        public static PipeKind byId(String id) {
            for (PipeKind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("unknown diamond pipe kind: " + id);
        }
    }

    public record PipeCandidate(
            int cellX,
            int cellZ,
            int anchorX,
            int anchorZ,
            double tiltX,
            double tiltZ,
            double baseRadius,
            PipeKind kind
    ) {
    }

    public record StructuralCandidate(
            int cellX,
            int cellZ,
            int anchorX,
            int anchorZ,
            int clusterCount
    ) {
    }
}
