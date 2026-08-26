package com.geostrata.worldgen.feature;

import com.geostrata.GeoStrata;
import com.geostrata.geology.GeologyDeterminism;
import com.geostrata.geology.GeologyProvinceProfiles;
import com.geostrata.geology.GeologyProvinceSampler;
import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.Optional;

/** Generates a broad, tapered and gently tilted lithological lens instead of a vanilla ore blob. */
public final class StrataLensFeature extends Feature<StrataLensConfig> {
    private static final long PROVINCE_ACCEPTANCE_SALT = 0x6A09E667F3BCC909L;

    public StrataLensFeature(Codec<StrataLensConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<StrataLensConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        Random random = context.getRandom();
        StrataLensConfig config = context.getConfig();

        if (!passesProvinceSuitability(world, origin, config)) {
            return false;
        }

        int longRadius = config.longRadius();
        double shortRatio = config.shortRadiusRatio()
                + (random.nextDouble() * 2.0 - 1.0) * config.shortRadiusVariation();
        int shortRadius = Math.max(2, (int) Math.round(longRadius * shortRatio));

        double angle = random.nextDouble() * Math.PI * 2.0;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double slope = (random.nextDouble() * 2.0 - 1.0) * config.maxSlope();
        double warpAmplitude = config.warpAmplitude()
                + (random.nextDouble() * 2.0 - 1.0) * config.warpVariation();
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
                        + warpAmplitude * Math.sin(
                        (along + across * 0.35) / config.warpWavelength() + warpPhase
                );
                double localHalfThickness = Math.max(
                        config.edgeHalfThickness(),
                        config.halfThickness() * taper
                );
                int minY = (int) Math.ceil(centerY - localHalfThickness);
                int maxY = (int) Math.floor(centerY + localHalfThickness);

                for (int y = minY; y <= maxY; y++) {
                    mutable.set(origin.getX() + dx, y, origin.getZ() + dz);
                    if (world.isOutOfHeightLimit(mutable)) {
                        continue;
                    }

                    BlockState existing = world.getBlockState(mutable);
                    for (OreFeatureConfig.Target target : config.targets()) {
                        if (!target.target.test(existing, random)) {
                            continue;
                        }
                        if (!shouldDiscardOnAirExposure(world, mutable, random, config.discardOnAirChance())) {
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

    private static boolean passesProvinceSuitability(
            StructureWorldAccess world,
            BlockPos origin,
            StrataLensConfig config
    ) {
        GeologyProvinceProfiles.Snapshot profiles = GeologyProvinceProfiles.current();
        if (!profiles.loaded()) {
            return true;
        }

        Optional<String> lithology = profiledLithology(config, profiles);
        if (lithology.isEmpty()) {
            return true;
        }

        GeologyProvinceSampler.Sample sample = GeologyProvinceSampler.sample(
                world.getSeed(),
                origin.getX(), origin.getZ()
        );
        double suitability = profiles.effectiveWeight(sample, lithology.get());
        double roll = GeologyDeterminism.unitRoll(
                world.getSeed(),
                origin.getX(), origin.getY(), origin.getZ(),
                PROVINCE_ACCEPTANCE_SALT
        );
        return GeologyDeterminism.passesChance(suitability, roll);
    }

    private static Optional<String> profiledLithology(
            StrataLensConfig config,
            GeologyProvinceProfiles.Snapshot profiles
    ) {
        if (config.targets().isEmpty()) {
            return Optional.empty();
        }

        Identifier first = Registries.BLOCK.getId(config.targets().get(0).state.getBlock());
        if (!GeoStrata.MOD_ID.equals(first.getNamespace()) || !profiles.lithologyIds().contains(first.getPath())) {
            return Optional.empty();
        }

        for (OreFeatureConfig.Target target : config.targets()) {
            Identifier targetId = Registries.BLOCK.getId(target.state.getBlock());
            if (!targetId.equals(first)) {
                return Optional.empty();
            }
        }
        return Optional.of(first.getPath());
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
