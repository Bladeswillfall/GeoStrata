package com.geostrata.platform.fabric.mixin.compat.cdg;

import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Keeps GeoStrata-marked dry chunks dry when CDG's optional infinite-deposit mode is enabled. */
@Pseudo
@Mixin(targets = "com.jesz.createdieselgenerators.world.OilChunksSavedData", remap = false)
abstract class OilChunksSavedDataMixin {
    @Shadow(remap = false)
    private Map<ChunkPos, Integer> chunks;

    @Inject(method = "getChunkOilAmount", at = @At("HEAD"), cancellable = true, remap = false)
    private void geostrata$preserveDryChunk(ChunkPos chunk, CallbackInfoReturnable<Integer> cir) {
        Integer amount = chunks.get(chunk);
        if (amount != null && amount == 0) {
            cir.setReturnValue(0);
        }
    }
}
