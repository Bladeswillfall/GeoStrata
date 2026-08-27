package com.geostrata.command;

import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.ProvinceDeformationProfiles;
import com.geostrata.geology.StructuralDeformationResponse;
import com.geostrata.geology.StructuralTransformField;
import com.geostrata.geology.StructuralTransformProfiles;
import com.geostrata.geology.TerrainMorphologySample;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Read-only diagnostics for terrain signals that may later inform geological deformation. */
public final class GeoStrataTerrainCommands {
    private GeoStrataTerrainCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("terrain")
                                .executes(context -> showTerrain(context.getSource())))
                        .then(CommandManager.literal("structure")
                                .executes(context -> showStructure(context.getSource())))
                        .then(CommandManager.literal("transform")
                                .executes(context -> showTransform(context.getSource()))))
        );
    }

    private static int showTerrain(ServerCommandSource source) {
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        TerrainMorphologySample sample = ChunkGeneratorTerrainMorphologySampler.sample(source.getWorld(), x, z);

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata terrain: raw generator height " + Math.round(sample.centerHeight())
                                + " | relief " + Math.round(sample.relief()) + " blocks"
                                + " | slope " + round(sample.slopeMagnitude())
                                + " [x " + round(sample.gradientX()) + ", z " + round(sample.gradientZ()) + "]"
                                + " | prominence " + round(sample.prominence()) + " blocks"
                                + " | spacing "
                                + ChunkGeneratorTerrainMorphologySampler.DEFAULT_SAMPLE_SPACING_BLOCKS + " blocks"
                                + " | diagnostic only; no geological deformation applied"
                ),
                false
        );
        return 1;
    }

    private static int showStructure(ServerCommandSource source) {
        ProvinceDeformationProfiles.Snapshot profiles = ProvinceDeformationProfiles.current();
        if (!profiles.loaded()) {
            source.sendError(Text.literal("GeoStrata province deformation profiles have not been loaded yet."));
            return 0;
        }

        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        TerrainMorphologySample terrain = ChunkGeneratorTerrainMorphologySampler.sample(source.getWorld(), x, z);
        GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(source.getWorld().getSeed(), x, z);
        StructuralDeformationResponse.Result response = StructuralDeformationResponse.evaluate(
                province,
                profiles,
                terrain
        );
        int primaryShare = percent(0.5 + 0.5 * province.interiorBlend(profiles.blendWidthBlocks()));

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata structure: " + province.province().displayName()
                                + " / " + province.neighborProvince().displayName()
                                + " | primary share " + primaryShare + "%"
                                + " | terrain signal " + percent(response.terrainSignal()) + "%"
                                + " | deformation " + percent(response.intensity()) + "%"
                                + " | dip " + percent(response.dipPotential()) + "%"
                                + ", fold " + percent(response.foldPotential()) + "%"
                                + ", fault " + percent(response.faultPotential()) + "%"
                                + " | normalized diagnostic only; no deformation applied"
                ),
                false
        );
        return 1;
    }

    private static int showTransform(ServerCommandSource source) {
        ProvinceDeformationProfiles.Snapshot deformationProfiles = ProvinceDeformationProfiles.current();
        StructuralTransformProfiles.Snapshot transformProfiles = StructuralTransformProfiles.current();
        if (!deformationProfiles.loaded() || !transformProfiles.loaded()) {
            source.sendError(Text.literal("GeoStrata structural transform metadata has not been loaded yet."));
            return 0;
        }

        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        long seed = source.getWorld().getSeed();
        TerrainMorphologySample terrain = ChunkGeneratorTerrainMorphologySampler.sample(source.getWorld(), x, z);
        GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(seed, x, z);

        StructuralTransformField.Field primary = fieldFor(
                seed,
                province.province(),
                province.siteX(),
                province.siteZ(),
                terrain,
                deformationProfiles,
                transformProfiles
        );
        StructuralTransformField.Field neighbor = fieldFor(
                seed,
                province.neighborProvince(),
                province.neighborSiteX(),
                province.neighborSiteZ(),
                terrain,
                deformationProfiles,
                transformProfiles
        );
        StructuralTransformField.Sample transform = StructuralTransformField.blend(
                primary.sample(position.x, position.z),
                neighbor.sample(position.x, position.z),
                province.interiorBlend(deformationProfiles.blendWidthBlocks())
        );

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata transform: total " + round(transform.totalOffset()) + " blocks"
                                + " [dip " + round(transform.dipOffset())
                                + ", fold " + round(transform.foldOffset())
                                + ", fault " + round(transform.faultOffset()) + "]"
                                + " | structural Y " + round(transform.transformY(position.y))
                                + " from world Y " + round(position.y)
                                + " | primary/neighbor dip ceilings now " + round(primary.dipDegrees())
                                + "° / " + round(neighbor.dipDegrees()) + "°"
                                + " | diagnostic vertical transform only; no blocks changed"
                ),
                false
        );
        return 1;
    }

    private static StructuralTransformField.Field fieldFor(
            long seed,
            com.geostrata.geology.GeologyProvince province,
            int siteX,
            int siteZ,
            TerrainMorphologySample terrain,
            ProvinceDeformationProfiles.Snapshot deformationProfiles,
            StructuralTransformProfiles.Snapshot transformProfiles
    ) {
        StructuralDeformationResponse.Result response = StructuralDeformationResponse.evaluate(
                deformationProfiles.normalization(),
                deformationProfiles.profileFor(province),
                terrain
        );
        return StructuralTransformField.forSite(
                seed,
                siteX,
                siteZ,
                transformProfiles.profileFor(province),
                response
        );
    }

    private static int percent(double value) {
        return (int) Math.round(value * 100.0);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
