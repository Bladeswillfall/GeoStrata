package com.geostrata.worldgen.placement;

import com.mojang.serialization.Codec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

import java.util.stream.Stream;

/** Places one fallback geological body inside the active terrain column. */
public final class SubsurfaceAnchorPlacementModifier extends PlacementModifier {
    public static final SubsurfaceAnchorPlacementModifier INSTANCE = new SubsurfaceAnchorPlacementModifier();
    public static final Codec<SubsurfaceAnchorPlacementModifier> CODEC = Codec.unit(INSTANCE);

    private SubsurfaceAnchorPlacementModifier() {
    }

    @Override
    public Stream<BlockPos> getPositions(FeaturePlacementContext context, Random random, BlockPos pos) {
        int minimumY = context.getWorld().getBottomY();
        int surfaceY = context.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, pos.getX(), pos.getZ()) - 1;
        int maximumY = Math.min(context.getWorld().getTopY() - 1, surfaceY);
        if (maximumY < minimumY) {
            return Stream.empty();
        }

        int y = minimumY + random.nextInt(maximumY - minimumY + 1);
        return Stream.of(new BlockPos(pos.getX(), y, pos.getZ()));
    }

    @Override
    public PlacementModifierType<?> getType() {
        return GeoStrataPlacementModifiers.SUBSURFACE_ANCHOR;
    }
}
