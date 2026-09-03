package com.geostrata.platform.fabric;

import com.geostrata.block.GradedOreBlock;
import com.geostrata.geology.GeologyResolver;
import com.geostrata.geology.HydrocarbonReservoirField;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Optional;

/** Lightweight player-facing signs derived from existing geology and generated blocks. */
public final class FabricGeologicalIndicatorRegistration {
    private static final int TICK_INTERVAL = 20;
    private static final int COAL_RADIUS_XZ = 6;
    private static final int COAL_RADIUS_Y = 4;
    private static final int COAL_SIGNAL_CAP = 16;
    private static final int OIL_SEEP_VISIBILITY_RADIUS = 20;

    private FabricGeologicalIndicatorRegistration() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(FabricGeologicalIndicatorRegistration::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        if (world.getTime() % TICK_INTERVAL != 0L) {
            return;
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            emitCoalHaze(world, player);
            emitOilSeep(world, player);
        }
    }

    private static void emitCoalHaze(ServerWorld world, ServerPlayerEntity player) {
        int surfaceY = world.getTopY(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                player.getBlockX(),
                player.getBlockZ()
        );
        if (player.getBlockY() >= surfaceY - 4) {
            return;
        }

        int signal = 0;
        for (BlockPos pos : BlockPos.iterateOutwards(
                player.getBlockPos(),
                COAL_RADIUS_XZ,
                COAL_RADIUS_Y,
                COAL_RADIUS_XZ
        )) {
            signal += coalSignal(world.getBlockState(pos));
            if (signal >= COAL_SIGNAL_CAP) {
                break;
            }
        }
        if (signal < 3) {
            return;
        }

        int particles = Math.min(10, 2 + signal / 2);
        world.spawnParticles(
                ParticleTypes.ASH,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                particles,
                3.5,
                1.8,
                3.5,
                0.004
        );
    }

    private static int coalSignal(BlockState state) {
        if (state.isOf(Blocks.COAL_BLOCK)) {
            return 8;
        }
        if (state.getBlock() instanceof GradedOreBlock graded && "coal".equals(graded.material())) {
            return graded.grade().baseYield();
        }
        return state.isIn(BlockTags.COAL_ORES) ? 1 : 0;
    }

    private static void emitOilSeep(ServerWorld world, ServerPlayerEntity player) {
        int surfaceY = world.getTopY(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                player.getBlockX(),
                player.getBlockZ()
        );
        if (Math.abs(player.getBlockY() - surfaceY) > 5) {
            return;
        }

        int sampleY = Math.max(world.getBottomY() + 16, Math.min(32, surfaceY - 24));
        Optional<HydrocarbonReservoirField.Reservoir> reservoir = GeologyResolver.resolve(
                        world,
                        player.getBlockX(),
                        sampleY,
                        player.getBlockZ()
                )
                .flatMap(geology -> HydrocarbonReservoirField.sample(
                        world.getSeed(),
                        player.getBlockX(),
                        player.getBlockZ(),
                        geology
                ));
        if (reservoir.isEmpty() || reservoir.get().pressure() < 0.55) {
            return;
        }

        HydrocarbonReservoirField.Reservoir body = reservoir.get();
        double dx = player.getX() - (body.seepX() + 0.5);
        double dz = player.getZ() - (body.seepZ() + 0.5);
        if (dx * dx + dz * dz > OIL_SEEP_VISIBILITY_RADIUS * OIL_SEEP_VISIBILITY_RADIUS) {
            return;
        }

        int seepY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, body.seepX(), body.seepZ());
        world.spawnParticles(
                ParticleTypes.SQUID_INK,
                body.seepX() + 0.5,
                seepY + 0.15,
                body.seepZ() + 0.5,
                2,
                0.35,
                0.08,
                0.35,
                0.005
        );
        world.spawnParticles(
                ParticleTypes.SMOKE,
                body.seepX() + 0.5,
                seepY + 0.25,
                body.seepZ() + 0.5,
                1,
                0.2,
                0.05,
                0.2,
                0.002
        );
    }
}
