package com.geostrata.item;

import com.geostrata.GeoStrata;
import com.geostrata.block.GeoStrataBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class GeoStrataItemGroups {
    public static final ItemGroup GEOSTRATA = Registry.register(
            Registries.ITEM_GROUP,
            GeoStrata.id("geostrata"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.geostrata.geostrata"))
                    .icon(() -> new ItemStack(GeoStrataBlocks.LIMESTONE))
                    .entries((context, entries) -> GeoStrataBlocks.allBlocks().forEach(entries::add))
                    .build()
    );

    private GeoStrataItemGroups() {
    }

    public static void register() {
        GeoStrata.LOGGER.info("Registered GeoStrata creative item group: {}", GEOSTRATA.getDisplayName().getString());
    }
}
