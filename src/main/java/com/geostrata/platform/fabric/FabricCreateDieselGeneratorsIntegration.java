package com.geostrata.platform.fabric;

import com.geostrata.GeoStrata;
import com.geostrata.geology.GeologyResolver;
import com.geostrata.geology.HydrocarbonReservoirField;
import com.geostrata.geology.LithologyCatalog;
import com.geostrata.geology.PetroleumChunkField;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/** Optional bridge that lets GeoStrata own CDG petroleum occurrence without a hard dependency. */
public final class FabricCreateDieselGeneratorsIntegration {
    static final String MOD_ID = "createdieselgenerators";
    private static final String OIL_DATA_CLASS = "com.jesz.createdieselgenerators.world.OilChunksSavedData";
    private static final Identifier CRUDE_OIL_ID = new Identifier(MOD_ID, "crude_oil");
    private static final int[][] SAMPLE_OFFSETS = {{8, 8}, {3, 3}, {12, 3}, {3, 12}, {12, 12}};
    private static OilStoreBridge oilStore;

    private FabricCreateDieselGeneratorsIntegration() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        try {
            oilStore = OilStoreBridge.create();
            ServerChunkEvents.CHUNK_LOAD.register(FabricCreateDieselGeneratorsIntegration::onChunkLoad);
            GeoStrata.LOGGER.info("GeoStrata is supplying geological oil chunks to Create: Diesel Generators");
        } catch (ReflectiveOperationException exception) {
            GeoStrata.LOGGER.warn("Create: Diesel Generators was detected but its oil-chunk API is incompatible", exception);
        }
    }

    private static void onChunkLoad(ServerWorld world, net.minecraft.world.chunk.WorldChunk chunk) {
        OilStoreBridge bridge = oilStore;
        if (bridge == null
                || !World.OVERWORLD.equals(world.getRegistryKey())
                || !LithologyCatalog.current().loaded()) {
            return;
        }
        try {
            ChunkPos pos = chunk.getPos();
            if (bridge.amount(world, pos) != -1) {
                return;
            }
            bridge.setAmount(world, pos, geologicalOilAmount(world, pos));
        } catch (ReflectiveOperationException exception) {
            oilStore = null;
            GeoStrata.LOGGER.warn("Disabling Create: Diesel Generators oil-chunk bridge after an API failure", exception);
        }
    }

    static int geologicalOilAmount(ServerWorld world, ChunkPos pos) {
        int startX = pos.getStartX();
        int startZ = pos.getStartZ();
        int sampleY = Math.max(world.getBottomY() + 16, Math.min(32, world.getTopY() - 16));
        Optional<GeologyResolver.PreparedChunk> prepared = GeologyResolver.prepareChunk(world, startX + 8, startZ + 8);
        if (prepared.isEmpty()) {
            return 0;
        }

        int amount = 0;
        for (int[] offset : SAMPLE_OFFSETS) {
            int x = startX + offset[0];
            int z = startZ + offset[1];
            int sampleAmount = prepared.get().resolve(x, sampleY, z)
                    .flatMap(geology -> HydrocarbonReservoirField.sample(world.getSeed(), x, z, geology))
                    .map(PetroleumChunkField::oilAmount)
                    .orElse(0);
            amount = Math.max(amount, sampleAmount);
        }
        return amount;
    }

    /** Places CDG's own source fluid at only the rarest high-pressure GeoStrata seeps. */
    public static void materializeFreeCrude(
            ServerWorld world,
            HydrocarbonReservoirField.Reservoir reservoir,
            int seepY
    ) {
        if (oilStore == null || !PetroleumChunkField.exposesFreeCrude(reservoir)) {
            return;
        }
        Block crudeOil = Registries.BLOCK.get(CRUDE_OIL_ID);
        if (crudeOil == Blocks.AIR) {
            return;
        }

        BlockPos target = new BlockPos(reservoir.seepX(), seepY, reservoir.seepZ());
        BlockState existing = world.getBlockState(target);
        if (existing.isOf(crudeOil) || !existing.isAir()) {
            return;
        }

        BlockState crudeState = crudeOil.getDefaultState();
        FluidState fluid = crudeState.getFluidState();
        if (fluid.isEmpty() || !fluid.isStill() || fluid.getFluid().getBucketItem() == Items.AIR) {
            return;
        }
        world.setBlockState(target, crudeState, Block.NOTIFY_LISTENERS);
    }

    private record OilStoreBridge(Method load, Method getAmount, Method setAmount) {
        static OilStoreBridge create() throws ReflectiveOperationException {
            Class<?> type = Class.forName(OIL_DATA_CLASS);
            return new OilStoreBridge(
                    method(type, "load", 1),
                    method(type, "getChunkOilAmount", 1),
                    method(type, "setChunkAmount", 2)
            );
        }

        int amount(ServerWorld world, ChunkPos pos) throws ReflectiveOperationException {
            Object store = invoke(load, null, world);
            return ((Number) invoke(getAmount, store, pos)).intValue();
        }

        void setAmount(ServerWorld world, ChunkPos pos, int amount) throws ReflectiveOperationException {
            Object store = invoke(load, null, world);
            invoke(setAmount, store, pos, amount);
        }

        private static Method method(Class<?> type, String name, int parameters) throws NoSuchMethodException {
            return Arrays.stream(type.getMethods())
                    .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == parameters)
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException(type.getName() + "." + name));
        }

        private static Object invoke(Method method, Object target, Object... arguments) throws ReflectiveOperationException {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof ReflectiveOperationException reflective) {
                    throw reflective;
                }
                throw exception;
            }
        }
    }
}
