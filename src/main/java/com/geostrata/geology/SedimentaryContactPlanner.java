package com.geostrata.geology;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts an ordered sedimentary succession into normalized, deterministic
 * contact intervals without choosing Minecraft block heights or mutating a world.
 */
public final class SedimentaryContactPlanner {
    private static final long PHASE_SALT = 0x3C6EF372FE94F82BL;

    private SedimentaryContactPlanner() {
    }

    public static Plan plan(
            long worldSeed,
            int siteX,
            int siteZ,
            SedimentarySuccessions.Succession succession
    ) {
        if (succession == null) {
            throw new IllegalArgumentException("succession must not be null");
        }
        if (succession.beds().isEmpty()) {
            throw new IllegalArgumentException("succession must contain at least one bed");
        }

        double totalThickness = 0.0;
        for (SedimentarySuccessions.Bed bed : succession.beds()) {
            double thickness = bed.relativeThickness();
            if (!(thickness > 0.0) || !Double.isFinite(thickness)) {
                throw new IllegalArgumentException(
                        "succession " + succession.id() + " contains invalid relative thickness for " + bed.lithology()
                );
            }
            totalThickness += thickness;
        }
        if (!(totalThickness > 0.0) || !Double.isFinite(totalThickness)) {
            throw new IllegalArgumentException("succession total relative thickness must be finite and positive");
        }

        List<Interval> intervals = new ArrayList<>(succession.beds().size());
        double cumulative = 0.0;
        for (int index = 0; index < succession.beds().size(); index++) {
            SedimentarySuccessions.Bed bed = succession.beds().get(index);
            double lower = cumulative / totalThickness;
            cumulative += bed.relativeThickness();
            double upper = index == succession.beds().size() - 1
                    ? 1.0
                    : cumulative / totalThickness;
            intervals.add(new Interval(index, bed.lithology(), bed.relativeThickness(), lower, upper));
        }

        double phase = GeologyDeterminism.unitRoll(worldSeed, siteX, 0, siteZ, PHASE_SALT);
        return new Plan(
                succession.id(),
                succession.continuity(),
                totalThickness,
                phase,
                List.copyOf(intervals)
        );
    }

    public record Interval(
            int ordinal,
            String lithology,
            double relativeThickness,
            double lowerFraction,
            double upperFraction
    ) {
        public Interval {
            if (ordinal < 0) {
                throw new IllegalArgumentException("interval ordinal must be non-negative");
            }
            if (lithology == null || lithology.isBlank()) {
                throw new IllegalArgumentException("interval lithology must not be blank");
            }
            if (!(relativeThickness > 0.0) || !Double.isFinite(relativeThickness)) {
                throw new IllegalArgumentException("interval relative thickness must be finite and positive");
            }
            if (!Double.isFinite(lowerFraction)
                    || !Double.isFinite(upperFraction)
                    || lowerFraction < 0.0
                    || upperFraction > 1.0
                    || !(upperFraction > lowerFraction)) {
                throw new IllegalArgumentException("interval fractions must define an increasing subset of 0..1");
            }
        }
    }

    public record Plan(
            String successionId,
            String continuity,
            double totalRelativeThickness,
            double phase,
            List<Interval> intervals
    ) {
        public Plan {
            if (successionId == null || successionId.isBlank()) {
                throw new IllegalArgumentException("successionId must not be blank");
            }
            if (continuity == null || continuity.isBlank()) {
                throw new IllegalArgumentException("continuity must not be blank");
            }
            if (!(totalRelativeThickness > 0.0) || !Double.isFinite(totalRelativeThickness)) {
                throw new IllegalArgumentException("totalRelativeThickness must be finite and positive");
            }
            if (!Double.isFinite(phase) || phase < 0.0 || phase >= 1.0) {
                throw new IllegalArgumentException("phase must be within [0, 1)");
            }
            intervals = List.copyOf(intervals);
            if (intervals.isEmpty()) {
                throw new IllegalArgumentException("plan must contain at least one interval");
            }
            if (Double.compare(intervals.get(0).lowerFraction(), 0.0) != 0
                    || Double.compare(intervals.get(intervals.size() - 1).upperFraction(), 1.0) != 0) {
                throw new IllegalArgumentException("plan intervals must span exactly 0..1");
            }
            for (int index = 0; index < intervals.size(); index++) {
                Interval interval = intervals.get(index);
                if (interval.ordinal() != index) {
                    throw new IllegalArgumentException("plan interval ordinals must match lower-to-upper order");
                }
                if (index > 0
                        && Double.compare(intervals.get(index - 1).upperFraction(), interval.lowerFraction()) != 0) {
                    throw new IllegalArgumentException("plan intervals must be contiguous");
                }
            }
        }

        /**
         * Returns the bed owning a normalized stratigraphic coordinate in [0, 1).
         * Contacts are lower-inclusive and upper-exclusive, so an exact contact
         * belongs to the overlying (next) bed.
         */
        public Interval bedAt(double normalizedCoordinate) {
            if (!Double.isFinite(normalizedCoordinate)
                    || normalizedCoordinate < 0.0
                    || normalizedCoordinate >= 1.0) {
                throw new IllegalArgumentException("normalized coordinate must be within [0, 1)");
            }
            for (Interval interval : intervals) {
                if (normalizedCoordinate < interval.upperFraction()) {
                    return interval;
                }
            }
            throw new IllegalStateException("normalized contact plan does not cover coordinate " + normalizedCoordinate);
        }
    }
}
