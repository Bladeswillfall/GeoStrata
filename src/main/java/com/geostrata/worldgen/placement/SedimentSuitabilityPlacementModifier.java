package com.geostrata.worldgen.placement;

import com.geostrata.geology.GeologyDeterminism;
import com.geostrata.geology.SedimentSuitability;
import com.geostrata.geology.TerrainMorphologySample;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

import java.util.stream.Stream;

/** Data-driven placement gate for shallow sediments using terrain, water and biome evidence. */
public final class SedimentSuitabilityPlacementModifier extends PlacementModifier {
    public static final Codec<SedimentSuitabilityPlacementModifier> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("id").forGetter(modifier -> modifier.id),
                    Codec.DOUBLE.fieldOf("base_chance").forGetter(modifier -> modifier.baseChance),
                    Codec.DOUBLE.fieldOf("flatness_weight").forGetter(modifier -> modifier.flatnessWeight),
                    Codec.DOUBLE.fieldOf("valley_weight").forGetter(modifier -> modifier.valleyWeight),
                    Codec.DOUBLE.fieldOf("submerged_weight").forGetter(modifier -> modifier.submergedWeight),
                    Codec.DOUBLE.fieldOf("preferred_biome_bonus").forGetter(modifier -> modifier.preferredBiomeBonus),
                    Codec.STRING.optionalFieldOf("preferred_biome_tag", "")
                            .forGetter(modifier -> modifier.preferredBiomeTag)
            ).apply(instance, SedimentSuitabilityPlacementModifier::new)
    );

    private static final int SAMPLE_SPACING_BLOCKS = 16;
    private static final long DECISION_SALT = 0x8F3D9A6B41C275E1L;

    private final String id;
    private final double baseChance;
    private final double flatnessWeight;
    private final double valleyWeight;
    private final double submergedWeight;
    private final double preferredBiomeBonus;
    private final String preferredBiomeTag;
    private final SedimentSuitability.Weights weights;

    public SedimentSuitabilityPlacementModifier(
            String id,
            double baseChance,
            double flatnessWeight,
            double valleyWeight,
            double submergedWeight,
            double preferredBiomeBonus,
            String preferredBiomeTag
    ) {
        this.id = id;
        this.baseChance = baseChance;
        this.flatnessWeight = flatnessWeight;
        this.valleyWeight = valleyWeight;
        this.submergedWeight = submergedWeight;
        this.preferredBiomeBonus = preferredBiomeBonus;
        this.preferredBiomeTag = preferredBiomeTag;
        this.weights = new SedimentSuitability.Weights(
                id,
                baseChance,
                flatnessWeight,
                valleyWeight,
                submergedWeight,
                preferredBiomeBonus
        );
        if (!preferredBiomeTag.isEmpty() && Identifier.tryParse(preferredBiomeTag) == null) {
            throw new IllegalArgumentException("invalid preferred biome tag: " + preferredBiomeTag);
        }
    }

    @Override
    public Stream<BlockPos> getPositions(FeaturePlacementContext context, Random random, BlockPos pos) {
        TerrainMorphologySample terrain = terrain(context, pos);
        boolean submerged = context.getBlockState(pos).getFluidState().isIn(FluidTags.WATER);
        boolean preferredBiome = preferredBiome(context, pos);
        double chance = SedimentSuitability.chance(
                SedimentSuitability.evidence(terrain, submerged, preferredBiome),
                weights
        );
        double roll = GeologyDeterminism.unitRoll(
                context.getWorld().getSeed(),
                pos.getX(), pos.getY(), pos.getZ(),
                decisionSalt()
        );
        return GeologyDeterminism.passesChance(chance, roll) ? Stream.of(pos) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> getType() {
        return GeoStrataPlacementModifiers.SEDIMENT_SUITABILITY;
    }

    private TerrainMorphologySample terrain(FeaturePlacementContext context, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        return TerrainMorphologySample.fromCardinalHeights(
                height(context, x, z),
                height(context, x - SAMPLE_SPACING_BLOCKS, z),
                height(context, x + SAMPLE_SPACING_BLOCKS, z),
                height(context, x, z - SAMPLE_SPACING_BLOCKS),
                height(context, x, z + SAMPLE_SPACING_BLOCKS),
                SAMPLE_SPACING_BLOCKS
        );
    }

    private int height(FeaturePlacementContext context, int x, int z) {
        return context.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, x, z);
    }

    private boolean preferredBiome(FeaturePlacementContext context, BlockPos pos) {
        if (preferredBiomeTag.isEmpty()) {
            return false;
        }
        Identifier identifier = Identifier.tryParse(preferredBiomeTag);
        TagKey<Biome> tag = TagKey.of(RegistryKeys.BIOME, identifier);
        return context.getWorld().getBiome(pos).isIn(tag);
    }

    private long decisionSalt() {
        return DECISION_SALT ^ Integer.toUnsignedLong(id.hashCode()) * 0x9E3779B97F4A7C15L;
    }
}
