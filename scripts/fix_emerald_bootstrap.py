#!/usr/bin/env python3
"""Small follow-up fixes found by the full branch build."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"fix anchor missing in {path.relative_to(ROOT)}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


catalog = ROOT / "src/main/java/com/geostrata/geology/OreOccurrenceCatalog.java"
constructor_anchor = '''        public OreGrade capNaturalGrade(OreGrade grade) {
'''
compat_constructor = '''        public Occurrence(
                String id,
                String providerMod,
                String outputItem,
                java.util.List<String> hostLithologies,
                java.util.List<GeologyProvince> provinceContexts,
                java.util.List<String> depositStyles,
                java.util.Map<OreGrade, String> gradeBlocks
        ) {
            this(
                    id,
                    providerMod,
                    outputItem,
                    hostLithologies,
                    provinceContexts,
                    depositStyles,
                    TerrainFilter.none(),
                    OreGrade.MASSIVE,
                    gradeBlocks
            );
        }

        public OreGrade capNaturalGrade(OreGrade grade) {
'''
replace_once(catalog, constructor_anchor, compat_constructor)

feature = ROOT / "src/main/java/com/geostrata/worldgen/feature/OreDepositFeature.java"
qualification = '''                    GeologyProvince province = GeologyProvinceSampler.sample(
                            worldSeed,
                            proposal.anchorX(),
                            proposal.anchorZ()
                    ).province();
                    if (!occurrence.provinceContexts().contains(province)) {
                        continue;
                    }
                    if (!occurrence.terrainFilter().matches(ChunkGeneratorTerrainMorphologySampler.sample(
                            world.toServerWorld(),
                            proposal.anchorX(),
                            proposal.anchorZ()
                    ))) {
                        continue;
                    }

                    OreDepositGeometry.Body body = OreDepositGeometry.forProposal(worldSeed, proposal);
'''
replacement = '''                    if (!qualifiesLocation(world, worldSeed, occurrence, proposal)) {
                        continue;
                    }

                    OreDepositGeometry.Body body = OreDepositGeometry.forProposal(worldSeed, proposal);
'''
replace_once(feature, qualification, replacement)

helper_anchor = '''    private static OreDepositCandidatePlanner.Proposal proposalForCell(
'''
helper = '''    private static boolean qualifiesLocation(
            StructureWorldAccess world,
            long worldSeed,
            OreOccurrenceCatalog.Occurrence occurrence,
            OreDepositCandidatePlanner.Proposal proposal
    ) {
        GeologyProvince province = GeologyProvinceSampler.sample(
                worldSeed,
                proposal.anchorX(),
                proposal.anchorZ()
        ).province();
        return occurrence.provinceContexts().contains(province)
                && occurrence.terrainFilter().matches(ChunkGeneratorTerrainMorphologySampler.sample(
                        world.toServerWorld(),
                        proposal.anchorX(),
                        proposal.anchorZ()
                ));
    }

    private static OreDepositCandidatePlanner.Proposal proposalForCell(
'''
replace_once(feature, helper_anchor, helper)

contract = ROOT / "src/test/java/com/geostrata/geology/GeologyResourceContractTest.java"
replace_once(
    contract,
    'Set.of("coal", "iron", "copper", "gold"),',
    'Set.of("coal", "iron", "copper", "gold", "emerald"),',
)
replace_once(
    contract,
    '        assertEquals(0.008, core.oreExperiment().activationChance("gold"), 1.0e-12);\n',
    '        assertEquals(0.008, core.oreExperiment().activationChance("gold"), 1.0e-12);\n'
    '        assertEquals(0.004, core.oreExperiment().activationChance("emerald"), 1.0e-12);\n',
)
replace_once(
    contract,
    'Set.of("coal", "iron", "copper", "gold"),',
    'Set.of("coal", "iron", "copper", "gold", "emerald"),',
)
