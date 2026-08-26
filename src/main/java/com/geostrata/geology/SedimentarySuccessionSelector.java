package com.geostrata.geology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stable diagnostic selection of one sedimentary succession per geological province site. */
public final class SedimentarySuccessionSelector {
    private static final long SELECTION_SALT = 0xBB67AE8584CAA73BL;
    private static final double OUT_OF_CONTEXT_MULTIPLIER = 0.2;

    private SedimentarySuccessionSelector() {
    }

    public static Selection selectForSite(
            long worldSeed,
            GeologyProvince province,
            int siteX,
            int siteZ,
            GeologyProvinceProfiles.Snapshot profiles,
            SedimentarySuccessions.Snapshot successions
    ) {
        if (!profiles.loaded()) {
            throw new IllegalArgumentException("province profiles must be loaded before selecting a succession");
        }
        if (!successions.loaded()) {
            throw new IllegalArgumentException("sedimentary successions must be loaded before selection");
        }

        List<ScoredSuccession> scored = new ArrayList<>();
        double total = 0.0;
        for (SedimentarySuccessions.Succession succession : successions.successions()) {
            double score = score(province, succession, profiles);
            if (score > 0.0 && Double.isFinite(score)) {
                scored.add(new ScoredSuccession(succession, score));
                total += score;
            }
        }
        if (scored.isEmpty() || !(total > 0.0) || !Double.isFinite(total)) {
            throw new IllegalStateException("no selectable sedimentary succession for " + province.id());
        }

        scored.sort(Comparator.comparing(entry -> entry.succession().id()));
        double unitRoll = GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, SELECTION_SALT);
        double target = unitRoll * total;
        double cumulative = 0.0;
        for (ScoredSuccession entry : scored) {
            cumulative += entry.score();
            if (target < cumulative) {
                return new Selection(entry.succession(), entry.score(), total, unitRoll);
            }
        }

        ScoredSuccession last = scored.get(scored.size() - 1);
        return new Selection(last.succession(), last.score(), total, unitRoll);
    }

    static double score(
            GeologyProvince province,
            SedimentarySuccessions.Succession succession,
            GeologyProvinceProfiles.Snapshot profiles
    ) {
        double weightedSuitability = 0.0;
        double totalThickness = 0.0;
        for (SedimentarySuccessions.Bed bed : succession.beds()) {
            weightedSuitability += profiles.weight(province, bed.lithology()) * bed.relativeThickness();
            totalThickness += bed.relativeThickness();
        }
        double meanSuitability = weightedSuitability / totalThickness;
        return meanSuitability * (succession.matchesContext(province) ? 1.0 : OUT_OF_CONTEXT_MULTIPLIER);
    }

    private record ScoredSuccession(SedimentarySuccessions.Succession succession, double score) {
    }

    public record Selection(
            SedimentarySuccessions.Succession succession,
            double score,
            double totalScore,
            double unitRoll
    ) {
    }
}
