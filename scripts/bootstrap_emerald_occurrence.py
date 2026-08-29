#!/usr/bin/env python3
"""One-shot branch bootstrap for the emerald occurrence and generated assets."""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
DATA = RES / "data/geostrata"
ASSETS = RES / "assets/geostrata"


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def add_unique(values: list[str], additions: list[str]) -> None:
    for value in additions:
        if value not in values:
            values.append(value)


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"bootstrap anchor missing in {path.relative_to(ROOT)}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def emerald_master() -> None:
    path = ASSETS / "textures/block/ore_source/master/emerald.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    palette = [
        (22, 124, 72),
        (28, 151, 84),
        (41, 177, 98),
        (70, 203, 121),
        (103, 220, 145),
    ]
    centers = ((3.2, 4.0, 4.2), (10.5, 4.5, 4.0), (6.5, 10.5, 4.8), (12.0, 11.5, 3.7))
    for y in range(16):
        for x in range(16):
            inside = any((x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2 for cx, cy, radius in centers)
            if not inside:
                continue
            index = (x * 11 + y * 17 + x * y * 3) % len(palette)
            red, green, blue = palette[index]
            if (x + 2 * y) % 11 == 0:
                red, green, blue = palette[-1]
            image.putpixel((x, y), (red, green, blue, 255))
    if sum(pixel[3] >= 32 for pixel in image.getdata()) < 118:
        raise SystemExit("generated emerald master does not contain enough dense pixels")
    image.save(path, optimize=True)


def loot_table(block: str, base_yield: int) -> dict:
    full = f"geostrata:{block}"
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{
                "type": "minecraft:alternatives",
                "children": [
                    {
                        "type": "minecraft:item",
                        "name": full,
                        "conditions": [{
                            "condition": "minecraft:match_tool",
                            "predicate": {"enchantments": [{
                                "enchantment": "minecraft:silk_touch",
                                "levels": {"min": 1},
                            }]},
                        }],
                        "functions": [{
                            "function": "minecraft:copy_state",
                            "block": full,
                            "properties": ["host"],
                        }],
                    },
                    {
                        "type": "minecraft:item",
                        "name": "minecraft:emerald",
                        "functions": [
                            {"function": "minecraft:set_count", "count": base_yield},
                            {
                                "function": "minecraft:apply_bonus",
                                "enchantment": "minecraft:fortune",
                                "formula": "minecraft:ore_drops",
                            },
                            {"function": "minecraft:explosion_decay"},
                        ],
                    },
                ],
            }],
        }],
    }


def main() -> None:
    occurrence_path = DATA / "geology/ore_occurrences.json"
    occurrence = load(occurrence_path)
    if not any(entry.get("id") == "emerald" for entry in occurrence["occurrences"]):
        occurrence["occurrences"].append({
            "id": "emerald",
            "providerMod": "minecraft",
            "outputItem": "minecraft:emerald",
            "hostLithologies": ["schist", "shale", "marble", "gneiss", "limestone", "slate"],
            "provinceContexts": ["orogenic_belt"],
            "terrainFilter": {
                "minimumReliefBlocks": 24,
                "requirePositiveProminence": True,
            },
            "depositStyles": ["vein"],
            "maximumNaturalGrade": "rich",
            "gradeBlocks": {
                "poor": "geostrata:poor_emerald_ore",
                "medium": "geostrata:medium_emerald_ore",
                "rich": "geostrata:rich_emerald_ore",
                "massive": "geostrata:massive_emerald_ore",
            },
        })
    write(occurrence_path, occurrence)

    experiment_path = DATA / "geology/ore_deposit_experiment.json"
    experiment = load(experiment_path)
    experiment["activationChancePerCandidate"]["emerald"] = 0.004
    write(experiment_path, experiment)

    matrix_path = DATA / "materials/ore_texture_matrix.json"
    matrix = load(matrix_path)
    matrix["ores"]["emerald"] = {
        "defaultHost": "schist",
        "validHosts": ["schist", "shale", "marble", "gneiss", "limestone", "slate"],
    }
    write(matrix_path, matrix)

    profiles_path = DATA / "materials/material_profiles.json"
    profiles = load(profiles_path)
    if not any(entry.get("id") == "emerald_ore" for entry in profiles["materials"]):
        profiles["materials"].append({
            "id": "emerald_ore",
            "primaryBlock": "geostrata:poor_emerald_ore",
            "derivedBlocks": [
                "geostrata:medium_emerald_ore",
                "geostrata:rich_emerald_ore",
                "geostrata:massive_emerald_ore",
            ],
            "family": "ore",
            "compatibilityRole": "geostrata:ore/emerald",
            "semanticTags": ["geostrata:ores"],
            "gameplay": {
                "breaking": {
                    "copyFrom": "minecraft:emerald_ore",
                    "hardness": 3.0,
                    "blastResistance": 3.0,
                    "requiresTool": True,
                    "mineableWith": "pickaxe",
                    "minimumToolTier": "iron",
                    "soundGroup": "stone",
                },
                "cultivation": {"status": "not_applicable"},
                "oreEconomy": {
                    "material": "emerald",
                    "outputItem": "minecraft:emerald",
                    "gradeOrder": ["poor", "medium", "rich", "massive"],
                    "source": "geostrata:geology/ore_occurrences",
                },
            },
            "assets": {
                "textureMatrix": "geostrata:materials/ore_texture_matrix",
                "material": "emerald",
            },
        })
    write(profiles_path, profiles)

    block_ids = [f"geostrata:{grade}_emerald_ore" for grade in ("poor", "medium", "rich", "massive")]
    for relative in (
        "tags/blocks/ores.json",
        "../minecraft/tags/blocks/mineable/pickaxe.json",
        "../minecraft/tags/blocks/needs_iron_tool.json",
    ):
        tag_path = DATA / relative
        tag = load(tag_path)
        add_unique(tag["values"], block_ids)
        write(tag_path, tag)

    lang_path = ASSETS / "lang/en_us.json"
    lang = load(lang_path)
    lang.update({
        "block.geostrata.poor_emerald_ore": "Poor Emerald Ore",
        "block.geostrata.medium_emerald_ore": "Medium Emerald Ore",
        "block.geostrata.rich_emerald_ore": "Rich Emerald Ore",
        "block.geostrata.massive_emerald_ore": "Massive Emerald Ore",
    })
    write(lang_path, lang)

    for grade, yield_count in (("poor", 1), ("medium", 2), ("rich", 4), ("massive", 8)):
        write(DATA / f"loot_tables/blocks/{grade}_emerald_ore.json", loot_table(f"{grade}_emerald_ore", yield_count))

    blocks = ROOT / "src/main/java/com/geostrata/block/GeoStrataBlocks.java"
    gold_line = '    public static final Block MASSIVE_GOLD_ORE = registerOre("massive_gold_ore", "gold", OreGrade.MASSIVE, Blocks.GOLD_ORE, 3.0F, BlockSoundGroup.STONE);\n'
    emerald_lines = gold_line + (
        '    public static final Block POOR_EMERALD_ORE = registerOre("poor_emerald_ore", "emerald", OreGrade.POOR, Blocks.EMERALD_ORE, 3.0F, BlockSoundGroup.STONE);\n'
        '    public static final Block MEDIUM_EMERALD_ORE = registerOre("medium_emerald_ore", "emerald", OreGrade.MEDIUM, Blocks.EMERALD_ORE, 3.0F, BlockSoundGroup.STONE);\n'
        '    public static final Block RICH_EMERALD_ORE = registerOre("rich_emerald_ore", "emerald", OreGrade.RICH, Blocks.EMERALD_ORE, 3.0F, BlockSoundGroup.STONE);\n'
        '    public static final Block MASSIVE_EMERALD_ORE = registerOre("massive_emerald_ore", "emerald", OreGrade.MASSIVE, Blocks.EMERALD_ORE, 3.0F, BlockSoundGroup.STONE);\n'
    )
    if "POOR_EMERALD_ORE" not in blocks.read_text(encoding="utf-8"):
        replace_once(blocks, gold_line, emerald_lines)

    ore_host = ROOT / "src/main/java/com/geostrata/block/OreHost.java"
    if 'case "emerald" -> SCHIST;' not in ore_host.read_text(encoding="utf-8"):
        replace_once(ore_host, '            case "gold" -> SLATE;\n', '            case "gold" -> SLATE;\n            case "emerald" -> SCHIST;\n')

    catalog = ROOT / "src/main/java/com/geostrata/geology/OreOccurrenceCatalog.java"
    text = catalog.read_text(encoding="utf-8")
    if "TerrainFilter terrainFilter" not in text:
        text = text.replace(
            '        List<GeologyProvince> contexts = parseContexts(id, requiredArray(object, "provinceContexts"));\n        List<String> styles = stringList(requiredArray(object, "depositStyles"), id + " depositStyles");\n        requireDepositStyles(id, styles);\n        Map<OreGrade, String> gradeBlocks = parseGradeBlocks(id, requiredObject(object, "gradeBlocks"));\n        return new Occurrence(id, providerMod, outputItem, hosts, contexts, styles, gradeBlocks);\n',
            '        List<GeologyProvince> contexts = parseContexts(id, requiredArray(object, "provinceContexts"));\n        TerrainFilter terrainFilter = parseTerrainFilter(id, object);\n        List<String> styles = stringList(requiredArray(object, "depositStyles"), id + " depositStyles");\n        requireDepositStyles(id, styles);\n        OreGrade maximumNaturalGrade = parseMaximumNaturalGrade(id, object);\n        Map<OreGrade, String> gradeBlocks = parseGradeBlocks(id, requiredObject(object, "gradeBlocks"));\n        return new Occurrence(id, providerMod, outputItem, hosts, contexts, styles, terrainFilter, maximumNaturalGrade, gradeBlocks);\n'
        )
        marker = '    private static Map<OreGrade, String> parseGradeBlocks(String material, JsonObject object) {\n'
        helpers = '''    private static TerrainFilter parseTerrainFilter(String material, JsonObject occurrence) {\n        JsonElement raw = occurrence.get("terrainFilter");\n        if (raw == null) {\n            return TerrainFilter.none();\n        }\n        if (!raw.isJsonObject()) {\n            throw new IllegalArgumentException(material + " terrainFilter must be an object");\n        }\n        JsonObject object = raw.getAsJsonObject();\n        int minimumReliefBlocks = requireInt(object, "minimumReliefBlocks");\n        boolean requirePositiveProminence = requireBoolean(object, "requirePositiveProminence");\n        if (minimumReliefBlocks < 0) {\n            throw new IllegalArgumentException(material + " minimumTerrainReliefBlocks must not be negative");\n        }\n        return new TerrainFilter(minimumReliefBlocks, requirePositiveProminence);\n    }\n\n    private static OreGrade parseMaximumNaturalGrade(String material, JsonObject object) {\n        JsonElement raw = object.get("maximumNaturalGrade");\n        if (raw == null) {\n            return OreGrade.MASSIVE;\n        }\n        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {\n            throw new IllegalArgumentException(material + " maximumNaturalGrade must be a string");\n        }\n        String id = raw.getAsString();\n        return ECONOMIC_GRADES.stream()\n                .filter(grade -> grade.id().equals(id))\n                .findFirst()\n                .orElseThrow(() -> new IllegalArgumentException(material + " uses unknown maximumNaturalGrade " + id));\n    }\n\n'''
        if marker not in text:
            raise SystemExit("could not insert ore occurrence helpers")
        text = text.replace(marker, helpers + marker, 1)
        old_record = '''    public record Occurrence(\n            String id,\n            String providerMod,\n            String outputItem,\n            List<String> hostLithologies,\n            List<GeologyProvince> provinceContexts,\n            List<String> depositStyles,\n            Map<OreGrade, String> gradeBlocks\n    ) {\n        public Occurrence {\n            hostLithologies = List.copyOf(hostLithologies);\n            provinceContexts = List.copyOf(provinceContexts);\n            depositStyles = List.copyOf(depositStyles);\n            EnumMap<OreGrade, String> copiedBlocks = new EnumMap<>(OreGrade.class);\n            copiedBlocks.putAll(gradeBlocks);\n            gradeBlocks = Collections.unmodifiableMap(copiedBlocks);\n        }\n    }\n'''
        new_record = '''    public record TerrainFilter(int minimumReliefBlocks, boolean requirePositiveProminence) {\n        public static TerrainFilter none() {\n            return new TerrainFilter(0, false);\n        }\n\n        public boolean matches(TerrainMorphologySample sample) {\n            if (sample == null || sample.relief() < minimumReliefBlocks) {\n                return false;\n            }\n            return !requirePositiveProminence || sample.prominence() > 0.0;\n        }\n    }\n\n    public record Occurrence(\n            String id,\n            String providerMod,\n            String outputItem,\n            List<String> hostLithologies,\n            List<GeologyProvince> provinceContexts,\n            List<String> depositStyles,\n            TerrainFilter terrainFilter,\n            OreGrade maximumNaturalGrade,\n            Map<OreGrade, String> gradeBlocks\n    ) {\n        public Occurrence {\n            hostLithologies = List.copyOf(hostLithologies);\n            provinceContexts = List.copyOf(provinceContexts);\n            depositStyles = List.copyOf(depositStyles);\n            if (terrainFilter == null || maximumNaturalGrade == null) {\n                throw new IllegalArgumentException("ore terrain filter and maximum natural grade must not be null");\n            }\n            EnumMap<OreGrade, String> copiedBlocks = new EnumMap<>(OreGrade.class);\n            copiedBlocks.putAll(gradeBlocks);\n            gradeBlocks = Collections.unmodifiableMap(copiedBlocks);\n        }\n\n        public OreGrade capNaturalGrade(OreGrade grade) {\n            if (grade == null) {\n                throw new IllegalArgumentException("ore grade must not be null");\n            }\n            return grade.ordinal() > maximumNaturalGrade.ordinal() ? maximumNaturalGrade : grade;\n        }\n    }\n'''
        if old_record not in text:
            raise SystemExit("could not replace ore occurrence record")
        text = text.replace(old_record, new_record, 1)
        catalog.write_text(text, encoding="utf-8")

    feature = ROOT / "src/main/java/com/geostrata/worldgen/feature/OreDepositFeature.java"
    text = feature.read_text(encoding="utf-8")
    if "ChunkGeneratorTerrainMorphologySampler" not in text:
        text = text.replace(
            'import com.geostrata.geology.CorrelatedSedimentaryRuntime;\n',
            'import com.geostrata.geology.CorrelatedSedimentaryRuntime;\nimport com.geostrata.geology.ChunkGeneratorTerrainMorphologySampler;\n',
            1,
        )
    province_check = '''                    if (!occurrence.provinceContexts().contains(province)) {\n                        continue;\n                    }\n\n                    OreDepositGeometry.Body body = OreDepositGeometry.forProposal(worldSeed, proposal);\n'''
    terrain_check = '''                    if (!occurrence.provinceContexts().contains(province)) {\n                        continue;\n                    }\n                    if (!occurrence.terrainFilter().matches(ChunkGeneratorTerrainMorphologySampler.sample(\n                            world.toServerWorld(),\n                            proposal.anchorX(),\n                            proposal.anchorZ()\n                    ))) {\n                        continue;\n                    }\n\n                    OreDepositGeometry.Body body = OreDepositGeometry.forProposal(worldSeed, proposal);\n'''
    if "occurrence.terrainFilter().matches" not in text:
        if province_check not in text:
            raise SystemExit("could not insert ore terrain filter")
        text = text.replace(province_check, terrain_check, 1)
    if "occurrence.capNaturalGrade(sample.grade())" not in text:
        text = text.replace(
            'GeoStrataBlocks.oreState(occurrence.id(), sample.grade(), host)',
            'GeoStrataBlocks.oreState(occurrence.id(), occurrence.capNaturalGrade(sample.grade()), host)',
            1,
        )
    feature.write_text(text, encoding="utf-8")

    test_path = ROOT / "src/test/java/com/geostrata/geology/OreOccurrenceConstraintTest.java"
    test_path.write_text('''package com.geostrata.geology;\n\nimport org.junit.jupiter.api.Test;\n\nimport java.util.List;\nimport java.util.Map;\n\nimport static org.junit.jupiter.api.Assertions.assertEquals;\nimport static org.junit.jupiter.api.Assertions.assertFalse;\nimport static org.junit.jupiter.api.Assertions.assertTrue;\n\nclass OreOccurrenceConstraintTest {\n    @Test\n    void terrainFilterUsesReliefAndPositiveProminence() {\n        OreOccurrenceCatalog.TerrainFilter filter = new OreOccurrenceCatalog.TerrainFilter(24, true);\n        assertFalse(filter.matches(new TerrainMorphologySample(100, 0, 0, 23, 10)));\n        assertFalse(filter.matches(new TerrainMorphologySample(100, 0, 0, 30, -1)));\n        assertTrue(filter.matches(new TerrainMorphologySample(100, 0, 0, 24, 1)));\n    }\n\n    @Test\n    void naturalGradeCapKeepsEmeraldOutOfMassiveGeneration() {\n        OreOccurrenceCatalog.Occurrence occurrence = new OreOccurrenceCatalog.Occurrence(\n                "emerald",\n                "minecraft",\n                "minecraft:emerald",\n                List.of("schist"),\n                List.of(GeologyProvince.OROGENIC_BELT),\n                List.of("vein"),\n                OreOccurrenceCatalog.TerrainFilter.none(),\n                OreGrade.RICH,\n                Map.of()\n        );\n        assertEquals(OreGrade.MEDIUM, occurrence.capNaturalGrade(OreGrade.MEDIUM));\n        assertEquals(OreGrade.RICH, occurrence.capNaturalGrade(OreGrade.MASSIVE));\n    }\n}\n''', encoding="utf-8")

    doc_path = ROOT / "docs/ORE_SYSTEM.md"
    doc = doc_path.read_text(encoding="utf-8")
    doc = doc.replace(
        "The catalog currently defines the phase-one materials coal, iron, copper and\ngold.",
        "The catalog currently defines coal, iron, copper, gold and emerald. Emerald is the first\noccurrence to add an explicit terrain filter and natural-grade ceiling.",
    )
    doc = doc.replace(
        "| Gold | 0.8% |\n",
        "| Gold | 0.8% |\n| Emerald | 0.4% |\n",
    )
    insertion = '''\n### Emerald occurrence\n\nEmerald remains tied to mountain/orogenic gameplay without a bespoke emerald prospecting\nsubsystem. Its occurrence is restricted to `orogenic_belt`, requires at least 24 blocks of\ncoarse 128-block-scale terrain relief with positive prominence, and uses the shared `vein`\nbody. This means terrain tells the player which mountain belts are worth exploring while the\nlocal host rock clips the vein naturally.\n\nValid hosts are, in preferred geological order, schist, shale, marble, gneiss, limestone and\nslate. The list deliberately excludes quartzite, igneous rocks and coarse clastics rather than\nturning rare edge cases into extra gameplay rules. Existing parent-aware metamorphism already\nlets shale/carbonate systems continue into slate/schist/gneiss and marble in orogenic chunks.\n\nThe shared grade contract still registers Poor/Medium/Rich/Massive blocks for consistent loot,\nSilk Touch and assets, but emerald declares `maximumNaturalGrade=rich`. Massive emerald is thus\nasset/economy compatible but is not placed by ordinary generation. If a future shared fault or\nstructural-intersection field justifies exceptional massive pockets, that can lift the cap in a\ngeneric structural rule rather than an emerald-only special case.\n\nThe current `vein` geometry represents fracture-controlled mineralization, but it is not yet\nbound to a first-class shared fault/shear field because GeoStrata does not currently expose one.\nThat binding should be added when faults become a shared geological primitive; emerald should\nnot invent a parallel fault simulator just for itself.\n'''
    if "### Emerald occurrence" not in doc:
        doc = doc.replace("\n## Grade contract\n", insertion + "\n## Grade contract\n", 1)
    doc_path.write_text(doc, encoding="utf-8")

    geology_doc = ROOT / "docs/GEOLOGY_MODEL.md"
    geology = geology_doc.read_text(encoding="utf-8")
    geology = geology.replace(
        "`ore_occurrences.json` defines the phase-one coal, iron, copper and gold geological contracts:",
        "`ore_occurrences.json` defines the coal, iron, copper, gold and emerald geological contracts:",
    )
    geology_doc.write_text(geology, encoding="utf-8")

    emerald_master()


if __name__ == "__main__":
    main()
