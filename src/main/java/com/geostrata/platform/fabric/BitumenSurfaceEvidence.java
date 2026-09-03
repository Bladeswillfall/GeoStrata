package com.geostrata.platform.fabric;

import com.geostrata.block.GeoStrataBlocks;
import com.geostrata.geology.HydrocarbonReservoirField;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/** Small terrain-aware bitumen crusts derived from existing deterministic seep locations. */
final class BitumenSurfaceEvidence {
    static final double MIN_PRESSURE = 0.72;

    private BitumenSurfaceEvidence() {
    }

    static void materialize(ServerWorld world, HydrocarbonReservoirField.Reservoir reservoir) {
        if (reservoir.pressure() < MIN_PRESSURE) {
            return;
        }

        int seepSurfaceY = world.getTopY(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                reservoir.seepX(),
                reservoir.seepZ()
        );
        int radius = reservoir.pressure() >= 0.9 ? 3 : 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                materializeAt(world, reservoir, seepSurfaceY, radius, dx, dz);
            }
        }
    }

    private static void materializeAt(
            ServerWorld world,
            HydrocarbonReservoirField.Reservoir reservoir,
            int seepSurfaceY,
            int radius,
            int dx,
            int dz
    ) {
        int distanceSquared = dx * dx + dz * dz;
        if (distanceSquared > radius * radius) {
            return;
        }

        int x = reservoir.seepX() + dx;
        int z = reservoir.seepZ() + dz;
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int heightDelta = surfaceY - seepSurfaceY;
        if (heightDelta > 1 || heightDelta < -2) {
            return;
        }

        BlockPos target = new BlockPos(x, surfaceY, z);
        BlockState existing = world.getBlockState(target);
        if (!replaceableEvidence(existing)
                || !FabricGeologicalIndicatorRegistration.supportsPetroleumEvidence(
                        world.getBlockState(target.down())
                )
                || !selected(world, reservoir, dx, dz, heightDelta, distanceSquared)) {
            return;
        }

        int layers = layerCount(reservoir.pressure(), heightDelta, distanceSquared);
        BlockState bitumen = GeoStrataBlocks.BITUMEN.getDefaultState().with(SnowBlock.LAYERS, layers);
        world.setBlockState(target, bitumen, Block.NOTIFY_LISTENERS);
    }

    private static boolean replaceableEvidence(BlockState state) {
        return state.isAir() || state.isOf(GeoStrataBlocks.PETROLEUM_STAIN);
    }

    private static boolean selected(
            ServerWorld world,
            HydrocarbonReservoirField.Reservoir reservoir,
            int dx,
            int dz,
            int heightDelta,
            int distanceSquared
    ) {
        if (dx == 0 && dz == 0) {
            return true;
        }
        long position = BlockPos.asLong(reservoir.seepX() + dx, 0, reservoir.seepZ() + dz);
        long reservoirSalt = ((long) reservoir.cellX() << 32) ^ (reservoir.cellZ() & 0xFFFF_FFFFL);
        int roll = Math.floorMod(Long.hashCode(world.getSeed() ^ position ^ reservoirSalt ^ 0xB17A_6E5EL), 100);
        return roll < selectionChance(reservoir.pressure(), heightDelta, distanceSquared);
    }

    static int selectionChance(double pressure, int heightDelta, int distanceSquared) {
        int chance = (int) Math.round(18.0 + pressure * 45.0) - distanceSquared * 7;
        chance -= Math.max(0, heightDelta) * 30;
        chance += Math.max(0, -heightDelta) * 10;
        return Math.max(4, Math.min(92, chance));
    }

    static int layerCount(double pressure, int heightDelta, int distanceSquared) {
        int pressureDepth = 1 + (int) Math.floor(Math.max(0.0, pressure - MIN_PRESSURE) * 10.0);
        int centerBonus = distanceSquared == 0 ? (pressure >= 0.88 ? 3 : 2) : 0;
        int depressionBonus = Math.max(0, -heightDelta);
        return Math.max(1, Math.min(SnowBlock.MAX_LAYERS, pressureDepth + centerBonus + depressionBonus));
    }
}
