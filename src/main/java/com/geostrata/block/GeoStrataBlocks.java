package com.geostrata.block;

import com.geostrata.GeoStrata;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class GeoStrataBlocks {
    private static final List<Block> ROCK_BLOCKS = new ArrayList<>();
    private static final List<Block> EARTH_BLOCKS = new ArrayList<>();

    public static final Block LIMESTONE = registerRock("limestone", rock(Blocks.STONE, 1.5F, BlockSoundGroup.STONE));
    public static final Block CHALK = registerRock("chalk", rock(Blocks.CALCITE, 1.0F, BlockSoundGroup.CALCITE));
    public static final Block SHALE = registerRock("shale", rock(Blocks.TUFF, 1.4F, BlockSoundGroup.TUFF));
    public static final Block SLATE = registerRock("slate", rock(Blocks.DEEPSLATE, 2.8F, BlockSoundGroup.DEEPSLATE));
    public static final Block MUDSTONE = registerRock("mudstone", rock(Blocks.STONE, 1.3F, BlockSoundGroup.STONE));
    public static final Block SILTSTONE = registerRock("siltstone", rock(Blocks.SANDSTONE, 1.2F, BlockSoundGroup.STONE));
    public static final Block MARBLE = registerRock("marble", rock(Blocks.CALCITE, 1.7F, BlockSoundGroup.CALCITE));
    public static final Block QUARTZITE = registerRock("quartzite", rock(Blocks.SMOOTH_QUARTZ, 2.0F, BlockSoundGroup.STONE));
    public static final Block SCHIST = registerRock("schist", rock(Blocks.TUFF, 1.8F, BlockSoundGroup.TUFF));
    public static final Block GNEISS = registerRock("gneiss", rock(Blocks.GRANITE, 1.9F, BlockSoundGroup.STONE));
    public static final Block BASALT = registerRock("basalt", rock(Blocks.BASALT, 2.0F, BlockSoundGroup.BASALT));
    public static final Block RHYOLITE = registerRock("rhyolite", rock(Blocks.GRANITE, 1.7F, BlockSoundGroup.STONE));
    public static final Block CONGLOMERATE = registerRock("conglomerate", rock(Blocks.COBBLESTONE, 1.6F, BlockSoundGroup.STONE));
    public static final Block BRECCIA = registerRock("breccia", rock(Blocks.COBBLESTONE, 1.7F, BlockSoundGroup.STONE));

    public static final Block LIMESTONE_STAIRS = registerRockVariant("limestone_stairs", new StairsBlock(LIMESTONE.getDefaultState(), rock(Blocks.STONE, 1.5F, BlockSoundGroup.STONE)));
    public static final Block LIMESTONE_SLAB = registerRockVariant("limestone_slab", new SlabBlock(rock(Blocks.STONE, 1.5F, BlockSoundGroup.STONE)));
    public static final Block LIMESTONE_WALL = registerRockVariant("limestone_wall", new WallBlock(rock(Blocks.STONE, 1.5F, BlockSoundGroup.STONE)));

    public static final Block CLAY_LOAM = registerEarth("clay_loam", earth(Blocks.DIRT, 0.6F, BlockSoundGroup.ROOTED_DIRT));
    public static final Block SANDY_LOAM = registerEarth("sandy_loam", earth(Blocks.COARSE_DIRT, 0.55F, BlockSoundGroup.GRAVEL));
    public static final Block SILTY_LOAM = registerEarth("silty_loam", earth(Blocks.DIRT, 0.55F, BlockSoundGroup.ROOTED_DIRT));
    public static final Block PEAT_SOIL = registerEarth("peat_soil", earth(Blocks.PODZOL, 0.45F, BlockSoundGroup.ROOTED_DIRT));
    public static final Block WET_MUD = registerEarth("wet_mud", earth(Blocks.MUD, 0.45F, BlockSoundGroup.MUD));
    public static final Block COMPACTED_MUD = registerEarth("compacted_mud", earth(Blocks.MUD, 0.7F, BlockSoundGroup.MUD));
    public static final Block BLUE_CLAY = registerEarth("blue_clay", earth(Blocks.CLAY, 0.6F, BlockSoundGroup.GRAVEL));
    public static final Block RED_CLAY = registerEarth("red_clay", earth(Blocks.CLAY, 0.6F, BlockSoundGroup.GRAVEL));

    private GeoStrataBlocks() {
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> ROCK_BLOCKS.forEach(entries::add));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
            ROCK_BLOCKS.forEach(entries::add);
            EARTH_BLOCKS.forEach(entries::add);
        });
        GeoStrata.LOGGER.info(
                "Registered {} rock blocks and {} earth blocks for the normal Fabric Loom runtime catalog",
                ROCK_BLOCKS.size(),
                EARTH_BLOCKS.size()
        );
    }

    public static int count() {
        return ROCK_BLOCKS.size() + EARTH_BLOCKS.size();
    }

    public static List<Block> allBlocks() {
        List<Block> blocks = new ArrayList<>(ROCK_BLOCKS.size() + EARTH_BLOCKS.size());
        blocks.addAll(ROCK_BLOCKS);
        blocks.addAll(EARTH_BLOCKS);
        return List.copyOf(blocks);
    }

    public static List<Block> rockBlocks() {
        return List.copyOf(ROCK_BLOCKS);
    }

    public static List<Block> earthBlocks() {
        return List.copyOf(EARTH_BLOCKS);
    }

    private static AbstractBlock.Settings rock(Block base, float hardness, BlockSoundGroup soundGroup) {
        return AbstractBlock.Settings.copy(base)
                .strength(hardness, 6.0F)
                .sounds(soundGroup)
                .requiresTool();
    }

    private static AbstractBlock.Settings earth(Block base, float hardness, BlockSoundGroup soundGroup) {
        return AbstractBlock.Settings.copy(base)
                .strength(hardness)
                .sounds(soundGroup);
    }

    private static Block registerRock(String name, AbstractBlock.Settings settings) {
        return registerRockVariant(name, new Block(settings));
    }

    private static Block registerRockVariant(String name, Block block) {
        block = register(name, block);
        ROCK_BLOCKS.add(block);
        return block;
    }

    private static Block registerEarth(String name, AbstractBlock.Settings settings) {
        Block block = register(name, settings);
        EARTH_BLOCKS.add(block);
        return block;
    }

    private static Block register(String name, AbstractBlock.Settings settings) {
        return register(name, new Block(settings));
    }

    private static Block register(String name, Block block) {
        block = Registry.register(Registries.BLOCK, GeoStrata.id(name), block);
        Registry.register(Registries.ITEM, GeoStrata.id(name), new BlockItem(block, new Item.Settings()));
        return block;
    }
}
