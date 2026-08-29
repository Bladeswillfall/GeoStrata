package com.geostrata.worldgen.feature;

import com.geostrata.geology.DiamondGeologyPlanner;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.OreFeatureConfig;

import java.util.List;

/** Replacement target plus intrusive kind for one rare diamond-pipe feature. */
public record DiamondPipeConfig(
        List<OreFeatureConfig.Target> targets,
        String pipeKind
) implements FeatureConfig {
    public static final Codec<DiamondPipeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OreFeatureConfig.Target.CODEC.listOf().fieldOf("targets").forGetter(DiamondPipeConfig::targets),
            Codec.STRING.fieldOf("pipe_kind").forGetter(DiamondPipeConfig::pipeKind)
    ).apply(instance, DiamondPipeConfig::new));

    public DiamondPipeConfig {
        targets = List.copyOf(targets);
        if (targets.size() != 1) {
            throw new IllegalArgumentException("diamond pipe requires exactly one replacement target");
        }
        DiamondGeologyPlanner.PipeKind.byId(pipeKind);
    }

    public DiamondGeologyPlanner.PipeKind kind() {
        return DiamondGeologyPlanner.PipeKind.byId(pipeKind);
    }
}
