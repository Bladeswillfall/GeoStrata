package com.geostrata.block;

import com.geostrata.geology.OreGrade;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.sound.BlockSoundGroup;

/** Defines GeoStrata block and block-item instances without owning loader registration timing. */
public final class GeoStrataBlocks {
    private static final List<Block> ROCK_BLOCKS = new ArrayList<>();
    private static final List<Block> EARTH_BLOCKS = new ArrayList<>();
    private static final List<Block> ORE_BLOCKS_LIST = new ArrayList<>();
    private static final Map<String, EnumMap<OreGrade, Block>> ORE_BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Block> BLOCKS_BY_NAME = new LinkedHashMap<>();
    private static final Map<String, Item> ITEMS_BY_NAME = new LinkedHashMap<>();

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

    public static final Block POOR_COAL_ORE = registerOre("poor_coal_ore", "coal", OreGrade.POOR, Blocks.COAL_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block MEDIUM_COAL_ORE = registerOre("medium_coal_ore", "coal", OreGrade.MEDIUM, Blocks.COAL_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block RICH_COAL_ORE = registerOre("rich_coal_ore", "coal", OreGrade.RICH, Blocks.COAL_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block MASSIVE_COAL_ORE = registerOre("massive_coal_ore", "coal", OreGrade.MASSIVE, Blocks.COAL_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block POOR_IRON_ORE = registerOre("poor_iron_ore", "iron", OreGrade.POOR, Blocks.IRON_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block MEDIUM_IRON_ORE = registerOre("medium_iron_ore", "iron", OreGrade.MEDIUM, Blocks.IRON_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block RICH_IRON_ORE = registerOre("rich_iron_ore", "iron", OreGrade.RICH, Blocks.IRON_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block MASSIVE_IRON_ORE = registerOre("massive_iron_ore", "iron", OreGrade.MASSIVE, Blocks.IRON_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block POOR_COPPER_ORE = registerOre("poor_copper_ore", "copper", OreGrade.POOR, Blocks.COPPER_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block MEDIUM_COPPER_ORE = registerOre("medium_copper_ore", "copper", OreGrade.MEDIUM, Blocks.COPPER_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block RICH_COPPER_ORE = registerOre("rich_copper_ore", "copper", OreGrade.RICH, Blocks.COPPER_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block MASSIVE_COPPER_ORE = registerOre("massive_copper_ore", "copper", OreGrade.MASSIVE, Blocks.COPPER_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block POOR_GOLD_ORE = registerOre("poor_gold_ore", "gold", OreGrade.POOR, Blocks.GOLD_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block MEDIUM_GOLD_ORE = registerOre("medium_gold_ore", "gold", OreGrade.MEDIUM, Blocks.GOLD_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block RICH_GOLD_ORE = registerOre("rich_gold_ore", "gold", OreGrade.RICH, Blocks.GOLD_ORE, 3.0F, BlockSoundGroup.STONE);
    public static final Block MASSIVE_GOLD_ORE = registerOre("massive_gold_ore", "gold", OreGrade.MASSIVE, Blocks.GOLD_ORE, 3.0F, BlockSoundGroup.STONE);

    private GeoStrataBlocks() {
    }

    public static int count() {
        return ROCK_BLOCKS.size() + EARTH_BLOCKS.size() + ORE_BLOCKS_LIST.size();
    }

    public static List<Block> rockBlocks() {
        return List.copyOf(ROCK_BLOCKS);
    }

    public static List<Block> allBlocks() {
        List<Block> blocks = new ArrayList<>(ROCK_BLOCKS.size() + EARTH_BLOCKS.size() + ORE_BLOCKS_LIST.size());
        blocks.addAll(ROCK_BLOCKS);
        blocks.addAll(EARTH_BLOCKS);
        blocks.addAll(ORE_BLOCKS_LIST);
        return List.copyOf(blocks);
    }

    public static Map<String, Block> blocksByName() {
        return Map.copyOf(BLOCKS_BY_NAME);
    }

    public static Map<String, Item> itemsByName() {
        return Map.copyOf(ITEMS_BY_NAME);
    }

    public static Block ore(String material, OreGrade grade) {
        Map<OreGrade, Block> grades = ORE_BLOCKS.get(material);
        if (grades == null || !grades.containsKey(grade)) {
            throw new IllegalArgumentException("unknown graded ore block: " + grade.id() + " " + material);
        }
        return grades.get(grade);
    }

    public static BlockState oreState(String material, OreGrade grade, String host) {
        Block block = ore(material, grade);
        if (!(block instanceof GradedOreBlock gradedOre)) {
            throw new IllegalStateException("graded ore registry contains non-graded block: " + material);
        }
        return gradedOre.withHost(host);
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

    private static Block registerOre(
            String name,
            String material,
            OreGrade grade,
            Block base,
            float hardness,
            BlockSoundGroup soundGroup
    ) {
        Block block = register(
                name,
                new GradedOreBlock(
                        material,
                        grade,
                        AbstractBlock.Settings.copy(base)
                                .strength(hardness, 3.0F)
                                .sounds(soundGroup)
                                .requiresTool()
                )
        );
        Block previous = ORE_BLOCKS
                .computeIfAbsent(material, ignored -> new EnumMap<>(OreGrade.class))
                .put(grade, block);
        if (previous != null) {
            throw new IllegalStateException("duplicate graded ore registration: " + grade.id() + " " + material);
        }
        ORE_BLOCKS_LIST.add(block);
        return block;
    }

    private static Block register(String name, AbstractBlock.Settings settings) {
        return register(name, new Block(settings));
    }

    private static Block register(String name, Block block) {
        if (BLOCKS_BY_NAME.putIfAbsent(name, block) != null) {
            throw new IllegalStateException("duplicate block definition: " + name);
        }
        if (ITEMS_BY_NAME.putIfAbsent(name, new BlockItem(block, new Item.Settings())) != null) {
            throw new IllegalStateException("duplicate block item definition: " + name);
        }
        return block;
    }
}
