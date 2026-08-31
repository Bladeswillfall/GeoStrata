package com.geostrata.worldgen.feature;

import net.minecraft.util.math.BlockBox;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

/** Shared protection for blocks intentionally placed by generated structure pieces. */
final class StructurePieceProtection {
    private StructurePieceProtection() {
    }

    static List<BlockBox> forChunk(StructureWorldAccess world, Chunk chunk) {
        return world.toServerWorld().getStructureAccessor().getStructureStarts(chunk.getPos(), structure -> true).stream()
                .flatMap(start -> start.getChildren().stream())
                .map(piece -> piece.getBoundingBox())
                .toList();
    }

    static boolean contains(List<BlockBox> pieces, int x, int y, int z) {
        for (BlockBox piece : pieces) {
            if (piece.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }
}
