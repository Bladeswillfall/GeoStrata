package com.geostrata.command;

import com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;
import com.geostrata.geology.CorrelatedSedimentaryRuntime;
import com.geostrata.geology.FaultDamageZone;
import com.geostrata.geology.GeologyProvinceSampler;
import com.geostrata.geology.SedimentaryFieldProfiles;
import com.geostrata.geology.SedimentaryStratigraphicField;
import com.geostrata.geology.TectonicFoldPolarity;
import com.geostrata.geology.TectonicStructuralField;
import com.geostrata.geology.TerraneSuture;
import com.geostrata.geology.TerrainAwareStructuralField;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/** Diagnostic view of the structural field that actually deforms experimental geology. */
public final class StructuralCommands {
    private static final String BACKGROUND_CONTINUITY = "regional";

    private StructuralCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("structure")
                                .executes(context -> show(context.getSource()))))
        );
    }

    private static int show(ServerCommandSource source) {
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int y = MathHelper.floor(position.y);
        int z = MathHelper.floor(position.z);
        long seed = source.getWorld().getSeed();
        GeologyProvinceSampler.Sample province = GeologyProvinceSampler.sample(seed, x, z);
        SedimentaryFieldProfiles.Snapshot profiles = SedimentaryFieldProfiles.current();
        if (!profiles.loaded()) {
            source.sendError(Text.literal("GeoStrata structural field metadata has not been loaded yet."));
            return 0;
        }

        TerrainAwareStructuralField.Field field;
        String authority;
        String polarity;
        Optional<CorrelatedSedimentaryRuntime.TerrainAwareSite> correlated =
                CorrelatedSedimentaryRuntime.resolve(source.getWorld(), x, z);
        if (correlated.isPresent()) {
            CorrelatedSedimentaryRuntime.TerrainAwareSite site = correlated.get();
            field = site.field();
            authority = "correlated:" + site.succession().id();
            TectonicFoldPolarity.Transform transform = site.foldPolarity().transform(field.tectonicField(), x, z);
            polarity = polarity(transform.verticalScale());
        } else {
            SedimentaryStratigraphicField.Field base = SedimentaryStratigraphicField.forSite(
                    seed,
                    province.siteX(),
                    province.siteZ(),
                    profiles.parametersFor(BACKGROUND_CONTINUITY)
            );
            field = ChunkGeneratorTerrainMorphologySampler.structuralField(
                    source.getWorld(),
                    x,
                    z,
                    province.province(),
                    base
            );
            authority = "province_background";
            polarity = "n/a";
        }

        TectonicStructuralField.Sample tectonic = field.tectonicSample(x, y, z);
        TectonicStructuralField.FaultTrace trace = field.tectonicField().nearestFault(x, y, z);
        TectonicStructuralField.Column faultColumn = field.tectonicField().column(x, z);
        boolean damaged = FaultDamageZone.contains(province.province(), faultColumn, y);
        String faultDistance = Double.isFinite(trace.distanceToFault())
                ? Long.toString(Math.round(trace.distanceToFault()))
                : "n/a";
        String faultLocation = Double.isFinite(trace.distanceToFault())
                ? Math.round(trace.x()) + "," + y + "," + Math.round(trace.z())
                : "n/a";
        String suture = sutureSummary(source, seed, y, province, profiles);

        source.sendFeedback(
                () -> Text.literal(
                        "GeoStrata structure: " + province.province().displayName()
                                + " | authority " + authority
                                + " | base dip/warp " + signed(field.baseField().verticalOffset(x, z))
                                + " | drape " + signed(field.drapeOffset(x, z))
                                + " | terrain fold " + signed(field.foldOffset(x, z))
                                + " | tectonic fold " + signed(tectonic.foldOffset())
                                + " | strata polarity " + polarity
                                + " | fault " + tectonic.faultRegime().name().toLowerCase()
                                + " offset " + signed(tectonic.faultOffset())
                                + " | nearest @ " + faultLocation + " ~" + faultDistance + " blocks"
                                + " | spacing ~" + Math.round(field.tectonicField().faultSpacingBlocks())
                                + ", throw ~" + Math.round(field.tectonicField().faultThrowBlocks())
                                + ", dip ~" + Math.round(field.tectonicField().faultDipDegrees(y)) + "°"
                                + (damaged ? " | damage zone" : "")
                                + suture
                                + " | total " + signed(field.verticalOffset(x, y, z))
                ),
                false
        );
        return 1;
    }

    private static String sutureSummary(
            ServerCommandSource source,
            long seed,
            int y,
            GeologyProvinceSampler.Sample province,
            SedimentaryFieldProfiles.Snapshot profiles
    ) {
        if (!TerraneSuture.canCross(province)) {
            return "";
        }
        double cycleThickness = profiles.parametersFor(BACKGROUND_CONTINUITY).cycleThicknessBlocks();
        TectonicStructuralField.Context primary = TectonicStructuralField.forSite(
                seed,
                province.province(),
                province.siteX(),
                province.siteZ(),
                cycleThickness
        );
        TectonicStructuralField.Context neighbor = TectonicStructuralField.forSite(
                seed,
                province.neighborProvince(),
                province.neighborSiteX(),
                province.neighborSiteZ(),
                cycleThickness
        );
        TerraneSuture.Contact contact = TerraneSuture.forColumn(
                province,
                primary,
                neighbor,
                source.getWorld().getSeaLevel()
        );
        String currentTerrane = contact.usesPrimary(y)
                ? province.province().displayName()
                : province.neighborProvince().displayName();
        return " | suture surface ~" + Math.round(province.distanceToBoundary())
                + " blocks, dip ~" + Math.round(contact.dipDegrees()) + "°"
                + ", current-Y terrane " + currentTerrane;
    }

    private static String polarity(double scale) {
        String state;
        if (scale < 0.0) {
            state = "overturned";
        } else if (Math.abs(scale) < 0.15) {
            state = "near-vertical";
        } else {
            state = "normal";
        }
        return state + " " + Math.round(scale * 100.0) + "%";
    }

    private static String signed(double value) {
        long rounded = Math.round(value);
        return rounded > 0 ? "+" + rounded : Long.toString(rounded);
    }
}
