package com.geostrata.block;

import com.geostrata.GeoStrata;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class GeoStrataBlocks {
    private static final List<Block> ROCK_BLOCKS = new ArrayList<>();
    private static final List<Block> EARTH_BLOCKS = new ArrayList<>();

    public static final Block LIMESTONE = registerRock(
            "limestone",
            AbstractBlock.Settings.copy(Blocks.STONE)
                    .strength(1.5F, 6.0F)
                    .sounds(BlockSoundGroup.STONE)
                    .requiresTool()
    );
    public static final Block SHALE = registerRock(
            "shale",
            AbstractBlock.Settings.copy(Blocks.TUFF)
                    .strength(1.4F, 6.0F)
                    .sounds(BlockSoundGroup.TUFF)
                    .requiresTool()
    );
    public static final Block CLAY_LOAM = registerEarth(
            "clay_loam",
            AbstractBlock.Settings.copy(Blocks.DIRT)
                    .strength(0.6F)
                    .sounds(BlockSoundGroup.ROOTED_DIRT)
    );
    public static final Block COMPACTED_MUD = registerEarth(
            "compacted_mud",
            AbstractBlock.Settings.copy(Blocks.MUD)
                    .strength(0.7F)
                    .sounds(BlockSoundGroup.MUD)
    );

    private GeoStrataBlocks() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> ROCK_BLOCKS.forEach(entries::add));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
            ROCK_BLOCKS.forEach(entries::add);
            EARTH_BLOCKS.forEach(entries::add);
        });
        GeoStrata.LOGGER.info(
                "Registered {} rock blocks and {} earth blocks for the placeholder runtime pass",
                ROCK_BLOCKS.size(),
                EARTH_BLOCKS.size()
        );
    }

    public static int count() {
        return ROCK_BLOCKS.size() + EARTH_BLOCKS.size();
    }

    private static Block registerRock(String name, AbstractBlock.Settings settings) {
        Block block = register(name, settings);
        ROCK_BLOCKS.add(block);
        return block;
    }

    private static Block registerEarth(String name, AbstractBlock.Settings settings) {
        Block block = register(name, settings);
        EARTH_BLOCKS.add(block);
        return block;
    }

    private static Block register(String name, AbstractBlock.Settings settings) {
        Block block = Registry.register(Registries.BLOCK, GeoStrata.id(name), new Block(settings));
        Registry.register(Registries.ITEM, GeoStrata.id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }
}
