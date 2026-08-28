package com.geostrata.command;

import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.MetamorphicIntensityField;
import com.geostrata.geology.TerrainMorphologySample;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Diagnostic command for observing the staged metamorphic-intensity field in live terrain. */
public final class MetamorphismCommands {
    private MetamorphismCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("metamorphism")
                                .executes(context -> showMetamorphism(context.getSource()))))
        );
    }

    private static int showMetamorphism(ServerCommandSource source) {
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        TerrainMorphologySample terrain = ChunkGeneratorTerrainMorphologySampler.sample(source.getWorld(), x, z);
        MetamorphicIntensityField.Sample sample = MetamorphicIntensityField.sample(
                source.getWorld().getSeed(),
                x,
                z,
                MetamorphicIntensityField.DEFAULT_PROVINCE_BLEND_WIDTH_BLOCKS,
                terrain
        );
        MetamorphicIntensityField.Suitability suitability = sample.suitability();

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata metamorphism: intensity " + percent(sample.intensity())
                                + " | likely " + suitability.dominantLithology()
                                + " | slate " + percent(suitability.slate())
                                + ", schist " + percent(suitability.schist())
                                + ", gneiss " + percent(suitability.gneiss())
                                + " | regional " + signedPercent(sample.regionalAdjustment())
                                + ", terrain " + signedPercent(sample.terrainAdjustment())
                                + " | " + sample.province().province().displayName()
                                + " toward " + sample.province().neighborProvince().displayName()
                                + " | diagnostic only; baseline metamorphic worldgen unchanged"
                ),
                false
        );
        return 1;
    }

    private static String percent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private static String signedPercent(double value) {
        long rounded = Math.round(value * 100.0);
        return (rounded > 0 ? "+" : "") + rounded + "%";
    }
}
