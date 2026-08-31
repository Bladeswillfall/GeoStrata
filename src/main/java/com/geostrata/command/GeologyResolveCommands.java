package com.geostrata.command;

import com.geostrata.geology.GeologyResolver;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/** In-game diagnostic for the same semantic geology authority used by worldgen consumers. */
public final class GeologyResolveCommands {
    private GeologyResolveCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("geostrata")
                        .then(CommandManager.literal("resolve")
                                .executes(context -> showResolvedGeology(context.getSource()))))
        );
    }

    private static int showResolvedGeology(ServerCommandSource source) {
        Vec3d position = source.getPosition();
        int x = MathHelper.floor(position.x);
        int y = MathHelper.floor(position.y);
        int z = MathHelper.floor(position.z);
        String actualBlock = Registries.BLOCK.getId(
                source.getWorld().getBlockState(new BlockPos(x, y, z)).getBlock()
        ).toString();

        return GeologyResolver.resolve(source.getWorld(), x, y, z)
                .map(result -> {
                    source.sendFeedback(() -> Text.literal(description(result, actualBlock, x, y, z)), false);
                    return 1;
                })
                .orElseGet(() -> {
                    source.sendFeedback(() -> Text.literal(unresolvedDescription(actualBlock, x, y, z)), false);
                    return 0;
                });
    }

    static String description(GeologyResolver.Result result, String actualBlock, int x, int y, int z) {
        return "GeoStrata resolve: " + result.lithology()
                + " | province " + result.province().displayName()
                + " | body " + result.bodyStyle().orElse("n/a")
                + " | parent " + result.parentLithology().orElse("n/a")
                + " | authority " + result.source().name().toLowerCase(Locale.ROOT)
                + " | actual " + actualBlock
                + " | at " + x + "," + y + "," + z;
    }

    static String unresolvedDescription(String actualBlock, int x, int y, int z) {
        return "GeoStrata resolve: no active semantic geology"
                + " | actual " + actualBlock
                + " | at " + x + "," + y + "," + z
                + " | advanced runtime may be disabled or unavailable";
    }
}
