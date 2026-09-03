package com.geostrata.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Snow-layer-style bitumen crust with depth-scaled sticky movement. */
public final class BitumenBlock extends SnowBlock {
    public BitumenBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        int layers = state.get(LAYERS);
        if (layers >= 3) {
            Vec3d velocity = entity.getVelocity();
            double horizontal = layers >= 7 ? 0.45 : layers >= 5 ? 0.62 : 0.8;
            double vertical = layers >= 7 ? 0.75 : 1.0;
            entity.setVelocity(
                    velocity.x * horizontal,
                    velocity.y * vertical,
                    velocity.z * horizontal
            );
        }
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        // Bitumen reuses snow geometry/state, not snow's light-driven melting behavior.
    }
}
