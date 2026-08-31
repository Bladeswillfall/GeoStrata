package com.geostrata.experiment.mixin;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Disables vanilla large iron/copper noise veins only in vanilla Overworld noise settings. */
@Mixin(NoiseChunkGenerator.class)
abstract class NoiseChunkGeneratorMixin {
    @Shadow
    @Final
    private RegistryEntry<ChunkGeneratorSettings> settings;

    @ModifyArg(
            method = "createChunkNoiseSampler",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/gen/chunk/ChunkNoiseSampler;create(Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/densityfunction/DensityFunctionTypes$Beardifying;Lnet/minecraft/world/gen/chunk/ChunkGeneratorSettings;Lnet/minecraft/world/gen/chunk/AquiferSampler$FluidLevelSampler;Lnet/minecraft/world/gen/chunk/Blender;)Lnet/minecraft/world/gen/chunk/ChunkNoiseSampler;"
            ),
            index = 3
    )
    private ChunkGeneratorSettings geostrata$disableVanillaOverworldOreVeins(ChunkGeneratorSettings original) {
        if (!original.oreVeins() || !isVanillaOverworldSettings()) {
            return original;
        }
        return new ChunkGeneratorSettings(
                original.generationShapeConfig(),
                original.defaultBlock(),
                original.defaultFluid(),
                original.noiseRouter(),
                original.surfaceRule(),
                original.spawnTarget(),
                original.seaLevel(),
                original.mobGenerationDisabled(),
                original.aquifers(),
                false,
                original.usesLegacyRandom()
        );
    }

    private boolean isVanillaOverworldSettings() {
        return settings.matchesKey(ChunkGeneratorSettings.OVERWORLD)
                || settings.matchesKey(ChunkGeneratorSettings.LARGE_BIOMES)
                || settings.matchesKey(ChunkGeneratorSettings.AMPLIFIED);
    }
}
