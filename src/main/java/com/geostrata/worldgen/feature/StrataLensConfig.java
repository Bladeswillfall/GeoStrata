package com.geostrata.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.OreFeatureConfig;

import java.util.List;

/**
 * Data-driven geometry and replacement contract for a broad stratigraphic lens.
 *
 * <p>The default limestone values reproduce the original pilot's statistical
 * geometry while allowing datapacks and future lithologies to tune body shape
 * without changing Java.</p>
 */
public record StrataLensConfig(
        List<OreFeatureConfig.Target> targets,
        float discardOnAirChance,
        int longRadius,
        double shortRadiusRatio,
        double shortRadiusVariation,
        double halfThickness,
        double edgeHalfThickness,
        double maxSlope,
        double warpAmplitude,
        double warpVariation,
        double warpWavelength
) implements FeatureConfig {
    public static final Codec<StrataLensConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OreFeatureConfig.Target.CODEC.listOf().fieldOf("targets").forGetter(StrataLensConfig::targets),
            Codec.FLOAT.fieldOf("discard_chance_on_air_exposure").forGetter(StrataLensConfig::discardOnAirChance),
            Codec.intRange(4, 64).fieldOf("long_radius").forGetter(StrataLensConfig::longRadius),
            Codec.DOUBLE.fieldOf("short_radius_ratio").forGetter(StrataLensConfig::shortRadiusRatio),
            Codec.DOUBLE.fieldOf("short_radius_variation").forGetter(StrataLensConfig::shortRadiusVariation),
            Codec.DOUBLE.fieldOf("half_thickness").forGetter(StrataLensConfig::halfThickness),
            Codec.DOUBLE.fieldOf("edge_half_thickness").forGetter(StrataLensConfig::edgeHalfThickness),
            Codec.DOUBLE.fieldOf("max_slope").forGetter(StrataLensConfig::maxSlope),
            Codec.DOUBLE.fieldOf("warp_amplitude").forGetter(StrataLensConfig::warpAmplitude),
            Codec.DOUBLE.fieldOf("warp_variation").forGetter(StrataLensConfig::warpVariation),
            Codec.DOUBLE.fieldOf("warp_wavelength").forGetter(StrataLensConfig::warpWavelength)
    ).apply(instance, StrataLensConfig::new));

    public StrataLensConfig {
        targets = List.copyOf(targets);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("strata lens requires at least one replacement target");
        }
        if (!between(discardOnAirChance, 0.0, 1.0)) {
            throw new IllegalArgumentException("discard chance must be between 0 and 1");
        }
        if (!between(shortRadiusRatio, 0.2, 1.0)
                || !between(shortRadiusVariation, 0.0, 0.4)
                || shortRadiusRatio - shortRadiusVariation < 0.2
                || shortRadiusRatio + shortRadiusVariation > 1.0) {
            throw new IllegalArgumentException("short-radius ratio/variation must remain within 0.2..1.0");
        }
        if (!between(halfThickness, 0.5, 12.0)
                || !between(edgeHalfThickness, 0.25, halfThickness)) {
            throw new IllegalArgumentException("invalid strata lens thickness");
        }
        if (!between(maxSlope, 0.0, 0.5)) {
            throw new IllegalArgumentException("max slope must be between 0 and 0.5");
        }
        if (!between(warpAmplitude, 0.0, 4.0)
                || !between(warpVariation, 0.0, warpAmplitude)
                || !between(warpWavelength, 2.0, 64.0)) {
            throw new IllegalArgumentException("invalid strata lens warp parameters");
        }
    }

    private static boolean between(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }
}
