package com.geostrata.command;

import com.geostrata.geology.CorrelatedExperimentDiagnostics;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.stream.Collectors;

/** Additional read-only command surface used to evaluate the opt-in correlated worldgen experiment. */
public final class CorrelatedExperimentCommands {
    private CorrelatedExperimentCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("experiment")
                                .then(CommandManager.literal("column")
                                        .executes(context -> showColumn(context.getSource())))))
        );
    }

    private static int showColumn(ServerCommandSource source) {
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int z = MathHelper.floor(position.z);
        CorrelatedExperimentDiagnostics.Report report = CorrelatedExperimentDiagnostics.inspect(
                source.getWorld().getSeed(),
                x,
                position.y,
                z,
                source.getWorld().getSeaLevel(),
                source.getWorld().getBottomY(),
                source.getWorld().getTopY()
        );

        if (!report.resolved()) {
            source.sendFeedback(
                    () -> Text.literal(
                            "GeoStrata correlated column: not owned"
                                    + " | reason " + report.ownership().reason()
                    ),
                    false
            );
            return 1;
        }

        source.sendFeedback(() -> Text.literal(currentSample(report)), false);
        source.sendFeedback(() -> Text.literal(columnSummary(report)), false);
        return 1;
    }

    private static String currentSample(CorrelatedExperimentDiagnostics.Report report) {
        return "GeoStrata correlated sample: " + report.lithology()
                + " | succession " + report.successionId()
                + " | chunk center " + report.chunkCenterX() + ", " + report.chunkCenterZ()
                + " | cycle " + report.cycleIndex()
                + ", position " + Math.round(report.fraction() * 100.0) + "%"
                + " | structural offset " + Math.round(report.verticalOffset()) + " blocks";
    }

    private static String columnSummary(CorrelatedExperimentDiagnostics.Report report) {
        String layers = report.layers().stream()
                .map(layer -> layer.minY() + ".." + layer.maxY() + " " + layer.lithology())
                .collect(Collectors.joining(" | "));
        return "GeoStrata correlated expected column " + report.minY() + ".." + report.maxY()
                + ": " + layers
                + " | compare against freshly generated host-stone sections";
    }
}
