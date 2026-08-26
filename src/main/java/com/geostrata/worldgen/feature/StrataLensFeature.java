package com.geostrata.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Generates a broad, tapered and gently tilted lithological lens instead of a
 * vanilla ore blob.
 *
 * <p>The feature deliberately reuses {@link OreFeatureConfig}: compatibility
 * packs can continue to extend GeoStrata's replacement tags, while the body
 * geometry is owned by GeoStrata. The config's {@code size} is treated as a
 * body-scale hint rather than an exact block count.</p>
 */
public final class StrataLensFeature extends Feature<OreFeatureConfig> {
    public StrataLensFeature(Codec<OreFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<OreFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        Random random = context.getRandom();
        OreFeatureConfig config = context.getConfig();

        int longRadius = Math.max(6, Math.min(14, (int) Math.ceil(Math.sqrt(config.size) * 1.35)));
        int shortRadius = Math.max(4, (int) Math.round(longRadius * (0.58 + random.nextDouble() * 0.16)));
        double halfThickness = Math.max(1.0, Math.min(2.5, config.size / 24.0));

        double angle = random.nextDouble() * Math.PI * 2.0;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double slope = (random.nextDouble() - 0.5) * 0.18;
        double warpAmplitude = 0.25 + random.nextDouble() * 0.45;
        double warpPhase = random.nextDouble() * Math.PI * 2.0;

        int placed = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -longRadius; dx <= longRadius; dx++) {
            for (int dz = -longRadius; dz <= longRadius; dz++) {
                double along = dx * cos + dz * sin;
                double across = -dx * sin + dz * cos;
                double radial = square(along / longRadius) + square(across / shortRadius);
                if (radial > 1.0) {
                    continue;
                }

                double taper = Math.sqrt(1.0 - radial);
                double centerY = origin.getY()
                        + slope * along
                        + warpAmplitude * Math.sin((along + across * 0.35) / 6.0 + warpPhase);
                double localHalfThickness = Math.max(0.55, halfThickness * taper);
                int minY = (int) Math.ceil(centerY - localHalfThickness);
                int maxY = (int) Math.floor(centerY + localHalfThickness);

                for (int y = minY; y <= maxY; y++) {
                    mutable.set(origin.getX() + dx, y, origin.getZ() + dz);
                    if (world.isOutOfHeightLimit(mutable)) {
                        continue;
                    }

                    BlockState existing = world.getBlockState(mutable);
                    for (OreFeatureConfig.Target target : config.targets) {
                        if (!target.target.test(existing, random)) {
                            continue;
                        }
                        if (!shouldDiscardOnAirExposure(world, mutable, random, config.discardOnAirChance)) {
                            world.setBlockState(mutable, target.state, Block.NOTIFY_LISTENERS);
                            placed++;
                        }
                        break;
                    }
                }
            }
        }

        return placed > 0;
    }

    private static boolean shouldDiscardOnAirExposure(
            StructureWorldAccess world,
            BlockPos pos,
            Random random,
            float discardChance
    ) {
        if (discardChance <= 0.0F) {
            return false;
        }

        for (Direction direction : Direction.values()) {
            if (world.getBlockState(pos.offset(direction)).isAir()) {
                return random.nextFloat() < discardChance;
            }
        }
        return false;
    }

    private static double square(double value) {
        return value * value;
    }
}
