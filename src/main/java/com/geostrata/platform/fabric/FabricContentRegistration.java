package com.geostrata.platform.fabric;

import com.geostrata.GeoStrata;
import com.geostrata.block.GeoStrataBlocks;
import com.geostrata.worldgen.feature.GeoStrataFeatures;
import com.geostrata.worldgen.placement.GeoStrataPlacementModifiers;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

/** Fabric-owned registry timing and creative-tab wiring for shared GeoStrata definitions. */
public final class FabricContentRegistration {
    private FabricContentRegistration() {
    }

    public static void register() {
        GeoStrataBlocks.blocksByName().forEach((name, block) ->
                Registry.register(Registries.BLOCK, GeoStrata.id(name), block));
        GeoStrataBlocks.itemsByName().forEach((name, item) ->
                Registry.register(Registries.ITEM, GeoStrata.id(name), item));

        Registry.register(Registries.FEATURE, GeoStrata.id("strata_lens"), GeoStrataFeatures.STRATA_LENS);
        Registry.register(
                Registries.FEATURE,
                GeoStrata.id("correlated_sedimentary"),
                GeoStrataFeatures.CORRELATED_SEDIMENTARY
        );
        Registry.register(Registries.FEATURE, GeoStrata.id("ore_deposit"), GeoStrataFeatures.ORE_DEPOSIT);
        Registry.register(
                Registries.PLACEMENT_MODIFIER_TYPE,
                GeoStrata.id("sediment_suitability"),
                GeoStrataPlacementModifiers.SEDIMENT_SUITABILITY
        );

        Registry.register(
                Registries.ITEM_GROUP,
                GeoStrata.id("geostrata"),
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.geostrata.geostrata"))
                        .icon(() -> new ItemStack(GeoStrataBlocks.LIMESTONE))
                        .entries((context, entries) -> GeoStrataBlocks.allBlocks().forEach(entries::add))
                        .build()
        );

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries ->
                GeoStrataBlocks.rockBlocks().forEach(entries::add));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries ->
                GeoStrataBlocks.allBlocks().forEach(entries::add));

        GeoStrata.LOGGER.info(
                "Registered {} GeoStrata blocks and their items through the Fabric adapter",
                GeoStrataBlocks.count()
        );
    }
}
